package io.somi.tools.model

data class ToolDefinition(
    val id: String,
    val description: String,
    val paramSchema: String,
    val regexPatterns: List<Regex> = emptyList(),
    val paramExtractor: ((String) -> Map<String, Any>)? = null,
) {
    @Volatile var descriptionVector: FloatArray? = null
}
