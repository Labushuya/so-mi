package io.somi.llm

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-2.1 stub. Returns a fixed placeholder so consumers can be wired
 * + tested before the real assets/soul.md reader lands in Phase 2.6.
 */
@Singleton
internal class StubSoulPromptLoader @Inject constructor() : SoulPromptLoader {
    override suspend fun load(): String = "[soul.md placeholder — wired in Phase 2.6]"
}
