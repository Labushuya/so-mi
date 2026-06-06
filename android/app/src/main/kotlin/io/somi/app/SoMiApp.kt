package io.somi.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application root.
 *
 * `@HiltAndroidApp` triggers Hilt's code generation: SoMiApp_HiltComponents
 * gets generated and the SingletonComponent is created here. Every
 * `@AndroidEntryPoint` Activity (currently just MainActivity) and every
 * `@HiltViewModel` resolves through this graph.
 *
 * Phase 2.1: empty body. Phase 2.5+ will hook in WorkManager configuration
 * (HiltWorkerFactory) for the model download manager.
 */
@HiltAndroidApp
class SoMiApp : Application()
