package io.somi.rag.kiwix

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.kiwix.libzim.Archive
import org.kiwix.libzim.SuggestionSearcher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps java-libkiwix ZIM-file access.
 *
 * Thread-safety: JNI handles in libzim are NOT thread-safe. All calls are
 * serialized through a single-thread dispatcher + Mutex — same pattern as
 * io.somi.rag.embed.Embedder.
 *
 * Lifecycle: open one ZIM at a time. KiwixAutoOpen calls openZim() on
 * startup; the repo stays open until the process dies (there is no explicit
 * close in java-libkiwix 2.6.0 for Archive — finalize handles it).
 */
@Singleton
class KiwixRepository @Inject constructor() {

    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val mutex = Mutex()

    private var archive: Archive? = null
    private var searcher: SuggestionSearcher? = null
    private var openPath: String? = null

    fun isOpen(): Boolean = archive != null

    /**
     * Opens a ZIM file. Idempotent: if the same path is already open,
     * returns true immediately. Replaces a previously opened ZIM if the
     * path differs.
     */
    suspend fun openZim(file: File): Boolean = withContext(dispatcher) {
        mutex.withLock {
            if (openPath == file.absolutePath && archive != null) return@withLock true
            runCatching {
                searcher?.dispose()
                searcher = null
                archive = null
                openPath = null

                val arc = Archive(file.absolutePath)
                searcher = SuggestionSearcher(arc)
                archive = arc
                openPath = file.absolutePath
                Log.i(TAG, "ZIM opened: ${file.name} (${file.length() / 1_048_576} MB)")
                true
            }.getOrElse { e ->
                Log.e(TAG, "ZIM open failed: ${file.name}", e)
                false
            }
        }
    }

    /**
     * Searches for entries matching [query] using the SuggestionSearcher
     * (title/prefix match). Returns up to [maxResults] entries with their
     * HTML stripped to plain text (max 600 chars each).
     */
    suspend fun search(query: String, maxResults: Int = 5): List<KiwixEntry> {
        if (query.isBlank()) return emptyList()
        return withContext(dispatcher) {
            mutex.withLock {
                val arc = archive ?: return@withLock emptyList()
                val src = searcher ?: return@withLock emptyList()
                runCatching {
                    val ss = src.suggest(query)
                    val iter = ss.getResults(0, maxResults)
                    val results = mutableListOf<KiwixEntry>()
                    while (iter.hasNext()) {
                        val item = iter.next()
                        val path = item.path ?: continue
                        val title = item.title.ifBlank { path }
                        val plain = runCatching {
                            val entry = arc.getEntryByPath(path)
                            val blob = entry.getItem(true).data
                            val html = String(blob.data, Charsets.UTF_8)
                            htmlToPlainText(html).take(600)
                        }.getOrDefault("")
                        results += KiwixEntry(title = title, path = path, plainText = plain)
                    }
                    results
                }.getOrElse { e ->
                    Log.w(TAG, "KIWIX search failed for '$query'", e)
                    emptyList()
                }
            }
        }
    }

    /** Fetches a single entry by path. Returns null if not found or ZIM not open. */
    suspend fun getEntry(path: String): KiwixEntry? = withContext(dispatcher) {
        mutex.withLock {
            val arc = archive ?: return@withLock null
            runCatching {
                val entry = arc.getEntryByPath(path)
                val blob = entry.getItem(true).data
                val html = String(blob.data, Charsets.UTF_8)
                KiwixEntry(
                    title = entry.title.ifBlank { path },
                    path = path,
                    plainText = htmlToPlainText(html).take(600),
                )
            }.getOrElse { null }
        }
    }

    // ------------------------------------------------------------------
    // HTML → plain text (no jsoup dep; sufficient for Wiktionary PoC)
    // ------------------------------------------------------------------

    private fun htmlToPlainText(html: String): String {
        var text = html
        // Remove <style> and <script> blocks entirely
        text = text.replace(Regex("""<style[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("""<script[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
        // Strip all remaining HTML tags
        text = text.replace(Regex("<[^>]+>"), " ")
        // Decode common HTML entities
        text = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#[0-9]+;")) { m ->
                m.value.removePrefix("&#").removeSuffix(";").toIntOrNull()
                    ?.toChar()?.toString() ?: " "
            }
        // Collapse whitespace
        return text.replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n\s*\n+"""), "\n")
            .trim()
    }

    private companion object {
        const val TAG = "KiwixRepository"
    }
}
