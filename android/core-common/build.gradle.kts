// core-common — Shared utilities, Result types, dispatcher provider.
//
// SPEC §3 rule: every other core-* module may import core-common. core-common
// itself imports nothing from siblings — keep it cycle-free or you'll regret it.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Target JVM bytecode 17 (matches AGP's compileOptions on the Android modules)
// without pinning the build to a specific JDK toolchain — the build runs with
// whatever JDK is on PATH (CI: Temurin 21, locally: Microsoft OpenJDK 21).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
