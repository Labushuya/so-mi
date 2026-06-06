// core-llm — Phase-2 LLM module.
//
// Phase 2.1 (this commit): empty Hilt-wired skeleton. Provides an abstract
//   `LlamaContext` interface (no real implementation) so the rest of the
//   graph compiles before native code lands. Phase 2.3 introduces the
//   sibling `:core-llm-llama` module that vendors llama.cpp's official
//   `examples/llama.android/lib` and provides the concrete LlamaCppContext.
//
// Module rule (SPEC §3): core-* may import core-common only. No siblings.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.somi.llm"
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

    buildFeatures {
        // BuildConfig disabled — module ships no flags. Re-enable if a model
        // path constant ever needs to be baked at build time.
        buildConfig = false
    }
}

dependencies {
    implementation(project(":core-common"))

    // Coroutines — LlamaContext.generate() returns Flow<String>.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Hilt — the LlamaContext provider lives here as @Singleton.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
