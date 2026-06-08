package io.somi.rag.embed

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import android.content.Context

/**
 * v0.14.0 M2 — ONNX-Runtime wrapper for the Phase-3 embedder.
 *
 * Pipeline:
 *   text → WordPieceTokenizer → input_ids/attention_mask/token_type_ids
 *        → OrtSession.run() → token-level hidden states
 *        → mean-pool over attention mask → L2-normalize → FloatArray(384)
 *
 * **Threading.** Same constraint as the LLM dispatcher: ONNX Runtime
 * sessions are not thread-safe across concurrent run() calls. We pin
 * everything to a single-thread dispatcher carved out of Dispatchers.IO,
 * plus a Mutex for belt-and-suspenders. embed() suspend-safe; can be
 * called from any caller thread.
 *
 * **Lifecycle.** Lazy: the OrtSession opens on first embed() call,
 * which is when tokenizer.json + model.onnx are guaranteed to exist
 * (download worker completed). isAvailable() returns false beforehand
 * so callers (RagOrchestrator in M8) can degrade gracefully.
 *
 * Singleton: an OrtSession holds memory-mapped tensor weights (~470 MB
 * FP32). Multiple instances would multiply that footprint.
 */
@Singleton
class Embedder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: EmbeddingModelStorage,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    private val mutex = Mutex()

    /** Lazy session — opened on first embed call, never closed in production. */
    @Volatile private var session: OrtSession? = null

    /** Lazy tokenizer — paired 1:1 with session. */
    @Volatile private var tokenizer: WordPieceTokenizer? = null

    /** Cached env handle; ONNX Runtime singleton shared across sessions. */
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    /** True if the model is on disk and the session can be opened. */
    suspend fun isAvailable(): Boolean = withContext(dispatcher) {
        storage.isInstalled(EmbeddingModelCatalog.DEFAULT)
    }

    /**
     * Embed [text] to a 384-dim L2-normalized vector. Lazy-opens the
     * session on first call. Throws if the model isn't on disk or the
     * ONNX session fails to open.
     */
    suspend fun embed(text: String): FloatArray = mutex.withLock {
        withContext(dispatcher) {
            ensureSession()
            val tok = tokenizer!!
            val sess = session!!

            val ids = tok.encode(text)
            val seqLen = ids.size

            val inputIds = ids.map { it.toLong() }.toLongArray()
            val attentionMask = LongArray(seqLen) { 1L }
            val tokenTypeIds = LongArray(seqLen) // all zeros

            val shape = longArrayOf(1L, seqLen.toLong())

            val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
            val attnMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)
            val tokenTypeTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape)

            try {
                val inputs = mapOf(
                    "input_ids" to inputIdsTensor,
                    "attention_mask" to attnMaskTensor,
                    "token_type_ids" to tokenTypeTensor,
                )
                val outputs = sess.run(inputs)
                try {
                    // First output is the token-level hidden states:
                    // shape [1, seqLen, 384] (FP32). Mean-pool over
                    // mask, L2-normalize.
                    @Suppress("UNCHECKED_CAST")
                    val hidden = outputs[0].value as Array<Array<FloatArray>>
                    return@withContext meanPoolAndNormalize(hidden[0], attentionMask)
                } finally {
                    outputs.close()
                }
            } finally {
                inputIdsTensor.close()
                attnMaskTensor.close()
                tokenTypeTensor.close()
            }
        }
    }

    private fun ensureSession() {
        if (session != null && tokenizer != null) return
        val model = EmbeddingModelCatalog.DEFAULT
        val onnxFile = storage.mainOnnxFor(model)
            ?: error("embedding model not on disk: ${model.id}")
        val tokFile = storage.tokenizerJsonFor(model)
            ?: error("tokenizer.json not on disk: ${model.id}")

        val opts = OrtSession.SessionOptions().apply {
            // CPU-only, single-thread per dispatcher contract.
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        Log.i(TAG, "opening OrtSession from ${onnxFile.absolutePath}")
        val s = env.createSession(onnxFile.absolutePath, opts)
        Log.i(TAG, "loading tokenizer from ${tokFile.absolutePath}")
        val t = WordPieceTokenizer.load(tokFile)
        session = s
        tokenizer = t
    }

    /**
     * Mean-pool the token-level hidden states (shape [seqLen, 384])
     * weighted by the attention mask, then L2-normalize the resulting
     * 384-dim vector. This is the canonical sentence-transformers
     * pooling for paraphrase-multilingual-MiniLM-L12-v2.
     */
    private fun meanPoolAndNormalize(
        tokenStates: Array<FloatArray>,
        mask: LongArray,
    ): FloatArray {
        val dim = tokenStates[0].size
        val out = FloatArray(dim)
        var maskSum = 0
        for (i in tokenStates.indices) {
            val m = mask[i].toInt()
            if (m == 0) continue
            maskSum += 1
            val row = tokenStates[i]
            for (d in 0 until dim) out[d] += row[d]
        }
        if (maskSum > 0) {
            val inv = 1f / maskSum
            for (d in 0 until dim) out[d] *= inv
        }
        // L2 normalize.
        var norm = 0f
        for (v in out) norm += v * v
        norm = sqrt(norm)
        if (norm > 0f) {
            val inv = 1f / norm
            for (d in 0 until dim) out[d] *= inv
        }
        return out
    }

    private companion object {
        const val TAG = "Embedder"
    }
}
