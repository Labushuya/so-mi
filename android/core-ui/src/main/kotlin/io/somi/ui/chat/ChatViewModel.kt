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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Phase-2.6 ChatViewModel — orchestrates the full lifecycle from boot
 * (hardware probe + model recommendation) through download → load →
 * warm → generate.
 *
 * Threading invariants (load-bearing, see [LlamaContext] kdoc):
 *  - Every native llama call runs on [llamaDispatcher], a single-thread
 *    dispatcher carved out of [Dispatchers.IO]. llama.cpp's `llama_decode`
 *    is blocking and not thread-safe; sharing a context across coroutines
 *    silently corrupts the KV cache.
 *  - The hardware probe runs on [Dispatchers.Default] (the GLES roundtrip
 *    is 10–50 ms — fine for the main thread but sloppy).
 *  - State emissions go through [_state]/[_messages]/[_boot]/[_selectedModel];
 *    UI should only collect via the public `asStateFlow` views.
 *
 * Generation cancellation: a single [generationJob] is held so
 * [cancelGeneration] can cancel just the in-flight stream without
 * tearing down the model itself. On cooperative cancellation we still
 * commit the partial text as the final ASSISTANT message — that's the
 * "stop typing" UX, not a "throw away the answer" UX.
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

    // --- Public state ----------------------------------------------------

    private val _state = MutableStateFlow<ChatState>(ChatState.LoadingModel)
    val state: StateFlow<ChatState> = _state.asStateFlow()

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

    // --- Internal --------------------------------------------------------

    /**
     * Single-thread dispatcher for ALL native llama calls. llama.cpp has
     * thread-affinity rules around the KV cache; pinning to one worker
     * thread keeps `llama_decode` invocations serialised without us
     * having to roll a manual mutex.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)

    /**
     * Lazy-cached soul.md text. Loaded once via [soulPromptLoader] at
     * boot and reused on every model warm.
     */
    @Volatile
    private var cachedSoul: String? = null

    /**
     * Currently running generation, if any. Cancelling this only kills
     * the streaming Flow — the model itself stays loaded and the
     * llamaDispatcher worker is free to take the next [submit].
     */
    private var generationJob: Job? = null

    /** Ongoing download-observer job; cancelled on retry/cancel. */
    private var downloadObserveJob: Job? = null

    init {
        // Hardware probe + soul.md cache + recovery from disk happen in
        // parallel where they can. The whole thing is bounded by the
        // probe's GLES roundtrip (~10–50 ms) and the assets read.
        viewModelScope.launch(Dispatchers.Default) {
            val info = hardwareDetector.snapshot()
            val rec = recommendModelTier(info)
            _boot.value = BootSnapshot(deviceInfo = info, recommendation = rec)

            // Auto-select the recommendation's auto tier. ModelCatalog.forTier
            // currently returns non-null for every tier in TIER_SPECS, but we
            // null-coalesce defensively — a future tier addition shouldn't
            // crash the app on first launch.
            val auto = ModelCatalog.forTier(rec.auto)
            if (auto != null) {
                _selectedModel.value = auto
            }

            // Pre-cache soul.md so the first load doesn't pay the assets
            // read cost on top of the mmap.
            cachedSoul = runCatching { soulPromptLoader.load() }
                .onFailure { Log.w(TAG, "soul.md load failed", it) }
                .getOrNull()

            // Resolve initial state from disk. If the user already has the
            // selected model installed (e.g. process recreate, app reopen),
            // jump straight to LoadingModel and warm. Otherwise show the
            // picker via NoModelInstalled.
            val selected = _selectedModel.value
            if (selected != null && modelStorage.isInstalled(selected)) {
                _state.value = ChatState.LoadingModel
                launchLoadModelAndWarm(selected)
            } else {
                _state.value = ChatState.NoModelInstalled
            }
        }
    }

    // --- Public API ------------------------------------------------------

    /**
     * User overrode the auto-selected tier from the picker. If the new
     * model is already installed we transition straight into
     * LoadingModel; otherwise we sit in NoModelInstalled until the user
     * taps download.
     */
    fun selectModel(manifest: ModelManifest) {
        _selectedModel.value = manifest
        downloadObserveJob?.cancel()
        downloadObserveJob = null

        if (modelStorage.isInstalled(manifest)) {
            _state.value = ChatState.LoadingModel
            launchLoadModelAndWarm(manifest)
        } else {
            _state.value = ChatState.NoModelInstalled
        }
    }

    /**
     * Kick off a download for the currently-selected model and start
     * mirroring [ModelManager.observe] into [_state]. On Installed we
     * automatically chain into the load + warm path — the user does not
     * need to tap a second button.
     */
    fun startDownload(wifiOnly: Boolean) {
        val manifest = _selectedModel.value ?: run {
            _state.value = ChatState.Error(
                "Kein Modell ausgewählt. Wähl eins aus der Liste.",
            )
            return
        }
        Log.i(TAG, "startDownload(${manifest.id}, wifiOnly=$wifiOnly)")
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

        // Polling fallback. If the WorkManager Flow emits SUCCEEDED but
        // our isInstalled-on-disk check then races (fs-cache lag, OEM
        // background-policy killing the worker just after promote, etc.),
        // a periodic disk-truth probe rescues us. Cheap: one File.exists()
        // per manifest part every 2 s, no native calls.
        viewModelScope.launch {
            while (isActiveDownload(manifest)) {
                kotlinx.coroutines.delay(POLL_INSTALLED_INTERVAL_MS)
                if (modelStorage.isInstalled(manifest) && _state.value is ChatState.DownloadingModel) {
                    Log.i(TAG, "polling rescue: model installed on disk; switching to LoadingModel")
                    downloadObserveJob?.cancel()
                    downloadObserveJob = null
                    _state.value = ChatState.LoadingModel
                    launchLoadModelAndWarm(manifest)
                    return@launch
                }
            }
        }
    }

    private fun isActiveDownload(manifest: ModelManifest): Boolean {
        val s = _state.value
        return s is ChatState.DownloadingModel && _selectedModel.value?.id == manifest.id
    }

    /** Cancel an in-flight download; the .part file stays for resume. */
    fun cancelDownload() {
        val manifest = _selectedModel.value ?: return
        modelManager.cancel(manifest)
        downloadObserveJob?.cancel()
        downloadObserveJob = null
        _state.value = ChatState.NoModelInstalled
    }

    /**
     * Submit a user turn. Only legal when the lifecycle is in [ChatState.Idle];
     * any other state is silently ignored (the UI is responsible for
     * disabling the composer otherwise — but defending here keeps a
     * stale tap from corrupting state).
     */
    fun submit(userText: String) {
        if (_state.value !is ChatState.Idle) {
            Log.w(TAG, "submit() ignored — state=${_state.value}")
            return
        }
        val text = userText.trim()
        if (text.isEmpty()) return

        // Phase 3a: persist USER row first; the Room-assigned id becomes
        // the streaming-bubble promptId, tying the LazyColumn key to the
        // persisted row.
        generationJob = viewModelScope.launch {
            val promptId = chatRepository.appendUser(text)
            runGeneration(promptId, text)
        }
    }

    /**
     * Stop the current generation. Commits whatever partial text we have
     * as the final ASSISTANT message — "stop typing", not "discard".
     * The model + KV cache stay live; the next [submit] reuses them.
     */
    fun cancelGeneration() {
        generationJob?.cancel()
    }

    /**
     * Recover from [ChatState.Error]. Tries the cheapest action that
     * could plausibly fix the problem: re-resolve the on-disk picture
     * for the currently-selected model and either warm or fall back to
     * the picker.
     */
    fun retry() {
        val manifest = _selectedModel.value
        if (manifest != null && modelStorage.isInstalled(manifest)) {
            _state.value = ChatState.LoadingModel
            launchLoadModelAndWarm(manifest)
        } else {
            _state.value = ChatState.NoModelInstalled
        }
    }

    // --- Internal: model lifecycle --------------------------------------

    private fun launchLoadModelAndWarm(manifest: ModelManifest) {
        viewModelScope.launch {
            try {
                loadModelAndWarm(manifest)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "load+warm failed", t)
                _state.value = ChatState.Error(
                    "Modell ließ sich nicht laden. Speicher voll? Probier nochmal.",
                    t,
                )
            }
        }
    }

    /**
     * Load the GGUF, install soul.md as the system prompt, and run a
     * tiny hidden warm-pass to fill the KV cache so the first user turn
     * doesn't pay the cold-prompt penalty.
     *
     * All native calls funnel through [llamaDispatcher].
     */
    private suspend fun loadModelAndWarm(manifest: ModelManifest) {
        val mainFile = modelStorage.mainFileFor(manifest) ?: error(
            "isInstalled lied: ${manifest.id}",
        )
        val soul = cachedSoul ?: run {
            // Late-load fallback if the init-time cache failed.
            val s = soulPromptLoader.load()
            cachedSoul = s
            s
        }

        // Wall-clock timing so future hangs are diagnosable. logcat:
        //   ChatViewModel: native load …
        //   ChatViewModel: native load done in 8123 ms
        //   ChatViewModel: native setSystemPrompt(N chars) …
        //   ChatViewModel: native setSystemPrompt done in NNNN ms
        Log.i(TAG, "loadModelAndWarm start id=${manifest.id} file=${mainFile.name} size=${mainFile.length()}")
        try {
            kotlinx.coroutines.withTimeout(LOAD_TIMEOUT_MS) {
                withContext(llamaDispatcher) {
                    val t0 = System.nanoTime()
                    llama.load(mainFile)
                    val t1 = System.nanoTime()
                    Log.i(TAG, "native load done in ${(t1 - t0) / 1_000_000} ms")

                    // soul_condensed.md is hand-crafted to ~600 chars
                    // (~200 Qwen2.5 tokens) — prefill is ~10 s on CPU
                    // 7B Q4_K_M, no truncation needed. Keep the
                    // safety-net cap at 1200 chars in case anyone
                    // ever swaps the asset for a fuller text.
                    val systemText = soul.take(MAX_SYSTEM_PROMPT_CHARS)
                    Log.i(TAG, "native setSystemPrompt(${systemText.length} chars) …")
                    llama.setSystemPrompt(systemText)
                    val t2 = System.nanoTime()
                    Log.i(TAG, "native setSystemPrompt done in ${(t2 - t1) / 1_000_000} ms")
                }
            }
        } catch (te: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "loadModelAndWarm timed out after $LOAD_TIMEOUT_MS ms", te)
            _state.value = ChatState.Error(
                "Modell-Laden hängt. Force-Stop App + nochmal — wenn's wieder hängt: kleineres Modell.",
                te,
            )
            return
        }

        // No warm-pass. The first real user turn pays cold-prompt
        // latency, which users already expect from a local LLM. The
        // earlier WarmDoneSignal scheme interacted badly with the
        // upstream sendUserPrompt's `while (!_cancelGeneration)` loop
        // (no public cancel hook) — could pin the dispatcher for a
        // full 1024-token cold decode if something else hung first.
        Log.i(TAG, "loadModelAndWarm done — entering Idle")
        _state.value = ChatState.Idle
    }

    // --- Internal: generation -------------------------------------------

    /**
     * Runs a single user→assistant turn. The USER row has already been
     * persisted by [submit]; [promptId] is its Room-assigned id. This
     * function does NOT touch the messages StateFlow directly — inserts
     * propagate through the Room invalidation tracker via
     * [chatRepository.observeMessages].
     */
    private suspend fun runGeneration(promptId: Long, userText: String) {
        // Flip into Generating with empty partial. The UI keys the
        // streaming bubble by promptId (== persisted USER row id).
        _state.value = ChatState.Generating(promptId = promptId, partialResponse = "")

        val partial = StringBuilder()
        var completed = false

        try {
            withContext(llamaDispatcher) {
                llama.generate(userText, maxTokens = MAX_TOKENS)
                    .cancellable()
                    .collect { chunk ->
                        partial.append(chunk)
                        // Re-emit Generating with the new partial. The
                        // partial is NOT persisted in Phase 3a — only
                        // the final accumulated text hits the DB on the
                        // commit path below.
                        _state.value = ChatState.Generating(
                            promptId = promptId,
                            partialResponse = partial.toString(),
                        )
                    }
            }
            completed = true
        } catch (ce: CancellationException) {
            Log.i(TAG, "generation cancelled by user @ promptId=$promptId")
        } catch (t: Throwable) {
            Log.e(TAG, "generation failed @ promptId=$promptId", t)
            _state.value = ChatState.Error(
                "Hat nicht geklappt. Versuch's nochmal.",
                t,
            )
            return
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
        _state.value = ChatState.Idle
    }

    // --- Internal: download status mirror -------------------------------

    private fun applyDownloadStatus(manifest: ModelManifest, status: ModelStatus) {
        when (status) {
            is ModelStatus.NotInstalled -> {
                Log.d(TAG, "  status → NotInstalled")
                _state.value = ChatState.NoModelInstalled
            }
            is ModelStatus.Downloading -> {
                Log.d(
                    TAG,
                    "  status → Downloading ${status.bytesDownloaded}/${status.bytesTotal} (part ${status.currentPart}/${status.totalParts})",
                )
                _state.value = ChatState.DownloadingModel(
                    bytesDownloaded = status.bytesDownloaded,
                    bytesTotal = status.bytesTotal,
                )
            }
            is ModelStatus.Verifying -> {
                Log.d(TAG, "  status → Verifying")
                _state.value = ChatState.DownloadingModel(
                    bytesDownloaded = manifest.totalSizeBytes,
                    bytesTotal = manifest.totalSizeBytes,
                )
            }
            is ModelStatus.Installed -> {
                Log.i(TAG, "  status → Installed @ ${status.mainFile.absolutePath}")
                downloadObserveJob?.cancel()
                downloadObserveJob = null
                _state.value = ChatState.LoadingModel
                launchLoadModelAndWarm(manifest)
            }
            is ModelStatus.Failed -> {
                Log.w(TAG, "  status → Failed reason=${status.reason} msg=${status.message}")
                downloadObserveJob?.cancel()
                downloadObserveJob = null
                _state.value = ChatState.Error(status.message)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Phase 2.10: do NOT close the engine here. Native model + KV
        // cache live for the duration of the process — pinned by
        // LlamaSessionService. Closing on Activity teardown caused the
        // model to be unloaded on every configuration change (rotation,
        // dark-mode toggle, locale switch) and then reloaded for 30+ s
        // when the user came back. The service decides when to free.
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
        const val POLL_INSTALLED_INTERVAL_MS = 2_000L
        // Hard cap on the system prompt during boot — defense in depth.
        // The actual asset (soul_condensed.md) is hand-crafted to ~600
        // chars; this cap only kicks in if someone swaps the asset for
        // a longer one without re-checking prefill latency on the
        // boot path.
        const val MAX_SYSTEM_PROMPT_CHARS = 1200
        // Hard ceiling on the load+setSystemPrompt path. 3 minutes is
        // generous on Magic V2; if we cross it, something is genuinely
        // stuck and the user gets an actionable Error state.
        const val LOAD_TIMEOUT_MS = 180_000L
    }
}
