// settings.gradle.kts — root settings for the so-mi multi-module Android build.
//
// Plugin and dependency repositories are pinned here so every module inherits
// the same resolution policy. FAIL_ON_PROJECT_REPOS prevents a stray
// `repositories { ... }` block in any module from silently re-introducing JCenter
// or some random Maven mirror.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "so-mi"

// SPEC §3 module map — `app` depends on every `core-*`; each `core-*` may
// only import `core-common`. That rule is enforced by review, not Gradle.
//
// Phase 2.3 exception: :core-llm-llama imports :core-llm because it
// implements the LlamaContext interface defined there. Documented in
// memory/phase2-architecture-decisions.md.
include(":app")
include(":core-common")
include(":core-llm")
include(":core-llm-llama")
include(":core-rag")
include(":core-tools")
include(":core-data")
include(":core-voice")
include(":core-ui")
