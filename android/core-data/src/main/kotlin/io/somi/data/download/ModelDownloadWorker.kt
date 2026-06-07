package io.somi.data.download

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
import io.somi.data.ModelCatalog
import io.somi.data.ModelPart
import io.somi.data.ModelStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Downloads every part of a [ModelManifest] sequentially, with resume
 * support, SHA-256 verification, and a foreground notification so the
 * OS doesn't kill the download mid-stream.
 *
 * Inputs (via WorkData):
 *   KEY_MODEL_ID   — the catalog id, e.g. "qwen2.5-7b-instruct-q4_k_m"
 *   KEY_USER_AGENT — User-Agent header sent to Hugging Face
 *
 * Progress (emitted via setProgress):
 *   KEY_BYTES_DONE / KEY_BYTES_TOTAL   — aggregate across parts
 *   KEY_CURRENT_PART / KEY_TOTAL_PARTS — 1-indexed
 *
 * Lifecycle:
 *   - Wraps the entire download in setForeground() with a low-importance
 *     ongoing notification so the system doesn't kill the worker.
 *   - On any IOException → Result.retry() (WorkManager backs off
 *     exponentially per the constraint set in [enqueueModelDownload]).
 *   - On SHA mismatch → Result.failure(reason="sha_mismatch") + delete .part.
 *   - On CancellationException → rethrows so WorkManager observes the
 *     cancel and the .part stays for the next resume.
 */
@HiltWorker
internal class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val storage: ModelStorage,
    private val httpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        buildForegroundInfo(downloaded = 0L, total = 0L, partLabel = "")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        DownloadNotifications.ensureChannel(applicationContext)

        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return@withContext Result.failure(workDataOf(KEY_REASON to REASON_BAD_INPUT))
        val userAgent = inputData.getString(KEY_USER_AGENT) ?: DEFAULT_USER_AGENT

        val manifest = ModelCatalog.byId(modelId)
            ?: return@withContext Result.failure(workDataOf(KEY_REASON to REASON_BAD_INPUT))

        // Preflight storage. If we can't fit the whole thing, fail loud
        // before opening a single connection.
        if (!storage.allocateBytes(manifest.totalSizeBytes)) {
            return@withContext Result.failure(workDataOf(KEY_REASON to REASON_STORAGE_FULL))
        }

        val downloader = ResumableDownloader(httpClient)
        val totalBytes = manifest.totalSizeBytes
        var bytesDoneAcrossParts = 0L

        try {
            for ((index, part) in manifest.parts.withIndex()) {
                val partLabel = "${index + 1}/${manifest.parts.size}"
                val finalFile = storage.finalFile(modelId, part.filename)
                if (finalFile.exists()) {
                    // Already promoted (likely a previous run completed
                    // this part before a later part failed). Just count
                    // its bytes and skip.
                    bytesDoneAcrossParts += part.sizeBytes
                    setProgress(progressData(bytesDoneAcrossParts, totalBytes, index + 1, manifest.parts.size))
                    continue
                }

                val partFile = storage.partFile(modelId, part.filename)
                val partStartingAt = bytesDoneAcrossParts

                val result = downloader.download(
                    url = part.url,
                    partFile = partFile,
                    userAgent = userAgent,
                ) { downloadedThisPart, _ ->
                    val total = partStartingAt + downloadedThisPart
                    maybeUpdateProgress(
                        downloaded = total,
                        total = totalBytes,
                        partLabel = partLabel,
                    )
                    setProgress(progressData(total, totalBytes, index + 1, manifest.parts.size))
                }

                // Verify before promoting. Surface a verifying flag in
                // setProgress so the UI can switch from "73 %" to "Wird
                // überprüft…" while we hash the bytes.
                setProgress(
                    workDataOf(
                        KEY_BYTES_DONE to bytesDoneAcrossParts + part.sizeBytes,
                        KEY_BYTES_TOTAL to totalBytes,
                        KEY_CURRENT_PART to index + 1,
                        KEY_TOTAL_PARTS to manifest.parts.size,
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
                setProgress(progressData(bytesDoneAcrossParts, totalBytes, index + 1, manifest.parts.size))
            }
            return@withContext Result.success()
        } catch (ce: CancellationException) {
            // Keep .part files for next attempt; rethrow so WM sees the cancel.
            throw ce
        } catch (io: IOException) {
            // Transient — let WorkManager back off and retry.
            Log.w(TAG, "transient I/O error; will retry", io)
            return@withContext Result.retry()
        } catch (t: Throwable) {
            Log.e(TAG, "unexpected failure", t)
            return@withContext Result.failure(workDataOf(KEY_REASON to REASON_UNKNOWN))
        }
    }

    // ------------------------------------------------------------------
    // Progress + foreground plumbing
    // ------------------------------------------------------------------

    @Volatile private var lastNotifyAt: Long = 0L

    private suspend fun maybeUpdateProgress(downloaded: Long, total: Long, partLabel: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifyAt < NOTIFY_THROTTLE_MS) return
        lastNotifyAt = now
        try {
            setForeground(buildForegroundInfo(downloaded, total, partLabel))
        } catch (e: Exception) {
            // setForeground can throw on Android 12+ if the OS thinks
            // the app shouldn't be foregrounding right now. Worker will
            // continue; just don't crash.
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

        val notif = NotificationCompat.Builder(applicationContext, DownloadNotifications.CHANNEL_ID)
            .setSmallIcon(io.somi.data.R.drawable.ic_notification)
            .setContentTitle("So-Mi: Modell wird geladen")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, pct.coerceIn(0, 100), total <= 0L)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Abbrechen", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                DownloadNotifications.NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(DownloadNotifications.NOTIF_ID, notif)
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

    companion object {
        const val KEY_MODEL_ID = "modelId"
        const val KEY_USER_AGENT = "userAgent"
        const val KEY_REASON = "reason"
        const val KEY_FAILED_PART = "failedPart"
        const val KEY_BYTES_DONE = "bytesDone"
        const val KEY_BYTES_TOTAL = "bytesTotal"
        const val KEY_CURRENT_PART = "currentPart"
        const val KEY_TOTAL_PARTS = "totalParts"
        // v0.11.2: surfaced for ~1s after byte transfer completes while
        // SHA-256 + AtomicInstall.promote run. UI switches from
        // percentage to "Wird überprüft…" when this is true.
        const val KEY_VERIFYING = "verifying"

        const val REASON_BAD_INPUT = "bad_input"
        const val REASON_STORAGE_FULL = "storage_full"
        const val REASON_CHECKSUM_MISMATCH = "checksum_mismatch"
        const val REASON_UNKNOWN = "unknown"

        private const val TAG = "ModelDownloadWorker"
        private const val NOTIFY_THROTTLE_MS = 500L
        private const val DEFAULT_USER_AGENT = "so-mi/0.x (+https://github.com/Labushuya/so-mi)"
    }
}
