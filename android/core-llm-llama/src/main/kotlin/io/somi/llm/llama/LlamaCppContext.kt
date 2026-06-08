package io.somi.llm.llama

import android.content.Context
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.UnsupportedArchitectureException
import dagger.hilt.android.qualifiers.ApplicationContext
import io.somi.common.llm.SamplerParams
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
 *
 * **v0.13.0 idempotency.** The vendored upstream is strict: its
 * `loadModel()` enforces `state is Initialized` and `setSystemPrompt`
 * enforces `_readyForSystemPrompt = true` (set false after first use).
 * After any Activity recreation on a process whose FGS keeps the
 * native engine alive, ChatViewModel's init runs again and used to
 * trip both checks — symptom: brief LoadingScreen flicker, then drop
 * back to FirstLaunchScreen, no way out. We bridge that gap here:
 * track the last successfully-loaded canonical path and the last
 * successfully-applied soul hash, short-circuit when they match.
 * The vendored upstream stays untouched.
 */
@Singleton
internal class LlamaCppContext @Inject constructor(
    @ApplicationContext private val context: Context,
) : LlamaContext {

    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)

    /** Canonical absolute path of the currently-loaded GGUF, or null. */
    @Volatile
    private var loadedCanonicalPath: String? = null

    /**
     * Hash of the last successfully-applied system prompt content, or
     * null. We hash so we don't pin a multi-KB string in the singleton
     * for the lifetime of the process. String.hashCode collisions are
     * not a security concern here — the worst case is a missed apply
     * of a content-equal prompt, which is a no-op.
     */
    @Volatile
    private var systemPromptHash: Int? = null

    override suspend fun load(modelFile: File) {
        require(modelFile.exists()) { "Model file does not exist: ${modelFile.absolutePath}" }
        require(modelFile.canRead()) { "Model file is not readable: ${modelFile.absolutePath}" }

        val canonical = canonicalPathOf(modelFile)

        // Idempotency: same file already loaded for this process?
        if (loadedCanonicalPath == canonical && engineHasModel()) {
            Log.i(TAG, "load(${modelFile.name}) short-circuit — already loaded")
            return
        }

        // Different file requested while a model is already loaded → tear
        // down first, otherwise the upstream `check(state is Initialized)`
        // will throw.
        if (loadedCanonicalPath != null && engineHasModel()) {
            Log.i(TAG, "load(${modelFile.name}) — releasing previously-loaded ${loadedCanonicalPath}")
            runCatching { engine.cleanUp() }
                .onFailure { Log.w(TAG, "cleanUp before reload failed", it) }
            // The system prompt for the prior model is no longer valid.
            systemPromptHash = null
        }

        try {
            engine.loadModel(modelFile.absolutePath)
            loadedCanonicalPath = canonical
            // A successful load implies a fresh KV cache; the next
            // setSystemPrompt MUST run, even if its content matches the
            // pre-reload value.
            systemPromptHash = null
        } catch (e: UnsupportedArchitectureException) {
            throw IllegalStateException(
                "Model architecture is not supported by llama.cpp at this build's pin: " +
                    modelFile.name,
                e,
            )
        }
    }

    override suspend fun isLoaded(modelFile: File): Boolean {
        if (!engineHasModel()) return false
        val canonical = canonicalPathOf(modelFile)
        return loadedCanonicalPath == canonical
    }

    override suspend fun setSystemPrompt(systemPrompt: String) {
        require(systemPrompt.isNotBlank()) { "System prompt cannot be blank" }

        val hash = systemPrompt.hashCode()
        if (systemPromptHash == hash && engineHasModel()) {
            Log.i(TAG, "setSystemPrompt short-circuit — same content already applied (hash=$hash)")
            return
        }

        // v0.13.0: the vendored upstream gates setSystemPrompt behind
        // `_readyForSystemPrompt`, which it sets true ONLY immediately
        // after loadModel and resets to false after the first
        // setSystemPrompt OR any user message. Calling setSystemPrompt
        // a second time on a warm engine therefore throws. The user-
        // visible scenario this hits: the in-app soul.md editor
        // saves an edit and triggers reloadSoul on a session where
        // the user has already chatted at least once.
        //
        // Workaround: when the engine is past the post-load window
        // (already chatted, or a previous setSystemPrompt has fired),
        // we reset by re-mmap'ing the model. cleanUp() returns the
        // engine to Initialized, loadModel() returns it to ModelReady
        // with `_readyForSystemPrompt = true`, then setSystemPrompt
        // succeeds. This resets the KV cache (intentional — the new
        // soul should reframe future turns from a clean prefix), but
        // the visible message history in the UI is preserved by
        // ChatRepository, so the user's prior messages stay on screen.
        val canonical = loadedCanonicalPath
        if (engineHasModel() && canonical != null) {
            Log.i(TAG, "setSystemPrompt: rotating engine to allow new soul (hash=$hash)")
            runCatching { engine.cleanUp() }
                .onFailure { Log.w(TAG, "cleanUp during soul rotate failed", it) }
            engine.loadModel(canonical)
            // loadedCanonicalPath stays the same; only the soul changes.
            systemPromptHash = null
        }

        engine.setSystemPrompt(systemPrompt)
        systemPromptHash = hash
        Log.i(TAG, "setSystemPrompt applied (hash=$hash, ${systemPrompt.length} chars)")
    }

    override fun generate(userMessage: String, maxTokens: Int): Flow<String> = flow {
        // Delegate straight to the upstream Flow; the upstream impl
        // handles the dedicated single-thread dispatcher + ChatML
        // template auto-application internally.
        engine.sendUserPrompt(userMessage, predictLength = maxTokens)
            .collect { token -> emit(token) }
    }

    override suspend fun setSamplerParams(params: SamplerParams) {
        // Forward to the upstream impl. setSamplerParams there is
        // already pinned to the engine's single-thread dispatcher;
        // calling this from any caller thread is safe.
        engine.setSamplerParams(
            temperature = params.temperature,
            topP = params.topP,
            repeatPenalty = params.repeatPenalty,
            topK = params.topK,
        )
    }

    override fun close() {
        // cleanUp() unloads the model but keeps the engine instance.
        // destroy() also tears down the engine; we want the singleton to
        // be re-loadable, so cleanUp() is the right level.
        engine.cleanUp()
        loadedCanonicalPath = null
        systemPromptHash = null
    }

    /**
     * Look at the upstream engine's state and decide whether it has a
     * usable model in memory right now. ModelReady is the canonical
     * state but generation/system-prompt-processing also imply a model
     * is loaded.
     */
    private fun engineHasModel(): Boolean = when (engine.state.value) {
        is InferenceEngine.State.ModelReady,
        is InferenceEngine.State.ProcessingSystemPrompt,
        is InferenceEngine.State.ProcessingUserPrompt,
        is InferenceEngine.State.Generating,
        is InferenceEngine.State.Benchmarking,
        -> true
        else -> false
    }

    private fun canonicalPathOf(file: File): String =
        runCatching { file.canonicalPath }
            .getOrElse { file.absolutePath }

    private companion object {
        const val TAG = "LlamaCppContext"
    }
}
