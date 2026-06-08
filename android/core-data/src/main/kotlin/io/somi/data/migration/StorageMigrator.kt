package io.somi.data.migration

import android.content.Context
import android.util.Log
import io.somi.data.StorageRoots
import java.io.File

/**
 * v0.15.0 — one-shot migration from scattered legacy paths to
 * the consolidated `SoMi/` root.
 *
 * **Trigger:** SoMiApp.onCreate calls [runIfNeeded] right after
 * super.onCreate (so Hilt's component IS already up — different
 * from the earlier-stage runIfNeededBeforeHilt approach the
 * planner mentioned). We're NOT Hilt-injected; this class takes
 * a plain Context.
 *
 * **Sentinel:** [StorageRoots.migrationSentinel] — empty file
 * created at the end of a successful run. Existence == "done,
 * skip". If the file is missing, runs even on subsequent cold
 * starts (idempotent: source paths are removed at the end of
 * each successful slice, so a partial run that resumes won't
 * double-copy).
 *
 * **Failure mode:** any individual slice that throws is logged
 * and skipped; the migration moves on. The sentinel is only
 * written if the whole run completes cleanly. So a partial
 * migration leaves the user on legacy paths AND we'll retry
 * next launch.
 */
class StorageMigrator(private val context: Context) {

    /**
     * v0.15.0 — Run on a dedicated background thread so the cross-
     * volume copy doesn't block Application.onCreate (the Room DB
     * migration alone can be many MB after months of chat). Returns
     * the latch the caller should `await()` ONLY before opening the
     * Room database — every other slice is best-effort and can race
     * with consumers (the app starts up against legacy paths if the
     * coroutine hasn't finished those slices yet, and corrects on
     * the next launch via the sentinel).
     *
     * For the LLM/memory slices (same-volume rename + free), the
     * blocking wait is acceptable too — they're O(directory entries),
     * not O(bytes).
     *
     * Caller pattern (SoMiApp.onCreate):
     *   val latch = StorageMigrator(this).runIfNeededAsync()
     *   // ... rest of onCreate, no DB access yet
     *   // Hilt's DatabaseModule is responsible for awaiting the
     *   // latch before opening the DB.
     */
    fun runIfNeeded() {
        val sentinel = StorageRoots.migrationSentinel(context)
        if (sentinel.exists()) {
            Log.i(TAG, "migration sentinel present; skipping")
            return
        }
        Log.i(TAG, "running storage migration v1 (synchronous)")
        runSlices(sentinel)
    }

    /**
     * Async wrapper. Spawns a daemon thread and returns immediately.
     * Use [runIfNeeded] if the caller is itself already on a
     * background thread (tests, services).
     */
    fun runIfNeededAsync(): Thread? {
        val sentinel = StorageRoots.migrationSentinel(context)
        if (sentinel.exists()) {
            Log.i(TAG, "migration sentinel present; skipping")
            return null
        }
        return Thread({
            Log.i(TAG, "running storage migration v1 (async)")
            runSlices(sentinel)
        }, "so-mi-storage-migrator").apply {
            isDaemon = true
            start()
        }
    }

    private fun runSlices(sentinel: File) {
        val t0 = System.currentTimeMillis()
        var allOk = true
        allOk = migrateLlm() && allOk
        allOk = migrateMemory() && allOk
        allOk = migrateSoul() && allOk
        allOk = migrateDb() && allOk
        allOk = migrateSettings() && allOk

        if (allOk) {
            try {
                sentinel.writeText("v1\n")
                Log.i(TAG, "migration complete (${System.currentTimeMillis() - t0} ms); sentinel written")
            } catch (t: Throwable) {
                Log.w(TAG, "migration finished but sentinel write failed", t)
            }
        } else {
            Log.w(TAG, "migration incomplete after ${System.currentTimeMillis() - t0} ms; will retry on next launch")
        }
    }

