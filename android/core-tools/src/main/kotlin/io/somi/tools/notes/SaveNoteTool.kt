package io.somi.tools.notes

import io.somi.common.memory.MemorySearchPort
import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveNoteTool @Inject constructor(
    private val memorySearch: MemorySearchPort,
) : ToolExecutor {
    override val toolId = "save_note"

    override suspend fun execute(call: ToolCall): ToolResult {
        val text = call.params["text"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return ToolResult(toolId, "", error = "Notiztext fehlt")
        val saved = runCatching { memorySearch.saveNote(text) }
            .getOrElse { return ToolResult(toolId, "", error = "Notiz konnte nicht gespeichert werden: ${it.message}") }
        return ToolResult(
            toolId,
            "[Notiz gespeichert]\n\"$saved\"",
            displayHint = "Notiz gespeichert",
        )
    }
}
