package io.somi.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-2.10 implementation. Reads `assets/soul_condensed.md` once per
 * process and caches it.
 *
 * The full `soul.md` (~4 KB / ~1500 tokens) was too long for a CPU-only
 * 7B Q4_K_M boot path — prefill took 5–15 minutes. Earlier attempts to
 * naively `.take(600)` killed the persona because the alias-handling
 * rules and address-style rules live further down in the file.
 *
 * The condensed file at `assets/soul_condensed.md` (~600 chars / ~200
 * Qwen2.5 tokens) is a hand-crafted compaction that preserves:
 *  - alias triad (So-Mi self / Songbird user-context / chingu trust-only)
 *  - address rule (first name OR nothing — never "Hey du" / "lieber Nutzer")
 *  - language hierarchy (DE default, EN code-switch, KO only for chingu)
 *  - tone (rauchig-leise / dringlich / trocken)
 *  - values (Freiheit / Loyalität / Pragmatismus / Misstrauen)
 *  - anti-corpo / anti-disclaimer / anti-cheerleading rules
 *
 * The full `soul.md` still ships in the same assets dir; Phase-3 RAG
 * will retrieve passages from it (Beispiel-Dialoge, full values, the
 * meta-reflection passages) on demand.
 *
 * Decoded as UTF-8 — soul_condensed.md contains German umlauts and
 * em-dashes; any other charset would corrupt them.
 */
@Singleton
internal class AssetsSoulPromptLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) : SoulPromptLoader {

    @Volatile
    private var cached: String? = null

    override suspend fun load(): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            cached = text
            return text
        }
    }

    private companion object {
        const val ASSET_NAME = "soul_condensed.md"
    }
}

