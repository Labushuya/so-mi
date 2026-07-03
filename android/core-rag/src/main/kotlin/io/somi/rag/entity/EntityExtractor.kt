package io.somi.rag.entity

import android.util.Log
import io.somi.common.llm.LlmCaller
import io.somi.rag.memory.OkfRelation
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class OkfExtractResult(
    val entities: List<String>,
    val relations: List<OkfRelation>,
)

/**
 * Extracts named entities and relations from a saved fact via a single short LLM call.
 *
 * Called fire-and-forget AFTER the main user-facing response has been delivered.
 * NEVER call this concurrently with an active llama.cpp generation — the LlmCaller
 * dispatches on limitedParallelism(1) and will queue, but you must not hold a
 * Dispatchers.IO context that could deadlock the OpenMP thread pool.
 *
 * Must be called from a background coroutine (e.g. viewModelScope.launch(Dispatchers.IO)).
 */
@Singleton
class EntityExtractor @Inject constructor(
    private val llmCaller: LlmCaller,
) {
    companion object {
        private const val TAG = "EntityExtractor"
        private const val TIMEOUT_MS = 3_000L
        private const val MAX_ENTITIES = 5
        private const val MAX_RELATIONS = 3
    }

    suspend fun extractEntities(factText: String): OkfExtractResult? {
        val prompt = """
Extrahiere Entitäten und Beziehungen. Antwort NUR JSON:
{"entities":["Name","Ort"],"relations":[{"from":"Ich","to":"Delos Cloud","rel":"arbeitet_bei"}]}
Fakt: $factText
JSON:""".trimIndent()

        val raw = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching { llmCaller.generate(prompt, maxTokens = 80) }.getOrNull()
        } ?: run {
            Log.d(TAG, "extractEntities: timeout or error for '${factText.take(30)}'")
            return null
        }

        return parseResult(raw)
    }

    private fun parseResult(raw: String): OkfExtractResult {
        return try {
            val s = raw.indexOf('{')
            val e = raw.lastIndexOf('}')
            if (s < 0 || e <= s) return OkfExtractResult(emptyList(), emptyList())
            val obj = JSONObject(raw.substring(s, e + 1))
            OkfExtractResult(
                entities = parseEntities(obj.optJSONArray("entities")),
                relations = parseRelations(obj.optJSONArray("relations")),
            )
        } catch (ex: Exception) {
            Log.w(TAG, "parseResult failed: ${ex.message}")
            OkfExtractResult(emptyList(), emptyList())
        }
    }

    private fun parseEntities(arr: JSONArray?): List<String> {
        arr ?: return emptyList()
        return (0 until minOf(arr.length(), MAX_ENTITIES))
            .mapNotNull { arr.optString(it).trim().takeIf { s -> s.length >= 2 } }
    }

    private fun parseRelations(arr: JSONArray?): List<OkfRelation> {
        arr ?: return emptyList()
        return (0 until minOf(arr.length(), MAX_RELATIONS)).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val from = o.optString("from", "").trim()
            val to = o.optString("to", "").trim()
            val rel = o.optString("rel", "").trim()
            if (to.isNotBlank() && rel.isNotBlank()) OkfRelation(from = from, target = to, rel = rel)
            else null
        }
    }
}
