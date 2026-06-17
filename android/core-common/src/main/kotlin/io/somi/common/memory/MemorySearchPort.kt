package io.somi.common.memory

interface MemorySearchPort {
    suspend fun topKFacts(query: String, k: Int = 10): List<String>
    suspend fun topKKeywords(query: String, k: Int = 10): List<String>

    /**
     * Returns up to [k] facts from a specific named category file.
     * [categoryId] = MemoryTopic.id ("persons","preferences","dates","technical","notes")
     * or a custom category file name. Returns empty list when not found.
     */
    suspend fun topKByCategory(categoryId: String, k: Int = 10): List<String>
}
