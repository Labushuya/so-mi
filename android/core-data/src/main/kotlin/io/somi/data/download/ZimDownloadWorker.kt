package io.somi.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.somi.data.StorageRoots
import io.somi.data.ZimCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Downloads a single ZIM file with resume support and SHA-256 verification.
 *
 * Modelled after [ModelDownloadWorker] — same foreground/progress/retry contract.
 *
 * Inputs: KEY_ZIM_ID
 * Progress: KEY_BYTES_DONE / KEY_BYTES_TOTAL
 * Failure reasons: KEY_REASON
 */
@HiltWorker
class ZimDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val httpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        buildForegroundInfo(0L, 0L)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        DownloadNotifications.ensureChannel(applicationContext)

        val zimId = inputData.getString(KEY_ZIM_ID)
            ?: return@withContext Result.failure(workDataOf(KEY_REASON to REASON_BAD_INPUT))

        val manifest = ZimCatalog.byId(zimId)
            ?: return@withContext Result.failure(workDataOf(KEY_REASON to REASON_BAD_INPUT))

        val destDir = StorageRoots.zim(applicationContext)
        val finalFile = File(destDir, manifest.filename)
        val partFile = File(destDir, "${manifest.filename}.part")

        if (finalFile.exists()) {
            Log.i(TAG, "${manifest.filename} already installed")
            return@withContext Result.success()
        }

        val downloader = ResumableDownloader(httpClient)

        try {
            val result = downloader.download(
                url = manifest.url,
                partFile = partFile,
                userAgent = USER_AGENT,
            ) { done, total ->
                maybeUpdateProgress(done, total)
                setProgress(workDataOf(KEY_BYTES_DONE to done, KEY_BYTES_TOTAL to total))
            }

            // SHA-256 verification
            setProgress(workDataOf(KEY_BYTES_DONE to manifest.sizeBytes, KEY_BYTES_TOTAL to manifest.sizeBytes, KEY_VERIFYING to true))
            if (!result.sha256Hex.equals(manifest.sha256, ignoreCase = true)) {
                Log.e(TAG, "SHA mismatch: expected=${manifest.sha256} got=${result.sha256Hex}")
                AtomicInstall.cleanupOnFailure(partFile)
                return@withContext Result.failure(workDataOf(KEY_REASON to REASON_CHECKSUM_MISMATCH))
            }

            AtomicInstall.promote(partFile, finalFile)
            Log.i(TAG, "${manifest.filename} installed (${finalFile.length() / 1_048_576} MB)")
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (io: IOException) {
            Log.w(TAG, "transient I/O error; will retry", io)
            Result.retry()
        } catch (t: Throwable) {
            Log.e(TAG, "unexpected failure", t)
            Result.failure(workDataOf(KEY_REASON to REASON_UNKNOWN))
        }
    }

    @Volatile private var lastNotifyAt: Long = 0L

    private suspend fun maybeUpdateProgress(downloaded: Long, total: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifyAt < NOTIFY_THROTTLE_MS) return
        lastNotifyAt = now
        runCatching { setForeground(buildForegroundInfo(downloaded, total)) }
    }

    private fun buildForegroundInfo(downloaded: Long, total: Long): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val text = if (total > 0L) {
            "%.0f / %.0f MB".format(downloaded / 1_048_576.0, total / 1_048_576.0)
        } else {
            "Verbinde…"
        }
        val pct = if (total > 0L) ((downloaded * 100) / total).toInt() else 0
        val notif = NotificationCompat.Builder(applicationContext, DownloadNotifications.CHANNEL_ID)
            .setSmallIcon(io.somi.data.R.drawable.ic_notification)
            .setContentTitle("So-Mi: Lexikon wird geladen")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, pct.coerceIn(0, 100), total <= 0L)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Abbrechen", cancelIntent)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(ZIM_NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ZIM_NOTIF_ID, notif)
        }
    }

    companion object {
        const val KEY_ZIM_ID = "zimId"
        const val KEY_BYTES_DONE = "bytesDone"
        const val KEY_BYTES_TOTAL = "bytesTotal"
        const val KEY_VERIFYING = "verifying"
        const val KEY_REASON = "reason"
        const val REASON_BAD_INPUT = "bad_input"
        const val REASON_CHECKSUM_MISMATCH = "checksum_mismatch"
        const val REASON_UNKNOWN = "unknown"

        private const val TAG = "ZimDownloadWorker"
        private const val NOTIFY_THROTTLE_MS = 500L
        private const val ZIM_NOTIF_ID = 0x502
        private const val USER_AGENT = "so-mi/0.x (+https://github.com/Labushuya/so-mi)"

        fun workTag(zimId: String) = "zim_download_$zimId"

        fun enqueue(context: Context, zimId: String) {
            val req = OneTimeWorkRequestBuilder<ZimDownloadWorker>()
                .setInputData(workDataOf(KEY_ZIM_ID to zimId))
                .addTag(workTag(zimId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workTag(zimId),
                ExistingWorkPolicy.KEEP,
                req,
            )
        }
    }
}
