package io.somi.common.memory

interface MemorySearchPort {
    suspend fun topKFacts(query: String, k: Int = 10): List<String>
    suspend fun topKKeywords(query: String, k: Int = 10): List<String>
    suspend fun topKByCategory(categoryId: String, k: Int = 10): List<String>

    /** All available category IDs (= .md file names without extension, including custom). */
    suspend fun availableCategories(): List<String>
}
