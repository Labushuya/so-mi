// core-rag — Phase-3 retrieval-augmented memory. M1 wires the
// foundation only: ObjectBox vector DB + ONNX Runtime. No entities
// other than a placeholder _Bootstrap, no embedder, no retrieval
// surface. M2 lands the embedder, M3 the MemoryFact entity, M4-M10
// the rest of the loop. Each milestone is its own commit so a
// regression in one doesn't taint the others.
//
// SPEC §3 ownership: ObjectBox vector store, bge embedder, libkiwix
// (deferred to v0.15.0). Module rule: only depends on :core-common.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // io.objectbox MUST be applied AFTER kotlin-android per the
    // ObjectBox docs — its KSP processor reads kotlin metadata to
    // generate the MyObjectBox schema, so the kotlin plugin has to
    // see entities first.
    alias(libs.plugins.objectbox)
}

android {
    namespace = "io.somi.rag"
    compileSdk = 35

    defaultConfig {
        minSdk = 30

        // arm64-only ship — Magic V2 + every modern phone. Both
        // ObjectBox-android and onnxruntime-android pull multi-ABI
        // .so payloads; without filtering at the library level the
        // app's APK merge would carry x86 / armv7 native libs we
        // never run, ballooning the install. Belt-and-suspenders
        // with the app module's filter.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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
    // v0.14.0 M2: reuse :core-data's ResumableDownloader + AtomicInstall
    // for the embedding-model download. Avoids duplicating ~200 lines of
    // tuned resume + sha256 logic. core-data is already a transitive
    // sibling of :core-rag in the app graph; the :core-rag → :core-data
    // edge is a deliberate exception to the "core-* depends only on
    // core-common" rule, documented in CLAUDE.md.
    implementation(project(":core-data"))

    // Phase-3 deps — see CLAUDE.md "ObjectBox/ONNX lockstep-pin"
    // invariant. Bumping either is a dedicated PR with smoke evidence.
    implementation(libs.objectbox.android)
    implementation(libs.objectbox.kotlin)
    implementation(libs.onnxruntime.android)

    // Hilt for the BoxStore singleton provider + worker factory.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager for the embedding-model download worker.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // OkHttp (transitive via :core-data, but explicit so :core-rag's
    // download worker doesn't break if :core-data ever drops it).
    implementation(libs.okhttp)

    // Smoke test exercises the BoxStore round-trip.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
