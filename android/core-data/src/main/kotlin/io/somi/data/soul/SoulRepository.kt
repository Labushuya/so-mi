package io.somi.data.soul

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.11.4 — file-backed soul.md repository.
 *
 * Layout under `$filesDir/soul/`:
 *   soul.md                          — live override (read by AssetsSoulPromptLoader)
 *   backups/<epochMillis>.md         — rolling backups, last [MAX_BACKUPS] kept
 *
 * Why a plain file and not DataStore / Room: soul.md is text the user
 * edits in a TextField. A flat UTF-8 file is trivially diffable, dump-
 * able via adb, and survives app updates the same way Room does (the
 * filesDir/ guarantee). EncryptedSharedPreferences would be overkill —
 * the persona is not a secret, it's the persona.
 *
 * Threading: every public function is `suspend` and runs on
 * [Dispatchers.IO]. Safe to call from the main thread.
 */
@Singleton
class SoulRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val rootDir: File by lazy {
        File(context.filesDir, "soul").apply { mkdirs() }
    }

    private val backupsDir: File by lazy {
        File(rootDir, "backups").apply { mkdirs() }
    }

    private val overrideFile: File get() = File(rootDir, "soul.md")

    private val _backups = MutableStateFlow<List<SoulBackup>>(emptyList())
    val backups: Flow<List<SoulBackup>> = _backups.asStateFlow()

    /** True if a user-edited override exists (vs. plain factory default). */
    suspend fun hasOverride(): Boolean = withContext(Dispatchers.IO) {
        overrideFile.exists() && overrideFile.length() > 0L
    }

    /** Read the live override, or null if only the asset default is in play. */
    suspend fun readOverride(): String? = withContext(Dispatchers.IO) {
        if (!overrideFile.exists()) null
        else runCatching { overrideFile.readText(Charsets.UTF_8) }
            .onFailure { Log.w(TAG, "override read failed", it) }
            .getOrNull()
    }

    /**
     * Persist [text] as the new override. Before writing, the previous
     * override (if any) is rotated into backups/<previousMtime>.md so
     * the user can roll back. Backup count is capped at [MAX_BACKUPS];
     * oldest entries are pruned.
     *
     * Returns the freshly written override file's mtime, or null on
     * write failure.
     */
    suspend fun save(text: String): Long? = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "Cannot persist a blank soul" }
        try {
            // Rotate the existing override into backups/.
            if (overrideFile.exists() && overrideFile.length() > 0L) {
                val stamp = overrideFile.lastModified()
                val backupFile = File(backupsDir, "$stamp.md")
                runCatching { overrideFile.copyTo(backupFile, overwrite = true) }
                    .onFailure { Log.w(TAG, "backup rotation failed", it) }
            }
            overrideFile.writeText(text, Charsets.UTF_8)
            pruneBackups()
            refreshBackupList()
            overrideFile.lastModified()
        } catch (t: Throwable) {
            Log.e(TAG, "save failed", t)
            null
        }
    }

    /** Restore an earlier backup as the live override. */
    suspend fun restore(backup: SoulBackup): Boolean = withContext(Dispatchers.IO) {
        val src = File(backupsDir, "${backup.timestamp}.md")
        if (!src.exists()) return@withContext false
        val text = runCatching { src.readText(Charsets.UTF_8) }
            .onFailure { Log.w(TAG, "restore read failed", it) }
            .getOrNull() ?: return@withContext false
        save(text) != null
    }

    /**
     * Drop the override entirely; next load falls through to the
     * factory-default asset. The current override is rotated into
     * backups/ first so this isn't a destructive action.
     */
    suspend fun reset(): Boolean = withContext(Dispatchers.IO) {
        if (overrideFile.exists()) {
            val stamp = overrideFile.lastModified()
            val backupFile = File(backupsDir, "$stamp.md")
            runCatching { overrideFile.copyTo(backupFile, overwrite = true) }
                .onFailure { Log.w(TAG, "reset rotation failed", it) }
            overrideFile.delete()
            pruneBackups()
            refreshBackupList()
            true
        } else {
            false
        }
    }

    /** Refresh [backups] flow from disk. Call once on app start. */
    suspend fun refreshBackupList() = withContext(Dispatchers.IO) {
        _backups.value = backupsDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(".md") }
            .mapNotNull { f ->
                f.nameWithoutExtension.toLongOrNull()?.let { ts ->
                    SoulBackup(timestamp = ts, lengthBytes = f.length())
                }
            }
            .sortedByDescending { it.timestamp }
    }

    private fun pruneBackups() {
        val files = backupsDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(".md") }
            .sortedByDescending { it.name.removeSuffix(".md").toLongOrNull() ?: 0L }
        if (files.size > MAX_BACKUPS) {
            files.drop(MAX_BACKUPS).forEach { stale ->
                runCatching { stale.delete() }
                    .onFailure { Log.w(TAG, "stale backup delete failed", it) }
            }
        }
    }

    private companion object {
        const val TAG = "SoulRepository"
        const val MAX_BACKUPS = 10
    }
}

/** Single backup entry surfaced to the Settings UI. */
data class SoulBackup(
    /** epoch millis when this backup was rotated out. */
    val timestamp: Long,
    val lengthBytes: Long,
)
