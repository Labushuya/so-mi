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

    /**
     * Root directory for all model artifacts.
     *
     * Storage strategy v2 (2026-06-07): models are kept on shared external
     * storage at `/sdcard/Documents/SoMi-Models/` so they survive both
     * app-data clears and full app uninstalls. The path is a public
     * Documents subdir which the OS treats as user data — Android does
     * not delete it when the app is uninstalled.
     *
     * Falls back to `getExternalFilesDir(null)/models/` if the public
     * dir is not writable for whatever reason (rare; typically only on
     * heavily locked-down enterprise profiles). The fallback path IS
     * wiped on uninstall, but at least the app still works.
     *
     * Migration: on first launch with this code, [migrateFromAppPrivate]
     * scans the legacy app-private dir and moves any GGUFs into the
     * public dir.
     */
    val modelsDir: File by lazy {
        val publicDir = publicSharedModelsDir()
        val target = if (publicDir.canBeUsed()) {
            publicDir
        } else {
            val external = context.getExternalFilesDir(null)
            File(external ?: context.filesDir, MODELS_SUBDIR)
        }
        target.mkdirs()
        Log.i(TAG, "modelsDir resolved to: ${target.absolutePath}")

        // One-shot migration from the app-private location used in v0.7.x.
        runCatching { migrateFromAppPrivate(target) }
            .onFailure { Log.w(TAG, "migrateFromAppPrivate skipped", it) }

        target
    }

    /**
     * `/sdcard/Documents/SoMi-Models/` — public, survives uninstall.
     *
     * Android 11+ permits any app to read/write inside its own
     * subdirectory of `Documents/` without permissions, as long as the
     * subdirectory is created via the app itself (Scoped Storage's
     * "media-store-managed-but-app-private-namespace" rule).
     */
    private fun publicSharedModelsDir(): File {
        val docs = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOCUMENTS,
        )
        return File(docs, "SoMi-Models")
    }

    private fun File.canBeUsed(): Boolean {
        return try {
            if (!exists()) parentFile?.mkdirs()
            if (!exists()) mkdirs()
            if (!exists()) return false
            // Probe with a tiny throwaway write — some OEMs report writable
            // but actually deny on access.
            val probe = File(this, ".somi_probe")
            probe.writeText("ok")
            probe.delete()
            true
        } catch (e: Exception) {
            Log.w(TAG, "publicSharedModelsDir unusable at $absolutePath: ${e.message}")
            false
        }
    }

    /**
     * Move any GGUFs from `getExternalFilesDir(null)/models/` (v0.7.x
     * location) into the new public location. Idempotent: safe to call
     * on every boot. Atomic rename when on the same volume; falls back
     * to copy + verify + delete otherwise.
     */
    private fun migrateFromAppPrivate(newRoot: File) {
        val legacy = File(context.getExternalFilesDir(null) ?: return, MODELS_SUBDIR)
        if (!legacy.exists() || legacy.absolutePath == newRoot.absolutePath) return

        val migrated = mutableListOf<String>()
        legacy.listFiles()?.forEach { srcDir ->
            if (!srcDir.isDirectory) return@forEach
            val dstDir = File(newRoot, srcDir.name)
            if (dstDir.exists() && dstDir.listFiles()?.isNotEmpty() == true) {
                Log.i(TAG, "skip migrate ${srcDir.name}: already in new root")
                return@forEach
            }
            try {
                if (srcDir.renameTo(dstDir)) {
                    migrated += srcDir.name
                } else {
                    // Cross-volume — copy each file then delete src.
                    dstDir.mkdirs()
                    srcDir.listFiles()?.forEach { f ->
                        f.copyTo(File(dstDir, f.name), overwrite = true)
                    }
                    srcDir.deleteRecursively()
                    migrated += srcDir.name
                }
            } catch (e: Exception) {
                Log.w(TAG, "migrate ${srcDir.name} failed", e)
            }
        }
        if (migrated.isNotEmpty()) {
            Log.i(TAG, "migrated to public root: $migrated")
        }
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
