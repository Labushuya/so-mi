package io.somi.data

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Disk layout for downloaded GGUF models.
 *
 *   $externalFilesDir/models/
 *       $modelId/
 *           <part1>.gguf            ← final, SHA-256 verified
 *           <part2>.gguf
 *           <part1>.gguf.part       ← in-flight (sidecar)
 *
 * The .part suffix is a contract: the loader MUST ignore any path
 * ending in .part. Only after a successful SHA-256 match do we rename
 * atomically — readers therefore either see no file or a complete,
 * verified file, never a torn one.
 *
 * Storage choice: `getExternalFilesDir(null)` because Magic V2's UFS
 * gives us hundreds of GB on the same volume as `filesDir`, and the
 * external path is MTP-visible so the user can sanity-check that a
 * 4.7 GiB download actually landed. Falls back to `filesDir` on
 * exotic OEM/work-profile configurations where externalFilesDir
 * returns null.
 *
 * NOT part of this class:
 *  - HTTP / WorkManager (lives in [io.somi.data.download.ModelDownloadWorker])
 *  - SHA-256 streaming digest (lives in the worker too)
 *  - Status emission (lives in [ModelManager])
 */
@Singleton
class ModelStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Root directory for all model artifacts. Created on first access. */
    val modelsDir: File by lazy {
        val external = context.getExternalFilesDir(null)
        val base = external ?: context.filesDir
        File(base, MODELS_SUBDIR).apply { mkdirs() }
    }

    /** Per-model directory; multi-shard files cohabit here. */
    fun directoryFor(modelId: String): File =
        File(modelsDir, modelId).apply { mkdirs() }

    /** Final on-disk path for a part. */
    fun finalFile(modelId: String, partFilename: String): File =
        File(directoryFor(modelId), partFilename)

    /** In-flight sidecar for a part. */
    fun partFile(modelId: String, partFilename: String): File =
        File(directoryFor(modelId), "$partFilename.part")

    /**
     * Best-effort: ask the platform to free [bytes] before we start a
     * download. Avoids us getting half-way through a 4 GB pull and
     * dying with ENOSPC.
     *
     * @return true if the system allocated (or believes it can allocate)
     *   the requested space; false if it gave up. The caller decides
     *   whether to abort or proceed.
     */
    fun allocateBytes(bytes: Long): Boolean {
        return try {
            val sm = context.getSystemService(StorageManager::class.java)
            val uuid = sm.getUuidForPath(modelsDir)
            val allocatable = sm.getAllocatableBytes(uuid)
            if (allocatable < bytes) {
                Log.w(TAG, "allocatable=$allocatable < requested=$bytes")
                return false
            }
            sm.allocateBytes(uuid, bytes)
            true
        } catch (e: Exception) {
            Log.w(TAG, "allocateBytes($bytes) failed", e)
            // Don't block on the preflight failure; the actual write will
            // surface ENOSPC if we really can't fit.
            true
        }
    }

    /** True iff every part of this manifest has been verified + renamed. */
    fun isInstalled(manifest: ModelManifest): Boolean =
        manifest.parts.all { finalFile(manifest.id, it.filename).exists() }

    /** Path to feed `LlamaContext.load(...)`; null if not installed. */
    fun mainFileFor(manifest: ModelManifest): File? =
        if (isInstalled(manifest)) finalFile(manifest.id, manifest.parts[0].filename) else null

    /**
     * Wipe a model's directory entirely, including .part sidecars.
     * Called on user "remove" or after a fatal verification failure.
     */
    fun delete(manifest: ModelManifest) {
        directoryFor(manifest.id).deleteRecursively()
    }

    /** UUID used as a request-id when the worker prefers one over modelId. */
    fun newRequestId(): String = UUID.randomUUID().toString()

    private companion object {
        const val TAG = "ModelStorage"
        const val MODELS_SUBDIR = "models"
    }
}
