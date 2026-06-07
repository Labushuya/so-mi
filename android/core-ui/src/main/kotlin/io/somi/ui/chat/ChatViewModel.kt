package io.somi.ui.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.somi.common.chat.ChatState
import io.somi.common.chat.Message
import io.somi.data.ChatRepository
import io.somi.data.DeviceInfo
import io.somi.data.HardwareDetector
import io.somi.data.ModelCatalog
import io.somi.data.ModelManager
import io.somi.data.ModelManifest
import io.somi.data.ModelStatus
import io.somi.data.ModelStorage
import io.somi.data.Recommendation
import io.somi.data.recommendModelTier
import io.somi.llm.LlamaContext
import io.somi.llm.SoulPromptLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Phase-2.6 ChatViewModel — orchestrates the full lifecycle from boot
 * (hardware probe + model recommendation) through download → load →
 * generate.
 *
 * **State design.** Older versions used a single sealed [ChatState] flow
 * where [ChatState.Error] was an exclusive variant. That collapsed two
 * orthogonal concerns — "where in the lifecycle are we" and "is there a
 * transient error to surface" — into one mode-switch. Result: a recoverable
 * generation failure put the entire UI into "error mode" and (because
 * Composer was gated on `state is ChatState.Idle`) silently disabled the
 * input field. The user could see the message but not type a new one.
 *
 * The fix is structural. This ViewModel keeps three private state flows:
 *  - [_lifecycle]: NoModel | Downloading | Loading | Ready
 *  - [_generation]: Idle | Streaming(promptId, partial)
 *  - [_errorBanner]: TransientError? — overlay, never blocks input
 *
 * The public [state] is a derived [combine]: when there is an error we
 * wrap the lifecycle in [ChatState.WithBanner] so the UI can render the
 * banner above whatever screen is showing. Routing and composer-enabled
 * read from the *inner* state — which is why a generation failure can
 * never lock the composer again.
 *
 * **Threading invariants** (load-bearing, see [LlamaContext] kdoc):
 *  - Every native llama call runs on [llamaDispatcher], a single-thread
 *    dispatcher carved out of [Dispatchers.IO]. llama.cpp's `llama_decode`
 *    is blocking and not thread-safe.
 *  - The hardware probe runs on [Dispatchers.Default] (the GLES roundtrip
 *    is 10–50 ms — fine for the main thread but sloppy).
 *
 * Generation cancellation: a single [generationJob] is held so
 * [cancelGeneration] can cancel just the in-flight stream without tearing
 * down the model itself. On cooperative cancellation we still commit the
 * partial text as the final ASSISTANT message — that's the "stop typing"
 * UX, not a "throw away the answer" UX.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llama: LlamaContext,
    private val soulPromptLoader: SoulPromptLoader,
    private val hardwareDetector: HardwareDetector,
    private val modelManager: ModelManager,
    private val modelStorage: ModelStorage,
    private val chatRepository: ChatRepository,
    @ApplicationContext private val appContext: Context,
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {

    // --- Orthogonal state axes (private) --------------------------------

    private enum class Lifecycle { NoModel, Downloading, Loading, Ready }

    private data class DownloadProgress(val bytesDownloaded: Long, val bytesTotal: Long)

    private data class GenerationStream(val promptId: Long, val partial: String)

    private data class TransientError(
        val message: String,
        val retryable: Boolean = true,
        val cause: Throwable? = null,
    )

    private val _lifecycle = MutableStateFlow(Lifecycle.Loading)
    private val _download = MutableStateFlow<DownloadProgress?>(null)
    private val _generation = MutableStateFlow<GenerationStream?>(null)
    private val _errorBanner = MutableStateFlow<TransientError?>(null)

    // --- Public state ----------------------------------------------------

    /**
     * Derived UI state. The inner lifecycle drives routing and composer-
     * enabled; an error banner (if present) is wrapped via
     * [ChatState.WithBanner] without blocking input.
     */
    val state: StateFlow<ChatState> = combine(
        _lifecycle, _download, _generation, _errorBanner,
    ) { lc, dl, gen, err ->
        val inner: ChatState = when (lc) {
            Lifecycle.NoModel -> ChatState.NoModelInstalled
            Lifecycle.Downloading -> ChatState.DownloadingModel(
                bytesDownloaded = dl?.bytesDownloaded ?: 0L,
                bytesTotal = dl?.bytesTotal ?: -1L,
            )
            Lifecycle.Loading -> ChatState.LoadingModel
            Lifecycle.Ready -> if (gen != null) {
                ChatState.Generating(promptId = gen.promptId, partialResponse = gen.partial)
            } else {
                ChatState.Idle
            }
        }
        if (err != null) ChatState.WithBanner(
            inner = inner,
            message = err.message,
            retryable = err.retryable,
            cause = err.cause,
        )
        else inner
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ChatState.LoadingModel,
    )

    /**
     * Persisted history, replayed by Room on every collect. Eagerly
     * shared so the first frame after process recreate sees the
     * already-loaded list rather than `emptyList()`.
     */
    val messages: StateFlow<List<Message>> = chatRepository.observeMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    private val _boot = MutableStateFlow<BootSnapshot?>(null)
    val boot: StateFlow<BootSnapshot?> = _boot.asStateFlow()

    private val _selectedModel = MutableStateFlow<ModelManifest?>(null)
    val selectedModel: StateFlow<ModelManifest?> = _selectedModel.asStateFlow()

    /**
     * Reactive on-disk model inventory for the Settings screen. Auto-
     * refreshed on init and after every [deleteModelInstance]. Settings
     * collects via collectAsStateWithLifecycle — no refresh-key hack.
     */
    private val _instances = MutableStateFlow<List<ModelStorage.ModelInstance>>(emptyList())
    val instances: StateFlow<List<ModelStorage.ModelInstance>> = _instances.asStateFlow()

    // --- Internal --------------------------------------------------------

    /**
     * Single-thread dispatcher for ALL native llama calls. llama.cpp has
     * thread-affinity rules around the KV cache; pinning to one worker
     * thread keeps `llama_decode` invocations serialised without us
     * having to roll a manual mutex.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** Lazy-cached soul.md text. Loaded once at boot. */
    @Volatile
    private var cachedSoul: String? = null

    /** Currently running generation, if any. */
    private var generationJob: Job? = null

    /** Ongoing download-observer job; cancelled on retry/cancel. */
    private var downloadObserveJob: Job? = null

    init {
        // Hardware probe + soul.md cache + recovery from disk happen in
        // parallel where they can. Bounded by the probe's GLES roundtrip
        // (~10–50 ms) and the assets read.
        viewModelScope.launch(Dispatchers.Default) {
            val info = hardwareDetector.snapshot()
            val rec = recommendModelTier(info)
            _boot.value = BootSnapshot(deviceInfo = info, recommendation = rec)

            val auto = ModelCatalog.forTier(rec.auto)
            if (auto != null) _selectedModel.value = auto

            cachedSoul = runCatching { soulPromptLoader.load() }
                .onFailure { Log.w(TAG, "soul.md load failed", it) }
                .getOrNull()

            refreshInstances()

            // Resolve initial state from disk. Use rescueSideload here so
            // a manually-dropped GGUF in modelsDir/ gets adopted into the
            // canonical /<id>/ subdir on first launch.
            val selected = _selectedModel.value
            if (selected != null && modelStorage.rescueSideload(selected)) {
                _lifecycle.value = Lifecycle.Loading
                launchLoadModel(selected)
            } else {
                _lifecycle.value = Lifecycle.NoModel
            }
        }
    }

    // --- Public API ------------------------------------------------------

    /**
     * User overrode the auto-selected tier from the picker. If the new
     * model is already installed we transition straight into Loading;
     * otherwise we sit in NoModel until the user taps download.
     */
    fun selectModel(manifest: ModelManifest) {
        _selectedModel.value = manifest
        downloadObserveJob?.cancel()
        downloadObserveJob = null
        _errorBanner.value = null

        if (modelStorage.rescueSideload(manifest)) {
            _lifecycle.value = Lifecycle.Loading
            launchLoadModel(manifest)
        } else {
            _lifecycle.value = Lifecycle.NoModel
        }
    }

    /**
     * Kick off a download for the currently-selected model and start
     * mirroring [ModelManager.observe] into [_lifecycle]/[_download].
     * On Installed we automatically chain into the load path — the user
     * does not need to tap a second button.
     */
    fun startDownload(wifiOnly: Boolean) {
        val manifest = _selectedModel.value ?: run {
            surfaceError("Kein Modell ausgewählt. Wähl eins aus der Liste.", retryable = false)
            return
        }
        Log.i(TAG, "startDownload(${manifest.id}, wifiOnly=$wifiOnly)")
        _errorBanner.value = null
        _lifecycle.value = Lifecycle.Downloading
        _download.value = DownloadProgress(0L, manifest.totalSizeBytes)

        modelManager.startDownload(manifest, wifiOnly = wifiOnly)
        downloadObserveJob?.cancel()
        downloadObserveJob = viewModelScope.launch {
            modelManager.observe(manifest)
                .onEach { status ->
                    Log.d(TAG, "downloadStatus = ${status::class.simpleName}")
                    applyDownloadStatus(manifest, status)
                }
                .collectLatest { /* terminal sink */ }
        }
    }

    /** Cancel an in-flight download; the .part file stays for resume. */
    fun cancelDownload() {
        val manifest = _selectedModel.value ?: return
        modelManager.cancel(manifest)
        downloadObserveJob?.cancel()
        downloadObserveJob = null
        _download.value = null
        _lifecycle.value = Lifecycle.NoModel
    }

    /**
     * Submit a user turn. Only legal when the lifecycle is Ready and no
     * generation is already running. A WithBanner overlay does NOT block
     * submission — the banner is just decoration.
     */
    fun submit(userText: String) {
        if (_lifecycle.value != Lifecycle.Ready) {
            Log.w(TAG, "submit() ignored — lifecycle=${_lifecycle.value}")
            return
        }
        if (_generation.value != null) {
            Log.w(TAG, "submit() ignored — generation already running")
            return
        }
        val text = userText.trim()
        if (text.isEmpty()) return

        // Tapping send dismisses any leftover banner.
        _errorBanner.value = null

        // Phase 3a: persist USER row first; the Room-assigned id becomes
        // the streaming-bubble promptId.
        generationJob = viewModelScope.launch {
            val promptId = chatRepository.appendUser(text)
            runGeneration(promptId, text)
        }
    }

    /**
     * Stop the current generation. Commits whatever partial text we have
     * as the final ASSISTANT message. Model + KV cache stay live.
     */
    fun cancelGeneration() {
        generationJob?.cancel()
    }

    /** Dismiss the error banner without taking any other action. */
    fun dismissError() {
        _errorBanner.value = null
    }

    /**
     * Recover from an error or stuck state. Tries the cheapest action
     * that could plausibly fix the problem: re-resolve the on-disk
     * picture for the currently-selected model and either load or fall
     * back to the picker.
     */
    fun retry() {
        _errorBanner.value = null
        val manifest = _selectedModel.value
        if (manifest != null && modelStorage.rescueSideload(manifest)) {
            _lifecycle.value = Lifecycle.Loading
            launchLoadModel(manifest)
        } else {
            _lifecycle.value = Lifecycle.NoModel
        }
    }

    /**
     * Settings: delete a specific instance directory. If the deleted
     * instance is the currently-active model, also unloads the engine
     * and transitions to NoModel so the picker returns immediately —
     * no process restart needed.
     */
    fun deleteModelInstance(instance: ModelStorage.ModelInstance) {
        viewModelScope.launch {
            val ok = modelStorage.deleteInstance(instance)
            if (!ok) {
                surfaceError(
                    "Löschen fehlgeschlagen — wahrscheinlich blockiert das System die Datei. Force-Stop und nochmal.",
                    retryable = false,
                )
            }
            // Refresh the list either way: a partial wipe should still
            // reflect the new on-disk truth.
            refreshInstances()

            val active = _selectedModel.value
            if (active?.id == instance.manifestId && _lifecycle.value == Lifecycle.Ready) {
                Log.i(TAG, "deleted active model — unloading engine and returning to picker")
                runCatching {
                    withContext(llamaDispatcher) { llama.close() }
                }.onFailure { Log.w(TAG, "llama.close() during delete failed", it) }
                generationJob?.cancel()
                _generation.value = null
                _lifecycle.value = Lifecycle.NoModel
            }
        }
    }

    /** Force-refresh the on-disk model inventory. */
    fun refreshInstances() {
        _instances.value = modelStorage.findAllInstances(ModelCatalog.ALL)
    }

    // --- Internal: model lifecycle --------------------------------------

    private fun launchLoadModel(manifest: ModelManifest) {
        viewModelScope.launch {
            try {
                loadModel(manifest)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "load failed", t)
                _lifecycle.value = Lifecycle.NoModel
                surfaceError(
                    "Modell ließ sich nicht laden. Speicher voll? Probier nochmal.",
                    cause = t,
                )
            }
        }
    }

    /**
     * Load the GGUF and install soul.md as the system prompt. No warm-pass:
     * the first user turn pays cold-prompt latency, which is expected from
     * a local LLM. All native calls funnel through [llamaDispatcher].
     */
    private suspend fun loadModel(manifest: ModelManifest) {
        val mainFile = modelStorage.mainFileFor(manifest) ?: error(
            "rescueSideload lied: ${manifest.id}",
        )
        val soul = cachedSoul ?: run {
            val s = soulPromptLoader.load()
            cachedSoul = s
            s
        }

        Log.i(TAG, "loadModel start id=${manifest.id} file=${mainFile.name} size=${mainFile.length()}")
        try {
            kotlinx.coroutines.withTimeout(LOAD_TIMEOUT_MS) {
                withContext(llamaDispatcher) {
                    val t0 = System.nanoTime()
                    llama.load(mainFile)
                    val t1 = System.nanoTime()
                    Log.i(TAG, "native load done in ${(t1 - t0) / 1_000_000} ms")

                    val systemText = soul.take(MAX_SYSTEM_PROMPT_CHARS)
                    Log.i(TAG, "native setSystemPrompt(${systemText.length} chars) …")
                    llama.setSystemPrompt(systemText)
                    val t2 = System.nanoTime()
                    Log.i(TAG, "native setSystemPrompt done in ${(t2 - t1) / 1_000_000} ms")
                }
            }
        } catch (te: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "loadModel timed out after $LOAD_TIMEOUT_MS ms", te)
            _lifecycle.value = Lifecycle.NoModel
            surfaceError(
                "Modell-Laden hängt. Force-Stop App + nochmal — wenn's wieder hängt: kleineres Modell.",
                cause = te,
            )
            return
        }

        Log.i(TAG, "loadModel done — entering Ready")
        _lifecycle.value = Lifecycle.Ready
    }

    // --- Internal: generation -------------------------------------------

    private suspend fun runGeneration(promptId: Long, userText: String) {
        // Open the streaming overlay; lifecycle stays Ready throughout.
        _generation.value = GenerationStream(promptId = promptId, partial = "")

        val partial = StringBuilder()
        var completed = false

        try {
            withContext(llamaDispatcher) {
                llama.generate(userText, maxTokens = MAX_TOKENS)
                    .cancellable()
                    .collect { chunk ->
                        partial.append(chunk)
                        _generation.value = GenerationStream(
                            promptId = promptId,
                            partial = partial.toString(),
                        )
                    }
            }
            completed = true
        } catch (ce: CancellationException) {
            Log.i(TAG, "generation cancelled by user @ promptId=$promptId")
        } catch (t: Throwable) {
            Log.e(TAG, "generation failed @ promptId=$promptId", t)
            // Banner on top, lifecycle stays Ready, composer stays usable.
            surfaceError("Hat nicht geklappt. Versuch's nochmal.", cause = t)
        } finally {
            generationJob = null
        }

        // Commit the final ASSISTANT message — both natural completion
        // and user-cancellation paths land here. If we got zero chunks
        // before cancellation, skip committing an empty bubble.
        val finalText = partial.toString()
        if (finalText.isNotEmpty() || completed) {
            chatRepository.appendAssistant(finalText)
        }
        _generation.value = null
    }

    // --- Internal: download status mirror -------------------------------

    private fun applyDownloadStatus(manifest: ModelManifest, status: ModelStatus) {
        when (status) {
            is ModelStatus.NotInstalled -> {
                _lifecycle.value = Lifecycle.NoModel
                _download.value = null
            }
            is ModelStatus.Downloading -> {
                _lifecycle.value = Lifecycle.Downloading
                _download.value = DownloadProgress(
                    bytesDownloaded = status.bytesDownloaded,
                    bytesTotal = status.bytesTotal,
                )
            }
            is ModelStatus.Verifying -> {
                _lifecycle.value = Lifecycle.Downloading
                _download.value = DownloadProgress(
                    bytesDownloaded = manifest.totalSizeBytes,
                    bytesTotal = manifest.totalSizeBytes,
                )
            }
            is ModelStatus.Installed -> {
                Log.i(TAG, "  status → Installed @ ${status.mainFile.absolutePath}")
                downloadObserveJob?.cancel()
                downloadObserveJob = null
                _download.value = null
                refreshInstances()
                _lifecycle.value = Lifecycle.Loading
                launchLoadModel(manifest)
            }
            is ModelStatus.Failed -> {
                Log.w(TAG, "  status → Failed reason=${status.reason} msg=${status.message}")
                downloadObserveJob?.cancel()
                downloadObserveJob = null
                _download.value = null
                _lifecycle.value = Lifecycle.NoModel
                surfaceError(status.message)
            }
        }
    }

    private fun surfaceError(message: String, retryable: Boolean = true, cause: Throwable? = null) {
        _errorBanner.value = TransientError(message = message, retryable = retryable, cause = cause)
    }

    override fun onCleared() {
        super.onCleared()
        // Phase 2.10: do NOT close the engine here. Native model + KV
        // cache live for the duration of the process — pinned by
        // LlamaSessionService.
    }

    /**
     * Hardware fingerprint + recommendation, captured once at startup.
     * Null until the background probe completes.
     */
    data class BootSnapshot(
        val deviceInfo: DeviceInfo,
        val recommendation: Recommendation,
    )

    private companion object {
        const val TAG = "ChatViewModel"
        const val MAX_TOKENS = 1024
        const val MAX_SYSTEM_PROMPT_CHARS = 1200
        const val LOAD_TIMEOUT_MS = 180_000L
    }
}
