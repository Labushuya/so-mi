package io.somi.common.memory

interface MemorySearchPort {
    /**
     * Top-[k] memory facts ranked by semantic relevance to [query].
     * Returns empty list (never throws) when the store is empty or unavailable.
     */
    suspend fun topKFacts(query: String, k: Int = 10): List<String>

    /**
     * Keyword-only scan — no embedder, no HNSW.
     * Safe to call while the LLM is loaded without risking OOM from
     * running ONNX + llama.cpp simultaneously.
     */
    suspend fun topKKeywords(query: String, k: Int = 10): List<String>
}
