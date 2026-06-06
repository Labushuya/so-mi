# Phase 1 acceptance build: minify is enabled in release, but the app is
# Compose + a single Activity. Compose / AndroidX ship their own consumer
# rules via AAR metadata, so no custom keep rules are needed here yet.
#
# Add module-specific rules as core-llm / core-rag / core-tools land — and
# verify with `./gradlew :app:assembleRelease` + on-device smoke test.
