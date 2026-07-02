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
import io.somi.rag.RagBootstrap
import io.somi.tools.executor.ToolDispatcher
import io.somi.tools.model.ToolCall
import io.somi.tools.prefs.ToolPrefsRepository
import io.somi.tools.routing.ToolRouter
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val conversationRepository: io.somi.data.ConversationRepository,
    private val samplerSettings: io.somi.data.settings.SamplerSettingsRepository,
    val soulRepository: io.somi.data.soul.SoulRepository,
    private val ragOrchestrator: io.somi.rag.RagOrchestrator,
    val uiSettings: io.somi.data.settings.UiSettingsRepository,
    private val ragBootstrap: RagBootstrap,
    private val toolRouter: ToolRouter,
    private val toolDispatcher: ToolDispatcher,
    private val toolPrefs: ToolPrefsRepository,
    @ApplicationContext private val appContext: Context,
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {

    // --- Orthogonal state axes (private) --------------------------------

    private val _pendingWebConsent = MutableStateFlow<ToolCall?>(null)
    val pendingWebConsent: StateFlow<ToolCall?> = _pendingWebConsent.asStateFlow()

    suspend fun checkpointDatabase() { /* WAL checkpoint is handled externally before backup */ }

    fun isToolEnabledFlow(toolId: String) = toolPrefs.toolEnabledFlow(toolId)
    fun setToolEnabled(toolId: String, enabled: Boolean) {
        viewModelScope.launch { toolPrefs.setToolEnabled(toolId, enabled) }
    }

    fun confirmWebConsent(originalText: String) {
        _pendingWebConsent.value = null
        viewModelScope.launch {
            toolPrefs.setWebConsentShown(true)
            submit(originalText)
        }
    }

    fun dismissWebConsent() { _pendingWebConsent.value = null }

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
    private val _activeToolHint = MutableStateFlow<String?>(null)

    // --- Public state ----------------------------------------------------

    /** Non-null while a tool is executing (e.g. "Wetter wird abgerufen…"). Shown as a chip in the UI. */
    val activeToolHint: StateFlow<String?> = _activeToolHint.asStateFlow()

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
    // v0.37.0 — active conversation ID as observable state so messages re-subscribes on switch
    private val _activeConversationId = MutableStateFlow(1L)
    private val _awaitingRenameInput = MutableStateFlow(false)

    val messages: StateFlow<List<Message>> = _activeConversationId
        .flatMapLatest { id -> chatRepository.observeMessagesForConversation(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    // v0.37.0 — Conversation list for the Chat-List screen
    val conversations: StateFlow<List<io.somi.data.db.ConversationEntity>> =
        conversationRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentConversationId: Long get() = _activeConversationId.value

    /** Switch to an existing conversation — messages Flow re-subscribes automatically. */
    fun switchConversation(id: Long) {
        viewModelScope.launch {
            _awaitingRenameInput.value = false
            generationJob?.cancel()
            _generation.value = null
            _activeConversationId.value = id
            chatRepository.setConversation(id)
            _lifecycle.value = Lifecycle.Ready
        }
    }

    /** Create a new conversation and switch to it. */
    suspend fun createNewConversation(title: String = "Neues Gespräch"): Long {
        val id = conversationRepository.create(title)
        _awaitingRenameInput.value = false
        generationJob?.cancel()
        _generation.value = null
        _activeConversationId.value = id
        chatRepository.setConversation(id)
        return id
    }

    /** Rename a conversation. */
    fun renameConversation(id: Long, newTitle: String) {
        viewModelScope.launch { conversationRepository.rename(id, newTitle) }
    }

    /** Delete a conversation and all its messages. */
    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            if (id == chatRepository.currentConversationId) {
                // Switch to another before deleting
                val remaining = conversations.value.filter { it.id != id }
                val next = remaining.firstOrNull()?.id
                    ?: conversationRepository.create("Neue Session")
                _activeConversationId.value = next
                chatRepository.setConversation(next)
            }
            // Delete messages for this conversation then the conversation itself
            val wasActive = _activeConversationId.value == id
            if (!wasActive) {
                // Delete non-active conversation's messages directly
                dao_deleteByConversation(id)
            }
            conversationRepository.delete(id)
        }
    }

    private suspend fun dao_deleteByConversation(id: Long) {
        // We can't inject MessageDao directly here; use chatRepository trick
        val tmp = chatRepository.currentConversationId
        chatRepository.setConversation(id)
        chatRepository.clearCurrentConversation()
        chatRepository.setConversation(tmp)
        _activeConversationId.value = tmp
    }


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

    // v0.15.1 — live status of every model in ModelCatalog.ALL for ModelCatalogScreen.
    // Declared here (before init{}) so startObservingModelStatuses() in init can write to it.
    private val _modelStatuses = MutableStateFlow(emptyMap<String, ModelStatus>())
    val modelStatuses: StateFlow<Map<String, ModelStatus>> = _modelStatuses.asStateFlow()

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
        // v0.38.0 — show banner if previous session crashed during model load
        val prefs = appContext.getSharedPreferences("somi_crash", android.content.Context.MODE_PRIVATE)
        val crashedModel = prefs.getString("last_crash_model", null)
        if (crashedModel != null) {
            prefs.edit().remove("last_crash_model").apply()
            // Surface after a short delay so the boot screen passes first
            viewModelScope.launch {
                kotlinx.coroutines.delay(2_000L)
                surfaceError(
                    "⚠️ Letztes Modell ($crashedModel) ist beim Laden abgestürzt. App ist automatisch auf das empfohlene Modell zurückgegangen.",
                    retryable = false,
                )
            }
        }

        // v0.15.0 — observe the embedder download for the Settings UI.
        // Cheap (a single Flow collector, no work until WorkManager has
        // something to report). Started here, not lazily, because the
        // Settings screen reads the StateFlow eagerly.
        startObservingEmbedderStatus()

        // v0.15.1 — observe per-model statuses for ModelCatalogScreen.
        startObservingModelStatuses()

        // v0.15.0 — greeting hook. Reacts to the FIRST transition into
        // Lifecycle.Ready per process launch (cold start) and to every
        // subsequent Ready→Ready re-entry that follows a stop/restart
        // gap longer than [GREETING_GAP_MS]. The Settings toggle decides
        // whether either of those fires.
        startGreetingHook()

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
                    val mainFile = modelStorage.mainFileFor(selected)
                    // v0.13.0: if the engine survived the Activity (FGS pin
                    // keeps the process alive), don't re-load. Jump
                    // straight to Ready and avoid the breathing-screen
                    // flicker → NoModel loop that was caused by the
                    // upstream's strict state-machine check.
                    if (mainFile != null && llama.isLoaded(mainFile)) {
                        Log.i(TAG, "boot: engine already loaded for ${selected.id}, jumping to Ready")
                        _lifecycle.value = Lifecycle.Ready
                        // Re-apply persisted sampler params just in case
                        // they changed in another VM instance.
                        runCatching {
                            llama.setSamplerParams(samplerSettings.params.value)
                        }.onFailure { Log.w(TAG, "post-boot sampler apply failed", it) }
                    } else {
                        _lifecycle.value = Lifecycle.Loading
                        launchLoadModel(selected)
                    }
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

        // v0.15.0 KV-trample-fix: cancel any in-flight generation BEFORE
        // we hand the dispatcher to a load() of a different model.
        // Without this, an active generation against the OLD model would
        // race the new load(), corrupting the upstream KV cache (the
        // engine is a process-wide singleton; load() on file B while
        // generate() is mid-decode against file A leaves the state
        // machine in an unrecoverable mix). Was previously fixed only
        // for deleteModelInstance(); now also covers picker-driven swap.
        generationJob?.cancel()
        _generation.value = null

        if (modelStorage.rescueSideload(manifest)) {
            // v0.13.0: same engine-already-loaded short-circuit as init.
            // Without this, picking the same model twice (rare but
            // possible) would re-enter Loading and trip upstream state
            // checks.
            viewModelScope.launch {
                val mainFile = modelStorage.mainFileFor(manifest)
                if (mainFile != null && llama.isLoaded(mainFile)) {
                    _lifecycle.value = Lifecycle.Ready
                } else {
                    _lifecycle.value = Lifecycle.Loading
                    launchLoadModel(manifest)
                }
            }
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
        val text = userText.trim()
        if (text.isEmpty()) return

        // v0.39.0 — handle rename input state
        if (_awaitingRenameInput.value) {
            _awaitingRenameInput.value = false
            viewModelScope.launch {
                chatRepository.appendUser(text)
                val id = chatRepository.currentConversationId
                conversationRepository.rename(id, text)
                chatRepository.appendAssistant("✅ Umbenannt in \"$text\".")
            }
            return
        }

        // v0.14.0 M6: gate on generationJob.isActive, NOT _generation.value.
        // _generation.value is set inside runGeneration AFTER appendUser
        // and handleRagTrigger have already suspended — leaving a
        // multi-hundred-millisecond window where a double-tap would
        // launch a parallel pipeline (duplicate USER rows, duplicate
        // memory saves, KV cache corruption when both runGenerations
        // serialize on the llama dispatcher). generationJob is assigned
        // synchronously below so this gate closes the window.
        if (generationJob?.isActive == true) {
            Log.w(TAG, "submit() ignored — generation already running (job active)")
            return
        }

        // v0.31.2 — test commands for band UI testing
        val testBanners = mapOf(
            "/trigger_error" to "Fehler: Dies ist ein Test-Fehlermeldungs-Band.",
            "/trigger_warning" to "⚠️ Warnung: Dies ist ein Test-Warnungs-Band.",
            "/trigger_success" to "✅ Erfolgreich: Dies ist ein Test-Erfolgs-Band.",
            "/trigger_info" to "ℹ️ Hinweis: Dies ist ein Test-Informations-Band.",
            "/trigger_chatband" to "System: Dies ist ein Test-Systemband.",
        )
        testBanners[text.lowercase()]?.let { msg ->
            viewModelScope.launch {
                chatRepository.appendUser(text)
                surfaceError(msg, retryable = false)
            }
            return
        }

        // v0.39.0 — chat management slash commands
        // v0.46.12 — German aliases: /leeren, /umbenennen, /archivieren
        when (text.lowercase()) {
            "/clear", "/leeren" -> {
                viewModelScope.launch {
                    chatRepository.appendUser(text)
                    chatRepository.clearCurrentConversation()
                    chatRepository.appendAssistant("✅ Gespräch geleert.")
                }
                return
            }
            "/rename", "/umbenennen" -> {
                _awaitingRenameInput.value = true    // synchronous, before coroutine
                viewModelScope.launch {
                    chatRepository.appendUser(text)
                    chatRepository.appendAssistant("Wie soll dieses Gespräch heißen? Gib den neuen Namen ein.")
                    // flag already set above
                }
                return
            }
            "/archive", "/archivieren" -> {
                viewModelScope.launch {
                    chatRepository.appendUser(text)
                    val id = chatRepository.currentConversationId
                    conversationRepository.rename(id, "📦 " + (conversations.value.firstOrNull { it.id == id }?.title?.removePrefix("📦 ") ?: "Archiviert"))
                    chatRepository.appendAssistant("✅ Archiviert. Das Gespräch erscheint in der Liste mit 📦.")
                }
                return
            }
        }
        // v0.46.12 — /suche as German alias for /search
        if (text.lowercase().startsWith("/suche ") || text.lowercase().startsWith("/search ")) {
            val prefixLen = if (text.lowercase().startsWith("/suche ")) 7 else 8
            val query = text.substring(prefixLen).trim()
            viewModelScope.launch {
                chatRepository.appendUser(text)
                if (query.isBlank()) {
                    chatRepository.appendAssistant("Sag mir wonach ich suchen soll: /search <Begriff>")
                } else {
                    val results = chatRepository.searchInCurrentConversation(query)
                    val reply = if (results.isEmpty()) "Keine Nachrichten mit \"$query\" gefunden."
                    else "${results.size} Treffer für \"$query\":\n" + results.take(5).joinToString("\n") { "· ${it.text.take(80)}" } +
                        if (results.size > 5) "\n…und ${results.size - 5} weitere." else ""
                    chatRepository.appendAssistant(reply)
                }
            }
            return
        }
        // v0.46.12 — /notiz: save directly to NOTES category (bypasses TriggerDetector)
        if (text.lowercase().startsWith("/notiz ")) {
            val noteText = text.substring(7).trim()
            viewModelScope.launch {
                chatRepository.appendUser(text)
                if (noteText.isBlank()) {
                    chatRepository.appendAssistant("Sag mir was ich notieren soll: /notiz <Text>")
                } else {
                    val augmented = "#Notizen $noteText"
                    val outcome = runCatching { ragOrchestrator.maybeSaveOnSubmit(augmented) }
                        .onFailure { Log.w(TAG, "/notiz rag save failed", it) }
                        .getOrDefault(io.somi.rag.SaveOutcome.NotTriggered)
                    if (outcome is io.somi.rag.SaveOutcome.Saved || outcome is io.somi.rag.SaveOutcome.NotTriggered) {
                        chatRepository.appendAssistant("Notiz gespeichert: \"$noteText\"")
                    }
                    // SaveFailed is handled by handleRagTrigger surface path; surface banner here too
                    if (outcome is io.somi.rag.SaveOutcome.SaveFailed) {
                        chatRepository.appendAssistant("Notiz konnte nicht gespeichert werden. Prüf den Speicher.")
                    }
                }
            }
            return
        }

        // Tapping send dismisses any leftover banner.
        _errorBanner.value = null

        // Phase 3a: persist USER row first; the Room-assigned id becomes
        // the streaming-bubble promptId.
        // v0.14.0 M6: BEFORE generation, run the RagOrchestrator
        // trigger pipeline. If "merk dir …" or similar fires, save
        // the fact + emit a short "Hab ich."-bubble. Then proceed with
        // the LLM generation as normal so follow-ups work without
        // a second tap.
        generationJob = viewModelScope.launch {
            val promptId = chatRepository.appendUser(text)
            val ragOutcome = handleRagTrigger(text)
            when (ragOutcome) {
                // Trigger saved OR keyword command: ack bubble already written,
                // no LLM generation needed. The ack IS the response.
                // This prevents the double-answer and removes the weird
                // "I'll remember X" followed by paraphrasing all known facts.
                is io.somi.rag.SaveOutcome.Saved,
                is io.somi.rag.SaveOutcome.KeywordAdded,
                is io.somi.rag.SaveOutcome.KeywordRemoved,
                is io.somi.rag.SaveOutcome.KeywordsShown -> {
                    generationJob = null
                    return@launch
                }
                else -> runGeneration(promptId, text)
            }
        }
    }

    /**
     * Runs the trigger → embed → save → mirror pipeline.
     * Returns the [SaveOutcome] so the caller can decide what to do
     * with the LLM input (ack-only vs. forward to LLM).
     */
    private suspend fun handleRagTrigger(userText: String): io.somi.rag.SaveOutcome {
        val outcome = runCatching { ragOrchestrator.maybeSaveOnSubmit(userText) }
            .onFailure { Log.w(TAG, "rag orchestrator threw", it) }
            .getOrDefault(io.somi.rag.SaveOutcome.NotTriggered)
        when (outcome) {
            is io.somi.rag.SaveOutcome.NotTriggered -> Unit
            is io.somi.rag.SaveOutcome.Saved -> {
                val ackId = chatRepository.appendAssistant("Notiert.")

                // v0.40.0/v0.41.1 — LLM-based reclassification, then update ack with details
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        val prompt = buildString {
                            append("Klassifiziere diesen Fakt in genau eine Kategorie. Antworte NUR mit dem Kategorie-Schlüssel.\n\n")
                            append("Kategorien: persons, preferences, dates, technical, notes\n\n")
                            append("Fakt: ${outcome.factText}\n\nKategorie:")
                        }
                        val llmTopic = withTimeoutOrNull(3000L) {
                            withContext(llamaDispatcher) {
                                llama.generate(prompt, maxTokens = 8)
                                    .fold("") { acc, chunk -> acc + chunk }
                            }
                        }?.trim()?.lowercase()?.split(Regex("\\s+"))?.firstOrNull()
                        if (llmTopic != null) {
                            ragOrchestrator.reclassifyAndMove(outcome.factText, outcome.topic, llmTopic)
                        }
                        val topicDisplay = when (llmTopic ?: outcome.topic.id) {
                            "persons" -> "Personen"
                            "preferences" -> "Vorlieben"
                            "dates" -> "Termine"
                            "technical" -> "Technik"
                            else -> "Notizen"
                        }
                        chatRepository.updateAssistantMessage(ackId, "\"${outcome.factText}\" → $topicDisplay")
                    }.onFailure {
                        Log.w(TAG, "LLM reclassify failed", it)
                        chatRepository.updateAssistantMessage(ackId, "\"${outcome.factText}\" gespeichert.")
                    }
                }
            }
            is io.somi.rag.SaveOutcome.SaveFailed -> {
                val msg = when (outcome.reason) {
                    io.somi.rag.SaveFailureReason.EMBEDDER_NOT_READY ->
                        "Gespeichert — aber ohne Vektorindex. Semantisches Erinnern kommt wenn das Gedächtnis-Modell geladen ist."
                    io.somi.rag.SaveFailureReason.IO ->
                        "Speicherfehler — konnte Erinnerung nicht auf Disk schreiben. Prüf den freien Speicher."
                }
                surfaceError(msg, retryable = false, cause = outcome.cause)
            }
            is io.somi.rag.SaveOutcome.KeywordAdded -> {
                chatRepository.appendAssistant("Hab's. \"${outcome.keyword}\" ist jetzt ein Keyword für ${outcome.categoryId.replace("_", " ")}.")
            }
            is io.somi.rag.SaveOutcome.KeywordRemoved -> {
                chatRepository.appendAssistant("Entfernt. \"${outcome.keyword}\" gilt nicht mehr für ${outcome.categoryId.replace("_", " ")}.")
            }
            is io.somi.rag.SaveOutcome.KeywordsShown -> {
                val kwText = if (outcome.keywords.isEmpty()) "Keine Keywords definiert."
                             else "Keywords für ${outcome.categoryId.replace("_", " ")}: ${outcome.keywords.joinToString(", ")}"
                chatRepository.appendAssistant(kwText)
            }
        }
        return outcome
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
            // v0.13.0: same engine-already-loaded short-circuit as init.
            // Without this, tapping retry on a still-loaded engine would
            // loop NoModel → Loading → NoModel because re-load throws.
            viewModelScope.launch {
                val mainFile = modelStorage.mainFileFor(manifest)
                if (mainFile != null && llama.isLoaded(mainFile)) {
                    _lifecycle.value = Lifecycle.Ready
                } else {
                    _lifecycle.value = Lifecycle.Loading
                    launchLoadModel(manifest)
                }
            }
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
                _selectedModel.value = null
                _lifecycle.value = Lifecycle.NoModel
            }
        }
    }

    /** Force-refresh the on-disk model inventory. */
    fun refreshInstances() {
        _instances.value = modelStorage.findAllInstances(ModelCatalog.ALL)
    }

    // ---------------------------------------------------------------
    // v0.15.1 — per-model status for ModelCatalogScreen.
    //
    // Merges ModelManager.observe() for every ModelCatalog.ALL entry
    // into a single StateFlow<Map<String, ModelStatus>> keyed by id.
    // The merge keeps one Flow<Pair> per model; each emission replaces
    // only the affected key so the map is a live aggregate.
    // ---------------------------------------------------------------

    private fun startObservingModelStatuses() {
        viewModelScope.launch {
            val perModel = ModelCatalog.ALL.map { manifest ->
                kotlinx.coroutines.flow.flow<Pair<String, ModelStatus>> {
                    modelManager.observe(manifest).collect { status ->
                        emit(manifest.id to status)
                    }
                }
            }
            merge(*perModel.toTypedArray()).collect { (id, status) ->
                _modelStatuses.value = _modelStatuses.value + (id to status)
                // Refresh instances when a model transitions to Installed or
                // NotInstalled — keeps the UI button (Herunterladen/Aktivieren)
                // in sync without requiring a full-app restart.
                if (status is ModelStatus.Installed || status is ModelStatus.NotInstalled) {
                    refreshInstances()
                }
            }
        }
    }

    /**
     * Initiate a download for [manifest] from the [ModelCatalogScreen].
     * Unlike [startDownload] (which operates on the currently-selected
     * model and drives the global lifecycle), this targets any manifest
     * explicitly — the user can queue a download for a model that is not
     * yet selected. The [modelStatuses] flow reflects progress.
     *
     * If [manifest] is already the selected model we delegate to the
     * standard [startDownload] path so the global lifecycle/download
     * progress bars stay in sync.
     */
    fun downloadModel(manifest: ModelManifest, wifiOnly: Boolean) {
        if (_selectedModel.value?.id == manifest.id) {
            // Route through the standard path to keep _lifecycle in sync.
            if (_wifiOnly.value != wifiOnly) _wifiOnly.value = wifiOnly
            startDownload(wifiOnly)
        } else {
            // Background download for a non-selected model.
            // modelStatuses will auto-update via the merge observer above.
            modelManager.startDownload(manifest, wifiOnly = wifiOnly)
        }
    }

    // ---------------------------------------------------------------
    // v0.15.0 — embedder-download visibility & manual ops.
    //
    // The user reported in v0.14.3 that they had no way to tell whether
    // the embedder was downloading, had finished, or had failed silently.
    // The Settings → Downloads section consumes [embedderStatus] and
    // exposes the buttons backed by the methods below.
    // ---------------------------------------------------------------

    /** v0.16.7 — cancel a background download for a non-selected model. */
    fun cancelModelDownload(manifest: ModelManifest) {
        modelManager.cancel(manifest)
    }

    /**
     * Snapshot of the embedder-download status for the Settings UI.
     *
     * **Disk-installed wins.** Once the artifact passes the SHA-check
     * and lives at the catalog path, it stays installed even if the
     * worker row was pruned by WorkManager's GC after 7 days. So the
     * Settings UI must consult disk first and only fall back to the
     * worker state for "currently running / queued / failed".
     */
    enum class EmbedderStatus { Installed, Running, Enqueued, Failed, NotPresent }

    private val _embedderStatus = MutableStateFlow(currentEmbedderStatus())
    val embedderStatus: StateFlow<EmbedderStatus> = _embedderStatus.asStateFlow()

    private fun currentEmbedderStatus(): EmbedderStatus =
        if (ragBootstrap.isEmbedderInstalled()) EmbedderStatus.Installed
        else EmbedderStatus.NotPresent

    /** Hook the WorkManager flow once Hilt has handed us appContext. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startObservingEmbedderStatus() {
        viewModelScope.launch {
            try {
                val wm = androidx.work.WorkManager.getInstance(appContext)
                wm.getWorkInfosForUniqueWorkFlow(
                    ragBootstrap.embedderWorkName,
                ).collectLatest { infos ->
                    // Always check disk truth — never trust WorkManager SUCCEEDED
                    // if the file was deleted (reinstallEmbedder clears the disk).
                    val installed = ragBootstrap.isEmbedderInstalled()
                    val info = infos.firstOrNull()
                    _embedderStatus.value = when {
                        installed && info?.state != androidx.work.WorkInfo.State.RUNNING -> EmbedderStatus.Installed
                        info?.state == androidx.work.WorkInfo.State.RUNNING -> EmbedderStatus.Running
                        info?.state == androidx.work.WorkInfo.State.ENQUEUED ||
                        info?.state == androidx.work.WorkInfo.State.BLOCKED -> EmbedderStatus.Enqueued
                        info?.state == androidx.work.WorkInfo.State.FAILED ||
                        info?.state == androidx.work.WorkInfo.State.CANCELLED -> EmbedderStatus.Failed
                        installed -> EmbedderStatus.Installed
                        else -> EmbedderStatus.NotPresent
                    }
                    // v0.41.1 — trigger backfill once when embedder becomes available
                    if (_embedderStatus.value == EmbedderStatus.Installed) {
                        val backfillInfos = wm.getWorkInfosForUniqueWork(
                            io.somi.rag.download.EmbeddingBackfillWorker.WORK_NAME
                        ).get()
                        val alreadyHandled = backfillInfos.any {
                            it.state == androidx.work.WorkInfo.State.SUCCEEDED ||
                            it.state == androidx.work.WorkInfo.State.RUNNING ||
                            it.state == androidx.work.WorkInfo.State.ENQUEUED
                        }
                        if (!alreadyHandled) {
                            val req = androidx.work.OneTimeWorkRequestBuilder<io.somi.rag.download.EmbeddingBackfillWorker>()
                                .build()
                            wm.enqueueUniqueWork(
                                io.somi.rag.download.EmbeddingBackfillWorker.WORK_NAME,
                                androidx.work.ExistingWorkPolicy.KEEP,
                                req,
                            )
                            Log.i(TAG, "embedding backfill enqueued")
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "embedder-status observer failed", t)
            }
        }
    }

    /**
     * v0.15.0 — User-triggered embedder re-enqueue. Used from the
     * Settings → Downloads section when the user wants to retry a
     * failed download. Uses REPLACE policy so a stuck FAILED row
     * doesn't block the new run.
     */
    fun manualEnqueueEmbedder() {
        ragBootstrap.forceEnqueueEmbedderDownload(appContext)
    }

    /**
     * v0.15.0 — User-triggered embedder delete + re-enqueue. Cancels
     * any in-flight worker FIRST so its `model.onnx.part` write
     * doesn't race the recursive directory delete (would either
     * crash with ENOENT or leave a half-written file that survives
     * the delete and the next run would SHA-fail on).
     */
    /** Delete embedder files without re-downloading. */
    fun deleteEmbedderOnly() {
        viewModelScope.launch {
            try {
                val wm = androidx.work.WorkManager.getInstance(appContext)
                wm.cancelUniqueWork(ragBootstrap.embedderWorkName).result.get()
            } catch (t: Throwable) {
                Log.w(TAG, "cancelUniqueWork before delete failed", t)
            }
            ragBootstrap.deleteEmbedder()
            _embedderStatus.value = EmbedderStatus.NotPresent
            kotlinx.coroutines.delay(300L)
            _embedderStatus.value = EmbedderStatus.NotPresent
        }
    }

    fun reinstallEmbedder() {
        viewModelScope.launch {
            try {
                val wm = androidx.work.WorkManager.getInstance(appContext)
                wm.cancelUniqueWork(ragBootstrap.embedderWorkName).result.get()
            } catch (t: Throwable) {
                Log.w(TAG, "cancelUniqueWork before reinstall failed", t)
            }
            ragBootstrap.deleteEmbedder()
            // Set NotPresent immediately and hold it — the WorkManager observer
            // may fire a stale SUCCEEDED emission before the new worker starts.
            // We force NotPresent here; the observer will correct to Running/Enqueued
            // once the new worker actually starts and emits a fresh state.
            _embedderStatus.value = EmbedderStatus.NotPresent
            kotlinx.coroutines.delay(300L) // let disk delete complete before observer re-fires
            _embedderStatus.value = EmbedderStatus.NotPresent // re-assert in case observer fired
            ragBootstrap.forceEnqueueEmbedderDownload(appContext)
        }
    }

    // ---------------------------------------------------------------
    // v0.15.0 — Greeting hook.
    //
    // Drops a single ASSISTANT message into the chat without invoking
    // the LLM, modulo the GreetingMode toggle:
    //   FULL       → on every Ready transition once the chat has been
    //                idle for >= GREETING_GAP_MS
    //   COLD_START → only on the first Ready of the process
    //   NONE       → never
    // ---------------------------------------------------------------

    private var lastGreetedAt: Long = 0L
    private var coldGreetingDone: Boolean = false

    private fun startGreetingHook() {
        viewModelScope.launch {
            _lifecycle.collectLatest { state ->
                if (state != Lifecycle.Ready) return@collectLatest
                val mode = uiSettings.state.value.greetingMode
                if (mode == io.somi.data.settings.GreetingMode.NONE) return@collectLatest

                val now = System.currentTimeMillis()
                val shouldGreet = when (mode) {
                    io.somi.data.settings.GreetingMode.COLD_START -> !coldGreetingDone
                    io.somi.data.settings.GreetingMode.FULL ->
                        !coldGreetingDone || (now - lastGreetedAt) >= GREETING_GAP_MS
                    io.somi.data.settings.GreetingMode.NONE -> false
                }
                if (!shouldGreet) return@collectLatest

                // v0.15.0 — pool load + line pick + persist all on IO.
                // viewModelScope defaults to Main.immediate; AssetManager
                // open + JSON parse + Room insert have no business on
                // the main thread.
                withContext(Dispatchers.IO) {
                    val pool = greetingPool ?: runCatching { loadGreetingPool() }
                        .onSuccess { greetingPool = it }
                        .getOrNull()
                    val line = pool?.pick(coldStart = !coldGreetingDone)
                    if (line != null) {
                        runCatching { chatRepository.appendAssistant(line) }
                            .onFailure { Log.w(TAG, "greeting append failed", it) }
                        coldGreetingDone = true
                        lastGreetedAt = now
                    }
                }
            }
        }
    }

    private var greetingPool: GreetingPool? = null

    private data class GreetingPool(
        val coldStart: List<String>,
        val welcomeBack: List<String>,
    ) {
        // Index-based pseudo-random: System.nanoTime mod size, no
        // RNG state needed, no java.util.Random allocation.
        fun pick(coldStart: Boolean): String? {
            val list = if (coldStart) this.coldStart else welcomeBack
            if (list.isEmpty()) return null
            val idx = (System.nanoTime() % list.size).toInt().let { if (it < 0) it + list.size else it }
            return list[idx]
        }
    }

    private fun loadGreetingPool(): GreetingPool {
        val text = appContext.assets.open("greeting-pool.json").bufferedReader().use { it.readText() }
        val obj = org.json.JSONObject(text)
        val cold = obj.optJSONArray("cold_start")
        val back = obj.optJSONArray("welcome_back")
        return GreetingPool(
            coldStart = (0 until (cold?.length() ?: 0)).map { cold!!.getString(it) },
            welcomeBack = (0 until (back?.length() ?: 0)).map { back!!.getString(it) },
        )
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
            // v0.38.0 OOM crash detection: write flag before load, clear after success.
            // If the process is killed mid-load (OOM), the flag persists for SoMiApp to read.
            val crashFlag = java.io.File(
                io.somi.data.StorageRoots.settings(appContext),
                "llm_crash.flag"
            )
            try {
                crashFlag.parentFile?.mkdirs()
                crashFlag.writeText(manifest.id)
            } catch (ignored: Throwable) {}

            try {
                loadModel(manifest)
                // Load succeeded — clear the flag
                runCatching { crashFlag.delete() }
            } catch (ce: CancellationException) {
                runCatching { crashFlag.delete() } // cancelled cleanly, not a crash
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

        // v0.18.5 M8 — Recall: inject known facts before each generation.
        // Reads from the .md mirror (no HNSW yet). If no facts saved, no
        // prefix added. This runs on Dispatchers.IO (file read is fast).
        val recallContext = withContext(Dispatchers.IO) {
            runCatching { ragOrchestrator.recallForPrompt(userText) }.getOrNull()
        }
        val recentMessages = withContext(Dispatchers.IO) {
            runCatching { chatRepository.getRecentMessages(limit = 16) }.getOrDefault(emptyList())
        }
        // Drop the just-appended current user turn (last element) so history
        // only contains completed prior exchanges.
        // Also filter out tool-result messages (lines starting with "[") to prevent
        // So-Mi from paraphrasing stale tool data when the tool is disabled.
        val historyContext = if (recentMessages.size > 1) {
            val history = recentMessages.dropLast(1).takeLast(14)
                .filter { msg ->
                    // Keep user messages always; filter assistant messages that are tool blocks
                    msg.author == io.somi.common.chat.Author.USER ||
                    !msg.text.trimStart().startsWith("[")
                }
            if (history.isEmpty()) null else buildString {
                append("Bisheriges Gespräch (neueste zuletzt):\n")
                history.forEach { msg ->
                    val role = if (msg.author == io.somi.common.chat.Author.USER) "Du" else "So-Mi"
                    append("$role: ${msg.text.take(200)}\n")
                }
                append("\n")
            }
        } else null

        // v0.43.0 — Tool routing before LLM generation (15s hard timeout)
        val toolResult = withTimeoutOrNull(15_000L) {
            runCatching {
                val toolCall = toolRouter.route(userText)
                if (toolCall != null) {
                    Log.i(TAG, "tool matched: ${toolCall.toolId} via ${toolCall.stage}")
                    // Web-consent is surfaced via the result footer text; no blocking dialog needed.
                    val hint = when (toolCall.toolId) {
                        "get_weather" -> "Wetterdaten werden abgerufen…"
                        "search_web" -> "Web-Suche läuft…"
                        "search_memory" -> "Erinnerungen werden durchsucht…"
                        else -> "Tool wird ausgeführt…"
                    }
                    _activeToolHint.value = hint
                    toolDispatcher.dispatch(toolCall).also { result ->
                        Log.i(TAG, "tool result: ${toolCall.toolId} error=${result.error} len=${result.contextBlock.length}")
                    }
                } else null
            }.onFailure { Log.w(TAG, "tool routing failed", it) }.getOrNull()
        }.also { if (it == null) Log.w(TAG, "tool dispatch timed out after 15s") }
        _activeToolHint.value = null

        val toolMode = uiSettings.state.value.toolMode
        val hasToolData = toolResult != null && toolResult.error == null
        // Suppress history whenever the query matches any tool pattern —
        // even if the tool is disabled — to prevent stale tool data from leaking.
        val toolWasAttemptedOrMatched = toolResult != null || toolRouter.matchesAnyToolPattern(userText)

        // SYSTEM_PROMPT mode: rebuild system prompt with tool data before generation.
        // This invalidates the KV cache (~2-3s) but ensures the LLM sees the tool
        // result as authoritative context rather than noise in the user turn.
        if (hasToolData && toolMode == io.somi.data.settings.ToolMode.SYSTEM_PROMPT) {
            val soul = cachedSoul ?: soulPromptLoader.load()
            val systemWithTool = buildString {
                append(soul.take(MAX_SYSTEM_PROMPT_CHARS))
                append("\n\n[Aktuelle Tool-Daten — verwende NUR diese für die folgende Antwort]\n")
                append(toolResult!!.contextBlock)
            }
            withContext(llamaDispatcher) {
                llama.setSystemPrompt(systemWithTool)
            }
        }

        val promptWithContext = buildString {
            // Suppress recall + history when query matches a tool pattern —
            // prevents stale tool data from leaking even when tools are disabled.
            if (!toolWasAttemptedOrMatched && recallContext != null) append(recallContext)
            if (!toolWasAttemptedOrMatched && historyContext != null) append(historyContext)
            if (hasToolData) {
                val contextData = if (toolMode == io.somi.data.settings.ToolMode.COMPACT)
                    toolResult!!.contextBlock.take(800) // ~200 tokens
                else
                    "" // already in system prompt
                if (contextData.isNotEmpty()) {
                    append(contextData)
                    append("\n\nBeantworte jetzt diese Anfrage direkt und präzise auf Basis der obigen Daten: ")
                } else {
                    Log.w(TAG, "tool ${toolResult!!.toolId} contextBlock empty in COMPACT mode")
                }
            } else if (toolResult != null && toolResult.error != null) {
                Log.i(TAG, "tool ${toolResult.toolId} error — proceeding without context: ${toolResult.error}")
            } else if (toolResult == null && toolWasAttemptedOrMatched) {
                // Tool matched by pattern but is disabled — tell So-Mi explicitly
                append("Hinweis: Das passende Tool für diese Anfrage ist gerade deaktiviert. ")
                append("Antworte aus deinem eigenen Wissen, weise aber darauf hin dass keine aktuellen Echtzeitdaten verfügbar sind und die Information veraltet sein könnte.\n\n")
            }
            append(userText)
        }
        Log.i(TAG, "promptWithContext len=${promptWithContext.length} hasToolData=$hasToolData")

        try {
            // v0.11.4: read max-tokens from the persisted SamplerParams
            // each turn so the user-facing slider in Settings actually
            // takes effect. Falls back to DEFAULTS.maxTokens (1024)
            // before SamplerSettingsRepository finishes its first disk
            // load — same behavior as v0.11.3's hardcoded MAX_TOKENS.
            val maxTokens = samplerSettings.params.value.maxTokens
            withContext(llamaDispatcher) {
                llama.generate(promptWithContext, maxTokens = maxTokens)
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
            surfaceError("Hat nicht geklappt. Versuch's nochmal.", cause = t)
        } finally {
            // Restore original system prompt if SYSTEM_PROMPT mode modified it
            if (hasToolData && toolMode == io.somi.data.settings.ToolMode.SYSTEM_PROMPT) {
                runCatching {
                    val soul = cachedSoul ?: soulPromptLoader.load()
                    withContext(llamaDispatcher) {
                        llama.setSystemPrompt(soul.take(MAX_SYSTEM_PROMPT_CHARS))
                    }
                }.onFailure { Log.w(TAG, "system prompt restore failed", it) }
            }
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
        // Auto-dismiss after 5 seconds; cleared earlier if user sends a new message.
        viewModelScope.launch {
            kotlinx.coroutines.delay(5_000L)
            if (_errorBanner.value?.message == message) _errorBanner.value = null
        }
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

        /**
         * v0.15.0 — gap after which a Ready→Ready re-entry counts as a
         * fresh "welcome back" instead of an in-session refresh. 5 min
         * matches MagicOS's typical background-resume window. Below
         * this, the user almost certainly just rotated the device or
         * popped a different app for a moment — greeting them again
         * would be noise.
         */
        const val GREETING_GAP_MS = 5L * 60 * 1000

        // v0.14.0 M6 — in-character ack bubble after a "merk dir"
        // trigger fires. Short by design; soul.md prefers terse over
        // chatty. M9's classifier may eventually replace this with
        // bucket-aware acks ("Notiert unter Personen.").
        const val SAVE_ACK_TEXT = "Hab ich."
    }
}
