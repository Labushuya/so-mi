package io.somi.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Unified TTS interface — Piper (natural voice) when available, Android TTS fallback.
 *
 * CRASH FIX (v0.58.5):
 * Piper (sherpa-onnx) initialises a native ONNX thread-pool on first use.
 * Running it on Dispatchers.Default or Dispatchers.IO while llama.cpp is active
 * causes native thread-pool contention → SIGABRT.
 *
 * Fix: dedicated single-thread dispatcher (limitedParallelism(1)) for ALL Piper
 * operations — init, synthesise, and AudioTrack playback. This is the same
 * isolation pattern used for llama.cpp (llamaDispatcher), guaranteeing that
 * the two C++ engines never share a thread.
 *
 * Additionally: speak() is a no-op when piperReady is false (engine still
 * initialising) to prevent calling synthesise() before OfflineTts is ready.
 */
object TtsHelper {

    private const val TAG = "TtsHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    // Dedicated single-thread dispatcher — Piper C++ threads stay isolated from LLM.
    private val piperDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val piperScope = CoroutineScope(SupervisorJob() + piperDispatcher)

    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false
    private val pendingQueue = ArrayDeque<String>()

    @Volatile private var piperReady = false
    private var piperInitJob: Job? = null
    private var activeTrack: AudioTrack? = null

    @Volatile private var currentRate: Float = 1.0f

    val isPiperReady: Boolean get() = piperReady

    fun init(context: Context) {
        initAndroidTts(context)
        if (PiperTtsEngine.isModelAvailable(context)) initPiper(context)
    }

    private fun initAndroidTts(context: Context) {
        mainHandler.post {
            if (androidTts != null) return@post
            val instance = TextToSpeech(context.applicationContext) { status ->
                mainHandler.post {
                    if (status == TextToSpeech.SUCCESS) {
                        val r = androidTts?.setLanguage(Locale("de", "DE"))
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                            androidTts?.setLanguage(Locale.ENGLISH)
                        androidTtsReady = true
                        while (pendingQueue.isNotEmpty())
                            androidTts?.speak(pendingQueue.removeFirst(), TextToSpeech.QUEUE_ADD, null, null)
                        Log.d(TAG, "Android TTS ready")
                    }
                }
            }
            androidTts = instance
        }
    }

    fun initPiper(context: Context) {
        if (piperReady || piperInitJob?.isActive == true) return
        piperInitJob = piperScope.launch {
            // Small delay on first init to let llama.cpp finish its own startup.
            // Avoids native thread-pool collision during cold-start when greeting fires.
            delay(500)
            val ok = PiperTtsEngine.init(context.applicationContext)
            piperReady = ok
            if (ok) Log.i(TAG, "Piper ready on dedicated dispatcher")
            else Log.w(TAG, "Piper init failed — Android TTS fallback")
        }
    }

    /**
     * Tear down and re-initialise the Piper engine without re-downloading the model.
     * Useful after an in-app model update or when the engine is in a bad state.
     */
    fun reinitPiper(context: Context) {
        piperInitJob?.cancel()
        piperInitJob = null
        piperReady = false
        piperScope.launch {
            PiperTtsEngine.shutdown()
            delay(200)
            val ok = PiperTtsEngine.init(context.applicationContext)
            piperReady = ok
            Log.i(TAG, "Piper re-init complete: ok=$ok")
        }
    }

    fun setSpeechRate(rate: Float) {
        currentRate = rate.coerceIn(0.25f, 4.0f)
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        // Guard: do not attempt Piper synthesis until engine is fully ready.
        // Calling synthesise() before OfflineTts is initialised crashes the process.
        if (piperReady) speakWithPiper(text) else speakWithAndroid(text)
    }

    private fun speakWithPiper(text: String) {
        piperScope.launch {
            stopActiveTrack()
            val samples = runCatching {
                PiperTtsEngine.synthesise(text, speed = currentRate)
            }.getOrElse { e ->
                Log.e(TAG, "Piper threw — degrading to Android TTS", e)
                piperReady = false
                speakWithAndroid(text)
                return@launch
            } ?: run {
                Log.w(TAG, "Piper returned null — degrading to Android TTS")
                piperReady = false
                speakWithAndroid(text)
                return@launch
            }
            playPcm(samples, PiperTtsEngine.sampleRate)
        }
    }

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(samples.size * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        activeTrack = track
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()
    }

    private fun stopActiveTrack() {
        val t = activeTrack ?: return
        runCatching { t.stop(); t.release() }
        activeTrack = null
    }

    private fun speakWithAndroid(text: String) {
        mainHandler.post {
            if (!androidTtsReady) { pendingQueue.addLast(text); return@post }
            androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun stop() {
        piperScope.launch { stopActiveTrack() }
        mainHandler.post { pendingQueue.clear(); androidTts?.stop() }
    }

    fun isSpeaking(): Boolean =
        activeTrack?.playState == AudioTrack.PLAYSTATE_PLAYING || androidTts?.isSpeaking == true

    fun shutdown() {
        piperScope.launch { stopActiveTrack(); PiperTtsEngine.shutdown(); piperReady = false }
        mainHandler.post {
            pendingQueue.clear()
            androidTts?.shutdown(); androidTts = null
            androidTtsReady = false
        }
        Log.d(TAG, "TTS shut down")
    }
}
