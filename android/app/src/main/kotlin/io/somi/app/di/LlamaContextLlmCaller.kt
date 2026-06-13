package io.somi.app.di

import io.somi.common.llm.LlmCaller
import io.somi.llm.LlamaContext
import kotlinx.coroutines.flow.fold
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaContextLlmCaller @Inject constructor(
    private val llama: LlamaContext,
) : LlmCaller {
    override suspend fun generate(prompt: String, maxTokens: Int): String {
        val sb = StringBuilder()
        llama.generate(prompt, maxTokens = maxTokens)
            .fold(sb) { acc, chunk -> acc.also { it.append(chunk) } }
        return sb.toString()
    }
}
