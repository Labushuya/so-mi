package io.somi.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin wrapper around Android SpeechRecognizer for voice input in the Composer.
 *
 * Must always be called from the Main thread — SpeechRecognizer requires a Looper.
 *
 * Pre-warming: call [warmUp] once when the Composer enters composition (LaunchedEffect)
 * and [tearDown] when it leaves (DisposableEffect). This hides the IPC bindService
 * latency (150–400 ms on MagicOS) so the first tap feels instant.
 *
 * [listening] prevents stacking multiple recognizer instances from rapid taps.
 */
object VoiceInputHelper {

    private const val TAG = "VoiceInputHelper"
    private val listening = AtomicBoolean(false)

    // Pre-warmed recognizer — consumed on first listen(), replaced via warmUp().
    // Access only from Main thread (SpeechRecognizer requirement).
    private var warmedRecognizer: SpeechRecognizer? = null

    fun isAvailable(context: Context): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Call once when the Composer enters composition to pre-bind the RecognitionService.
     * Main thread only.
     */
    fun warmUp(context: Context) {
        if (warmedRecognizer != null) return
        if (!isAvailable(context)) return
        warmedRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        Log.d(TAG, "pre-warmed SpeechRecognizer")
    }

    /**
     * Call when the Composer leaves composition (DisposableEffect.onDispose).
     * Main thread only.
     */
    fun tearDown() {
        warmedRecognizer?.destroy()
        warmedRecognizer = null
        Log.d(TAG, "torn down SpeechRecognizer")
    }

    suspend fun listen(context: Context, onReady: (() -> Unit)? = null): String? {
        if (!listening.compareAndSet(false, true)) {
            Log.d(TAG, "already listening, ignoring tap")
            return null
        }
        return try {
            listenInternal(context, onReady)
        } finally {
            listening.set(false)
        }
    }

    private suspend fun listenInternal(context: Context, onReady: (() -> Unit)?): String? =
        suspendCancellableCoroutine { cont ->
            // Use pre-warmed instance to skip IPC bind latency; consume it so
            // it is not accidentally reused. warmUp() can be called again after.
            val recognizer = warmedRecognizer?.also { warmedRecognizer = null }
                ?: SpeechRecognizer.createSpeechRecognizer(context)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val best = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    recognizer.destroy()
                    // Pre-warm the next session immediately after this one ends.
                    warmUp(context)
                    if (cont.isActive) cont.resume(best)
                }

                override fun onError(error: Int) {
                    recognizer.destroy()
                    warmUp(context)
                    if (!cont.isActive) return
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_CLIENT ->
                            cont.resume(null)
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            Log.d(TAG, "recognizer busy (error=$error)")
                            cont.resume(null)
                        }
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                            Log.w(TAG, "no network for speech recognition (error=$error)")
                            cont.resume(null)
                        }
                        else -> {
                            Log.w(TAG, "speech recognition failed (error=$error)")
                            cont.resume(null)
                        }
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) { onReady?.invoke() }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            cont.invokeOnCancellation {
                recognizer.cancel()
                recognizer.destroy()
                warmUp(context)
            }

            recognizer.startListening(intent)
        }
}
