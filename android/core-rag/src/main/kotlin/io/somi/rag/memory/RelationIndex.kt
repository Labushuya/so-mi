package io.somi.rag.memory

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class OkfIndexEntry(
    val factId: String,
    val file: String,
    val entities: List<String>,
    val relations: List<OkfRelation>,
)

object RelationIndex {

    private const val TAG = "RelationIndex"
    private const val MAX_LINKED_FILES = 5

    // Per-categoryId mutex to prevent concurrent writes to the same index file.
    // Index files are scanned in filesystem order; results from earlier files take
    // priority up to MAX_LINKED_FILES when findLinkedFiles() hits the cap.
    private val mutexMap = ConcurrentHashMap<String, Mutex>()
    private fun mutexFor(id: String): Mutex = mutexMap.getOrPut(id) { Mutex() }

    private fun indexFile(rootDir: File, categoryId: String): File =
        File(rootDir, "_index_${categoryId}.json")

    // -----------------------------------------------------------------------
    // Serialization
    // -----------------------------------------------------------------------

    private fun OkfIndexEntry.toJson(): JSONObject = JSONObject().apply {
        put("factId", factId)
        put("file", file)
        put("entities", JSONArray(entities))
        put("relations", JSONArray(relations.map { rel ->
            JSONObject().apply {
                put("from", rel.from)
                put("target", rel.target)
                put("rel", rel.rel)
            }
        }))
    }

    private fun JSONObject.toOkfIndexEntry(): OkfIndexEntry {
        val entsArr = optJSONArray("entities") ?: JSONArray()
        val relsArr = optJSONArray("relations") ?: JSONArray()
        val entities = (0 until entsArr.length()).map { entsArr.getString(it) }
        val relations = (0 until relsArr.length()).mapNotNull { i ->
            val o = relsArr.optJSONObject(i) ?: return@mapNotNull null
            OkfRelation(
                from = o.optString("from", ""),
                target = o.optString("target", ""),
                rel = o.optString("rel", ""),
            ).takeIf { it.target.isNotBlank() && it.rel.isNotBlank() }
        }
        return OkfIndexEntry(
            factId = getString("factId"),
            file = getString("file"),
            entities = entities,
            relations = relations,
        )
    }

    private fun readEntries(file: File): MutableList<OkfIndexEntry> {
        if (!file.exists()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            (0 until arr.length()).map { arr.getJSONObject(it).toOkfIndexEntry() }.toMutableList()
        }.getOrElse { ex ->
            Log.w(TAG, "failed to read ${file.name}: ${ex.message}")
            mutableListOf()
        }
    }

    private fun writeEntries(file: File, entries: List<OkfIndexEntry>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(JSONArray(entries.map { it.toJson() }).toString(2), Charsets.UTF_8)
        }.onFailure { Log.e(TAG, "failed to write ${file.name}: ${it.message}") }
    }

    // -----------------------------------------------------------------------
    // Public API (all suspend — callers are already in IO coroutines)
    // -----------------------------------------------------------------------

    /** Insert or replace the entry for [entry.factId] in the category index. */
    suspend fun upsert(rootDir: File, categoryId: String, entry: OkfIndexEntry) {
        mutexFor(categoryId).withLock {
            val file = indexFile(rootDir, categoryId)
            val entries = readEntries(file)
            val idx = entries.indexOfFirst { it.factId == entry.factId }
            if (idx >= 0) entries[idx] = entry else entries.add(entry)
            writeEntries(file, entries)
        }
    }

    /** Load all entries for [categoryId]. Returns empty list if file absent. */
    fun loadFor(rootDir: File, categoryId: String): List<OkfIndexEntry> =
        runCatching { readEntries(indexFile(rootDir, categoryId)) }
            .getOrElse { emptyList() }

    /** Scan all _index_*.json files and return .md files whose entities match [entityName]. */
    fun findLinkedFiles(rootDir: File, entityName: String): List<File> {
        val results = mutableListOf<File>()
        runCatching {
            val indexFiles = rootDir.listFiles { f ->
                f.isFile && f.name.startsWith("_index_") && f.name.endsWith(".json")
            } ?: return emptyList()
            for (indexFile in indexFiles) {
                if (results.size >= MAX_LINKED_FILES) break
                for (entry in readEntries(indexFile)) {
                    if (results.size >= MAX_LINKED_FILES) break
                    if (entry.entities.any { it.equals(entityName, ignoreCase = true) }) {
                        results += File(rootDir, entry.file)
                    }
                }
            }
        }.onFailure { Log.w(TAG, "findLinkedFiles($entityName): ${it.message}") }
        return results
    }
}
