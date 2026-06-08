package io.somi.rag.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.somi.data.download.AtomicInstall
import io.somi.data.download.ResumableDownloader
import io.somi.rag.embed.EmbeddingModelCatalog
import io.somi.rag.embed.EmbeddingModelStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * v0.14.0 M2 — downloads the embedder ONNX model + tokenizer.json on
 * first launch. Same shape as [io.somi.data.download.ModelDownloadWorker]:
 * ResumableDownloader + AtomicInstall + foreground notification, just
 * for the embedding artifacts under `$filesDir/models/embeddings/<id>/`.
 *
 * Lifecycle:
 *  - Wraps the entire download in setForeground() with a low-importance
 *    ongoing notification so the system doesn't kill the worker.
 *  - On any IOException → Result.retry() (WorkManager backs off).
 *  - On SHA mismatch → Result.failure(reason="checksum_mismatch") + delete .part.
 *  - On CancellationException → rethrows so WorkManager observes the cancel.
 */
@HiltWorker
internal class EmbeddingModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val storage: EmbeddingModelStorage,
    private val httpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        buildForegroundInfo(downloaded = 0L, total = 0L, partLabel = "")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ensureChannel(applicationContext)

        // Single hardcoded model in v0.14.0 — DEFAULT from the catalog.
        val model = EmbeddingModelCatalog.DEFAULT
        val downloader = ResumableDownloader(httpClient)
        val totalBytes = model.totalSizeBytes
        var bytesDoneAcrossParts = 0L

        try {
            for ((index, part) in model.parts.withIndex()) {
                val partLabel = "${index + 1}/${model.parts.size}"
                val finalFile = storage.finalFile(model.id, part.filename)
                if (finalFile.exists() && finalFile.length() > 0L) {
                    // Already promoted — count and skip.
                    bytesDoneAcrossParts += part.sizeBytes
                    setProgress(progressData(bytesDoneAcrossParts, totalBytes, index + 1, model.parts.size))
                    continue
                }

                val partFile = storage.partFile(model.id, part.filename)
                val partStartingAt = bytesDoneAcrossParts

                val result = downloader.download(
                    url = part.url,
                    partFile = partFile,
                    userAgent = USER_AGENT,
                ) { downloadedThisPart, _ ->
                    val total = partStartingAt + downloadedThisPart
                    maybeUpdateProgress(total, totalBytes, partLabel)
                    setProgress(progressData(total, totalBytes, index + 1, model.parts.size))
                }

                // Verify before promoting.
                setProgress(
                    workDataOf(
                        KEY_BYTES_DONE to bytesDoneAcrossParts + part.sizeBytes,
                        KEY_BYTES_TOTAL to totalBytes,
                        KEY_CURRENT_PART to index + 1,
                        KEY_TOTAL_PARTS to model.parts.size,
                        KEY_VERIFYING to true,
                    ),
                )
                if (!result.sha256Hex.equals(part.sha256, ignoreCase = true)) {
                    Log.e(TAG, "sha mismatch on ${part.filename}: " +
                        "expected=${part.sha256} got=${result.sha256Hex}")
                    AtomicInstall.cleanupOnFailure(partFile)
                    return@withContext Result.failure(
                        workDataOf(
                            KEY_REASON to REASON_CHECKSUM_MISMATCH,
                            KEY_FAILED_PART to part.filename,
                        ),
                    )
                }

                AtomicInstall.promote(partFile, finalFile)
                bytesDoneAcrossParts += part.sizeBytes
                setProgress(progressData(bytesDoneAcrossParts, totalBytes, index + 1, model.parts.size))
            }
            return@withContext Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (io: IOException) {
            Log.w(TAG, "transient I/O error; will retry", io)
            return@withContext Result.retry()
        } catch (t: Throwable) {
            Log.e(TAG, "unexpected failure", t)
            return@withContext Result.failure(workDataOf(KEY_REASON to REASON_UNKNOWN))
        }
    }

    @Volatile private var lastNotifyAt: Long = 0L

    private suspend fun maybeUpdateProgress(downloaded: Long, total: Long, partLabel: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifyAt < NOTIFY_THROTTLE_MS) return
        lastNotifyAt = now
        try {
            setForeground(buildForegroundInfo(downloaded, total, partLabel))
        } catch (e: Exception) {
            Log.w(TAG, "setForeground failed; continuing", e)
        }
    }

    private fun buildForegroundInfo(
        downloaded: Long,
        total: Long,
        partLabel: String,
    ): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val text = if (total > 0L) {
            val mbDone = downloaded / 1_048_576.0
            val mbTotal = total / 1_048_576.0
            val partSuffix = if (partLabel.isNotBlank()) " (Teil $partLabel)" else ""
            "%.0f / %.0f MB%s".format(mbDone, mbTotal, partSuffix)
        } else {
            "Verbinde…"
        }

        val pct = if (total > 0L) ((downloaded * 100) / total).toInt() else 0

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(io.somi.data.R.drawable.ic_notification)
            .setContentTitle("So-Mi: Erinnerungs-Modell wird geladen")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, pct.coerceIn(0, 100), total <= 0L)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Abbrechen", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notif)
        }
    }

    private fun progressData(
        downloaded: Long,
        total: Long,
        currentPart: Int,
        totalParts: Int,
    ) = workDataOf(
        KEY_BYTES_DONE to downloaded,
        KEY_BYTES_TOTAL to total,
        KEY_CURRENT_PART to currentPart,
        KEY_TOTAL_PARTS to totalParts,
    )

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Erinnerungs-Modell",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "So-Mi lädt das Modell für ihr Gedächtnis herunter."
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val WORK_NAME = "embedding-model-download"

        const val KEY_REASON = "reason"
        const val KEY_FAILED_PART = "failedPart"
        const val KEY_BYTES_DONE = "bytesDone"
        const val KEY_BYTES_TOTAL = "bytesTotal"
        const val KEY_CURRENT_PART = "currentPart"
        const val KEY_TOTAL_PARTS = "totalParts"
        const val KEY_VERIFYING = "verifying"

        const val REASON_CHECKSUM_MISMATCH = "checksum_mismatch"
        const val REASON_UNKNOWN = "unknown"

        // Distinct from the LLM-download channel + notif id so the two
        // can run side-by-side without clobbering each other's UI.
        private const val CHANNEL_ID = "embedding_downloads"
        private const val NOTIF_ID = 0x502

        private const val TAG = "EmbeddingDownloadWorker"
        private const val NOTIFY_THROTTLE_MS = 500L
        private const val USER_AGENT = "so-mi/0.x (+https://github.com/Labushuya/so-mi)"
    }
}
