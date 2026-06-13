package io.somi.common.llm

/**
 * Narrow seam so core-tools can call the local LLM for Stage-3 plan-pass
 * without depending on core-llm directly. Bound in app/ via Hilt.
 */
interface LlmCaller {
    suspend fun generate(prompt: String, maxTokens: Int = 512): String
}
