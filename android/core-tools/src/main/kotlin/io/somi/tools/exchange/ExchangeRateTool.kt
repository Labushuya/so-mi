package io.somi.tools.exchange

import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExchangeRateTool @Inject constructor() : ToolExecutor {
    override val toolId = "get_exchange_rate"
    private val client = ExchangeRateClient()

    override suspend fun execute(call: ToolCall): ToolResult {
        val from = call.params["from"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return ToolResult(toolId, "", error = "Ausgangswährung fehlt")
        val to = call.params["to"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return ToolResult(toolId, "", error = "Zielwährung fehlt")
        val amount = when (val a = call.params["amount"]) {
            is Int -> a.toDouble(); is Double -> a
            is String -> a.toDoubleOrNull() ?: 1.0; else -> 1.0
        }
        val result = runCatching { client.convert(from, to, amount) }.getOrElse { "Wechselkurs nicht verfügbar." }
        return if (result.contains("nicht") || result.contains("Fehler"))
            ToolResult(toolId, "", error = result)
        else
            ToolResult(toolId, "[Wechselkurs]\n$result", displayHint = "$from → $to")
    }
}
