package io.somi.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * Piper TTS engine backed by sherpa-onnx OfflineTts.
 *
 * Models (priority: medium > x_low):
 *   de_DE-eva_k-medium.onnx  — ~64 MB, 22 kHz, better expressiveness (if present)
 *   de_DE-eva_k-x_low.onnx   — ~20 MB, 16 kHz, baseline quality
 *
 * VITS parameters tuned for So-Mi's character:
 *   noiseScale 0.8 (more expressive vs default 0.667)
 *   noiseScaleW 0.9 (more variation in pacing vs default 0.8)
 *
 * Thread safety: init() and synthesise() must be called on TtsHelper's piperDispatcher.
 */
object PiperTtsEngine {

    private const val TAG = "PiperTtsEngine"

    private const val MODEL_MEDIUM = "de_DE-eva_k-medium.onnx"
    private const val MODEL_X_LOW  = "de_DE-eva_k-x_low.onnx"

    // VITS params tuned for So-Mi: more expressive than sherpa-onnx defaults
    private const val NOISE_SCALE   = 0.8f
    private const val NOISE_SCALE_W = 0.9f
    private const val LENGTH_SCALE  = 1.0f

    private var tts: OfflineTts? = null

    fun modelDir(context: Context): File =
        File(context.getExternalFilesDir(null), "piper").also { it.mkdirs() }

    /**
     * "medium", "x_low", or "none" — which quality tier is installed.
     * Used by the UI to show the active model label.
     */
    fun availableModelQuality(context: Context): String {
        val dir = modelDir(context)
        return when {
            File(dir, MODEL_MEDIUM).exists() -> "medium"
            File(dir, MODEL_X_LOW).exists()  -> "x_low"
            else                             -> "none"
        }
    }

    fun isModelAvailable(context: Context): Boolean =
        availableModelQuality(context) != "none"

    private fun resolveModelFile(context: Context): File? {
        val dir = modelDir(context)
        File(dir, MODEL_MEDIUM).takeIf { it.exists() }?.let { return it }
        File(dir, MODEL_X_LOW).takeIf  { it.exists() }?.let { return it }
        return null
    }

    suspend fun init(context: Context): Boolean {
        if (tts != null) return true
        val modelFile = resolveModelFile(context) ?: run {
            Log.d(TAG, "no model in ${modelDir(context).absolutePath}")
            return false
        }
        return runCatching {
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model       = modelFile.absolutePath,
                        lexicon     = "",
                        tokens      = "",
                        dataDir     = "",
                        noiseScale  = NOISE_SCALE,
                        noiseScaleW = NOISE_SCALE_W,
                        lengthScale = LENGTH_SCALE,
                    ),
                    numThreads = 2,
                    debug      = false,
                    provider   = "cpu",
                ),
                maxNumSentences = 1,
            )
            tts = OfflineTts(config = config)
            Log.i(TAG, "Piper ready — ${modelFile.name}")
            true
        }.onFailure {
            Log.e(TAG, "Piper init failed", it)
        }.getOrDefault(false)
    }

    suspend fun synthesise(text: String, speed: Float = 1.0f): FloatArray? {
        val engine = tts ?: return null
        return runCatching {
            engine.generate(text = text, sid = 0, speed = speed).samples
        }.onFailure {
            Log.e(TAG, "synthesis failed for '${text.take(30)}'", it)
        }.getOrNull()
    }

    val sampleRate: Int get() = tts?.sampleRate() ?: 16000

    fun shutdown() {
        tts?.release()
        tts = null
        Log.d(TAG, "Piper shut down")
    }
}
