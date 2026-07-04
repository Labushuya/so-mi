plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.somi.voice"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Target arm64-v8a only — Magic V2 target device.
        // The static-link AAR has no separate libonnxruntime.so for arm64,
        // so there is no conflict with core-rag's onnxruntime-android:1.18.0.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        // sherpa-onnx static AAR may duplicate some symbols; pick-first resolves.
        jniLibs.pickFirsts += listOf("**/libsherpa-onnx-jni.so")
    }
}

dependencies {
    // sherpa-onnx AAR lives in :app/libs — app depends on it directly.
    // core-voice only uses sherpa-onnx classes; the AAR is provided transitively
    // via the app module's fileTree dependency.
    compileOnly(fileTree(mapOf("dir" to "../app/libs", "include" to listOf("*.aar"))))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(project(":core-common"))
}
