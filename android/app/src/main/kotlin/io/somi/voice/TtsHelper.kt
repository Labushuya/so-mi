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
 * Thread safety:
 *  - All TTS API calls must happen on the Main thread (TextToSpeech requirement).
 *  - init() posts to the Main Looper to guarantee this.
 *  - pendingQueue holds texts that arrive before initialization completes.
 */
object TtsHelper {

    private const val TAG = "TtsHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var initialized = false
    private val pendingQueue = ArrayDeque<String>()

    /** Call once on app start (any thread). Safe to call multiple times. */
    fun init(context: Context) {
        mainHandler.post {
            if (initialized || tts != null) return@post
            val appCtx = context.applicationContext
            // Assign to local first, then to field — avoids race where the
            // OnInitListener fires before `tts =` has run.
            val instance = TextToSpeech(appCtx) { status ->
                mainHandler.post {
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(Locale.GERMAN)
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "German TTS unavailable, falling back to English")
                            tts?.setLanguage(Locale.ENGLISH)
                        }
                        initialized = true
                        Log.d(TAG, "TTS initialized, draining ${pendingQueue.size} queued texts")
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
     * Speak [text]. Uses QUEUE_ADD so streaming chunks don't interrupt each other.
     * If TTS is not yet initialized, the text is queued and spoken once ready.
     * Call on any thread.
     */
    fun speak(text: String) {
        mainHandler.post {
            if (!initialized) {
                pendingQueue.addLast(text)
                return@post
            }
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    /** Stop current playback and clear the TTS queue. Call on any thread. */
    fun stop() {
        mainHandler.post {
            pendingQueue.clear()
            tts?.stop()
        }
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking ?: false

    /** Call when the app is closing to release the TTS service binding. */
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
