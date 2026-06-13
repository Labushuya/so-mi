package io.somi.tools.model

data class ToolCall(
    val toolId: String,
    val params: Map<String, Any>,
    val stage: RoutingStage,
)
