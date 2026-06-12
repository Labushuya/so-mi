package io.somi.rag

import android.util.Log
import io.somi.llm.LlamaContext
import io.somi.rag.embed.Embedder
import io.somi.rag.memory.MemoryFileRepository
import io.somi.rag.memory.MemoryStore
import io.somi.rag.memory.MemoryTopic
import io.somi.rag.trigger.TriggerDetector
import io.somi.rag.trigger.TriggerMatch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * v0.14.0 M6 — orchestrates the trigger → embed → save → mirror pipeline.
 * v0.18.5 M8 — adds recall: recallForPrompt() reads the .md files and
 * injects the most relevant facts as context before LLM generation.
 *
 * Public surface used by ChatViewModel:
 *  - [maybeSaveOnSubmit] — called pre-LLM with the user's message;
 *    returns a [SaveOutcome] the ChatViewModel surfaces in the UI.
 *
 * The orchestrator is the **only** seam where ChatViewModel touches
 * RAG. Everything else (embedder lazy-load, ObjectBox writes, .md
 * file mirror, decay metadata) is internal.
 *
 * Failure semantics: if anything below the trigger detection throws
 * (embedder not yet downloaded, ObjectBox write race, .md file IO),
 * the outcome reflects the failure but **the user message still
 * goes to the LLM**. RAG is best-effort; a failed save must not
 * block the chat.
 */
