package io.somi.data

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import io.somi.data.download.ModelDownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-facing API for downloading and tracking GGUF models.
 *
 * Wraps [WorkManager] under a clean Flow surface so callers (Phase 2.5
 * picker UI, ChatViewModel) don't need to know about WorkInfo. One
 * [ModelManifest] = one unique-work entry, keyed by the manifest's id.
 *
 * Threading: every method is safe to call from any thread.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: ModelStorage,
) {

    private val wm: WorkManager get() = WorkManager.getInstance(context)

    /**
     * Kick off (or resume) the download of [manifest]. If the model is
     * already installed, this is a no-op. If a download is already in
     * flight for this id, KEEP-policy means we don't restart it from
     * byte 0.
     *
     * @param wifiOnly if true, the work waits for an unmetered network.
     *   The picker should default to true on metered connections + show
     *   a one-tap override.
     */
    fun startDownload(manifest: ModelManifest, wifiOnly: Boolean = true) {
        if (storage.isInstalled(manifest)) {
            Log.i(TAG, "${manifest.id} already installed; no-op")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_MODEL_ID to manifest.id,
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .addTag(TAG_DOWNLOAD)
            .addTag("model:${manifest.id}")
            .build()

        wm.enqueueUniqueWork(
            uniqueName(manifest.id),
            // KEEP — re-enqueueing while a download is running picks up
            // where it left off rather than restarting from 0.
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Cancel an in-flight download. The .part file stays for resume. */
    fun cancel(manifest: ModelManifest) {
        wm.cancelUniqueWork(uniqueName(manifest.id))
    }

    /**
     * Stream the lifecycle status of [manifest]. Emits [ModelStatus]
     * snapshots; the latest emission reflects current truth.
     *
     * Initial emission is computed synchronously from on-disk state
     * (Installed if all parts present, NotInstalled otherwise) so the
     * picker UI never flickers through "Downloading" on cold launch.
     */
    fun observe(manifest: ModelManifest): Flow<ModelStatus> {
        if (storage.isInstalled(manifest)) {
            val main = storage.mainFileFor(manifest)!!
            return flowOf(ModelStatus.Installed(main))
        }
        return wm.getWorkInfosForUniqueWorkFlow(uniqueName(manifest.id))
            .map { infos -> infos.toStatus(manifest) }
    }

    private fun List<WorkInfo>.toStatus(manifest: ModelManifest): ModelStatus {
        // Disk-truth wins. If the GGUF is already on-disk and verified
        // (atomic-promoted by the Worker), surface Installed regardless
        // of what WorkManager thinks — covers the brief race where the
        // worker has finalised the file but WM hasn't propagated SUCCEEDED
        // to its observers yet.
        if (storage.isInstalled(manifest)) {
            return ModelStatus.Installed(storage.mainFileFor(manifest)!!)
        }

        val info = firstOrNull()
            ?: return ModelStatus.NotInstalled

        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                // Constraints not yet met (e.g. waiting for Wi-Fi).
                ModelStatus.Downloading(
                    bytesDownloaded = 0L,
                    bytesTotal = manifest.totalSizeBytes,
                    currentPart = 1,
                    totalParts = manifest.parts.size,
                )
            }
            WorkInfo.State.RUNNING -> {
                val done = info.progress.getLong(ModelDownloadWorker.KEY_BYTES_DONE, 0L)
                val total = info.progress.getLong(
                    ModelDownloadWorker.KEY_BYTES_TOTAL,
                    manifest.totalSizeBytes,
                )
                val current = info.progress.getInt(ModelDownloadWorker.KEY_CURRENT_PART, 1)
                val totalParts = info.progress.getInt(
                    ModelDownloadWorker.KEY_TOTAL_PARTS,
                    manifest.parts.size,
                )
                ModelStatus.Downloading(done, total, current, totalParts)
            }
            WorkInfo.State.SUCCEEDED -> {
                // SUCCEEDED but isInstalled is false (above branch already
                // handled the happy case). Either the worker reported
                // success but the file is gone (race with delete?) or the
                // promote failed silently. Surface as a recoverable error.
                ModelStatus.Failed(
                    ModelStatus.Reason.UNKNOWN,
                    "Worker meldet success, aber Datei fehlt. Versuch nochmal.",
                )
            }
            WorkInfo.State.FAILED -> info.outputData.toFailure()
            WorkInfo.State.CANCELLED -> ModelStatus.Failed(
                ModelStatus.Reason.CANCELLED,
                "Abgebrochen.",
            )
        }
    }

    private fun androidx.work.Data.toFailure(): ModelStatus.Failed {
        val reason = getString(ModelDownloadWorker.KEY_REASON)
        return when (reason) {
            ModelDownloadWorker.REASON_STORAGE_FULL -> ModelStatus.Failed(
                ModelStatus.Reason.STORAGE_FULL,
                "Zu wenig Speicher. Mach was frei und versuch's nochmal.",
            )
            ModelDownloadWorker.REASON_CHECKSUM_MISMATCH -> ModelStatus.Failed(
                ModelStatus.Reason.CHECKSUM_MISMATCH,
                "Datei ist korrupt. Lösche sie und lade nochmal.",
            )
            ModelDownloadWorker.REASON_BAD_INPUT -> ModelStatus.Failed(
                ModelStatus.Reason.UNKNOWN,
                "Ungültige Modell-ID.",
            )
            else -> ModelStatus.Failed(
                ModelStatus.Reason.TRANSIENT_NETWORK,
                "Download hat's nicht geschafft. Probier's gleich nochmal.",
            )
        }
    }

    /** Wipe a model and any cached .part. */
    fun delete(manifest: ModelManifest) {
        cancel(manifest)
        storage.delete(manifest)
    }

    private fun uniqueName(modelId: String) = "$WORK_NAME_PREFIX$modelId"

    companion object {
        const val TAG_DOWNLOAD = "model-download"
        private const val WORK_NAME_PREFIX = "download-"
        private const val TAG = "ModelManager"
    }
}
