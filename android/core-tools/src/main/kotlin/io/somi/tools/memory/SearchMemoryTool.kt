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

    // German aliases → canonical category file id
    private val categoryAliases = mapOf(
        "personen" to "persons", "person" to "persons", "persons" to "persons",
        "vorlieben" to "preferences", "vorliebe" to "preferences", "preferences" to "preferences", "mögen" to "preferences",
        "termine" to "dates", "termin" to "dates", "dates" to "dates", "kalender" to "dates",
        "technik" to "technical", "technical" to "technical", "technisch" to "technical", "geräte" to "technical",
        "notizen" to "notes", "notiz" to "notes", "notes" to "notes", "sonstiges" to "notes",
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val query = call.params["query"]?.toString()
            ?: return ToolResult(toolId, "", error = "query fehlt")
        val k = (call.params["k"] as? Int) ?: 10
        val normalized = query.trim().lowercase()

        // Category query: if the query exactly matches a known category name,
        // return all facts from that category file (up to k).
        val categoryId = categoryAliases[normalized]
        if (categoryId != null) {
            val facts = runCatching { memorySearch.topKByCategory(categoryId, k) }.getOrElse { emptyList() }
            if (facts.isEmpty()) {
                return ToolResult(toolId, "", error = "Keine Erinnerungen in Kategorie: $query", displayHint = "Kategorie: $query")
            }
            val label = query.replaceFirstChar { it.uppercaseChar() }
            val block = buildString {
                append("[Erinnerungen — $label (${facts.size} Einträge)]\n")
                facts.forEach { append("- $it\n") }
            }
            return ToolResult(toolId, block, displayHint = "Kategorie: $label")
        }

        // Regular keyword search across all categories
        val facts = runCatching { memorySearch.topKKeywords(query, k) }.getOrElse { emptyList() }
        if (facts.isEmpty()) {
            return ToolResult(toolId, "", error = "Keine Erinnerungen zu: $query", displayHint = "Erinnerungssuche")
        }
        val block = buildString {
            append("[Erinnerungssuche: \"$query\"]\n")
            facts.forEach { append("- $it\n") }
        }
        return ToolResult(toolId, block, displayHint = "Erinnerung: $query")
    }
}
