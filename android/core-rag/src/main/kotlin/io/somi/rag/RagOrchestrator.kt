package io.somi.rag

import android.util.Log
import io.somi.rag.embed.Embedder
import io.somi.rag.memory.MemoryFileRepository
import io.somi.rag.memory.MemoryStore
import io.somi.rag.memory.MemoryTopic
import io.somi.rag.trigger.TriggerDetector
import io.somi.rag.trigger.TriggerMatch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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
        val match: TriggerMatch = triggerDetector.detect(userText)
            ?: return SaveOutcome.NotTriggered

        return try {
            val now = System.currentTimeMillis()

            // v0.22.0 M9 — Multi-fact extraction + TopicClassifier.
            // Split the fact text into individual facts (by "und" conjunctions)
            // and classify each into the best-matching topic.
            val rawFacts = splitIntoFacts(normalizeFact(match.factText))
            val classified = rawFacts.map { fact -> fact to classifyFact(fact) }

            val embedderReady = runCatching { embedder.isAvailable() }.getOrDefault(false)

            classified.forEach { (fact, topic) ->
                memoryFiles.append(fact, topic, now)
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
                Log.i(TAG, "saved: '$fact' → ${topic.id}")
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
        // Split on " und " at clause boundaries (not inside dates like "2. und 3. März")
        val parts = normalized.split(Regex("\\s+und\\s+(?=[A-ZÄÖÜ]|ich |er |sie |es |du )"))
        return parts.map { it.trim().replaceFirstChar { c -> c.uppercaseChar() } }
            .filter { it.length >= 3 }
            .ifEmpty { listOf(normalized) }
    }

    /**
     * v0.22.0 M9 — classify a single fact into a MemoryTopic using
     * keyword heuristics. LLM-based classification comes in v0.22.1.
     */
    private fun classifyFact(fact: String): MemoryTopic {
        val lower = fact.lowercase()
        return when {
            // Dates: numbers with dots/slashes, month names, year patterns
            lower.contains(Regex("\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}")) -> MemoryTopic.DATES
            lower.contains(Regex("\\b(januar|februar|märz|april|mai|juni|juli|august|september|oktober|november|dezember|january|february|march|april|may|june|july|august|september|october|november|december)\\b")) -> MemoryTopic.DATES
            lower.contains(Regex("\\b(geboren|geburtstag|birthday|geb\\.|am \\d+\\.)")) -> MemoryTopic.DATES
            lower.contains(Regex("\\b(termin|meeting|treffen|uhr|morgen|übermorgen|nächste woche)\\b")) -> MemoryTopic.DATES
            // Persons: name, identity, relationships
            lower.contains(Regex("\\b(heiße|name ist|bin .{1,30} jahre|mein name|ich heiße|ich bin .{1,20}|meine (frau|mann|schwester|bruder|mutter|vater|kind|freundin|freund))\\b")) -> MemoryTopic.PERSONS
            lower.contains(Regex("\\b(wohne in|lebe in|komme aus|wohnung|adresse)\\b")) -> MemoryTopic.PERSONS
            // Preferences
            lower.contains(Regex("\\b(mag|liebe|esse gern|trinke gern|höre gern|schaue gern|spiele gern|interessiere mich|hobby|lieblings|am liebsten|gefällt mir|lieber|bevorzuge)\\b")) -> MemoryTopic.PREFERENCES
            // Technical
            lower.contains(Regex("\\b(nutze|benutze|habe .{1,20} gerät|mein (computer|laptop|handy|telefon|auto|fahrrad)|software|app|modell|version|passwort|server|api|code)\\b")) -> MemoryTopic.TECHNICAL
            // Default
            else -> MemoryTopic.NOTES
        }
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
        val allFacts = MemoryTopic.entries.flatMap { topic ->
            val file = File(memoryFiles.rootDir, "${topic.id}.md")
            if (!file.exists()) return@flatMap emptyList()
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
            append("[Bekannte Fakten über den Nutzer]\n")
            allFacts.forEach { append("- $it\n") }
            append("[Ende der Fakten]\n")
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
}

enum class SaveFailureReason {
    /** bge model still downloading or never installed. */
    EMBEDDER_NOT_READY,
    /** ObjectBox / file IO threw. */
    IO,
}
