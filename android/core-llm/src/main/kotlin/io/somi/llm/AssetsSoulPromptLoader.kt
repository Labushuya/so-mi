package io.somi.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-2.6 implementation. Reads `assets/soul.md` once per process and
 * caches it in a volatile field so concurrent callers see the same string
 * without re-opening the asset stream.
 *
 * The file is shipped verbatim from `/soul/soul.md` at the repo root and
 * placed under `app/src/main/assets/soul.md` by the `:app` module's build
 * config. AGP picks `src/main/assets/` up automatically — no manual
 * `sourceSets` wiring needed.
 *
 * Decoded as UTF-8 (the platform default on Android) — soul.md contains
 * German umlauts and em-dashes, so any other charset would corrupt them.
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
            val text = context.assets.open("soul.md").bufferedReader().use { it.readText() }
            cached = text
            return text
        }
    }
}
