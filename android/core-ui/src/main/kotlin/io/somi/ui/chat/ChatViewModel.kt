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
import kotlinx.coroutines.async
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
    val soulPromptLoader: SoulPromptLoader,
    private val hardwareDetector: HardwareDetector,
    private val modelManager: ModelManager,
    private val modelStorage: ModelStorage,
    private val chatRepository: ChatRepository,
    private val samplerSettings: io.somi.data.settings.SamplerSettingsRepository,
    val soulRepository: io.somi.data.soul.SoulRepository,
    @ApplicationContext private val appContext: Context,
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {

    // --- Orthogonal state axes (private) --------------------------------

    private enum class Lifecycle { Booting, NoModel, Downloading, Loading, Ready }

    private data class DownloadProgress(val bytesDownloaded: Long, val bytesTotal: Long)

    private data class GenerationStream(val promptId: Long, val partial: String)

    private data class TransientError(
        val message: String,
        val retryable: Boolean = true,
        val cause: Throwable? = null,
    )

    private val _lifecycle = MutableStateFlow(Lifecycle.Booting)
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
            Lifecycle.Booting -> ChatState.Booting
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
        initialValue = ChatState.Booting,
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

    /**
     * In-session-persistent Wi-Fi-only download toggle. Lives here (not
     * in FirstLaunchScreen's local Compose state) so it survives
     * navigation away and back. Reset to TRUE on every cold start —
     * persistence would risk a forgotten "off" costing the user
     * mobile-data money.
     */
    private val _wifiOnly = MutableStateFlow(true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun setWifiOnly(value: Boolean) {
        _wifiOnly.value = value
    }

    /**
     * Live sampler-param state. Initial value comes from disk via
     * [SamplerSettingsRepository]; user edits in Settings push back
     * through [applySamplerParams] which both persists and forwards to
     * the native engine.
     */
    val samplerParams: StateFlow<io.somi.common.llm.SamplerParams> = samplerSettings.params

    /** Backups list for the Soul-Editor screen. */
    val soulBackups = soulRepository.backups

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

    /**
     * Currently running model-load job, if any. Held so [launchLoadModel]
     * can drop a duplicate request (rotation, double-retry tap) before
     * it can queue behind the in-flight one and trample the KV cache via
     * a second setSystemPrompt.
     */
    private var loadJob: Job? = null

    /** Ongoing download-observer job; cancelled on retry/cancel. */
    private var downloadObserveJob: Job? = null

    init {
        // Boot-time work: hardware probe, soul.md cache, on-disk scan.
        // All three are independent and I/O-bound, so we run them as
        // parallel async{}s. The whole block is wrapped in try/catch:
        // an uncaught throw here used to strand the user on the
        // LoadingScreen forever ("the spinner that never ends").
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val probeAsync = async { hardwareDetector.snapshot() }
                val soulAsync = async {
                    runCatching { soulPromptLoader.load() }
                        .onFailure { Log.w(TAG, "soul.md load failed", it) }
                        .getOrNull()
                }
                val instancesAsync = async { modelStorage.findAllInstances(ModelCatalog.ALL) }

                val info = probeAsync.await()
                val rec = recommendModelTier(info)
                _boot.value = BootSnapshot(deviceInfo = info, recommendation = rec)

                val auto = ModelCatalog.forTier(rec.auto)
                if (auto != null) _selectedModel.value = auto

                cachedSoul = soulAsync.await()
                _instances.value = instancesAsync.await()

                // Resolve initial state from disk. rescueSideload adopts
                // a manually-dropped GGUF in modelsDir/ into the canonical
                // /<id>/ subdir on first launch.
                val selected = _selectedModel.value
                if (selected != null && modelStorage.rescueSideload(selected)) {
                    _lifecycle.value = Lifecycle.Loading
                    launchLoadModel(selected)
                } else {
                    _lifecycle.value = Lifecycle.NoModel
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "boot probe failed; falling back to NoModel + banner", t)
                _lifecycle.value = Lifecycle.NoModel
                surfaceError(
                    "Initialisierung hat geknirscht. Wenn's wieder hängt: Force-Stop und neu starten.",
                    cause = t,
                )
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

    /**
     * v0.11.4 — apply user-edited sampler params live.
     *
     * Persists to disk first (so a process restart preserves the
     * choice) and then forwards to the native engine on the
     * llamaDispatcher (mandatory: common_sampler_free + init must NOT
     * race with a generateNextToken in flight).
     *
     * Safe to call in any lifecycle state. If the engine isn't loaded
     * yet, the persistence still happens; the params are re-applied
     * after the next [loadModel] via the init-block forwarding hook.
     */
    fun applySamplerParams(params: io.somi.common.llm.SamplerParams) {
        viewModelScope.launch {
            samplerSettings.save(params)
            if (_lifecycle.value == Lifecycle.Ready || _lifecycle.value == Lifecycle.Loading) {
                runCatching { llama.setSamplerParams(params) }
                    .onFailure { Log.w(TAG, "applySamplerParams: native call failed", it) }
            }
        }
    }

    /**
     * v0.11.4 — re-pump the system prompt after the user edited
     * soul.md in Settings. Cancels any active generation, drops the
     * loader cache, reloads from disk (filesDir override → asset
     * fallback), and re-runs setSystemPrompt on the engine. Keeps the
     * model loaded; only the persona changes.
     */
    fun reloadSoul() {
        if (_lifecycle.value != Lifecycle.Ready) {
            Log.w(TAG, "reloadSoul: ignored — lifecycle=${_lifecycle.value}")
            return
        }
        viewModelScope.launch {
            generationJob?.cancel()
            _generation.value = null
            cachedSoul = null
            soulPromptLoader.invalidate()
            try {
                val newSoul = soulPromptLoader.load()
                cachedSoul = newSoul
                withContext(llamaDispatcher) {
                    llama.setSystemPrompt(newSoul.take(MAX_SYSTEM_PROMPT_CHARS))
                }
                Log.i(TAG, "reloadSoul: applied new system prompt (${newSoul.length} chars)")
            } catch (t: Throwable) {
                Log.e(TAG, "reloadSoul failed", t)
                surfaceError(
                    "Persönlichkeit-Reload schiefgegangen. Soul-Editor nochmal öffnen?",
                    cause = t,
                )
            }
        }
    }

    // --- Internal: model lifecycle --------------------------------------

    private fun launchLoadModel(manifest: ModelManifest) {
        // Re-entrancy guard: if a load is already in flight (configuration
        // change, rotation, double-tap on retry), drop the second request.
        // The active load() owns the llamaDispatcher; firing a second one
        // would queue behind it and then trample the KV cache once
        // setSystemPrompt resets it twice.
        val existing = loadJob
        if (existing != null && existing.isActive) {
            Log.i(TAG, "launchLoadModel: load already in flight for ${manifest.id}, ignoring re-entry")
            return
        }
        loadJob = viewModelScope.launch {
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
            } finally {
                loadJob = null
            }
        }
    }

    /**
     * Load the GGUF and install soul.md as the system prompt.
     *
     * `setSystemPrompt(soul)` runs the soul tokens through llama_decode
     * — that IS a prefill, despite the v0.11.0 kdoc claim of "no warm-
     * pass". On Magic V2 + 7B Q4_K_M with the 4096 KV cache (set in
     * ai_chat.cpp), this takes ~10–30 s of native CPU time. The first
     * user turn then pays only generation latency, not warm-up.
     *
     * All native calls funnel through [llamaDispatcher].
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

        // Apply persisted sampler params after engine is up. The native
        // side defaults are already correct on first launch; this hook
        // ensures user-tuned values from a previous session take effect
        // before the first generate.
        runCatching {
            withContext(llamaDispatcher) { llama.setSamplerParams(samplerSettings.params.value) }
        }.onFailure { Log.w(TAG, "post-load sampler apply failed", it) }
    }

    // --- Internal: generation -------------------------------------------

    private suspend fun runGeneration(promptId: Long, userText: String) {
        // Open the streaming overlay; lifecycle stays Ready throughout.
        _generation.value = GenerationStream(promptId = promptId, partial = "")

        val partial = StringBuilder()
        var completed = false

        try {
            // v0.11.4: read max-tokens from the persisted SamplerParams
            // each turn so the user-facing slider in Settings actually
            // takes effect. Falls back to DEFAULTS.maxTokens (1024)
            // before SamplerSettingsRepository finishes its first disk
            // load — same behavior as v0.11.3's hardcoded MAX_TOKENS.
            val maxTokens = samplerSettings.params.value.maxTokens
            withContext(llamaDispatcher) {
                llama.generate(userText, maxTokens = maxTokens)
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
        const val MAX_SYSTEM_PROMPT_CHARS = 1200
        const val LOAD_TIMEOUT_MS = 180_000L
    }
}