    /** Old: $externalFilesDir/models/  →  $externalFilesDir/SoMi/llm/ */
    private fun migrateLlm(): Boolean {
        val external = context.getExternalFilesDir(null) ?: return true
        val legacy = File(external, "models")
        if (!legacy.exists() || !legacy.isDirectory) return true
        val target = StorageRoots.llm(context)
        return moveContents(legacy, target, label = "llm")
    }

    /** Old: $externalFilesDir/memory/  →  $externalFilesDir/SoMi/memory/ */
    private fun migrateMemory(): Boolean {
        val external = context.getExternalFilesDir(null) ?: return true
        val legacy = File(external, "memory")
        if (!legacy.exists() || !legacy.isDirectory) return true
        val target = StorageRoots.memory(context)
        return moveContents(legacy, target, label = "memory")
    }

    /** Old: $filesDir/soul/  →  $externalFilesDir/SoMi/soul/ — cross-volume copy. */
    private fun migrateSoul(): Boolean {
        val legacy = File(context.filesDir, "soul")
        if (!legacy.exists() || !legacy.isDirectory) return true
        val target = StorageRoots.soul(context)
        return copyAndDeleteTree(legacy, target, label = "soul")
    }

    /** Old: $filesDir/somi.db (+ -shm + -wal)  →  $externalFilesDir/SoMi/db/somi.db — cross-volume. */
    private fun migrateDb(): Boolean {
        val legacyDir = context.filesDir
        val targetDir = StorageRoots.db(context)
        var anyMoved = false
        var allOk = true
        for (suffix in listOf("", "-shm", "-wal")) {
            val src = File(legacyDir, "somi.db$suffix")
            if (!src.exists()) continue
            val dst = File(targetDir, "somi.db$suffix")
            try {
                src.copyTo(dst, overwrite = true)
                if (!src.delete()) {
                    Log.w(TAG, "db migration: could not delete legacy $src")
                }
                anyMoved = true
            } catch (t: Throwable) {
                Log.e(TAG, "db migration failed for $src", t)
                allOk = false
            }
        }
        if (anyMoved) Log.i(TAG, "db migration: moved Room DB to SoMi/db/")
        return allOk
    }

    /** Old: $filesDir/settings/  →  $externalFilesDir/SoMi/settings/ — cross-volume. */
    private fun migrateSettings(): Boolean {
        val legacy = File(context.filesDir, "settings")
        if (!legacy.exists() || !legacy.isDirectory) return true
        val target = StorageRoots.settings(context)
        return copyAndDeleteTree(legacy, target, label = "settings")
    }

    /**
     * Same-volume rename for free. Falls back to copy+delete if
     * renameTo fails (cross-volume edge case under FUSE).
     */
    private fun moveContents(src: File, dst: File, label: String): Boolean {
        var ok = true
        for (entry in src.listFiles().orEmpty()) {
            val target = File(dst, entry.name)
            if (target.exists()) {
                Log.i(TAG, "$label migration: target $target exists, skipping")
                continue
            }
            try {
                if (!entry.renameTo(target)) {
                    if (entry.isDirectory) {
                        entry.copyRecursively(target, overwrite = false)
                        entry.deleteRecursively()
                    } else {
                        entry.copyTo(target, overwrite = false)
                        entry.delete()
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "$label migration failed for $entry", t)
                ok = false
            }
        }
        // Try to clean up the now-empty legacy dir.
        runCatching { src.delete() }
        return ok
    }

    /**
     * Cross-volume copy + delete. Used for filesDir → externalFilesDir
     * moves where renameTo would fail with EXDEV.
     */
    private fun copyAndDeleteTree(src: File, dst: File, label: String): Boolean {
        return try {
            src.copyRecursively(dst, overwrite = false) { _, exception ->
                Log.w(TAG, "$label: copy threw; continuing", exception)
                kotlin.io.OnErrorAction.SKIP
            }
            src.deleteRecursively()
            Log.i(TAG, "$label migration: copied tree to ${dst.absolutePath}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "$label migration failed", t)
            false
        }
    }

    private companion object {
        const val TAG = "StorageMigrator"
    }
}
