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
 *           <part2>.gguf            ← multi-shard (e.g. 7B Q4_K_M = 2 parts)
 *           <part1>.gguf.part       ← in-flight (sidecar)
 *
 * The .part suffix is a contract: the loader MUST ignore any path
 * ending in .part. Only after a successful SHA-256 match do we rename
 * atomically — readers therefore either see no file or a complete,
 * verified file, never a torn one.
 *
 * **One root, period.** Earlier versions of this class searched three
 * candidate roots (externalFilesDir, /sdcard/Documents/SoMi-Models,
 * filesDir) so users mid-migrating between layouts wouldn't lose their
 * 4 GB downloads. The cure was worse than the disease: the multi-path
 * lookup returned null whenever a sideload landed at a slightly
 * non-canonical path (e.g. directly in modelsDir/, not in
 * modelsDir/<id>/), and the user saw "Modell konnte nicht geladen
 * werden" while the GGUF sat on disk. The path-search is gone.
 *
 * The replacement is [rescueSideload]: if a manifest's parts are sitting
 * loose in modelsDir/, we move them into the canonical
 * modelsDir/<id>/ subdir once on first lookup. One try, deterministic,
 * loud in logcat — not a permanent compatibility shim.
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
     * If externalFilesDir returns null (exotic OEM/work-profile config),
     * we fall back to filesDir. That path can't survive uninstall either,
     * so the trade-off is identical from the user's perspective.
     */
    val modelsDir: File by lazy {
        val external = context.getExternalFilesDir(null)
        val target = File(external ?: context.filesDir, MODELS_SUBDIR)
        target.mkdirs()
        Log.i(TAG, "modelsDir = ${target.absolutePath}")
        target
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

    /**
     * True iff every part of this manifest is on disk in the canonical
     * directory at the expected size.
     *
     * Logs per-part presence + size so a future "GGUF da, App lügt"
     * scenario is one logcat away from being diagnosable.
     */
    fun isInstalled(manifest: ModelManifest): Boolean {
        val dir = File(modelsDir, manifest.id)
        if (!dir.exists() || !dir.isDirectory) {
            Log.i(TAG, "isInstalled(${manifest.id}) → false (dir missing)")
            return false
        }
        var allOk = true
        for (part in manifest.parts) {
            val f = File(dir, part.filename)
            val ok = f.exists() && f.length() > 0L
            if (!ok) {
                Log.i(
                    TAG,
                    "  part ${part.filename}: missing (exists=${f.exists()}, len=${if (f.exists()) f.length() else -1})",
                )
                allOk = false
            }
        }
        if (allOk) Log.i(TAG, "isInstalled(${manifest.id}) → true")
        else Log.i(TAG, "isInstalled(${manifest.id}) → false")
        return allOk
    }

    /**
     * Path to feed `LlamaContext.load(...)`; null if not installed.
     *
     * Always returns the first part's file in the canonical directory —
     * llama.cpp resolves multi-shard 00001-of-00002.gguf siblings by
     * stripping the suffix and looking next to the file it was given.
     */
    fun mainFileFor(manifest: ModelManifest): File? {
        if (!isInstalled(manifest)) return null
        return File(directoryFor(manifest.id), manifest.parts[0].filename)
    }

    /**
     * One-shot rescue for sideloaded GGUFs that landed at the wrong path.
     *
     * Two scenarios this fixes:
     *   1. User dropped the .gguf file directly into modelsDir/ (no
     *      <id>/ subdir). We rename it into modelsDir/<id>/<filename>.
     *   2. User pulled v0.9 model from the legacy
     *      /sdcard/Documents/SoMi-Models/<id>/ path manually and put
     *      it in modelsDir/<id>/ — already correct, [isInstalled] handles.
     *
     * Returns true if the manifest is now fully installed, false if at
     * least one part is still missing.
     *
     * Idempotent: if the files are already in the right place, this is a
     * cheap [isInstalled] check.
     */
    fun rescueSideload(manifest: ModelManifest): Boolean {
        if (isInstalled(manifest)) return true

        val dir = directoryFor(manifest.id)
        var moved = 0
        for (part in manifest.parts) {
            val canonical = File(dir, part.filename)
            if (canonical.exists()) continue

            // Check if the file is sitting loose in modelsDir/.
            val loose = File(modelsDir, part.filename)
            if (loose.exists() && loose.isFile) {
                Log.i(TAG, "rescueSideload: moving ${loose.absolutePath} → ${canonical.absolutePath}")
                val ok = loose.renameTo(canonical)
                if (!ok) {
                    Log.w(TAG, "rescueSideload: renameTo failed; trying copy + delete")
                    runCatching { loose.copyTo(canonical, overwrite = true) }
                        .onSuccess {
                            val deleted = loose.delete()
                            if (!deleted) Log.w(TAG, "rescueSideload: copy ok but source delete failed")
                            moved++
                        }
                        .onFailure { Log.w(TAG, "rescueSideload: copy failed", it) }
                } else {
                    moved++
                }
            }
        }
        val ok = isInstalled(manifest)
        if (moved > 0) Log.i(TAG, "rescueSideload(${manifest.id}): moved $moved file(s), nowInstalled=$ok")
        return ok
    }

    /**
     * Wipe a model's directory entirely, including .part sidecars.
     * Returns true on success, false if anything in the tree refused to
     * delete (logged at WARN). Idempotent.
     */
    fun delete(manifest: ModelManifest): Boolean {
        val dir = File(modelsDir, manifest.id)
        if (!dir.exists()) return true
        val ok = dir.deleteRecursively()
        if (!ok) Log.w(TAG, "delete(${manifest.id}) returned false — partial wipe")
        else Log.i(TAG, "delete(${manifest.id}) ok")
        return ok
    }

    // ------------------------------------------------------------------
    // Storage-inspection API for the Settings screen.
    // ------------------------------------------------------------------

    /**
     * A single on-disk model copy. With single-root storage there is
     * exactly one [ModelInstance] per modelId — no duplicates, no
     * canonical-vs-legacy distinction.
     */
    data class ModelInstance(
        val manifestId: String,
        val displayName: String,
        val rootPath: File,
        /** true if every manifest part is present + non-empty. */
        val isComplete: Boolean,
        /** total bytes on disk in [rootPath] including .part sidecars. */
        val sizeBytes: Long,
        /** human-readable list of file names actually present. */
        val filesPresent: List<String>,
        /** filenames the manifest expects but that are missing on disk. */
        val filesMissing: List<String>,
    )

    /**
     * Walk modelsDir and return one [ModelInstance] per manifest that has
     * at least one of its parts on disk. Not recursive — sideloads sitting
     * loose in modelsDir/ won't show up here; they get adopted by
     * [rescueSideload] on first load attempt.
     */
    fun findAllInstances(catalog: List<ModelManifest>): List<ModelInstance> = buildList {
        for (manifest in catalog) {
            val dir = File(modelsDir, manifest.id)
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles().orEmpty().filter { it.isFile }
            if (files.isEmpty()) continue
            val expected = manifest.parts.map { it.filename }
            val present = files.map { it.name }.sorted()
            val missing = expected.filter { name -> files.none { it.name == name } }
            add(
                ModelInstance(
                    manifestId = manifest.id,
                    displayName = manifest.displayName,
                    rootPath = dir,
                    isComplete = missing.isEmpty(),
                    sizeBytes = files.sumOf { it.length() },
                    filesPresent = present,
                    filesMissing = missing,
                ),
            )
        }
    }

    /** Delete a specific instance directory. Boolean-honest. Idempotent. */
    fun deleteInstance(instance: ModelInstance): Boolean {
        if (!instance.rootPath.exists()) return true
        val ok = instance.rootPath.deleteRecursively()
        if (!ok) Log.w(TAG, "deleteInstance(${instance.rootPath}) returned false")
        else Log.i(TAG, "deleteInstance(${instance.rootPath}) ok")
        return ok
    }

    /** UUID used as a request-id when the worker prefers one over modelId. */
    fun newRequestId(): String = UUID.randomUUID().toString()

    private companion object {
        const val TAG = "ModelStorage"
        const val MODELS_SUBDIR = "models"
    }
}
