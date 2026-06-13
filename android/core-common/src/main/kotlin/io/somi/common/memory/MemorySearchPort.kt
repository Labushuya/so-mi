package io.somi.common.memory

interface MemorySearchPort {
    /**
     * Top-[k] memory facts ranked by semantic relevance to [query].
     * Returns empty list (never throws) when the store is empty or unavailable.
     */
    suspend fun topKFacts(query: String, k: Int = 10): List<String>
}
