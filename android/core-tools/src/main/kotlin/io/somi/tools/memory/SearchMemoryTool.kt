package io.somi.tools.memory

import io.somi.common.memory.MemorySearchPort
import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchMemoryTool @Inject constructor(
    private val memorySearch: MemorySearchPort,
) : ToolExecutor {
    override val toolId = "search_memory"

    override suspend fun execute(call: ToolCall): ToolResult {
        val query = call.params["query"]?.toString()
            ?: return ToolResult(toolId, "", error = "query fehlt")
        val k = (call.params["k"] as? Int) ?: 10
        // Use keyword-only scan — avoids ONNX embedder while LLM is loaded (OOM risk)
        val facts = runCatching { memorySearch.topKKeywords(query, k) }.getOrElse { emptyList() }
        if (facts.isEmpty()) {
            return ToolResult(toolId, "Keine Erinnerungen zu: $query", displayHint = "Erinnerungssuche")
        }
        val block = buildString {
            append("[Erinnerungssuche: \"$query\"]\n")
            facts.forEach { append("- $it\n") }
        }
        return ToolResult(toolId, block, displayHint = "Erinnerung: $query")
    }
}
