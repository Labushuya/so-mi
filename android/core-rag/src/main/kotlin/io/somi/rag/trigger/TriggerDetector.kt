package io.somi.rag.trigger

import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.14.0 M5 — explicit-trigger detection for the Hybrid-Lern-RAG.
 *
 * Per v0.14.0 user-locked decisions: keyword-match is the default-on
 * trigger path. The Auto-Toggle in M10 layers an LLM-vote on top
 * for users who want fuzzy detection.
 *
 * **Detection rule.** A trigger fires when the user message starts
 * with (or contains as standalone phrase) one of the [PHRASES].
 * The phrase is **stripped** from the resulting fact text — if the
 * user types "merk dir, ich heiße Christopher", the saved fact is
 * "ich heiße Christopher" (lowercased first letter preserved
 * because the user's casing matters for downstream embedding).
 *
 * Rules in order:
 *   1. Detection is case-insensitive.
 *   2. Multi-word phrases match across word boundaries
 *      ("merk dir" matches but "merkbar" doesn't — anchored at \b).
 *   3. After the phrase, an optional comma/colon/dash is also stripped.
 *   4. The remaining fact must be at least [MIN_FACT_CHARS] chars to
 *      avoid saving accidental matches like "merk dir das".
 */
@Singleton
class TriggerDetector @Inject constructor() {

    /**
     * @return [TriggerMatch] if a trigger fires, null if no match or
     *   the post-strip fact is too short.
     */
    fun detect(userText: String): TriggerMatch? {
        val text = userText.trim()
        if (text.isEmpty()) return null

        for (phrase in PHRASES) {
            val match = phrase.regex.find(text) ?: continue
            // Strip the matched phrase + a trailing punctuation mark.
            val tail = text.substring(match.range.last + 1)
                .trimStart()
                .trimStart { it in PUNCTUATION_AFTER_TRIGGER }
                .trim()
            if (tail.length < MIN_FACT_CHARS) {
                // Trigger fired but the fact is empty / "merk dir das"
                // — don't save garbage. M9's classifier could ask
                // "was genau soll ich mir merken?", but in M5 we just
                // return null so the chat goes through the LLM normally.
                return null
            }
            return TriggerMatch(
                triggerPhrase = match.value,
                factText = tail,
                matchedAt = match.range.first,
            )
        }
        return null
    }

    companion object {
        /** Minimum characters after stripping the trigger phrase. */
        const val MIN_FACT_CHARS = 3

        /**
         * Locked trigger set per v0.14.0 planning. Each phrase is
         * anchored at word boundaries so "merkt" / "merkbar" don't
         * trigger.
         */
        private val PHRASES: List<TriggerPhrase> = listOf(
            // German
            TriggerPhrase("merk dir"),
            TriggerPhrase("merk's dir"),
            TriggerPhrase("merke dir"),
            TriggerPhrase("vergiss nicht"),
            TriggerPhrase("wichtig:"),
            TriggerPhrase("speichere"),
            TriggerPhrase("erinnere dich"),
            // English
            TriggerPhrase("remember"),
            // Slash-command
            TriggerPhrase("/note"),
        )

        private val PUNCTUATION_AFTER_TRIGGER = charArrayOf(
            ',', ':', ';', '-', '–', '—', '!', '.', '?',
        )
    }
}

/**
 * Successful trigger match. The caller (ChatViewModel in M6) uses
 * [factText] as the to-save string and [triggerPhrase] purely for
 * logging.
 */
data class TriggerMatch(
    val triggerPhrase: String,
    val factText: String,
    val matchedAt: Int,
)

private class TriggerPhrase(phrase: String) {
    /** Word-boundary-anchored, case-insensitive. */
    val regex: Regex = run {
        // Escape regex specials in the phrase, then anchor at \b at
        // both ends. The `:` in "wichtig:" is not a word char, so we
        // skip the trailing \b for phrases that already end on punct.
        val escaped = Regex.escape(phrase)
        val endAnchor = if (phrase.last().isLetterOrDigit()) "\\b" else ""
        Regex("\\b$escaped$endAnchor", RegexOption.IGNORE_CASE)
    }

    val value: String = phrase
}
