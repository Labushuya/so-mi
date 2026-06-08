package io.somi.rag.memory

/**
 * v0.14.0 M3 — taxonomy for the user-facing memory buckets.
 *
 * Locked set per v0.14.0 planning. Dynamic topic creation comes in
 * M9 (TopicClassifier) — the user's "Mischung aus allen drei Optionen"
 * decision means: predefined buckets first, classifier picks one,
 * with disambiguation question when uncertain. Until M9 ships, every
 * memory lands in [NOTES] — that's the M6 hardcoded fallback.
 *
 * Each topic maps 1:1 to a Markdown file under
 * `$externalFilesDir/memory/<id>.md` (M4) — keeping the user-readable
 * mirror in sync with the ObjectBox row's `topic` field.
 */
enum class MemoryTopic(val id: String, val displayName: String) {
    PERSONS("persons", "Personen"),
    PREFERENCES("preferences", "Vorlieben"),
    DATES("dates", "Termine"),
    TECHNICAL("technical", "Technik"),
    NOTES("notes", "Notizen");

    companion object {
        fun byId(id: String): MemoryTopic? =
            entries.firstOrNull { it.id == id }
    }
}
