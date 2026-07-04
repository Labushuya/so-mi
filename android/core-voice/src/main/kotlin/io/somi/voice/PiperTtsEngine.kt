package io.somi.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Piper TTS engine backed by sherpa-onnx OfflineTts.
 *
 * Model: de_DE-eva_k-x_low (20 MB, 16 kHz, VITS architecture).
 * The model file is downloaded on-demand to getExternalFilesDir()/piper/
 * and is NOT bundled in the APK.
 *
 * Thread safety: init() and generate() must run on Dispatchers.IO.
 * Audio playback scheduling is handled by the caller (TtsHelper).
 */
object PiperTtsEngine {

    private const val TAG = "PiperTtsEngine"
    private const val MODEL_FILENAME = "de_DE-eva_k-x_low.onnx"
    private const val CONFIG_FILENAME = "de_DE-eva_k-x_low.onnx.json"

    private var tts: OfflineTts? = null

    /** Returns the directory where the Piper model should be stored. */
    fun modelDir(context: Context): File =
        File(context.getExternalFilesDir(null), "piper").also { it.mkdirs() }

    /** Returns true if the model file is present on disk. */
    fun isModelAvailable(context: Context): Boolean =
        File(modelDir(context), MODEL_FILENAME).exists()

    /**
     * Initialises the Piper engine. Must be called on a background thread.
     * No-op if already initialised or if the model is not downloaded yet.
     * Returns true on success.
     */
    suspend fun init(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (tts != null) return@withContext true
        val dir = modelDir(context)
        val modelFile = File(dir, MODEL_FILENAME)
        if (!modelFile.exists()) {
            Log.d(TAG, "model not found at ${modelFile.absolutePath} — Piper unavailable")
            return@withContext false
        }
        runCatching {
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelFile.absolutePath,
                        lexicon = "",
                        tokens = "",
                        dataDir = "",
                        noiseScale = 0.667f,
                        noiseScaleW = 0.8f,
                        lengthScale = 1.0f,
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            )
            tts = OfflineTts(config = config)
            Log.i(TAG, "Piper TTS engine ready (${modelFile.name})")
            true
        }.onFailure {
            Log.e(TAG, "Piper TTS init failed", it)
        }.getOrDefault(false)
    }

    /**
     * Synthesises [text] and returns raw PCM samples (Float32, mono 16kHz).
     * Returns null on error or if not initialised.
     * Must be called on a background thread.
     */
    suspend fun synthesise(text: String, speed: Float = 1.0f): FloatArray? = withContext(Dispatchers.IO) {
        val engine = tts ?: return@withContext null
        runCatching {
            val audio = engine.generate(text = text, sid = 0, speed = speed)
            audio.samples
        }.onFailure {
            Log.e(TAG, "Piper synthesis failed for '${text.take(30)}'", it)
        }.getOrNull()
    }

    /** Sample rate of the generated audio (16000 Hz for eva_k x_low). */
    val sampleRate: Int get() = tts?.sampleRate() ?: 16000

    fun shutdown() {
        tts?.release()
        tts = null
        Log.d(TAG, "Piper TTS engine shut down")
    }
}
