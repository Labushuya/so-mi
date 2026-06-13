package io.somi.tools.executor

import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult

interface ToolExecutor {
    val toolId: String
    suspend fun execute(call: ToolCall): ToolResult
}
