package io.somi.rag

import android.util.Log
import io.somi.rag.embed.Embedder
import io.somi.rag.memory.MemoryFileRepository
import io.somi.rag.memory.MemoryStore
import io.somi.rag.memory.MemoryTopic
import io.somi.rag.trigger.TriggerDetector
import io.somi.rag.trigger.TriggerMatch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.14.0 M6 — orchestrates the trigger → embed → save → mirror
 * pipeline.
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

        // Embedder availability guard — if the bge-mini-LM model
        // isn't on disk yet (first launch, download in flight), we
        // tell the UI explicitly. ChatViewModel can either show a
        // banner ("Erinnerungs-Modell lädt noch…") or just proceed
        // with the LLM.
        val embedderReady = runCatching { embedder.isAvailable() }
            .getOrDefault(false)
        if (!embedderReady) {
            Log.i(TAG, "trigger fired but embedder not ready — skipping save")
            return SaveOutcome.SaveFailed(
                triggerPhrase = match.triggerPhrase,
                reason = SaveFailureReason.EMBEDDER_NOT_READY,
            )
        }

        return try {
            val embedding = embedder.embed(match.factText)
            // M6 hardcoded topic per plan. M9 replaces this with
            // TopicClassifier output + disambiguation chat-bubble.
            val topic = MemoryTopic.NOTES
            val now = System.currentTimeMillis()
            memoryStore.save(
                fact = match.factText,
                topic = topic,
                embedding = embedding,
                confidence = 1.0f,
                supersedesId = 0,
                now = now,
            )
            memoryFiles.append(match.factText, topic, now)
            Log.i(TAG, "saved: '${match.factText.take(60)}…' topic=${topic.id}")
            SaveOutcome.Saved(
                triggerPhrase = match.triggerPhrase,
                factText = match.factText,
                topic = topic,
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

    private companion object {
        const val TAG = "RagOrchestrator"
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
