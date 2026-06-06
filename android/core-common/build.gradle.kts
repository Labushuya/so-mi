// core-common — Shared utilities, Result types, dispatcher provider.
//
// SPEC §3 rule: every other core-* module may import core-common. core-common
// itself imports nothing from siblings — keep it cycle-free or you'll regret it.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
