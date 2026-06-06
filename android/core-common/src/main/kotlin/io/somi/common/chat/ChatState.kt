package io.somi.common.chat

/**
 * Phase-2 chat lifecycle state.
 *
 * Sealed interface — every concrete UI surface must handle every variant
 * (the compiler enforces it via `when` exhaustiveness). Lives in
 * `core-common` so `core-llm`, `core-data`, `core-ui`, and `app` can all
 * reference it without forming a cross-`core-*` dependency cycle.
 *
 * The full Phase-2 lifecycle, in order:
 *
 *   NoModelInstalled
 *        │  user picks tier on first-launch + taps "download"
 *        ▼
 *   DownloadingModel(progress, totalBytes)
 *        │  WorkManager finishes, sha-256 verified
 *        ▼
 *   LoadingModel
 *        │  llama_load_model_from_file + warm-pass complete
 *        ▼
 *   Idle ◀──────────────────────────────────┐
 *        │  user submits a message          │
 *        ▼                                  │
 *   Generating(promptId, partialResponse)   │
 *        │  Flow<String> completes          │
 *        └──────────────────────────────────┘
 *
 *   Error(message)  — terminal-ish; UI must offer retry / reset.
 *
 * Phase 2.1 only constructs LoadingModel from the smoke screen. The
 * rest of the variants come online with later sub-phases (2.2 hardware
 * detection → NoModelInstalled, 2.4 → DownloadingModel, 2.6 → Idle +
 * Generating).
 */
sealed interface ChatState {

    /**
     * No GGUF on disk yet. UI shows the Hardware-Ampel + model picker.
     * Phase 2.5.
     */
    data object NoModelInstalled : ChatState

    /**
     * WorkManager is fetching the model file.
     *
     * @param bytesDownloaded current progress in bytes
     * @param bytesTotal expected total size (from the Content-Length header
     *   or the manifest entry). May be -1 before the request resolves.
     */
    data class DownloadingModel(
        val bytesDownloaded: Long,
        val bytesTotal: Long,
    ) : ChatState {
        /** 0f..1f, or null if total size is not yet known. */
        val progress: Float?
            get() = if (bytesTotal > 0L) {
                (bytesDownloaded.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    /**
     * GGUF exists on disk; llama.cpp is mmap-loading + running the
     * post-download warm pass. UI shows a spinner with "Modell wird
     * vorgewärmt …" so the cold-load latency (5–15 s on Magic V2 for
     * Qwen2.5 7B Q4_K_M) doesn't read as a freeze.
     */
    data object LoadingModel : ChatState

    /**
     * Model loaded, no generation in flight. Composer enabled. The only
     * state where ChatViewModel.submit() is allowed.
     */
    data object Idle : ChatState

    /**
     * A generation is in flight. The assistant bubble keyed by [promptId]
     * is being live-typed via [partialResponse]; on each Flow<String>
     * chunk the ViewModel emits a new [Generating] with the appended text.
     *
     * @param promptId monotonic id of the user prompt that triggered this
     *   generation. Used by the UI to key the streaming bubble in the
     *   LazyColumn so token chunks land in the same composable.
     * @param partialResponse text accumulated so far. The terminal state
     *   transitions to [Idle]; the partial text becomes the final
     *   AssistantMessage in the conversation history.
     */
    data class Generating(
        val promptId: Long,
        val partialResponse: String,
    ) : ChatState

    /**
     * Anything went wrong: model download failed, model load failed, JNI
     * crashed, OOM, etc. The message is user-facing and must already be in
     * soul.md voice (German, terse, no corpo-smile) — that's the
     * ViewModel's responsibility, not the caller's.
     *
     * @param message user-facing line
     * @param cause the throwable, kept for logcat; not surfaced in the UI
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : ChatState
}
