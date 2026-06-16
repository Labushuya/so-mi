package io.somi.app.di

import io.somi.common.llm.LlmCaller
import io.somi.llm.LlamaContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaContextLlmCaller @Inject constructor(
    private val llama: LlamaContext,
) : LlmCaller {
    // Stable single-thread dispatcher — same slot reused across calls, matching
    // the serialisation guarantee of ChatViewModel's llamaDispatcher.
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    override suspend fun generate(prompt: String, maxTokens: Int): String {
        val sb = StringBuilder()
        withContext(dispatcher) {
            llama.generate(prompt, maxTokens = maxTokens)
                .fold(sb) { acc, chunk -> acc.also { it.append(chunk) } }
        }
        return sb.toString()
    }
}
