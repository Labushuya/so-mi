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

    private companion object {
        const val TAG = "RagBootstrap"
    }
}
