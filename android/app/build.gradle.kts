// app — Phase 1 acceptance build.
//
// Ships a single Compose chat-shell screen (Songbird-reskinned port of the
// Odysseus chat surface) that displays the live versionName/versionCode in
// the top bar — the smoke signal proving the release-please → versionCode
// injection → signed APK → Releases attach pipeline works end-to-end. Once
// green, Phase 2 wires the LLM / RAG modules behind it.
//
// Hard rules (SPEC §11 + §10):
//   - Single applicationId across debug + release. NO applicationIdSuffix.
//   - Debug AND release sign with the same CI keystore so the in-app updater
//     (Phase 5) can install over the previously-installed build without
//     INSTALL_FAILED_UPDATE_INCOMPATIBLE.
//   - versionCode/versionName are -P inputs from CI; sane local-dev defaults.
//   - isMinifyEnabled = false on release for Phase 1: the empty proguard-
//     rules.pro combined with R8 was stripping Compose runtime classes and
//     crashing the app on launch. Re-enable once we author proper keep rules.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.somi.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.somi.app"
        minSdk = 29
        targetSdk = 35

        // CI passes -PversionCode=$((10000 + GITHUB_RUN_NUMBER)) and
        // -PversionName=<release-please version>. Locally these fall back so
        // `./gradlew :app:assembleRelease` just works.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = project.findProperty("versionName") as String? ?: "0.0.0-dev"
    }

    signingConfigs {
        create("release") {
            // SPEC §5 verbatim. Public keystore, public password — see
            // scripts/init-keystore.sh for the rationale (sideload-only,
            // signature stability matters more than secrecy).
            storeFile = rootProject.file("../keystore/ci.keystore")
            storePassword = "ci-password-public"
            keyAlias = "ci"
            keyPassword = "ci-password-public"
        }
    }

    buildTypes {
        debug {
            // No applicationIdSuffix — preserve the update path.
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            // Phase 1: minification DISABLED. The empty proguard-rules.pro on
            // top of the optimize baseline was stripping Compose runtime
            // classes — that's what crashed v0.2.0 on launch. Re-enable in
            // Phase 2 once we author hand-validated keep rules for Compose +
            // kotlinx-serialization + Hilt.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true   // enables BuildConfig.VERSION_NAME / VERSION_CODE
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Phase 2.1: pull in the new modules. core-llm exposes LlamaContext
    // (NoOp impl for now), core-data exposes HardwareDetector (stub),
    // core-ui exposes ChatViewModel.
    implementation(project(":core-common"))
    implementation(project(":core-llm"))
    implementation(project(":core-data"))
    implementation(project(":core-ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt — application-level wiring + ViewModel injection bridge.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
