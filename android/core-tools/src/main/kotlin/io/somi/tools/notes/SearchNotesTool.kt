package io.somi.tools.notes

import io.somi.common.memory.MemorySearchPort
import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchNotesTool @Inject constructor(
    private val memorySearch: MemorySearchPort,
) : ToolExecutor {
    override val toolId = "search_notes"

    override suspend fun execute(call: ToolCall): ToolResult {
        val query = call.params["query"]?.toString()?.takeIf { it.isNotBlank() }
        val k = (call.params["k"] as? Int) ?: 10

        // If no query, return all notes; otherwise keyword search within notes
        val facts = runCatching {
            if (query == null) memorySearch.topKByCategory("notes", k)
            else memorySearch.topKByCategory("notes", k).let { all ->
                val lower = query.lowercase()
                val tokens = lower.split(' ').filter { it.length >= 3 }
                if (tokens.isEmpty()) all
                else all.filter { note -> tokens.any { t -> note.lowercase().contains(t) } }
            }
        }.getOrElse { emptyList() }

        if (facts.isEmpty()) {
            return ToolResult(toolId, "", error = "Keine Notizen${if (query != null) " zu: $query" else ""}", displayHint = "Notizen")
        }
        val block = buildString {
            append("[Notizen${if (query != null) ": \"$query\"" else ""} (${facts.size})]\n")
            facts.forEach { append("- $it\n") }
        }
        return ToolResult(toolId, block, displayHint = "Notizen (${facts.size})")
    }
}
