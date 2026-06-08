package io.somi.rag

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.Lazy
import io.objectbox.BoxStore
import io.somi.rag.download.EmbeddingModelDownloadWorker
import io.somi.rag.embed.EmbeddingModelCatalog
import io.somi.rag.embed.EmbeddingModelStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.14.0 M1 — lazy facade that opens the [BoxStore] on first
 * `ensureOpen()` call. Lets the app warm-up coroutine in
 * [io.somi.app.SoMiApp] force the open without leaking BoxStore
 * types upward; consumers of `:core-rag` see only this singleton.
 *
 * v0.14.3: also schedules the embedding-model download via
 * [scheduleEmbedderDownload]. v0.14.0–v0.14.2 shipped the worker
 * class but never enqueued it — this fixes the dead-code path
 * the user noticed when no notification appeared on first launch.
 *
 * **Lifecycle:** the BoxStore is opened on first ensureOpen() and
 * never closed in production. ObjectBox's own JNI takes care of
 * orderly shutdown when the process dies.
 */
@Singleton
class RagBootstrap @Inject constructor(
    private val boxStoreLazy: Lazy<BoxStore>,
    private val embeddingStorage: EmbeddingModelStorage,
) {
    /** Force the BoxStore open. Returns immediately on subsequent calls. */
    fun ensureOpen() {
        boxStoreLazy.get()
    }

    /**
     * v0.14.3 — schedule the one-shot embedding-model download if
     * the artifacts aren't already on disk. Idempotent across
     * relaunches via [ExistingWorkPolicy.KEEP]: if a previous run
     * is in flight or already succeeded, the call is a no-op.
     *
     * Wi-Fi-only via [NetworkType.UNMETERED] — same gate as the
     * LLM download. The user never sees a metered surprise; the
     * download notification ("So-Mi: Erinnerungs-Modell wird
     * geladen") is the visible signal.
     */
    fun scheduleEmbedderDownload(context: Context) {
        if (embeddingStorage.isInstalled(EmbeddingModelCatalog.DEFAULT)) {
            Log.i(TAG, "scheduleEmbedderDownload: already installed; skip")
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<EmbeddingModelDownloadWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            EmbeddingModelDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
        Log.i(TAG, "scheduleEmbedderDownload: enqueued (Wi-Fi only, KEEP policy)")
    }

    /**
     * v0.15.0 — manually re-enqueue the embedder download. Same as
     * [scheduleEmbedderDownload] but uses [ExistingWorkPolicy.REPLACE]
     * so the user can force a retry even if a previous attempt failed
     * with `Result.failure()` and is still sitting in the WorkManager
     * queue. Triggered from the Settings → Downloads section.
     *
     * Caller is responsible for deleting any stale on-disk file before
     * calling — otherwise [EmbeddingModelDownloadWorker] short-circuits
     * on the SHA-check.
     */
    fun forceEnqueueEmbedderDownload(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<EmbeddingModelDownloadWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            EmbeddingModelDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.i(TAG, "forceEnqueueEmbedderDownload: enqueued (REPLACE policy)")
    }

    /**
     * v0.15.0 — delete the on-disk embedder artifact so the next
     * [scheduleEmbedderDownload] / [forceEnqueueEmbedderDownload] call
     * will actually re-download instead of short-circuiting.
     */
    fun deleteEmbedder() {
        embeddingStorage.delete(EmbeddingModelCatalog.DEFAULT)
        Log.i(TAG, "deleteEmbedder: artifact removed")
    }

    /** v0.15.0 — quick on-disk check for Settings UI. */
    fun isEmbedderInstalled(): Boolean =
        embeddingStorage.isInstalled(EmbeddingModelCatalog.DEFAULT)

    /**
     * v0.15.0 — public name of the embedder-download unique-work
     * entry. Exposed so consumers in `:core-ui` (which can't see the
     * `internal` Worker class itself) can subscribe to its WorkInfo
     * Flow and call `cancelUniqueWork`. Mirrors the value in
     * [io.somi.rag.download.EmbeddingModelDownloadWorker.WORK_NAME].
     */
    val embedderWorkName: String = EmbeddingModelDownloadWorker.WORK_NAME

    private companion object {
        const val TAG = "RagBootstrap"
    }
}
