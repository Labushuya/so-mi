package io.somi.common.memory

interface MemorySearchPort {
    suspend fun topKFacts(query: String, k: Int = 10): List<String>
    suspend fun topKKeywords(query: String, k: Int = 10): List<String>
    suspend fun topKByCategory(categoryId: String, k: Int = 10): List<String>
    suspend fun availableCategories(): List<String>
    /** Appends a note to the notes category. Returns the saved text. */
    suspend fun saveNote(text: String): String
}
