package io.somi.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Singleton TTS wrapper using Android's built-in TextToSpeech engine.
 * No model download required.
 *
 * Voice defaults tuned for a So-Mi-like profile:
 *  pitch 0.85  — slightly lower than default 1.0, feminine-professional
 *  speechRate 0.95 — marginally slower for clearer articulation
 */
object TtsHelper {

    private const val TAG = "TtsHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var initialized = false
    private val pendingQueue = ArrayDeque<String>()

    private var currentPitch: Float = 0.85f
    private var currentSpeechRate: Float = 0.95f

    fun init(context: Context) {
        mainHandler.post {
            if (initialized || tts != null) return@post
            val appCtx = context.applicationContext
            val instance = TextToSpeech(appCtx) { status ->
                mainHandler.post {
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(Locale.GERMAN)
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "German TTS unavailable, falling back to English")
                            tts?.setLanguage(Locale.ENGLISH)
                        }
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
