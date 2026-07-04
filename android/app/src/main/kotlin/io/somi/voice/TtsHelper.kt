package io.somi.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Singleton TTS wrapper using Android's built-in TextToSpeech engine.
 * No model download required.
 *
 * Voice selection strategy (in order of preference):
 *  1. Neural/enhanced German female voice (Google TTS premium, if installed)
 *  2. Any German female voice
 *  3. Any German voice (gender-neutral)
 *  4. System default
 *
 * Voice defaults tuned for a So-Mi-like profile:
 *  pitch 0.85  — slightly lower than default 1.0, feminine-professional
 *  speechRate 0.90 — slightly slower for natural cadence
 */
object TtsHelper {

    private const val TAG = "TtsHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var initialized = false
    private val pendingQueue = ArrayDeque<String>()

    private var currentPitch: Float = 0.85f
    private var currentSpeechRate: Float = 0.90f

    fun init(context: Context) {
        mainHandler.post {
            if (initialized || tts != null) return@post
            val appCtx = context.applicationContext
            val instance = TextToSpeech(appCtx) { status ->
                mainHandler.post {
                    if (status == TextToSpeech.SUCCESS) {
                        selectBestVoice()
                        tts?.setPitch(currentPitch)
                        tts?.setSpeechRate(currentSpeechRate)
                        initialized = true
                        Log.d(TAG, "TTS initialized (pitch=$currentPitch rate=$currentSpeechRate), draining ${pendingQueue.size} queued")
                        while (pendingQueue.isNotEmpty()) {
                            tts?.speak(pendingQueue.removeFirst(), TextToSpeech.QUEUE_ADD, null, null)
                        }
                    } else {
                        Log.e(TAG, "TTS init failed status=$status")
                        tts = null
                    }
                }
            }
            tts = instance
        }
    }

    /**
     * Selects the best available German voice in this priority order:
     *  1. Neural/network quality German female (Google TTS premium)
     *  2. Normal quality German female
     *  3. Any German voice
     *  4. No change (system default)
     *
     * "Neural" voices on Android are identified by quality QUALITY_VERY_HIGH
     * or QUALITY_HIGH and names containing "enhanced", "premium", "neural",
     * or "wavenet" (Google's naming convention).
     */
    private fun selectBestVoice() {
        val engine = tts ?: return
        val german = Locale("de", "DE")

        val voices = runCatching { engine.voices }
            .getOrNull()
            ?.filter { v ->
                v.locale.language == "de" && !v.isNetworkConnectionRequired
            }
            ?: run {
                // Fallback: just set the language, no voice selection
                val result = engine.setLanguage(german)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "German TTS unavailable, falling back to English")
                    engine.setLanguage(Locale.ENGLISH)
                }
                return
            }

        if (voices.isEmpty()) {
            engine.setLanguage(german)
            return
        }

        Log.d(TAG, "Available German voices: ${voices.map { it.name }}")

        // Score each voice: higher = better
        fun scoreVoice(v: Voice): Int {
            var score = 0
            // Quality score (QUALITY_VERY_HIGH = 400, HIGH = 300, NORMAL = 200, LOW = 100)
            score += v.quality
            // Prefer female voices for So-Mi
            if (v.name.contains("female", ignoreCase = true) ||
                v.name.contains("feminin", ignoreCase = true)) score += 500
            // Prefer neural/premium/enhanced voices
            val nameLower = v.name.lowercase()
            if (nameLower.contains("neural") || nameLower.contains("wavenet") ||
                nameLower.contains("premium") || nameLower.contains("enhanced") ||
                nameLower.contains("studio")) score += 1000
            return score
        }

        val best = voices.maxByOrNull { scoreVoice(it) }
        if (best != null) {
            val result = engine.setVoice(best)
            if (result == TextToSpeech.SUCCESS) {
                Log.i(TAG, "Selected voice: ${best.name} (quality=${best.quality})")
            } else {
                Log.w(TAG, "setVoice failed for ${best.name}, falling back to setLanguage")
                engine.setLanguage(german)
            }
        } else {
            engine.setLanguage(german)
        }
    }

    fun speak(text: String) {
        mainHandler.post {
            if (!initialized) { pendingQueue.addLast(text); return@post }
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    fun stop() {
        mainHandler.post {
            pendingQueue.clear()
            tts?.speak("", TextToSpeech.QUEUE_FLUSH, null, null)
            tts?.stop()
        }
    }

    fun applyVoiceSettings(pitch: Float, speechRate: Float) {
        mainHandler.post {
            currentPitch = pitch
            currentSpeechRate = speechRate
            tts?.setPitch(pitch)
            tts?.setSpeechRate(speechRate)
        }
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking ?: false

    fun shutdown() {
        mainHandler.post {
            pendingQueue.clear()
            tts?.shutdown()
            tts = null
            initialized = false
            Log.d(TAG, "TTS shut down")
        }
    }
}
