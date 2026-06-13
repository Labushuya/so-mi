package io.somi.tools.model

data class ToolResult(
    val toolId: String,
    val contextBlock: String,
    val displayHint: String? = null,
    val error: String? = null,
)
