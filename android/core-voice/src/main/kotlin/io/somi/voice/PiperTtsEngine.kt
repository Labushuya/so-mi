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
 * Available female German voices (all ~60MB, 16kHz):
 *   eva_k   — smooth, measured, slightly breathy
 *   kerstin — warmer, more conversational
 *   ramona  — clearer diction, more assertive
 *
 * VITS parameters tuned for So-Mi's character:
 *   noiseScale 0.85 — expressive delivery
 *   noiseScaleW 0.9 — natural pacing variation
 */
object PiperTtsEngine {

    private const val TAG = "PiperTtsEngine"

    enum class Voice(val filename: String, val displayName: String) {
        EVA_K ("de_DE-eva_k-x_low.onnx",   "Eva K — glatt"),
        KERSTIN("de_DE-kerstin-low.onnx",   "Kerstin — warm"),
        RAMONA ("de_DE-ramona-low.onnx",    "Ramona — klar"),
    }

    // Inline configs (sample_rate, espeak voice) for each model
    private val VOICE_CONFIGS = mapOf(
        Voice.EVA_K  to """{"audio":{"sample_rate":16000},"espeak":{"voice":"de"},"inference":{"noise_scale":0.667,"length_scale":1.0,"noise_w":0.8},"num_speakers":1,"phoneme_type":"espeak","quality":"x_low","language":{"code":"de_DE"}}""",
        Voice.KERSTIN to """{"audio":{"sample_rate":16000},"espeak":{"voice":"de"},"inference":{"noise_scale":0.667,"length_scale":1.0,"noise_w":0.8},"num_speakers":1,"phoneme_type":"espeak","quality":"low","language":{"code":"de_DE"}}""",
        Voice.RAMONA  to """{"audio":{"sample_rate":16000},"espeak":{"voice":"de"},"inference":{"noise_scale":0.667,"length_scale":1.0,"noise_w":0.8},"num_speakers":1,"phoneme_type":"espeak","quality":"low","language":{"code":"de_DE"}}""",
    )

    // Download URLs
    val VOICE_URLS = mapOf(
        Voice.EVA_K   to "https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/eva_k/x_low/de_DE-eva_k-x_low.onnx",
        Voice.KERSTIN to "https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/kerstin/low/de_DE-kerstin-low.onnx",
        Voice.RAMONA  to "https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/ramona/low/de_DE-ramona-low.onnx",
    )

    private const val NOISE_SCALE   = 0.85f
    private const val NOISE_SCALE_W = 0.9f

    private var tts: OfflineTts? = null
    private var activeVoice: Voice? = null

    fun modelDir(context: Context): File =
        File(context.getExternalFilesDir(null), "piper").also { it.mkdirs() }

    fun isVoiceInstalled(context: Context, voice: Voice): Boolean =
        File(modelDir(context), voice.filename).exists()

    fun installedVoices(context: Context): List<Voice> =
        Voice.entries.filter { isVoiceInstalled(context, it) }

    /** Returns the first installed voice, or null if none. */
    fun bestAvailableVoice(context: Context): Voice? =
        Voice.entries.firstOrNull { isVoiceInstalled(context, it) }

    fun isModelAvailable(context: Context): Boolean = bestAvailableVoice(context) != null

    fun availableModelQuality(context: Context): String =
        bestAvailableVoice(context)?.displayName ?: "none"

    fun inlineConfigFor(voice: Voice): String = VOICE_CONFIGS[voice] ?: "{}"

    suspend fun init(context: Context, preferredVoice: Voice? = null): Boolean {
        val targetVoice = preferredVoice ?: bestAvailableVoice(context) ?: run {
            Log.d(TAG, "no Piper model installed")
            return false
        }
        // Already loaded with the same voice
        if (tts != null && activeVoice == targetVoice) return true
        // Different voice requested — shut down first
        if (tts != null) shutdown()

        val modelFile = File(modelDir(context), targetVoice.filename)
        if (!modelFile.exists()) {
            Log.w(TAG, "${targetVoice.filename} requested but not on disk")
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
                        lengthScale = 1.0f,
                    ),
                    numThreads = 2,
                    debug      = false,
                    provider   = "cpu",
                ),
                maxNumSentences = 1,
            )
            tts = OfflineTts(config = config)
            activeVoice = targetVoice
            Log.i(TAG, "Piper ready — ${targetVoice.displayName} (${modelFile.name})")
            true
        }.onFailure {
            Log.e(TAG, "Piper init failed for ${targetVoice.filename}", it)
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
        activeVoice = null
        Log.d(TAG, "Piper shut down")
    }
}
