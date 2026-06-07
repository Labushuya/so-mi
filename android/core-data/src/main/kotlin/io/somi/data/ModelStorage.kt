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
     * Storage location: `getExternalFilesDir(null)/models/`. App-private
     * external storage:
     *  - Plain File API works (no MANAGE_EXTERNAL_STORAGE needed)
     *  - llama.cpp can mmap the file directly
     *  - Survives reboot but NOT uninstall and NOT "Clear data"
     *
     * Earlier attempts to use `/sdcard/Documents/SoMi-Models/` for
     * uninstall-survival failed: that path requires either
     * MANAGE_EXTERNAL_STORAGE (a heavy permission we declined to ask for)
     * or MediaStore content URIs (which llama.cpp cannot mmap by path).
     * The trade-off is real but worth it: re-download on uninstall is
     * painful, but better than an app that doesn't start at all.
     *
     * Migration from earlier path layouts is best-effort and never
     * blocks startup — see [tryMigrateLegacy].
     */
    val modelsDir: File by lazy {
        val external = context.getExternalFilesDir(null)
        val target = File(external ?: context.filesDir, MODELS_SUBDIR)
        target.mkdirs()
        Log.i(TAG, "modelsDir = ${target.absolutePath}")

        // Best-effort: scan well-known older paths and move anything we
        // find into the canonical location. NEVER throws — a failed
        // migration just leaves the legacy file in place.
        runCatching { tryMigrateLegacy(target) }
            .onFailure { Log.w(TAG, "tryMigrateLegacy failed (non-fatal)", it) }

        target
    }

    /**
     * Best-effort migration from the v0.10.0 misadventure
     * (/sdcard/Documents/SoMi-Models/) into the canonical
     * externalFilesDir/models/ location.
     *
     * If we can read the legacy path (we usually can — read-only access
     * to public Documents/ is allowed even without MANAGE_EXTERNAL_STORAGE
     * for files the app itself created in v0.10.0), we copy each file
     * into the new root. If the read fails, we silently skip — the user
     * just re-downloads.
     *
     * Idempotent: re-running with files already in place is a no-op.
     */
    private fun tryMigrateLegacy(newRoot: File) {
        val legacy = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOCUMENTS,
            ),
            "SoMi-Models",
        )
        if (!legacy.exists() || !legacy.isDirectory) return

        legacy.listFiles()?.forEach { srcDir ->
            if (!srcDir.isDirectory) return@forEach
            val dstDir = File(newRoot, srcDir.name)
            if (dstDir.exists() && dstDir.listFiles()?.isNotEmpty() == true) {
                Log.i(TAG, "skip migrate ${srcDir.name}: already in newRoot")
                return@forEach
            }
            try {
                dstDir.mkdirs()
                srcDir.listFiles()?.forEach { f ->
                    f.copyTo(File(dstDir, f.name), overwrite = true)
                }
                Log.i(TAG, "migrated ${srcDir.name} from legacy Documents path")
            } catch (e: Exception) {
                Log.w(TAG, "migrate ${srcDir.name} skipped: ${e.message}")
            }
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

    /** True iff every part of this manifest can be found in any known historical location. */
    fun isInstalled(manifest: ModelManifest): Boolean =
        findInstalledRoot(manifest) != null

    /**
     * Path to feed `LlamaContext.load(...)`; null if not installed.
     *
     * Searches every historical storage location so users coming from
     * v0.7.x / v0.9.x / v0.10.x don't lose their 4.4 GB downloads when
     * we change the canonical path under their feet. The first root
     * that has all parts wins — no migration, no copy, llama.cpp opens
     * the file where it actually is.
     */
    fun mainFileFor(manifest: ModelManifest): File? {
        val root = findInstalledRoot(manifest) ?: return null
        return File(root, manifest.parts[0].filename)
    }

    /**
     * Walk every known historical storage layout and return the first
     * directory containing all parts of [manifest]. Order matters: the
     * canonical (current) location wins if both have valid copies, so
     * a future cleanup can prune the legacy locations safely.
     *
     * Logged at INFO so future "where is my model?" debugging is one
     * logcat away.
     */
    private fun findInstalledRoot(manifest: ModelManifest): File? {
        val candidates = candidateRoots(manifest.id)
        for (root in candidates) {
            val allPresent = manifest.parts.all { File(root, it.filename).exists() }
            if (allPresent) {
                Log.i(TAG, "model ${manifest.id} found at: ${root.absolutePath}")
                return root
            }
        }
        Log.i(TAG, "model ${manifest.id} NOT FOUND in any of:")
        candidates.forEach { Log.i(TAG, "  - ${it.absolutePath}") }
        return null
    }

    /**
     * Every storage path we have ever used for [modelId], canonical first.
     * Order:
     *   1. modelsDir/<id>/   — current canonical (externalFilesDir/models/<id>/)
     *   2. /sdcard/Documents/SoMi-Models/<id>/  — v0.10.0 misadventure
     *   3. context.filesDir/models/<id>/  — fallback when externalFilesDir was null
     *
     * Adding a path here is the migration. Removing one needs a
     * deprecation cycle so users on old paths still find their model.
     */
    private fun candidateRoots(modelId: String): List<File> = buildList {
        add(File(modelsDir, modelId))

        val docsDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOCUMENTS,
            ),
            "SoMi-Models",
        )
        add(File(docsDir, modelId))

        add(File(context.filesDir, "$MODELS_SUBDIR/$modelId"))
    }

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
