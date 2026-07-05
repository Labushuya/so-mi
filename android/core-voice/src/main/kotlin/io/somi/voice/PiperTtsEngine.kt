package io.somi.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Piper TTS engine backed by sherpa-onnx OfflineTts.
 *
 * CRASH FIX (v0.59.5):
 * tts is held in an AtomicReference. getAndSet(null) in shutdown() atomically
 * takes the reference — if synthesise() wins the race it gets a valid object,
 * if shutdown() wins first synthesise() gets null and returns early.
 * This eliminates the null-pointer dereference in generateImpl when shutdown()
 * releases the native object while generate() is still running.
 */
object PiperTtsEngine {

    private const val TAG = "PiperTtsEngine"

    enum class Voice(val filename: String, val displayName: String) {
        EVA_K  ("de_DE-eva_k-x_low.onnx", "Eva K — glatt"),
        KERSTIN("de_DE-kerstin-low.onnx",  "Kerstin — warm"),
        RAMONA ("de_DE-ramona-low.onnx",   "Ramona — klar"),
    }

    private val VOICE_CONFIGS = mapOf(
        Voice.EVA_K   to """{"audio":{"sample_rate":16000},"espeak":{"voice":"de"},"inference":{"noise_scale":0.667,"length_scale":1.0,"noise_w":0.8},"num_speakers":1,"phoneme_type":"espeak","quality":"x_low","language":{"code":"de_DE"}}""",
        Voice.KERSTIN to """{"audio":{"sample_rate":16000},"espeak":{"voice":"de"},"inference":{"noise_scale":0.667,"length_scale":1.0,"noise_w":0.8},"num_speakers":1,"phoneme_type":"espeak","quality":"low","language":{"code":"de_DE"}}""",
        Voice.RAMONA  to """{"audio":{"sample_rate":16000},"espeak":{"voice":"de"},"inference":{"noise_scale":0.667,"length_scale":1.0,"noise_w":0.8},"num_speakers":1,"phoneme_type":"espeak","quality":"low","language":{"code":"de_DE"}}""",
    )

    val VOICE_URLS = mapOf(
        Voice.EVA_K   to "https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/eva_k/x_low/de_DE-eva_k-x_low.onnx",
        Voice.KERSTIN to "https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/kerstin/low/de_DE-kerstin-low.onnx",
        Voice.RAMONA  to "https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/ramona/low/de_DE-ramona-low.onnx",
    )

    private const val NOISE_SCALE   = 0.85f
    private const val NOISE_SCALE_W = 0.9f

    // AtomicReference ensures that getAndSet(null) in shutdown() and get() in
    // synthesise() cannot see a torn state — one of them wins cleanly.
    private val ttsRef = AtomicReference<OfflineTts?>(null)
    private var activeVoice: Voice? = null

    fun modelDir(context: Context): File =
        File(context.getExternalFilesDir(null), "piper").also { it.mkdirs() }

    fun isVoiceInstalled(context: Context, voice: Voice): Boolean =
        File(modelDir(context), voice.filename).exists()

    fun installedVoices(context: Context): List<Voice> =
        Voice.entries.filter { isVoiceInstalled(context, it) }

    fun bestAvailableVoice(context: Context): Voice? =
        Voice.entries.firstOrNull { isVoiceInstalled(context, it) }

    fun isModelAvailable(context: Context): Boolean = bestAvailableVoice(context) != null

    fun availableModelQuality(context: Context): String =
        bestAvailableVoice(context)?.displayName ?: "none"

    fun inlineConfigFor(voice: Voice): String = VOICE_CONFIGS[voice] ?: "{}"

    private fun resolveModelFile(context: Context, preferredVoice: Voice?): File? {
        val dir = modelDir(context)
        val candidates = if (preferredVoice != null)
            listOf(preferredVoice) + Voice.entries.filter { it != preferredVoice }
        else Voice.entries.toList()
        return candidates.map { File(dir, it.filename) }.firstOrNull { it.exists() }
    }

    suspend fun init(context: Context, preferredVoice: Voice? = null): Boolean {
        // Already initialised with compatible voice
        if (ttsRef.get() != null && (preferredVoice == null || activeVoice == preferredVoice)) return true
        // Different voice — shut down first
        if (ttsRef.get() != null) shutdown()

        val modelFile = resolveModelFile(context, preferredVoice) ?: run {
            Log.d(TAG, "no Piper model installed")
            return false
        }
        val voice = Voice.entries.firstOrNull { modelFile.name == it.filename }
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
            ttsRef.set(OfflineTts(config = config))
            activeVoice = voice
            Log.i(TAG, "Piper ready — ${modelFile.name}")
            true
        }.onFailure {
            Log.e(TAG, "Piper init failed for ${modelFile.name}", it)
        }.getOrDefault(false)
    }

    suspend fun synthesise(text: String, speed: Float = 1.0f): FloatArray? {
        // Atomic get — if shutdown() has already called getAndSet(null) we get null here.
        val engine = ttsRef.get() ?: return null
        return runCatching {
            engine.generate(text = text, sid = 0, speed = speed).samples
        }.onFailure {
            Log.e(TAG, "synthesis failed for '${text.take(30)}'", it)
        }.getOrNull()
    }

    val sampleRate: Int get() = ttsRef.get()?.sampleRate() ?: 16000

    fun shutdown() {
        // getAndSet(null) atomically takes ownership — any concurrent synthesise()
        // call will get null from ttsRef.get() and return early rather than
        // calling generate() on a released native object.
        val old = ttsRef.getAndSet(null)
        old?.release()
        activeVoice = null
        Log.d(TAG, "Piper shut down")
    }
}
