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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Unified TTS interface. Uses Piper TTS (natural offline voice) when the
 * model is available, falls back to Android system TTS otherwise.
 *
 * Model download: the Piper model (de_DE-eva_k-x_low.onnx, ~20 MB) is
 * downloaded on first tap of "Stimme herunterladen" in Settings → Sprachausgabe.
 * The engine initialises automatically after download completes.
 */
object TtsHelper {

    private const val TAG = "TtsHelper"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Android TTS fallback
    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false
    private val pendingQueue = ArrayDeque<String>()

    // Piper engine state
    private var piperReady = false
    private var piperInitJob: Job? = null
    private var activeTrack: AudioTrack? = null

    /** True if Piper model is on disk and engine is initialised. */
    val isPiperReady: Boolean get() = piperReady

    /**
     * Call once when entering the chat screen (any thread).
     * Initialises Android TTS immediately; attempts Piper init if model exists.
     */
    fun init(context: Context) {
        initAndroidTts(context)
        if (PiperTtsEngine.isModelAvailable(context)) {
            initPiper(context)
        }
    }

    private fun initAndroidTts(context: Context) {
        mainHandler.post {
            if (androidTts != null) return@post
            val appCtx = context.applicationContext
            val instance = TextToSpeech(appCtx) { status ->
                mainHandler.post {
                    if (status == TextToSpeech.SUCCESS) {
                        val r = androidTts?.setLanguage(Locale("de", "DE"))
                        if (r == TextToSpeech.LANG_MISSING_DATA ||
                            r == TextToSpeech.LANG_NOT_SUPPORTED) {
                            androidTts?.setLanguage(Locale.ENGLISH)
                        }
                        androidTtsReady = true
                        while (pendingQueue.isNotEmpty()) {
                            androidTts?.speak(pendingQueue.removeFirst(), TextToSpeech.QUEUE_ADD, null, null)
                        }
                        Log.d(TAG, "Android TTS ready")
                    }
                }
            }
            androidTts = instance
        }
    }

    /**
     * Initialise Piper engine asynchronously. Safe to call multiple times.
     * Called automatically from [init] if model exists, or after download completes.
     */
    fun initPiper(context: Context) {
        if (piperReady || piperInitJob?.isActive == true) return
        piperInitJob = scope.launch {
            val ok = PiperTtsEngine.init(context.applicationContext)
            piperReady = ok
            if (ok) Log.i(TAG, "Piper TTS ready — using natural voice")
            else Log.w(TAG, "Piper TTS init failed — using Android TTS fallback")
        }
    }

    /**
     * Speak [text]. Uses Piper if ready, Android TTS otherwise.
     * Any running speech is stopped first.
     * Call on any thread.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        if (piperReady) {
            speakWithPiper(text)
        } else {
            speakWithAndroid(text)
        }
    }

    private fun speakWithPiper(text: String) {
        scope.launch {
            stopActiveTrack()
            val samples = PiperTtsEngine.synthesise(text) ?: run {
                Log.w(TAG, "Piper synthesis returned null, falling back to Android TTS")
                speakWithAndroid(text)
                return@launch
            }
            playPcm(samples, PiperTtsEngine.sampleRate)
        }
    }

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(samples.size * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        activeTrack = track
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        Log.d(TAG, "Piper playing ${samples.size} samples at ${sampleRate}Hz")
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

    /** Stop any active speech immediately. */
    fun stop() {
        scope.launch { stopActiveTrack() }
        mainHandler.post {
            pendingQueue.clear()
            androidTts?.stop()
        }
    }

    fun isSpeaking(): Boolean =
        activeTrack?.playState == AudioTrack.PLAYSTATE_PLAYING ||
        androidTts?.isSpeaking == true

    /** Release all resources. */
    fun shutdown() {
        scope.launch { stopActiveTrack(); PiperTtsEngine.shutdown() }
        mainHandler.post {
            pendingQueue.clear()
            androidTts?.shutdown()
            androidTts = null
            androidTtsReady = false
            piperReady = false
        }
        Log.d(TAG, "TTS shut down")
    }
}
