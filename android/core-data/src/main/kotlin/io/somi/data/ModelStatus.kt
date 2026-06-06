package io.somi.data

/**
 * Lifecycle of one model on this device.
 *
 * The ModelManager exposes a Flow<ModelStatus> per model id; the picker
 * UI in Phase 2.5 collects it. Status transitions are monotonic until a
 * terminal — Installed and Failed are terminal until the user re-asks.
 *
 *   NotInstalled
 *      │  manager.startDownload(id)
 *      ▼
 *   Downloading(bytes, total)         ← repeated emissions, ~2 Hz
 *      │  download finished, stream-digest matched
 *      ▼
 *   Verifying                         ← post-write SHA-256 sanity check
 *      │  hash OK; atomic rename .part → .gguf
 *      ▼
 *   Installed
 *
 *   Failed(reason)                    ← terminal until retried
 */
sealed interface ModelStatus {

    /** No bytes on disk and no work in flight. */
    data object NotInstalled : ModelStatus

    /**
     * @param bytesDownloaded sum across all parts that have completed +
     *   the in-flight part's current offset
     * @param bytesTotal sum of every part's manifest size
     * @param currentPart 1-indexed part number (e.g. 1 of 2 for a 7B shard)
     * @param totalParts total shard count from the manifest
     */
    data class Downloading(
        val bytesDownloaded: Long,
        val bytesTotal: Long,
        val currentPart: Int,
        val totalParts: Int,
    ) : ModelStatus {
        /** 0f..1f. */
        val progress: Float get() =
            if (bytesTotal > 0L) (bytesDownloaded.toFloat() / bytesTotal.toFloat())
                .coerceIn(0f, 1f)
            else 0f
    }

    /** Bytes complete; SHA-256 verify in progress. Brief — seconds. */
    data object Verifying : ModelStatus

    /**
     * Final files on disk, all SHA-256 hashes verified, ready for
     * `LlamaContext.load(modelFile)`.
     *
     * @param mainFile the path to pass to llama.cpp. For multi-shard
     *   models this is the part 00001 file; the loader auto-discovers
     *   siblings.
     */
    data class Installed(val mainFile: java.io.File) : ModelStatus

    /**
     * Terminal failure. The .part file may or may not still exist —
     * for transient errors (network) we keep it for resume; for fatal
     * (SHA mismatch, storage full) we delete it. The reason string is
     * user-facing (German, soul.md voice in the eventual UI text).
     */
    data class Failed(val reason: Reason, val message: String) : ModelStatus

    enum class Reason {
        /** No internet, server 5xx, timeout — retry on next resume. */
        TRANSIENT_NETWORK,

        /** SHA mismatch after a complete download — file corrupt or upstream changed. */
        CHECKSUM_MISMATCH,

        /** Not enough storage before we even started. */
        STORAGE_FULL,

        /** User explicitly cancelled. */
        CANCELLED,

        /** Anything else; logcat has the stacktrace. */
        UNKNOWN,
    }
}
