package io.somi.tools.summarize

import io.somi.common.llm.LlmCaller
import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummarizeTool @Inject constructor(
    private val llmCaller: LlmCaller,
) : ToolExecutor {
    override val toolId = "summarize"

    override suspend fun execute(call: ToolCall): ToolResult {
        val text = call.params["text"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return ToolResult(toolId, "", error = "Zu zusammenfassender Text fehlt")
        val maxWords = (call.params["max_words"] as? Int) ?: 100
        val style = call.params["style"]?.toString() ?: "neutral"

        val styleInstruction = when (style) {
            "bullets" -> "Fasse in 3-5 Stichpunkten zusammen."
            "short" -> "Fasse in einem einzigen Satz zusammen."
            else -> "Fasse prägnant in $maxWords Wörtern oder weniger zusammen."
        }

        val prompt = buildString {
            append("$styleInstruction\n\n")
            append("Text:\n$text\n\n")
            append("Zusammenfassung:")
        }

        val summary = runCatching {
            llmCaller.generate(prompt, maxTokens = 256)
                .trim()
                .removePrefix("Zusammenfassung:").trim()
        }.getOrElse { return ToolResult(toolId, "", error = "Zusammenfassung fehlgeschlagen: ${it.message}") }

        if (summary.isBlank()) return ToolResult(toolId, "", error = "Leere Zusammenfassung")

        return ToolResult(
            toolId,
            "[Zusammenfassung]\n$summary",
            displayHint = "Zusammenfassung",
        )
    }
}
