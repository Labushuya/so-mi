// core-data — device + storage + model-download + chat-persistence.
//
// Phase 2.2: RAM/storage/GPU detection + recommendModelTier.
// Phase 2.4: model-download stack — ModelStorage, ModelManager,
//   ModelDownloadWorker, ResumableDownloader, AtomicInstall, DownloadNotifications.
// Phase 3a (this commit): Room-backed chat persistence — SoMiDatabase,
//   MessageEntity, MessageDao, ChatRepository, DatabaseModule.
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

// Phase 3a: Room schema export so future migrations can diff against
// the committed schema JSON. KSP creates the directory on first build.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(project(":core-common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    // Phase 2.4: model download stack.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Phase 3a: Room. room-ktx pulls in suspend + Flow query support;
    // room-compiler runs through KSP (room ≥ 2.6 supports KSP natively).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
}
