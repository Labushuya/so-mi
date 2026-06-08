// core-llm-llama — Phase-2.3 native llama.cpp binding.
//
// Vendored upstream (git submodule) llama.cpp at tag b9543 lives at
// `upstream/`. We don't copy ggml/llama/common sources — `add_subdirectory`
// in our CMakeLists points at upstream/ directly, so a `git submodule
// update` is the only thing required to bump the pin.
//
// Public Kotlin API consumed verbatim from the upstream
// examples/llama.android/lib/ source set:
//   - com.arm.aichat.AiChat (entry-point object)
//   - com.arm.aichat.InferenceEngine (interface)
//   - com.arm.aichat.internal.InferenceEngineImpl (impl, JNI shim host)
//   - com.arm.aichat.gguf.* (GGUF metadata reader)
//
// Module rule (SPEC §3): core-* may import core-common only.
// core-llm-llama is a deliberate exception — it imports :core-llm to see
// the LlamaContext interface it implements. Documented in
// memory/phase2-architecture-decisions.md.
//
// minSdk: upstream sets 33; we override to 30 — that's the minimum
// required by upstream's logging.h (uses __android_log_is_loggable
// which is API-30+). All so-mi modules now align on minSdk 30.
// NDK r27 (matching SPEC §3) over upstream's r29 — KleidiAI compiles
// fine on r27's CMake 3.22.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.somi.llm.llama"
    compileSdk = 35
    ndkVersion = "27.0.12077973"  // SPEC §3 pin; matches CI setup-android packages line

    defaultConfig {
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    // Static-link ggml/llama/common into libai-chat.so.
                    // Upstream's BUILD_SHARED_LIBS=ON path produces a
                    // libllama-common.so with hidden visibility, which is
                    // why our linker couldn't see common_chat_templates_*
                    // symbols. Static link → all symbols available, one
                    // consolidated .so, smaller APK.
                    "-DBUILD_SHARED_LIBS=OFF",
                    "-DLLAMA_BUILD_APP=OFF",
                    "-DLLAMA_BUILD_COMMON=ON",
                    "-DLLAMA_OPENSSL=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_CURL=OFF",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_BACKEND_DL=OFF",  // requires shared libs
                    "-DGGML_CPU_ALL_VARIANTS=OFF",  // requires backend-dl
                    "-DGGML_LLAMAFILE=OFF",
                    // Phase 2.3 ships CPU-only per locked-in decision in
                    // memory/phase2-architecture-decisions.md. GPU backends
                    // get prototyped in 2.5 once we have a stable baseline.
                    "-DGGML_OPENCL=OFF",
                    "-DGGML_VULKAN=OFF",
                )
                cppFlags += listOf("-O3", "-fvisibility=hidden")
                abiFilters += listOf("arm64-v8a")
            }
        }

        ndk {
            // arm64-v8a only — Magic V2 + every modern Android device.
            // Skipping x86_64 keeps APK size under control and matches
            // what we'd ship to real users; emulator dev uses a different
            // build variant if needed.
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Vendored upstream Kotlin sources — copied into src/main/kotlin/
    // (com.arm.aichat package) since v0.11.4 because we need to extend
    // InferenceEngine with setSamplerParams, which means patching
    // InferenceEngineImpl.kt. Editing inside upstream/ would be wiped
    // by the next git submodule update; copying once gives us a stable
    // override surface. Native ai_chat.cpp follows the same pattern
    // (see core-llm-llama/src/main/cpp/ai_chat.cpp header comment).
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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
        buildConfig = false
    }

    packaging {
        // libc_chat.so + libllama.so + libggml-*.so all per-ABI.
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {
    // Sees the LlamaContext interface defined in :core-llm.
    implementation(project(":core-common"))
    implementation(project(":core-llm"))

    // Used by the upstream InferenceEngineImpl Kotlin sources.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
