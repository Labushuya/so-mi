package io.somi.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.somi.data.ModelCatalog
import io.somi.data.ModelStorage
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
    }

    private companion object {
        const val TAG = "SoMiApp"
    }
}
