package io.somi.tools.routing

import android.util.Log
import io.somi.common.embed.TextEmbedder
import io.somi.common.llm.LlmCaller
import io.somi.tools.model.RoutingStage
import io.somi.tools.model.ToolCall
import io.somi.tools.registry.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRouter @Inject constructor(
    private val registry: ToolRegistry,
    private val embedder: TextEmbedder,
    private val llmCaller: LlmCaller,
) {
    companion object {
        private const val COSINE_THRESHOLD = 0.78f
        private const val TAG = "ToolRouter"
        private val JSON_BLOCK_RE = Regex("""(?s)\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}""")
        private val PLAN_PROMPT = """
Wähle das passende Tool für die Anfrage. Antworte NUR mit JSON, kein weiterer Text.
Falls kein Tool passt: {"tool":"none","params":{}}

Verfügbare Tools:
{{TOOLS}}

Anfrage: {{QUERY}}

JSON:""".trimIndent()
    }

    suspend fun route(query: String): ToolCall? {
        if (registry.tools.isEmpty()) return null
        return stageRegex(query)
            ?: stageEmbedding(query)
            ?: stageLlmPlan(query)
    }

    private fun stageRegex(query: String): ToolCall? {
        val lower = query.lowercase()
        for (tool in registry.tools) {
            if (tool.regexPatterns.isEmpty()) continue
            if (tool.regexPatterns.any { it.containsMatchIn(lower) }) {
                val params = tool.paramExtractor?.invoke(query) ?: emptyMap()
                Log.d(TAG, "stage1 match: ${tool.id}")
                return ToolCall(tool.id, params, RoutingStage.REGEX)
            }
        }
        return null
    }

    private suspend fun stageEmbedding(query: String): ToolCall? {
        val queryVec = embedder.embed(query) ?: return null
        var bestTool = registry.tools.firstOrNull() ?: return null
        var bestScore = -1f
        for (tool in registry.tools) {
            val vec = tool.descriptionVector ?: run {
                val v = embedder.embed(tool.description) ?: return null
                tool.descriptionVector = v
                v
            }
            val score = cosine(queryVec, vec)
            if (score > bestScore) { bestScore = score; bestTool = tool }
        }
        return if (bestScore >= COSINE_THRESHOLD) {
            Log.d(TAG, "stage2 match: ${bestTool.id} score=$bestScore")
            ToolCall(bestTool.id, emptyMap(), RoutingStage.EMBEDDING)
        } else null
    }

    private suspend fun stageLlmPlan(query: String): ToolCall? {
        val prompt = PLAN_PROMPT
            .replace("{{TOOLS}}", registry.descriptionBlock())
            .replace("{{QUERY}}", query)
        val raw = withContext(Dispatchers.Default) {
            runCatching { llmCaller.generate(prompt) }.getOrDefault("")
        }
        return parsePlanResponse(raw)
    }

    private fun parsePlanResponse(raw: String): ToolCall? {
        val jsonStr = JSON_BLOCK_RE.find(raw)?.value ?: raw.trim()
        return runCatching {
            val obj = JSONObject(jsonStr)
            val toolId = obj.getString("tool")
            if (toolId == "none") return null
            registry.find(toolId) ?: return null
            val paramsObj = obj.optJSONObject("params") ?: JSONObject()
            val params = buildMap<String, Any> {
                paramsObj.keys().forEach { key -> put(key, paramsObj.get(key)) }
            }
            Log.d(TAG, "stage3 match: $toolId")
            ToolCall(toolId, params, RoutingStage.LLM_PLAN)
        }.getOrNull()
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }
}
