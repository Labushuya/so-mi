package io.somi.tools.weather

import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherTool @Inject constructor() : ToolExecutor {
    override val toolId = "get_weather"
    private val client = OpenMeteoClient()

    override suspend fun execute(call: ToolCall): ToolResult {
        val location = call.params["location"]?.toString()
            ?: return ToolResult(toolId, "", error = "location fehlt")
        val days = (call.params["days"] as? Int) ?: 1
        val result = runCatching { client.fetch(location, days) }
            .getOrElse { "Wetterdaten nicht verfügbar." }
        return ToolResult(toolId, "[Wetter]\n$result", displayHint = "Wetter $location")
    }
}