@Singleton
class RagOrchestrator @Inject constructor(
    private val triggerDetector: TriggerDetector,
    private val embedder: Embedder,
    private val memoryStore: MemoryStore,
    private val memoryFiles: MemoryFileRepository,
    private val llama: LlamaContext,
) {

    /**
     * Pre-LLM hook for ChatViewModel.submit().
     *
     * If the user message contains a trigger phrase, we save the fact
     * to the memory store + .md mirror, then return [SaveOutcome.Saved]
     * so the ChatViewModel can render the "Hab ich."-bubble. If no
     * trigger matched, returns [SaveOutcome.NotTriggered] and the
     * normal LLM generation proceeds.
     *
     * In M6 every trigger lands in [MemoryTopic.NOTES] (hardcoded
     * fallback). M9's TopicClassifier replaces this with intelligent
     * routing.
     */
    suspend fun maybeSaveOnSubmit(userText: String): SaveOutcome {
        // v0.26.0 — check for keyword management commands FIRST.
        // These don't need a trigger phrase — they're direct commands.
        val kwCmd = io.somi.rag.trigger.KeywordCommandDetector.detect(userText)
        if (kwCmd != null) {
            val root = memoryFiles.rootDir
            val knownIds = root.listFiles()
                ?.filter { it.extension == "md" }
                ?.filter { f -> MemoryTopic.entries.none { it.id == f.nameWithoutExtension } }
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
            val resolvedId = io.somi.rag.trigger.KeywordCommandDetector.resolveCategory(kwCmd.categoryHint, knownIds)
            return when (kwCmd.action) {
                io.somi.rag.trigger.KeywordCommandDetector.Action.ADD -> {
                    if (resolvedId != null) {
                        withContext(kotlinx.coroutines.Dispatchers.IO) { addKeyword(resolvedId, kwCmd.keyword) }
                        SaveOutcome.KeywordAdded(kwCmd.keyword, resolvedId)
                    } else SaveOutcome.NotTriggered
                }
                io.somi.rag.trigger.KeywordCommandDetector.Action.REMOVE -> {
                    if (resolvedId != null) {
                        withContext(kotlinx.coroutines.Dispatchers.IO) { removeKeyword(resolvedId, kwCmd.keyword) }
                        SaveOutcome.KeywordRemoved(kwCmd.keyword, resolvedId)
                    } else SaveOutcome.NotTriggered
                }
                io.somi.rag.trigger.KeywordCommandDetector.Action.SHOW -> {
                    val keywords = if (resolvedId != null) getKeywordsForCategory(resolvedId) else emptyList()
                    SaveOutcome.KeywordsShown(resolvedId ?: kwCmd.categoryHint, keywords)
                }
            }
        }

        val match: TriggerMatch = triggerDetector.detect(userText)
            ?: return SaveOutcome.NotTriggered

        return try {
            val now = System.currentTimeMillis()

            // v0.22.0 M9 — Multi-fact extraction + TopicClassifier.
            // Split the fact text into individual facts (by "und" conjunctions)
            // and classify each into the best-matching topic.
            val rawFacts = splitIntoFacts(normalizeFact(match.factText))
            val classified = rawFacts.map { fact -> fact to (runCatching { classifyFactWithLlm(fact) }.getOrElse { classifyFact(fact) }) }

            val embedderReady = runCatching { embedder.isAvailable() }.getOrDefault(false)

            classified.forEach { (fact, topic) ->
                try {
                    // Custom categories take priority over enum classification.
                    // A user who created "Beruf & Job" clearly wants job-related
                    // facts there — even if classifyFact() picked PERSONS first.
                    val customCategoryId = findCustomCategory(fact)

                    if (customCategoryId != null) {
                        // Write directly to the custom .md file with dedup
                        val customFile = File(memoryFiles.rootDir, "$customCategoryId.md")
                        customFile.parentFile?.mkdirs()
                        if (!customFile.exists()) {
                            customFile.writeText("# ${customCategoryId.replaceFirstChar { it.uppercaseChar() }.replace("_", " ")}\n\n<!-- Eigene Kategorie -->\n\n")
                        }
                        val alreadyExists = customFile.readLines()
                            .filter { it.trimStart().startsWith("- ") }
                            .map { it.trimStart().removePrefix("- ").replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim().lowercase() }
                            .any { it == fact.lowercase() }
                        if (!alreadyExists) {
                            val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.GERMAN).format(java.util.Date(now))
                            customFile.appendText("- $fact  _(gespeichert: $ts)_\n")
                            Log.i(TAG, "saved to custom: '$fact' → $customCategoryId")
                        } else {
                            Log.i(TAG, "skipped duplicate in custom: '$fact'")
                        }
                    } else {
                        memoryFiles.append(fact, topic, now)
                    }

                    val embedding = if (embedderReady) {
                        runCatching { embedder.embed(fact) }.getOrNull()
                    } else null
                    memoryStore.save(
                        fact = fact,
                        topic = topic,
                        embedding = embedding ?: FloatArray(384) { 0f },
                        confidence = if (embedding != null) 1.0f else 0f,
                        supersedesId = 0,
                        now = now,
                    )
                    Log.i(TAG, "saved: '$fact' → ${customCategoryId ?: topic.id}")
                } catch (t: Throwable) {
                    Log.e(TAG, "failed to save fact '$fact'", t)
                }
            }

            val savedText = classified.joinToString(", ") { (f, _) -> f }
            SaveOutcome.Saved(
                triggerPhrase = match.triggerPhrase,
                factText = savedText,
                topic = classified.firstOrNull()?.second ?: MemoryTopic.NOTES,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "save pipeline failed", t)
            SaveOutcome.SaveFailed(
                triggerPhrase = match.triggerPhrase,
                reason = SaveFailureReason.IO,
                cause = t,
            )
        }
    }

    /**
     * v0.22.0 M9 — split a normalized fact string into individual facts.
     * "Ich heiße Christopher und wurde am 26.09.1990 geboren" →
     * ["Ich heiße Christopher", "Wurde am 26.09.1990 geboren"]
     */
    private fun splitIntoFacts(normalized: String): List<String> {
        // Split on " und " — generously, any conjunction between facts.
        // A date like "2. und 3. März" won't appear here because it's
        // inside a fact, not between two complete clauses.
        // We split and capitalize each part separately.
        val parts = normalized.split(Regex("\\s+und\\s+"))
        val result = parts.map { it.trim().replaceFirstChar { c -> c.uppercaseChar() } }
            .filter { it.length >= 5 }  // skip fragments like "am"
        // If splitting produces only one part longer than 40 chars, try
        // splitting on comma + "und" or semicolon too.
        return result.ifEmpty { listOf(normalized) }
    }

    /**
     * v0.22.1 — LLM-based fact classifier. Asks the already-loaded LLM to
     * classify [fact] into a [MemoryTopic] category key. Falls back to the
     * regex-based [classifyFact] on timeout, error, or unrecognised output.
     *
     * LlamaContext.generate() handles its own dispatcher switching internally;
     * no additional withContext() wrapper is needed here.
     */
    private suspend fun classifyFactWithLlm(fact: String): MemoryTopic {
        val prompt = """Klassifiziere diesen Fakt in genau eine Kategorie. Antworte NUR mit dem Kategorie-Schlüssel, nichts sonst.

Kategorien:
- persons (Personen, Namen, Orte, Familie)
- preferences (Vorlieben, Hobbys, Geschmack)
- dates (Termine, Geburtstage, Zeitangaben)
- technical (Technik, Software, Geräte)
- notes (alles andere)

Fakt: $fact

Kategorie:"""
        val result = withTimeoutOrNull(3000L) {
            llama.generate(prompt, maxTokens = 8)
                .fold("") { acc, chunk -> acc + chunk }
        } ?: return classifyFact(fact)
        val key = result.trim().lowercase().split(Regex("\\s+")).firstOrNull() ?: ""
        return MemoryTopic.entries.firstOrNull { it.id == key } ?: classifyFact(fact)
    }

    /**
     * v0.22.0 M9 — classify a single fact into a MemoryTopic using
     * keyword heuristics. LLM-based classification comes in v0.22.1.
     */
    private fun classifyFact(fact: String): MemoryTopic {
        val lower = fact.lowercase()

        // Check custom categories first — if a fact's keywords match a
        // custom category name (e.g. "beruf" in "beruf_und_job.md"), prefer it.
        // We return NOTES as a sentinel; the caller maps NOTES to a custom category
        // when one is found. This avoids changing the return type.
        // (Custom category routing is handled in the caller via a secondary pass.)

        return when {
            // Dates
            lower.contains(Regex("\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}")) -> MemoryTopic.DATES
            lower.contains(Regex("\\b(januar|februar|märz|april|mai|juni|juli|august|september|oktober|november|dezember|january|february|march|april|may|june|july|august|september|october|november|december)\\b")) -> MemoryTopic.DATES
            lower.contains(Regex("\\b(geboren|geburtstag|birthday|geb\\.|am \\d+\\.)")) -> MemoryTopic.DATES
            lower.contains(Regex("\\b(termin|meeting|treffen|uhr|morgen|übermorgen|nächste woche)\\b")) -> MemoryTopic.DATES
            // Persons
            lower.contains(Regex("\\b(heiße|name ist|bin .{1,30} jahre|mein name|ich heiße|ich bin .{1,20}|meine (frau|mann|schwester|bruder|mutter|vater|kind|freundin|freund))\\b")) -> MemoryTopic.PERSONS
            lower.contains(Regex("\\b(wohne in|lebe in|komme aus|wohnung|adresse)\\b")) -> MemoryTopic.PERSONS
            // Preferences
            lower.contains(Regex("\\b(mag|liebe|esse gern|trinke gern|höre gern|schaue gern|spiele gern|interessiere mich|hobby|lieblings|am liebsten|gefällt mir|lieber|bevorzuge)\\b")) -> MemoryTopic.PREFERENCES
            // Technical
            lower.contains(Regex("\\b(nutze|benutze|habe .{1,20} gerät|mein (computer|laptop|handy|telefon|auto|fahrrad)|software|app|modell|version|passwort|server|api|code)\\b")) -> MemoryTopic.TECHNICAL
            else -> MemoryTopic.NOTES
        }
    }

    /**
     * v0.25.0 — Find a custom .md category whose name matches keywords
     * in the fact. For example, if a "beruf_und_job.md" exists and the
     * fact contains "ingenieur" or "arbeite", route to that file instead
     * of NOTES. Returns null if no match.
     */
    private fun findCustomCategory(fact: String): String? {
        val lower = fact.lowercase()
        val root = memoryFiles.rootDir
        val customFiles = root.listFiles()
            ?.filter { it.extension == "md" }
            ?.filter { f -> MemoryTopic.entries.none { it.id == f.nameWithoutExtension } }
            ?: return null

        // Load user-defined keywords from .keywords.json
        val userKeywords = loadUserKeywords(root)

        return customFiles.firstOrNull { file ->
            val id = file.nameWithoutExtension
            // Keywords from filename (e.g. "beruf_und_job" → ["beruf", "job"])
            val filenameKeywords = id.split("_").filter { it.length >= 4 && it != "und" }
            // User-defined keywords for this category
            val customKeywords = userKeywords[id] ?: emptyList()
            val allKeywords = (filenameKeywords + customKeywords).toSet()
            allKeywords.any { keyword -> lower.contains(keyword) }
        }?.nameWithoutExtension
    }

    /**
     * Loads user-defined category keywords from SoMi/memory/.keywords.json.
     * Format: {"beruf_und_job": ["engineer", "sre", "ingenieur"], ...}
     */
    private fun loadUserKeywords(root: java.io.File): Map<String, List<String>> {
        val file = java.io.File(root, ".keywords.json")
        if (!file.exists()) return emptyMap()
        return try {
            val json = org.json.JSONObject(file.readText())
            json.keys().asSequence().associate { key ->
                val arr = json.getJSONArray(key)
                key to (0 until arr.length()).map { arr.getString(it) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "failed to load keywords.json", t)
            emptyMap()
        }
    }

    /**
     * v0.26.0 — Adds a keyword for a custom category. Called when the user
     * says "Füge 'X' als Keyword für Y hinzu" or via the Settings UI.
     */
    fun addKeyword(categoryId: String, keyword: String) {
        val root = memoryFiles.rootDir
        val file = java.io.File(root, ".keywords.json")
        val current = loadUserKeywords(root).toMutableMap()
        val existing = current[categoryId]?.toMutableList() ?: mutableListOf()
        if (!existing.contains(keyword.lowercase())) {
            existing.add(keyword.lowercase())
        }
        current[categoryId] = existing
        val json = org.json.JSONObject()
        current.forEach { (k, v) ->
            val arr = org.json.JSONArray()
            v.forEach { arr.put(it) }
            json.put(k, arr)
        }
        file.writeText(json.toString(2))
        Log.i(TAG, "keyword added: '$keyword' → $categoryId")
    }

    fun removeKeyword(categoryId: String, keyword: String) {
        val root = memoryFiles.rootDir
        val file = java.io.File(root, ".keywords.json")
        val current = loadUserKeywords(root).toMutableMap()
        current[categoryId] = (current[categoryId] ?: emptyList()).filter { it != keyword.lowercase() }
        val json = org.json.JSONObject()
        current.forEach { (k, v) ->
            val arr = org.json.JSONArray()
            v.forEach { arr.put(it) }
            json.put(k, arr)
        }
        file.writeText(json.toString(2))
    }

    fun getKeywordsForCategory(categoryId: String): List<String> {
        return loadUserKeywords(memoryFiles.rootDir)[categoryId] ?: emptyList()
    }

    /**
     * v0.18.5 M8 — Recall: reads all saved facts from the .md mirror
     * and formats them as a compact context block for injection into
     * the LLM prompt before generation.
     *
     * Uses the .md files (not ObjectBox) because many facts were saved
     * before the embedder was available and have a zero-vector. Full
     * HNSW vector search comes in a later version once all facts have
     * real embeddings. For now: return ALL non-empty facts (capped at
     * MAX_RECALL_FACTS) so the context stays within KV-cache budget.
     *
     * @return formatted context string, or null if no facts exist.
     */
    fun recallForPrompt(): String? {
        // Read ALL .md files in the memory directory — including custom categories
        // created by the user (e.g. "Beruf & Job" → "beruf_und_job.md").
        val root = memoryFiles.rootDir
        val mdFiles = root.listFiles()?.filter { it.extension == "md" } ?: emptyList()
        val allFacts = mdFiles.flatMap { file ->
            file.readLines()
                .filter { it.trimStart().startsWith("- ") }
                .mapNotNull { line ->
                    line.trimStart()
                        .removePrefix("- ")
                        .replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "")
                        .trim()
                        .takeIf { it.isNotBlank() }
                }
        }.take(MAX_RECALL_FACTS)

        if (allFacts.isEmpty()) return null

        return buildString {
            // Natural-language framing so So-Mi integrates the facts into her
            // responses smoothly rather than parroting them mechanically.
            append("Was du über deinen Nutzer weißt (diese Fakten kennt der Nutzer — nutze sie natürlich im Gespräch, wiederhole sie nicht einfach):\n")
            allFacts.forEach { append("- $it\n") }
            append("\n")
        }
    }

    /**
     * Normalize the raw fact text extracted after stripping the trigger phrase.
     * Removes leading subordinating conjunctions so "dass ich Christopher heiße"
     * becomes "ich heiße Christopher".
     */
    private fun normalizeFact(raw: String): String {
        val lower = raw.trimStart()
        // Strip leading subordinating conjunctions (dass, weil, ob, damit, etc.)
        val withoutConj = LEADING_CONJUNCTIONS.fold(lower) { acc, conj ->
            if (acc.startsWith(conj, ignoreCase = true))
                acc.substring(conj.length).trimStart()
            else acc
        }
        // Capitalize first letter
        return withoutConj.replaceFirstChar { it.uppercaseChar() }
    }

    private companion object {
        const val TAG = "RagOrchestrator"
        const val MAX_RECALL_FACTS = 20

        private val LEADING_CONJUNCTIONS = listOf(
            "dass ", "das ", "weil ", "ob ", "damit ", "obwohl ", "obgleich ",
            "that ", "because ", "since ", "although ",
        )
    }
}

