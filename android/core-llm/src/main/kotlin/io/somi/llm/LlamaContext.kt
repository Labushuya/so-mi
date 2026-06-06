package io.somi.llm

import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * The minimal Phase-2 LLM contract.
 *
 * Implementations: in 2.1 only [NoOpLlamaContext]; in 2.3 the JNI-backed
 * `LlamaCppContext` from `:core-llm-llama` becomes the default Hilt
 * binding.
 *
 * Threading rules (load-bearing — re-read before deviating):
 *  - All native calls happen on a dedicated single-thread dispatcher
 *    (`Dispatchers.Default.limitedParallelism(1)` is the cheap pick).
 *    `llama_decode` is blocking and not thread-safe; sharing a context
 *    across coroutines corrupts the KV cache silently.
 *  - [load] is `suspend` because mmap'ing 4.7 GB of weights takes
 *    seconds. Callers must show LoadingModel state until it resolves.
 *  - [generate] returns a cold [Flow]. No tokens flow until the consumer
 *    collects. Cancelling the collector aborts the generation cooperatively
 *    — the implementation checks `coroutineContext.isActive` between
 *    decode steps.
 *  - [close] is idempotent and may be called from any thread; the
 *    implementation must marshal to the dedicated dispatcher internally.
 */
interface LlamaContext {

    /**
     * mmap-load a GGUF model. Idempotent — calling twice with the same
     * file is a no-op; calling with a different file releases the old
     * context first.
     *
     * @param modelFile path to a `.gguf` on disk (not in assets)
     * @throws IllegalStateException if the file isn't a valid GGUF or the
     *   load fails for ABI / version reasons
     */
    suspend fun load(modelFile: File)

    /**
     * Stream a generation. The prompt should already include the soul.md
     * system prefix and the conversation history — assembly is the
     * caller's job (`PromptAssembly.build`, Phase 2.6). The Flow emits
     * decoded text chunks (typically 1-8 chars; llama.cpp coalesces
     * short tokens). The Flow completes when the model emits its
     * end-of-turn token or [maxTokens] is reached.
     *
     * @param prompt the fully-assembled chat-template-formatted prompt
     * @param maxTokens generation budget; the model stops at this many
     *   even if it hasn't decided to end a turn
     */
    fun generate(prompt: String, maxTokens: Int): Flow<String>

    /**
     * Release the native context and KV cache. Safe to call without
     * having called [load]; safe to call multiple times.
     */
    fun close()
}
