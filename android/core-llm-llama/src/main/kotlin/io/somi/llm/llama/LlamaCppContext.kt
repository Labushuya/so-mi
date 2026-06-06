package io.somi.llm.llama

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.UnsupportedArchitectureException
import dagger.hilt.android.qualifiers.ApplicationContext
import io.somi.llm.LlamaContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts the upstream `com.arm.aichat.InferenceEngine` onto our
 * [LlamaContext] interface. This is the only place the rest of the app
 * touches `com.arm.aichat.*` — every other module sees only
 * [LlamaContext], which keeps the upstream package boundary contained
 * to `:core-llm-llama`.
 *
 * Lifecycle (caller's responsibility):
 *  1. [load] mmap's the GGUF and warms the backend; expect 1-15 s.
 *  2. [setSystemPrompt] runs soul.md through the model exactly once.
 *  3. [generate] streams per-turn responses; the model holds the KV
 *     cache so each turn re-uses prior context.
 *  4. [close] tears down the model and frees the KV cache.
 *
 * Singleton scope (Hilt) is non-negotiable: the upstream
 * `InferenceEngineImpl` is a process-wide singleton hosting the native
 * model + KV cache. Multiple Hilt instances would all alias the same
 * native state but lose Kotlin-side state-flow coherence.
 */
@Singleton
internal class LlamaCppContext @Inject constructor(
    @ApplicationContext private val context: Context,
) : LlamaContext {

    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)

    override suspend fun load(modelFile: File) {
        require(modelFile.exists()) { "Model file does not exist: ${modelFile.absolutePath}" }
        require(modelFile.canRead()) { "Model file is not readable: ${modelFile.absolutePath}" }
        try {
            engine.loadModel(modelFile.absolutePath)
        } catch (e: UnsupportedArchitectureException) {
            throw IllegalStateException(
                "Model architecture is not supported by llama.cpp at this build's pin: " +
                    modelFile.name,
                e,
            )
        }
    }

    override suspend fun setSystemPrompt(systemPrompt: String) {
        engine.setSystemPrompt(systemPrompt)
    }

    override fun generate(userMessage: String, maxTokens: Int): Flow<String> = flow {
        // Delegate straight to the upstream Flow; the upstream impl
        // handles the dedicated single-thread dispatcher + ChatML
        // template auto-application internally.
        engine.sendUserPrompt(userMessage, predictLength = maxTokens)
            .collect { token -> emit(token) }
    }

    override fun close() {
        // cleanUp() unloads the model but keeps the engine instance.
        // destroy() also tears down the engine; we want the singleton to
        // be re-loadable, so cleanUp() is the right level.
        engine.cleanUp()
    }
}
