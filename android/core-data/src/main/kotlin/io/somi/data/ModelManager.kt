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
     * Kick off (or resume) the download of [manifest].
     *
     * Policy nuance (v0.11.2 fix):
     *  - If the model is already installed → no-op.
     *  - If a download is RUNNING for this id → KEEP (preserve byte-resume
     *    progress; flipping the toggle mid-download is intentionally a no-op
     *    because the worker has open sockets we'd kill for a transient
     *    constraint change).
     *  - In every other state (ENQUEUED waiting for Wi-Fi, BLOCKED, none
     *    yet) → REPLACE so a re-tap with a flipped wifiOnly value actually
     *    takes effect. v0.11.1 used blanket KEEP, which silently dropped
     *    the new constraints — that's why "WLAN-only off" + "Herunterladen"
     *    appeared to do nothing on mobile data.
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

        // Probe the existing work state synchronously. WorkManager exposes
        // getWorkInfosForUniqueWork() as a ListenableFuture; .get() with
        // a short timeout is safe here (the call is local and returns
        // immediately for non-existent work).
        val existingState = try {
            wm.getWorkInfosForUniqueWork(uniqueName(manifest.id))
                .get(500, TimeUnit.MILLISECONDS)
                .firstOrNull()
                ?.state
        } catch (t: Throwable) {
            Log.w(TAG, "could not query existing work state for ${manifest.id}; treating as fresh", t)
            null
        }

        val policy = if (existingState == WorkInfo.State.RUNNING) {
            Log.i(TAG, "${manifest.id} download already RUNNING; keeping it (resume preserved)")
            ExistingWorkPolicy.KEEP
        } else {
            // ENQUEUED / BLOCKED / SUCCEEDED / FAILED / CANCELLED / null —
            // replace so the new constraints take effect. WorkManager
            // tears the old WorkRequest down; the .part files on disk
            // make the next attempt resume from where the bytes are.
            Log.i(TAG, "${manifest.id} download policy = REPLACE (existing state = $existingState)")
            ExistingWorkPolicy.REPLACE
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

        wm.enqueueUniqueWork(uniqueName(manifest.id), policy, request)
    }

    /** Cancel an in-flight download. The .part file stays for resume. */
    fun cancel(manifest: ModelManifest) {
        wm.cancelUniqueWork(uniqueName(manifest.id))
    }

    /**
     * Stream the lifecycle status of [manifest].
     *
     * v0.11.2 fix: the disk-truth check now runs through rescueSideload
     * so a manually-dropped GGUF outside the canonical /<id>/ subdir is
     * adopted instead of triggering a 4 GB re-download.
     *
     * The upstream WorkInfo flow is filtered for empty emissions —
     * WorkManager occasionally emits an empty list during the
     * enqueue-to-DB-write race window, and an empty list at this layer
     * used to map to NotInstalled, which kicked the UI back from
     * Downloading to the picker right after the user tapped download.
     */
    fun observe(manifest: ModelManifest): Flow<ModelStatus> {
        if (storage.rescueSideload(manifest)) {
            val main = storage.mainFileFor(manifest)!!
            return flowOf(ModelStatus.Installed(main))
        }
        return wm.getWorkInfosForUniqueWorkFlow(uniqueName(manifest.id))
            .map { infos -> infos.toStatus(manifest) }
    }

    private fun List<WorkInfo>.toStatus(manifest: ModelManifest): ModelStatus {
        // Disk-truth wins. If the GGUF is on-disk (or can be rescued from
        // a non-canonical path), surface Installed regardless of what
        // WorkManager thinks — covers the brief race where the worker has
        // finalised the file but WM hasn't propagated SUCCEEDED yet.
        if (storage.rescueSideload(manifest)) {
            return ModelStatus.Installed(storage.mainFileFor(manifest)!!)
        }

        val info = firstOrNull()
        if (info == null) {
            // Empty WorkInfo list. Two interpretations:
            //  a) genuinely no work has ever been enqueued for this id
            //     → NotInstalled is correct
            //  b) work was just enqueued but the DB write hasn't settled
            //     yet → emitting NotInstalled here flips the UI from
            //     Downloading back to the picker, which is the v0.11.1
            //     "tap Herunterladen, nothing happens" symptom
            //
            // We can't distinguish (a) from (b) at this layer. The fix
            // is to treat empty as "still pending" rather than "never
            // existed": surface Downloading with 0 bytes. ChatViewModel
            // sets _lifecycle to Downloading optimistically right before
            // observe() subscribes anyway, so we just preserve that
            // optimism. The next emission (RUNNING) will refine.
            return ModelStatus.Downloading(
                bytesDownloaded = 0L,
                bytesTotal = manifest.totalSizeBytes,
                currentPart = 1,
                totalParts = manifest.parts.size,
            )
        }

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
                val verifying = info.progress.getBoolean(ModelDownloadWorker.KEY_VERIFYING, false)
                if (verifying) {
                    ModelStatus.Verifying
                } else {
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
            }
            WorkInfo.State.SUCCEEDED -> {
                // SUCCEEDED but rescueSideload still couldn't find the
                // model — the promote really failed or someone deleted
                // the file in between. Surface as recoverable error.
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
