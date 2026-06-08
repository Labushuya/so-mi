package io.somi.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.somi.data.ModelCatalog
import io.somi.data.ModelStorage
import io.somi.rag.RagBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application root.
 *
 * Phase 2.1: `@HiltAndroidApp` triggers Hilt code-gen — every
 * `@AndroidEntryPoint` Activity and `@HiltViewModel` resolves through
 * the SingletonComponent rooted here.
 *
 * Phase 2.4: implements [Configuration.Provider] so WorkManager picks
 * up Hilt's worker factory. Without this the auto-init removal in
 * AndroidManifest.xml leaves WorkManager unconfigured and any worker
 * enqueued via ModelManager throws IllegalStateException("WorkManager
 * is not initialized properly").
 *
 * Phase 2.10 / v0.11.2: starts [LlamaSessionService] EAGERLY at
 * Application.onCreate — but only when a model is actually installed
 * on disk. Reasoning: in v0.11.1, MainActivity.onCreate was racing
 * Android 14's 5 s "ForegroundServiceDidNotStartInTime" guard. The
 * 7 B Q4_K_M soul.md prefill on Magic V2 routinely exceeds 5 s; if
 * the FGS doesn't promote in time, the OS kills the process mid-load
 * and the ANR dialog briefly flashes then auto-dismisses (= what
 * the user reported as 'Freeze beim Aufwärmen'). Starting the FGS
 * here, before any Activity or Compose work, closes that race.
 *
 * The on-disk check is cheap — one [ModelStorage.findAllInstances]
 * pass at start-up. When no model is installed, we don't post the
 * permanent "Modell läuft" notification; the FGS only appears once
 * the engine is actually about to be loaded. MainActivity covers
 * the post-download case by calling [LlamaSessionService.start]
 * itself when a download completes.
 */
@HiltAndroidApp
class SoMiApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var modelStorage: ModelStorage

    /**
     * v0.14.0 M1 — RAG vector store (ObjectBox). The bootstrap is a
     * lazy facade; touching `ensureOpen()` is the moment the
     * BoxStore actually opens. We do that on a background coroutine
     * after FGS-promote so cold-start time stays ±50ms of v0.13.0.
     */
    @Inject lateinit var ragBootstrap: RagBootstrap

    /**
     * Application-scoped supervisor for fire-and-forget warmups
     * (RAG bootstrap, future Phase-3 indexers). SupervisorJob so
     * one failure doesn't tear down siblings. Lives for the
     * process lifetime — never cancelled.
     */
    private val warmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Promote to foreground service the instant the process starts —
        // BUT only if there is something worth pinning. A fresh-install
        // user who hasn't picked a model yet should not see a permanent
        // "Modell läuft im Hintergrund" notification with no model in
        // sight.
        try {
            val anyComplete = modelStorage
                .findAllInstances(ModelCatalog.ALL)
                .any { it.isComplete }
            if (anyComplete) {
                Log.i(TAG, "model on disk — starting LlamaSessionService eagerly")
                LlamaSessionService.start(this)
            } else {
                Log.i(TAG, "no model installed yet — deferring LlamaSessionService start")
            }
        } catch (t: Throwable) {
            // Disk inspection failure must never crash app startup. The
            // FGS will then start on the slower MainActivity path; load
            // may race the OS guard but the user can still launch.
            Log.w(TAG, "deferred FGS start (probe threw)", t)
        }

        // v0.14.0 M1 — open the RAG BoxStore in the background. Cheap
        // (~10-50ms) but no point doing it on the main thread of cold
        // start. Failures are logged and swallowed: a missing RAG
        // store should not brick the app — the chat path doesn't
        // depend on it yet (M6 onwards will, by which point any open
        // failure will surface as a TalkBack-friendly banner).
        warmupScope.launch {
            try {
                ragBootstrap.ensureOpen()
                Log.i(TAG, "RAG bootstrap: BoxStore opened")
                // v0.14.3: schedule the embedder-model download. Idempotent
                // via ExistingWorkPolicy.KEEP. Was missing in v0.14.0-v0.14.2 —
                // worker class shipped but never enqueued, so first-launch
                // users saw no foreground notification and no download.
                ragBootstrap.scheduleEmbedderDownload(this@SoMiApp)
            } catch (t: Throwable) {
                Log.w(TAG, "RAG bootstrap failed", t)
            }
        }
    }

    private companion object {
        const val TAG = "SoMiApp"
    }
}