/**
 * Outcome the ChatViewModel uses to decide what to render.
 *
 * - [NotTriggered]: no keyword found; ChatViewModel proceeds with
 *   the normal LLM generation.
 * - [Saved]: trigger fired, save succeeded; ChatViewModel renders a
 *   short "Hab ich."-bubble, then ALSO proceeds with the LLM
 *   generation so the user can immediately ask follow-up
 *   questions.
 * - [SaveFailed]: trigger fired but the save failed; ChatViewModel
 *   shows a small banner explaining the cause and proceeds with
 *   the LLM.
 */
sealed interface SaveOutcome {
    data object NotTriggered : SaveOutcome

    data class Saved(
        val triggerPhrase: String,
        val factText: String,
        val topic: MemoryTopic,
    ) : SaveOutcome

    data class SaveFailed(
        val triggerPhrase: String,
        val reason: SaveFailureReason,
        val cause: Throwable? = null,
    ) : SaveOutcome

    /** v0.26.0 — keyword management outcomes */
    data class KeywordAdded(val keyword: String, val categoryId: String) : SaveOutcome
    data class KeywordRemoved(val keyword: String, val categoryId: String) : SaveOutcome
    data class KeywordsShown(val categoryId: String, val keywords: List<String>) : SaveOutcome
}

enum class SaveFailureReason {
    /** bge model still downloading or never installed. */
    EMBEDDER_NOT_READY,
    /** ObjectBox / file IO threw. */
    IO,
}
