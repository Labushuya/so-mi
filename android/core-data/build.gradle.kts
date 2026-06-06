// core-data — device + storage + model-download facts.
//
// Phase 2.2 (RAM/storage/GPU detection + recommendModelTier).
// Phase 2.4 (this commit): adds the model-download stack —
//   ModelStorage, ModelManager, ModelDownloadWorker, ResumableDownloader,
//   AtomicInstall, DownloadNotifications.
//
// Module rule (SPEC §3): core-* may import core-common only.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.somi.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core-common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    // Phase 2.4: model download stack.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)

    // Phase 2.4: WorkManager → Flow conversion lives in lifecycle-livedata-ktx.
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Phase 2.4: Hilt @HiltWorker / @AssistedInject support for the
    // ModelDownloadWorker. KSP processor generates the @AssistedInject
    // factory bindings.
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
