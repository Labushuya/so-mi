package io.somi.common.chat

/**
 * Phase-2 chat lifecycle state.
 *
 * Sealed interface — every concrete UI surface must handle every variant
 * (the compiler enforces it via `when` exhaustiveness). Lives in
 * `core-common` so `core-llm`, `core-data`, `core-ui`, and `app` can all
 * reference it without forming a cross-`core-*` dependency cycle.
 *
 * Lifecycle ladder:
 *
 *   Booting
 *        │  hardware probe + soul.md cache + on-disk scan complete
 *        ▼
 *   NoModelInstalled
 *        │  user picks tier on first-launch + taps "download"
 *        ▼
 *   DownloadingModel(progress, totalBytes)
 *        │  WorkManager finishes, sha-256 verified
 *        ▼
 *   LoadingModel
 *        │  llama_load_model_from_file complete
 *        ▼
 *   Idle ◀──────────────────────────────────┐
 *        │  user submits a message          │
 *        ▼                                  │
 *   Generating(promptId, partialResponse)   │
 *        │  Flow<String> completes          │
 *        └──────────────────────────────────┘
 *
 * **Error is a decorator, not a mode.** Earlier versions had
 * `Error(message)` as an exclusive variant — which meant a transient
 * generation failure would put the whole UI into "error mode" and
 * silently disable the composer. The fix is structural: errors are
 * banners painted on top of whatever lifecycle state was running, via
 * [WithBanner]. The composer reads its enabled-flag from the *inner*
 * state of the decorator, so a generation failure no longer locks the
 * input field — the user can dismiss the banner or just keep typing.
 *
 * UI rule: when matching, always use [unwrap] / [banner] (or
 * `if (state is WithBanner)` first) so `is Idle` continues to mean
 * "model loaded, ready to take input" — even if a banner is overlaid.
 */
sealed interface ChatState {

    /**
     * Pre-flight: ChatViewModel is running its init coroutine (hardware
     * probe + soul.md cache + on-disk model scan). UI shows a minimal
     * splash without text or spinner — the LoadingScreen ("Ich richte
     * mich ein…") is reserved for the actual llama_load phase, where
     * the wait is real and the user benefits from feedback.
     *
     * Typical duration: 100–500 ms. If it stretches longer the init
     * coroutine has thrown silently, which is now caught and routed to
     * NoModelInstalled with an error banner — but Booting itself is
     * never the long-tail surface.
     */
    data object Booting : ChatState

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
     * GGUF exists on disk; llama.cpp is mmap-loading. UI shows a
     * spinner so the cold-load latency (5–15 s on Magic V2 for
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
     */
    data class Generating(
        val promptId: Long,
        val partialResponse: String,
    ) : ChatState

    /**
     * Decorator: an error banner painted on top of [inner]. The inner
     * state continues to drive routing and composer-enabled — this is
     * NOT a mode-switch, just a one-line banner the user can dismiss
     * or retry.
     *
     * The wrapper is never wrapped recursively (a new error replaces the
     * banner; see ChatViewModel.surfaceError). So pattern-matchers can
     * safely assume `inner !is WithBanner`.
     *
     * @param inner the lifecycle state that was running when the error
     *   occurred. Routing and composer-enabled read from here.
     * @param message user-facing line (already in soul.md voice — terse,
     *   German, no corpo-smile).
     * @param retryable if true the banner shows a "Erneut versuchen" button.
     * @param cause kept for logcat; not surfaced in UI.
     */
    data class WithBanner(
        val inner: ChatState,
        val message: String,
        val retryable: Boolean = true,
        val cause: Throwable? = null,
    ) : ChatState

    companion object {
        /** Strip a banner if present, returning the underlying lifecycle. */
        fun ChatState.unwrap(): ChatState = if (this is WithBanner) inner else this

        /** The banner overlay if present, else null. */
        fun ChatState.banner(): WithBanner? = this as? WithBanner
    }
}
