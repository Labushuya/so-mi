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
    // sherpa-onnx 1.13.3 static-link: ONNX Runtime baked into libsherpa-onnx-jni.so.
    // No separate libonnxruntime.so for arm64 → no conflict with core-rag ONNX 1.18.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(project(":core-common"))
}
