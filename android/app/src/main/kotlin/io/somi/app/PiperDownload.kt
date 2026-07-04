package io.somi.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import io.somi.voice.PiperTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

private const val TAG = "PiperDownload"

sealed interface PiperDownloadState {
    data class Progress(val percent: Int) : PiperDownloadState
    data object Done : PiperDownloadState
    data object Failed : PiperDownloadState
}

private const val MODEL_URL =
    "https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/eva_k/x_low/de_DE-eva_k-x_low.onnx"
private const val CONFIG_URL =
    "https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/eva_k/x_low/de_DE-eva_k-x_low.onnx.json"

/**
 * Downloads the Piper TTS model to getExternalFilesDir()/piper/.
 * Emits progress (0–100) then Done or Failed.
 * Uses the same Channel-Bridge pattern as UpdateChecker.
 */
fun downloadPiperModel(context: Context): Flow<PiperDownloadState> = flow {
    val modelDir = PiperTtsEngine.modelDir(context)
    val destFile = File(modelDir, "de_DE-eva_k-x_low.onnx")
    val configFile = File(modelDir, "de_DE-eva_k-x_low.onnx.json")
    if (destFile.exists()) destFile.delete()

    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val doneChannel = Channel<Boolean>(capacity = 1)
    var receiver: BroadcastReceiver? = null

    try {
        val downloadId = dm.enqueue(
            DownloadManager.Request(Uri.parse(MODEL_URL)).apply {
                setTitle("Piper TTS — So-Mi Stimme")
                setDescription("Natürliche Offline-Stimme (~20 MB)")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destFile))
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
                )
                setMimeType("application/octet-stream")
            }
        )
        Log.i(TAG, "Piper model download enqueued id=$downloadId")

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                runCatching { ctx.unregisterReceiver(this) }
                val cursor = dm.query(DownloadManager.Query().setFilterById(id))
                val success = cursor?.use { c ->
                    c.moveToFirst() &&
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                        DownloadManager.STATUS_SUCCESSFUL
                } ?: false
                Log.i(TAG, "download complete success=$success")
                doneChannel.trySend(success)
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED,
        )

        var elapsed = 0L
        var done = false
        while (!done) {
            val signal = doneChannel.tryReceive()
            if (signal.isSuccess) {
                val ok = signal.getOrNull() ?: false
                emit(PiperDownloadState.Progress(100))
                if (ok) {
                    // Also download the config JSON (tiny, synchronous)
                    runCatching {
                        val cfg = java.net.URL(CONFIG_URL).readBytes()
                        configFile.writeBytes(cfg)
                    }.onFailure { Log.w(TAG, "config download failed", it) }
                    emit(PiperDownloadState.Done)
                } else {
                    emit(PiperDownloadState.Failed)
                }
                done = true
                break
            }
            val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
            val pct = cursor?.use { c ->
                if (!c.moveToFirst()) return@use null
                when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                        val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val dl = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        if (total > 0) ((dl * 100L) / total).toInt().coerceIn(0, 99) else 0
                    }
                    else -> null
                }
            }
            if (pct != null) emit(PiperDownloadState.Progress(pct))

            delay(600)
            elapsed += 600
            if (elapsed >= 300_000L) {
                Log.w(TAG, "Piper download timed out")
                dm.remove(downloadId)
                emit(PiperDownloadState.Failed)
                done = true
            }
        }
    } finally {
        doneChannel.close()
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
    }
}.flowOn(Dispatchers.IO)
