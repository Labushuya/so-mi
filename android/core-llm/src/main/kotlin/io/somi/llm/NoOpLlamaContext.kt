package io.somi.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-2.1 placeholder LlamaContext. Deactivated from Phase 2.3 onward
 * via the Hilt binding swap in `:core-llm-llama:LlamaCppModule`, but the
 * class stays around for unit tests and any debug variant that doesn't
 * pull in native code.
 *
 * Returns immediately for [load], no-ops for [setSystemPrompt], emits an
 * empty [Flow] for [generate], and is a no-op for [close]. The Hilt
 * graph compiles + the smoke screen renders before native code lands.
 */
@Singleton
internal class NoOpLlamaContext @Inject constructor() : LlamaContext {

    override suspend fun load(modelFile: File) {
        // no-op
    }

    override suspend fun setSystemPrompt(systemPrompt: String) {
        // no-op
    }

    override fun generate(userMessage: String, maxTokens: Int): Flow<String> = flowOf()

    override fun close() {
        // no-op
    }
}
