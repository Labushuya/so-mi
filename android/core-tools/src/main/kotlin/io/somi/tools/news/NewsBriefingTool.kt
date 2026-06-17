package io.somi.tools.news

import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsBriefingTool @Inject constructor() : ToolExecutor {
    override val toolId = "news_briefing"
    private val fetcher = RssFetcher()

    override suspend fun execute(call: ToolCall): ToolResult {
        val maxItems = (call.params["max_items"] as? Int)?.coerceIn(1, 15) ?: 9
        val items = runCatching { fetcher.fetch(maxPerFeed = (maxItems / 3).coerceAtLeast(1)) }.getOrElse { emptyList() }
        if (items.isEmpty()) return ToolResult(toolId, "", error = "Nachrichten nicht verfügbar.")
        val block = buildString {
            append("[Nachrichten-Briefing]\n")
            items.take(maxItems).forEach { item -> append("- [${item.source}] ${item.title}\n") }
            append("(Quellen verlassen das Gerät)\n")
        }
        return ToolResult(toolId, block, displayHint = "Nachrichten (${items.size})")
    }
}
