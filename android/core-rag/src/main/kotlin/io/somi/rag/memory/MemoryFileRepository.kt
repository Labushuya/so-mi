package io.somi.rag.memory

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.14.0 M4 — Markdown mirror of the conversational memory.
 *
 * Disk layout:
 *
 *   $externalFilesDir/memory/
 *       persons.md
 *       preferences.md
 *       dates.md
 *       technical.md
 *       notes.md
 *
 * Why mirror at all: the user wanted user-readable, ADB-pullable
 * `.md` files (per v0.14.0 planning). ObjectBox is the authoritative
 * store; the `.md` files are a one-way export. Editing them by hand
 * has no effect on the engine — the next save() rewrites the file
 * from the ObjectBox state. Documented in CLAUDE.md.
 *
 * **Concurrency.** A single Mutex serializes writes across topics.
 * The .md files are tiny (kilobytes); contention is unlikely but
 * a mid-write append from a parallel save would corrupt headers.
 *
 * Format:
 *
 *     # Personen
 *     <comment block: "auto-generiert von So-Mi, nicht manuell editieren">
 *
 *     - Christopher heißt Christopher (gespeichert: 2026-06-08 15:14)
 *     - Hat einen Bruder namens Stefan (gespeichert: 2026-06-08 15:30)
 */
@Singleton
class MemoryFileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val mutex = Mutex()

    val rootDir: File by lazy {
        // v0.15.0: was externalFilesDir/memory directly; now under
        // SoMi/memory/ for unified user-visible path.
        val target = io.somi.data.StorageRoots.memory(context)
        Log.i(TAG, "memory root = ${target.absolutePath}")
        target
    }

    private fun fileFor(topic: MemoryTopic): File =
        File(rootDir, "${topic.id}.md")

    /**
     * Append a single fact to its topic file. Idempotent at the
     * filesystem level (we never check for duplicates — the
     * ObjectBox row id is the source of truth, the `.md` is just a
     * mirror).
     */
    suspend fun append(
        fact: String,
        topic: MemoryTopic,
        createdAt: Long = System.currentTimeMillis(),
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = fileFor(topic)
            file.parentFile?.mkdirs()
            if (!file.exists()) writeHeader(file, topic)
            // Deduplicate: skip if a very similar fact already exists
            if (!isDuplicate(fact, file)) {
                file.appendText(formatBullet(fact, createdAt))
                Log.i(TAG, "appended: $fact")
            } else {
                Log.i(TAG, "skipped duplicate: $fact")
            }
        }
    }

    /**
     * v0.29.0 — Duplikat-Erkennung. Gibt true zurück wenn [newFact] bereits
     * in [file] enthalten ist (exakt oder fast gleich — normalisierter Text
     * mit Levenshtein-Distanz ≤ 2 bei Fakten bis 30 Zeichen).
     */
    private fun isDuplicate(newFact: String, file: File): Boolean {
        if (!file.exists()) return false
        val normalized = newFact.trim().lowercase()
        val existing = file.readLines()
            .filter { it.trimStart().startsWith("- ") }
            .map { it.trimStart().removePrefix("- ").replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim().lowercase() }
        return existing.any { ex ->
            ex == normalized || levenshtein(ex, normalized) <= 2
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val m = a.length; val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) { 0 } }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                       else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[m][n]
    }

    /**
     * Rewrite a topic file from the current ObjectBox state. Called
     * after deletes / moves / edits in the Memory-Browser (M7) so the
     * `.md` file matches the live store.
     */
    suspend fun rewrite(topic: MemoryTopic, facts: List<MemoryFact>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = fileFor(topic)
                file.parentFile?.mkdirs()
                val sb = StringBuilder()
                appendHeader(sb, topic)
                for (f in facts.sortedBy { it.createdAt }) {
                    sb.append(formatBullet(f.fact, f.createdAt))
                }
                file.writeText(sb.toString())
            }
        }

    /** Remove a single fact from a topic file by exact text match. */
    suspend fun remove(fact: String, topic: MemoryTopic) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = fileFor(topic)
            if (!file.exists()) return@withLock
            val normalized = fact.trim().lowercase()
            val kept = file.readLines().filter { line ->
                if (!line.trimStart().startsWith("- ")) return@filter true
                val lineFact = line.trimStart().removePrefix("- ")
                    .replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim().lowercase()
                lineFact != normalized
            }
            file.writeText(kept.joinToString("\n") + "\n")
        }
    }

    /** Wipe a topic file (used by tests + Settings reset). */
    suspend fun clear(topic: MemoryTopic) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = fileFor(topic)
            if (file.exists()) file.delete()
        }
    }

    /** Wipe everything. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            for (topic in MemoryTopic.entries) {
                val f = fileFor(topic)
                if (f.exists()) f.delete()
            }
        }
    }

    private fun writeHeader(file: File, topic: MemoryTopic) {
        val sb = StringBuilder()
        appendHeader(sb, topic)
        file.writeText(sb.toString())
    }

    private fun appendHeader(sb: StringBuilder, topic: MemoryTopic) {
        sb.append("# ").append(topic.displayName).append('\n')
        sb.append("\n")
        sb.append("<!-- Auto-generiert von So-Mi. ObjectBox ist die ")
        sb.append("Quelle der Wahrheit; manuelle Änderungen werden ")
        sb.append("beim nächsten Speichern überschrieben. -->\n")
        sb.append("\n")
    }

    private fun formatBullet(fact: String, createdAt: Long): String {
        val ts = TIMESTAMP_FORMAT.format(Date(createdAt))
        // Single-line bullets only — newlines in the fact text get
        // collapsed to spaces so the bullet stays one logical row.
        val safe = fact.replace('\n', ' ').replace('\r', ' ').trim()
        return "- $safe  _(gespeichert: $ts)_\n"
    }

    private companion object {
        const val TAG = "MemoryFileRepository"
        val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMAN)
    }
}
