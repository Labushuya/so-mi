package io.somi.data.download

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Why the .part sidecar:
 *
 * GGUF files are loaded by mmap'ing a header that contains tensor
 * offsets. If a half-downloaded file sits at the final path, any
 * reader (including a previous crashed app session) may try to mmap
 * it, hit EOF mid-tensor, and either crash or load garbage weights.
 *
 * The .part suffix is a contract: the loader MUST ignore any path
 * ending in .part. Only after a successful SHA-256 match do we rename
 * atomically — readers therefore either see no file or a complete,
 * verified file, never a torn one.
 */
internal object AtomicInstall {

    /**
     * Promote a verified .part to its final filename, atomically when
     * the platform supports it (every modern Android does, for
     * same-volume same-filesystem moves).
     */
    fun promote(partFile: File, finalFile: File) {
        require(partFile.exists()) { "missing .part: $partFile" }
        finalFile.parentFile?.mkdirs()
        try {
            Files.move(
                partFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            // Cross-mount / FUSE / scoped storage — fall back to a
            // non-atomic replace. Both source and target on Android-
            // -app-private storage should always be on the same volume,
            // but the fallback keeps us safe on weird OEM ROMs.
            Files.move(
                partFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    /** Best-effort cleanup after a fatal failure. Exceptions swallowed. */
    fun cleanupOnFailure(partFile: File) {
        runCatching { if (partFile.exists()) partFile.delete() }
    }
}
