package io.somi.llm

import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * The minimal Phase-2 LLM contract.
 *
 * Implementations: in 2.1 only [NoOpLlamaContext]; from 2.3 onward the
 * `LlamaCppContext` from `:core-llm-llama` becomes the default Hilt
 * binding.
 *
 * Threading rules (load-bearing — re-read before deviating):
 *  - All native calls happen on a dedicated single-thread dispatcher.
 *    `llama_decode` is blocking and not thread-safe; sharing a context
 *    across coroutines corrupts the KV cache silently.
 *  - [load] is `suspend` because mmap'ing 4.7 GB of weights takes
 *    seconds. Callers must show LoadingModel state until it resolves.
 *  - [setSystemPrompt] runs the system message through the model once
 *    (warming the KV cache); subsequent [generate] calls reuse the cache.
 *    Set this to soul.md exactly once after [load], never on every turn.
 *  - [generate] returns a cold [Flow]. No tokens flow until the consumer
 *    collects. Cancelling the collector aborts generation cooperatively.
 *  - [close] is idempotent and may be called from any thread.
 *
 * Upstream wrapping note: the implementation in `:core-llm-llama` adapts
 * the `com.arm.aichat.InferenceEngine` API, which itself runs the
 * model's GGUF chat template internally. That means callers pass
 * **plain user text** to [generate], NOT a pre-formatted ChatML prompt.
 * If you ever need to bypass the template (one-shot completion, tool
 * use), drop down to the upstream API directly via a different binding.
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
     * Set the system prompt (soul.md). Pre-feeds it to the model and
     * caches the resulting KV state, so subsequent [generate] calls
     * don't re-process the prefix on every turn.
     *
     * Must be called after [load] and before any [generate]. Call once
     * per session; calling again is allowed but discards the existing
     * KV cache.
     */
    suspend fun setSystemPrompt(systemPrompt: String)

    /**
     * Stream a user-message generation.
     *
     * The string is the **raw user text**, not a ChatML-formatted prompt.
     * The implementation runs it through the GGUF's built-in chat
     * template, appends the appropriate role markers, and streams the
     * assistant response.
     *
     * The Flow emits decoded text chunks (typically 1-8 chars; llama.cpp
     * coalesces short tokens). The Flow completes when the model emits
     * its end-of-turn token or [maxTokens] is reached.
     *
     * @param userMessage raw user text for this turn
     * @param maxTokens generation budget; the model stops at this many
     *   even if it hasn't decided to end a turn
     */
    fun generate(userMessage: String, maxTokens: Int): Flow<String>

    /**
     * Release the native context and KV cache. Safe to call without
     * having called [load]; safe to call multiple times.
     */
    fun close()
}
