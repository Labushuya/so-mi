package io.somi.tools.registry

import io.somi.tools.model.ToolDefinition
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor(
    val tools: Set<@JvmSuppressWildcards ToolDefinition>,
) {
    fun find(toolId: String): ToolDefinition? = tools.firstOrNull { it.id == toolId }

    fun descriptionBlock(): String =
        tools.joinToString("\n") { "- ${it.id}: ${it.description}" }
}
