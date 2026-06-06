// core-data — device + storage facts.
//
// Phase 2.1 (this commit): empty Hilt-wired skeleton with a stub
//   HardwareDetector. Real hardware probing (RAM, storage, GPU,
//   Vulkan/OpenCL availability) lands in Phase 2.2 along with the
//   `recommendModelTier()` function from SPEC §7.
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
        minSdk = 29
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

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
