package io.somi.rag.memory

import io.somi.common.memory.MemorySearchPort
import io.somi.rag.embed.Embedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemorySearchAdapter @Inject constructor(
    private val memoryStore: MemoryStore,
    private val embedder: Embedder,
    private val memoryFiles: MemoryFileRepository,
) : MemorySearchPort {

    override suspend fun topKFacts(query: String, k: Int): List<String> =
        withContext(Dispatchers.IO) {
            if (runCatching { embedder.isAvailable() }.getOrDefault(false)) {
                val vec = runCatching { embedder.embed(query) }.getOrNull()
                    ?: return@withContext keywordScan(query, k)
                memoryStore.topK(vec, k).map { it.fact.fact }
            } else {
                keywordScan(query, k)
            }
        }

    override suspend fun topKKeywords(query: String, k: Int): List<String> =
        withContext(Dispatchers.IO) { keywordScan(query, k) }

    private fun keywordScan(query: String, k: Int): List<String> {
        val tokens = query.lowercase().split(' ').filter { it.length >= 3 }
        return memoryFiles.rootDir
            .listFiles()
            ?.filter { it.extension == "md" }
            ?.flatMap { f ->
                f.readLines()
                    .filter { it.trimStart().startsWith("- ") }
                    .map {
                        it.trimStart().removePrefix("- ")
                            .replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim()
                    }
                    .filter { line -> tokens.any { t -> line.lowercase().contains(t) } }
            }
            ?.take(k)
            ?: emptyList()
    }
}
