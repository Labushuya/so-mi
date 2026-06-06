package io.somi.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-2.1 placeholder LlamaContext.
 *
 * Returns immediately for [load], emits an empty [Flow] for [generate],
 * and is a no-op for [close]. Lets the Hilt graph + ViewModel + UI
 * compile and run end-to-end before the real JNI binding lands in 2.3,
 * so we can verify the dependency injection wiring in isolation from
 * native-code complexity.
 *
 * Replaced by `LlamaCppContext` (in `:core-llm-llama`) in Phase 2.3 via
 * a Hilt binding switch, not a code edit here.
 */
@Singleton
internal class NoOpLlamaContext @Inject constructor() : LlamaContext {

    override suspend fun load(modelFile: File) {
        // 2.1: no-op. Future LoadingModel UI state still resolves
        // immediately, exposing it as a momentary flicker rather than a
        // 5–15 s freeze. Real load lives in LlamaCppContext (2.3).
    }

    override fun generate(prompt: String, maxTokens: Int): Flow<String> = flowOf()

    override fun close() {
        // 2.1: nothing to release.
    }
}
