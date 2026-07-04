package io.somi.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
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
 * CRASH FIX (v0.59.1):
 * AudioTrack.MODE_STATIC + WRITE_BLOCKING blocks the piperDispatcher thread.
 * A concurrent speak() → stopActiveTrack() causes Use-After-Free on the native object.
 * Fix: AudioTrack.MODE_STREAM with non-blocking chunked writes.
 *
 * Re-Init race: piperReady and isReinitialising are updated atomically (single
 * coroutine block, no suspension point between them).
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

    // Guarded by piperDispatcher — only one play/stop at a time.
    private var activeTrack: AudioTrack? = null
    private var stopRequested = false

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

    fun initPiper(context: Context, preferredVoice: PiperTtsEngine.Voice? = null) {
        // Block if reinit is running — two concurrent init jobs on piperDispatcher
        // both write PiperTtsEngine.tts, causing Use-After-Free.
        if (isReinitialising || piperReady || piperInitJob?.isActive == true) return
        piperInitJob = piperScope.launch {
            delay(500)
            val ok = PiperTtsEngine.init(context.applicationContext, preferredVoice)
            piperReady = ok
            if (ok) Log.i(TAG, "Piper ready (${PiperTtsEngine.availableModelQuality(context)})")
            else Log.w(TAG, "Piper init failed — Android TTS fallback")
        }
    }

    fun reinitPiper(context: Context, preferredVoice: PiperTtsEngine.Voice? = null) {
        piperInitJob?.cancel()
        piperInitJob = null
        piperReady = false
        isReinitialising = true
        piperScope.launch {
            stopActiveTrackInternal()
            runCatching { PiperTtsEngine.shutdown() }
            delay(300)
            val ok = runCatching { PiperTtsEngine.init(context.applicationContext, preferredVoice) }.getOrElse { e ->
                Log.e(TAG, "Piper re-init threw", e)
                false
            }
            isReinitialising = false
            piperReady = ok
            Log.i(TAG, "Piper re-init complete: ok=$ok")
        }
    }

    fun setSpeechRate(rate: Float) { currentRate = rate.coerceIn(0.25f, 4.0f) }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (isReinitialising) return
        if (piperReady) speakWithPiper(text) else speakWithAndroid(text)
    }

    private fun speakWithPiper(text: String) {
        piperScope.launch {
            stopActiveTrackInternal()
            stopRequested = false
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
            playPcmStream(samples, PiperTtsEngine.sampleRate)
        }
    }

    /**
     * Plays PCM samples via AudioTrack in STREAM mode.
     * STREAM mode writes in small chunks — the thread is not blocked for the full
     * duration, so a concurrent stopActiveTrackInternal() call is safe.
     */
    private fun playPcmStream(samples: FloatArray, sampleRate: Int) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        val chunkSize = minBuf.coerceAtLeast(4096)

        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            chunkSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )

        activeTrack = track
        track.play()

        var offset = 0
        while (offset < samples.size && !stopRequested) {
            val end = (offset + chunkSize).coerceAtMost(samples.size)
            track.write(samples, offset, end - offset, AudioTrack.WRITE_NON_BLOCKING)
            offset = end
        }
        // Drain remaining samples then release.
        track.stop()
        track.release()
        if (activeTrack === track) activeTrack = null
    }

    private fun stopActiveTrackInternal() {
        stopRequested = true
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
        piperScope.launch { stopActiveTrackInternal() }
        mainHandler.post { pendingQueue.clear(); androidTts?.stop() }
    }

    fun isSpeaking(): Boolean =
        activeTrack?.playState == AudioTrack.PLAYSTATE_PLAYING || androidTts?.isSpeaking == true

    fun shutdown() {
        piperScope.launch {
            stopActiveTrackInternal()
            PiperTtsEngine.shutdown()
            piperReady = false
            isReinitialising = false
        }
        mainHandler.post {
            pendingQueue.clear()
            androidTts?.shutdown(); androidTts = null
            androidTtsReady = false
        }
        Log.d(TAG, "TTS shut down")
    }
}
