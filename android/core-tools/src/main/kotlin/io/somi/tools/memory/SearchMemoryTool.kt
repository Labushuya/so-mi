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
        val normalized = query.trim().lowercase()

        // Try to match against actual category file names (handles custom categories + emojis).
        // File IDs are already normalized (lowercase, underscores, no emojis) by the app.
        // We fuzzy-match: query "familie" matches file "familie.md" or "👨_familie.md" etc.
        val categories = runCatching { memorySearch.availableCategories() }.getOrElse { emptyList() }
        val matchedCategory = categories.firstOrNull { catId ->
            // Direct match
            catId == normalized ||
            // Query is contained in the category id (e.g. "familie" in "familie" or "meine_familie")
            catId.contains(normalized) ||
            // Category id is contained in query (e.g. "persons" in "personen")
            normalized.contains(catId) ||
            // German aliases for built-in categories
            builtinAlias(normalized) == catId
        }

        if (matchedCategory != null) {
            val facts = runCatching { memorySearch.topKByCategory(matchedCategory, k) }.getOrElse { emptyList() }
            if (facts.isEmpty()) {
                return ToolResult(toolId, "", error = "Keine Einträge in Kategorie: $query", displayHint = "Kategorie: $query")
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

    private fun builtinAlias(query: String): String? = when (query) {
        "personen", "person", "leute" -> "persons"
        "vorlieben", "vorliebe", "mögen", "favoriten" -> "preferences"
        "termine", "termin", "kalender", "datum" -> "dates"
        "technik", "technisch", "geräte", "software" -> "technical"
        "notizen", "notiz", "sonstiges", "allgemein" -> "notes"
        else -> null
    }
}
