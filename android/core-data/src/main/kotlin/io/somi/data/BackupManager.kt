package io.somi.data

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * v0.25.0 — Backup & Import für So-Mis Daten.
 * Plain class — kein Hilt, damit sie direkt aus Composables nutzbar ist.
 *
 * Exportiert: SoMi/memory/, SoMi/soul/, SoMi/settings/
 * Nicht exportiert: SoMi/llm/ (zu groß), SoMi/db/ (ObjectBox-Format)
 *
 * openHelper: wenn gesetzt, wird vor dem DB-Kopieren ein WAL-Checkpoint
 * ausgeführt, damit alle pending WAL-Daten in somi.db geflusht sind.
 */
class BackupManager(
    private val context: Context,
    private val openHelper: SupportSQLiteOpenHelper? = null,
) {

    fun createBackup(): File {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.GERMAN).format(Date())
        val zipFile = File(StorageRoots.root(context), "so-mi-backup-$ts.zip")
        zipFile.parentFile?.mkdirs()

        // Flush pending WAL data into somi.db before copying.
        openHelper?.writableDatabase?.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            // User data dirs
            listOf("memory", "soul", "settings").forEach { dirName ->
                val dir = File(StorageRoots.root(context), dirName)
                if (!dir.exists()) return@forEach
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryName = "$dirName/${file.relativeTo(dir).path.replace('\\', '/')}"
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().copyTo(zip)
                    zip.closeEntry()
                }
            }
            // v0.39.0: include Room DB (chat history + conversations)
            val dbDir = StorageRoots.db(context)
            listOf("somi.db", "somi.db-shm", "somi.db-wal").forEach { name ->
                val f = File(dbDir, name)
                // somi.db must exist and be non-empty; WAL side-files may
                // legitimately be zero-length after a successful checkpoint.
                if (name == "somi.db" && (!f.exists() || f.length() == 0L)) return@forEach
                if (!f.exists()) return@forEach
                zip.putNextEntry(ZipEntry("db/$name"))
                f.inputStream().copyTo(zip)
                zip.closeEntry()
            }
        }

        Log.i(TAG, "backup: ${zipFile.name} (${zipFile.length() / 1024} KB)")
        return zipFile
    }

    fun importBackup(zipFile: File): Int {
        var count = 0
        val root = StorageRoots.root(context)
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(root, entry.name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zip.copyTo(out) }
                    count++
                }
                entry = zip.nextEntry
            }
        }
        Log.i(TAG, "import: $count files")
        return count
    }

    private companion object {
        const val TAG = "BackupManager"
    }
}
