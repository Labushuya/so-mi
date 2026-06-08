// Top-level build script. Concrete configuration lives in each module's
// build.gradle.kts; this file only declares plugins so the version catalog
// is the single source of truth for plugin versions.
//
// v0.14.0 ObjectBox Gradle plugin:
//   ObjectBox does not publish a Gradle Plugin Portal marker artifact, so
//   `alias(libs.plugins.objectbox) apply false` resolution fails against
//   the standard plugin sources. We pull the plugin classpath manually
//   here, then `apply` it from :core-rag's plugins block via the legacy
//   plugin-id syntax. This is the documented ObjectBox setup pattern
//   (see https://docs.objectbox.io/getting-started — Android with Kotlin).
buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath(libs.objectbox.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
