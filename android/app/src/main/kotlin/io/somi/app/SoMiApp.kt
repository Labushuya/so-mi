package io.somi.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
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
 */
@HiltAndroidApp
class SoMiApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
