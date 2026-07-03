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

/**
 * Thin wrapper around Android SpeechRecognizer for voice input in the Composer.
 *
 * Must be called from the Main thread — SpeechRecognizer requires a Looper.
 * Returns the highest-confidence result, or null on any error / cancellation.
 */
object VoiceInputHelper {

    private const val TAG = "VoiceInputHelper"

    fun isAvailable(context: Context): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun listen(context: Context): String? =
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val best = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    recognizer.destroy()
                    if (cont.isActive) cont.resume(best)
                }

                override fun onError(error: Int) {
                    recognizer.destroy()
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

                override fun onReadyForSpeech(params: Bundle?) = Unit
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
            }

            recognizer.startListening(intent)
        }
}
