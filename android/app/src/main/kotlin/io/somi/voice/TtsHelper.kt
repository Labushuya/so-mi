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
 * All Piper operations run on piperDispatcher (limitedParallelism(1)) to isolate
 * the sherpa-onnx native thread-pool from llama.cpp's OpenMP threads.
 *
 * RE-INIT RACE FIX (v0.58.6):
 * isReinitialising=true blocks speak() for the ~2-4s window during reinitPiper().
 * No native object is touched while shutdown/init is in progress.
 */
object TtsHelper {

    private const val TAG = "TtsHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val piperDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val piperScope = CoroutineScope(SupervisorJob() + piperDispatcher)

    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false
    private val pendingQueue = ArrayDeque<String>()

    @Volatile private var piperReady = false
    @Volatile private var isReinitialising = false
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
            delay(500) // let llama.cpp finish startup first
            val ok = PiperTtsEngine.init(context.applicationContext)
            piperReady = ok
            if (ok) Log.i(TAG, "Piper ready (${PiperTtsEngine.availableModelQuality(context)})")
            else Log.w(TAG, "Piper init failed — Android TTS fallback")
        }
    }

    /**
     * Restarts the Piper engine without re-downloading the model (~2-4s).
     * Speak calls are silently dropped during the restart window.
     */
    fun reinitPiper(context: Context) {
        piperInitJob?.cancel()
        piperInitJob = null
        piperReady = false
        isReinitialising = true
        piperScope.launch {
            runCatching { PiperTtsEngine.shutdown() }
            delay(200)
            val ok = runCatching { PiperTtsEngine.init(context.applicationContext) }.getOrElse { e ->
                Log.e(TAG, "Piper re-init threw", e)
                false
            }
            piperReady = ok
            isReinitialising = false
            Log.i(TAG, "Piper re-init complete: ok=$ok")
        }
    }

    fun setSpeechRate(rate: Float) { currentRate = rate.coerceIn(0.25f, 4.0f) }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (isReinitialising) return  // engine in undefined state — drop silently
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
        piperScope.launch { stopActiveTrack(); PiperTtsEngine.shutdown(); piperReady = false; isReinitialising = false }
        mainHandler.post {
            pendingQueue.clear()
            androidTts?.shutdown(); androidTts = null
            androidTtsReady = false
        }
        Log.d(TAG, "TTS shut down")
    }
}
