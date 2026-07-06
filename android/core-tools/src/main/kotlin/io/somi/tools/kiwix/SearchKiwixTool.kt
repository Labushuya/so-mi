package io.somi.tools.kiwix

import io.somi.common.kiwix.KiwixSearchPort
import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchKiwixTool @Inject constructor(
    private val kiwixSearch: KiwixSearchPort,
) : ToolExecutor {

    override val toolId = "search_kiwix"

    override suspend fun execute(call: ToolCall): ToolResult {
        if (!kiwixSearch.isOpen()) {
            return ToolResult(toolId, "", error = "Kein Offline-Lexikon installiert. Bitte in Einstellungen → Downloads → Wiktionary herunterladen.", displayHint = "Lexikon nicht verfügbar")
        }
        val query = call.params["query"]?.toString()?.trim()
            ?: return ToolResult(toolId, "", error = "query fehlt")

        val results = runCatching { kiwixSearch.search(query, maxResults = 3) }.getOrElse { emptyList() }
        if (results.isEmpty()) {
            return ToolResult(toolId, "", error = "Kein Eintrag für: $query", displayHint = "Lexikon: $query")
        }

        val block = buildString {
            append("[Wörterbuch-Einträge zu '$query']:\n")
            results.forEach { entry ->
                append("[${entry.title}] ${entry.plainText.take(300)}\n")
            }
        }
        return ToolResult(toolId, block, displayHint = "Lexikon: $query")
    }
}
