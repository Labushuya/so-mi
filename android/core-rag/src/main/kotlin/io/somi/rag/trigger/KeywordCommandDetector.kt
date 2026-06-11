package io.somi.rag.trigger

/**
 * v0.26.0 — Erkennt Befehle zur Keyword-Verwaltung für eigene Kategorien.
 *
 * Erkannte Muster (case-insensitive):
 *   "Füge 'engineer' als Keyword für Beruf & Job hinzu"
 *   "Add 'sre' as keyword for beruf_und_job"
 *   "Entferne 'engineer' als Keyword von Beruf & Job"
 *   "Zeig mir die Keywords für Beruf & Job"
 */
object KeywordCommandDetector {

    data class KeywordCommand(
        val action: Action,
        val keyword: String,      // empty for SHOW
        val categoryHint: String, // raw category name/hint from user
    )

    enum class Action { ADD, REMOVE, SHOW }

    private val ADD_PATTERNS = listOf(
        Regex("""(?:füge?|add)\s+['"„]?(\w+)['"„]?\s+als\s+keyword\s+(?:für|for)\s+(.+?)(?:\s+hinzu)?$""", RegexOption.IGNORE_CASE),
        Regex("""keyword\s+['"„]?(\w+)['"„]?\s+(?:für|for|zu)\s+(.+?)\s+(?:hinzufügen|hinzufüge?|add)""", RegexOption.IGNORE_CASE),
        Regex("""(\w+)\s+ist\s+ein\s+keyword\s+(?:für|for)\s+(.+)""", RegexOption.IGNORE_CASE),
    )

    private val REMOVE_PATTERNS = listOf(
        Regex("""(?:entferne?|remove|lösche?)\s+['"„]?(\w+)['"„]?\s+als\s+keyword\s+(?:von|from|für|for)\s+(.+)""", RegexOption.IGNORE_CASE),
    )

    private val SHOW_PATTERNS = listOf(
        Regex("""(?:zeig?|show|welche)\s+keywords?\s+(?:für|for|von|of)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""keywords?\s+(?:für|for|von|of)\s+(.+)""", RegexOption.IGNORE_CASE),
    )

    fun detect(userText: String): KeywordCommand? {
        val text = userText.trim()

        ADD_PATTERNS.forEach { pattern ->
            pattern.find(text)?.let { m ->
                return KeywordCommand(Action.ADD, m.groupValues[1].trim(), m.groupValues[2].trim())
            }
        }

        REMOVE_PATTERNS.forEach { pattern ->
            pattern.find(text)?.let { m ->
                return KeywordCommand(Action.REMOVE, m.groupValues[1].trim(), m.groupValues[2].trim())
            }
        }

        SHOW_PATTERNS.forEach { pattern ->
            pattern.find(text)?.let { m ->
                return KeywordCommand(Action.SHOW, "", m.groupValues[1].trim())
            }
        }

        return null
    }

    /**
     * Resolves a user-provided category hint (e.g. "Beruf & Job", "beruf")
     * to a category file ID by fuzzy matching against known category IDs.
     */
    fun resolveCategory(hint: String, knownIds: List<String>): String? {
        val lower = hint.lowercase().replace("&", "und").replace(" ", "_").replace(Regex("[^a-z0-9_äöüß]"), "")
        // Exact match first
        if (lower in knownIds) return lower
        // Partial match
        return knownIds.firstOrNull { id ->
            id.contains(lower) || lower.contains(id) ||
            id.split("_").any { part -> part.length >= 3 && lower.contains(part) }
        }
    }
}
