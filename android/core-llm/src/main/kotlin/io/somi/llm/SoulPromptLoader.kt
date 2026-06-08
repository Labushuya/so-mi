package io.somi.llm

/**
 * Reads `assets/soul.md` once per process and caches it. The returned
 * String is fed verbatim into the LLM prompt as the `system` block on
 * EVERY request — never RAG-retrieved, never paraphrased, never
 * overwritten. SPEC §2 is the only source of truth for So-Mi's voice.
 *
 * Phase 2.1: stub returning a placeholder. The real implementation in
 * Phase 2.6 reads `app/src/main/assets/soul.md` via `Context.assets.open`.
 * We don't need it loaded for 2.1's smoke test (no generation runs), but
 * the interface is here so the ViewModel can constructor-inject it
 * without a future signature change.
 */
interface SoulPromptLoader {
    /** Returns the soul.md contents. Cached after first call. */
    suspend fun load(): String

    /**
     * Drop the in-memory cache. Next [load] re-reads from disk —
     * filesDir-override first, then assets fallback.
     *
     * Called from ChatViewModel.reloadSoul() after the user saves an
     * edit in the Settings → Persönlichkeit screen.
     */
    fun invalidate()
}
