package io.somi.tools.web

import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSearchTool @Inject constructor() : ToolExecutor {
    override val toolId = "search_web"
    private val client = SearxngClient()

    override suspend fun execute(call: ToolCall): ToolResult {
        val query = call.params["query"]?.toString()
            ?: return ToolResult(toolId, "", error = "query fehlt")
        val maxResults = (call.params["max_results"] as? Int) ?: 5
        val results = runCatching { client.search(query, maxResults) }.getOrElse { emptyList() }
        if (results.isEmpty()) {
            return ToolResult(toolId, "", error = "Keine Web-Ergebnisse für: $query", displayHint = "Web-Suche")
        }
        val block = buildString {
            append("[Web-Suche: \"$query\"]\n")
            results.forEachIndexed { i, r ->
                append("${i + 1}. ${r.title}: ${r.snippet.take(150)}\n")
            }
            append("(Suchanfragen verlassen das Gerät)\n")
        }
        return ToolResult(toolId, block, displayHint = "Web: $query")
    }
}
