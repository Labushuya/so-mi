package io.somi.tools.executor

import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolDispatcher @Inject constructor(
    private val executors: Set<@JvmSuppressWildcards ToolExecutor>,
) {
    private val map by lazy { executors.associateBy { it.toolId } }

    suspend fun dispatch(call: ToolCall): ToolResult =
        map[call.toolId]?.execute(call)
            ?: ToolResult(call.toolId, "", error = "Unknown tool: ${call.toolId}")
}
