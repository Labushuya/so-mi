# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Roadmap & Single Source of Truth

**Read `ROADMAP.md` at the start of every session before reading `SPEC.md`.** It tracks IST-state vs. SPEC.md SOLL-state plus all user-agreed deviations from chat sessions that aren't in SPEC.md. SPEC.md is the original plan; ROADMAP.md tracks reality. When they conflict, ROADMAP.md wins because it's where session-locked decisions live (e.g. multilingual-MiniLM instead of bge-small per 2026-06-08, no Online-Boost per user veto, 14B-models pending after v0.15.0).

When a planning workflow is launched, pass ROADMAP.md as additional context — not just SPEC.md. Plan-agents that consult only SPEC.md will reach wrong conclusions about scope (this happened in v0.14.3 — task #87 14B-models was wrongly closed as superseded because the plan-agent didn't see ROADMAP).

After each release that changes scope: update ROADMAP.md "Aktueller Stand" + "Pipeline" sections. After each user agreement that adds a deviation: add it to the "User-Vereinbarungen" section with a date stamp.

## Repository Status

**Phase 1 (Skeleton + Pipeline) is in place.** The repo has the full `android/` multi-module Gradle setup, the build-and-release GitHub Actions pipeline, all shell scripts including the verbatim `init-keystore.sh` from SPEC §6, `soul/soul.md` verbatim from SPEC §2, and the release-please configuration. **What's still missing for the first green CI run:** the user must execute `bash scripts/init-keystore.sh` once and commit `keystore/ci.keystore`. Until that file is on `main`, the build job fails its keystore-presence check.

The `gradle-wrapper.jar` is intentionally not committed — the GitHub Actions workflow generates it on first run via `gradle wrapper`. Once the first CI run completes, the jar is regenerated identically on every clean checkout; you can commit it then if you want the CI to skip the bootstrap step.

Phases 2–5 are not yet started. When asked to "implement X" or "do the next thing," **read `SPEC.md` end-to-end first** and then resume at the earliest unchecked phase. Do not improvise architecture that contradicts the spec — escalate to the user instead.

## What So-Mi Is

An offline-first, single-user Android personal assistant: local LLM (llama.cpp) + KIWIX RAG + ObjectBox vector DB for conversation memory + tool-calling (12 tools) + voice. Distribution is sideload-only via GitHub Releases — **never** Play Store. The persona is modeled on Songbird/So-Mi from Cyberpunk 2077: Phantom Liberty, but as a daily-use companion, not a 1:1 copy.

## Non-Negotiable Invariants

These are easy to break by accident; reread before changing anything in their orbit.

- **`soul/soul.md` is the only source of personality.** It is injected as a fixed `system` prefix on every LLM call, never retrieved from the vector DB, never paraphrased, never overridden by RAG output. The Cyberpunk-flavored German prose in `SPEC.md` §2 is the canonical text — copy it verbatim into `soul/soul.md` when creating it.
- **One `applicationId` for all build types.** No `applicationIdSuffix` for debug. Otherwise the in-app updater (§10) cannot install over the previous version and the whole sideload model breaks.
- **The CI keystore is committed in cleartext** at `keystore/ci.keystore` with public password `ci-password-public`. This is intentional (sideload-only, signature stability matters more than secrecy) and documented in `scripts/init-keystore.sh`. Do not rotate it; do not move it to GitHub Secrets; do not gate releases on a different signing identity. Replacing it bricks updates for any installed copy.
- **`versionCode = 10000 + GITHUB_RUN_NUMBER`** — monotonic, computed in CI, passed via `ORG_GRADLE_PROJECT_versionCode`. Don't hardcode it in `build.gradle.kts`.
- **No telemetry, no analytics, no Crashlytics.** Privacy is a feature, not a default-to-revisit.
- **No "I'm here to help" / "Sure, happy to assist" / disclaimer chains.** Generic assistant phrasing is treated as a bug — see `SPEC.md` §11 and the "Was ich nie tue" section of `soul.md`.
- **The Claude API is optional, never required.** Offline must work standalone. The user's API key, if entered, lives in `EncryptedSharedPreferences` behind a `BiometricPrompt` gate — never in the APK, never logged.

## Architecture in One Pass

Multi-module Gradle build under `android/` (see `SPEC.md` §3):

- `app/` — Compose UI, navigation, ViewModels, Hilt wiring. Allowed to import any `core-*` module.
- `core-llm/` — llama.cpp JNI wrapper (NDK r27, arm64-v8a, OpenCL backend for Adreno), prompt templates, token `Flow<String>`.
- `core-rag/` — libkiwix wrapper, ONNX embeddings (bge-small), ObjectBox HNSW retrieval.
- `core-tools/` — Tool-call dispatcher; 12 tools defined in `SPEC.md` §9. JSON-Schema → GBNF grammar for constrained decoding.
- `core-data/` — Room, DataStore, EncryptedSharedPreferences, Keystore, hardware detection.
- `core-voice/` — SpeechRecognizer + sherpa-onnx Whisper-tiny offline fallback; Piper TTS with `so-mi`/`so-mi-whisper`/`so-mi-glitch` voice profiles.
- `core-ui/` — Reusable Compose components, theme, glitch shaders.
- `core-common/` — Utils, `Result` types, dispatcher provider.

**Module rule:** `core-*` modules may only import `core-common`. Cross-`core-*` dependencies go through `app/` wiring or interfaces in `core-common`.

### Acknowledged drift from SPEC §3

`SPEC.md` §3 names "Compose BOM 2026.05.x" alongside "Kotlin 2.0.x" and "AGP 8.5+". That triple is internally inconsistent — Compose BOM 2026.05.x ships against Kotlin 2.3.x / AGP 9.x, which contradicts the Kotlin 2.0.x pin. **The Phase 1 catalog pins Compose BOM to `2024.10.01`** — the latest BOM that pairs cleanly with the SPEC-mandated Kotlin 2.0.21 + AGP 8.5.2. Library entries in `android/gradle/libs.versions.toml` are otherwise the SPEC's library list at the latest stable mid-2024 versions.

This drift is deliberate and documented; bump the BOM when you also bump Kotlin/AGP, in lockstep. Don't bump the BOM in isolation.

The runtime request flow (`SPEC.md` §8) is **soul.md prefix → intent router → optional tool-call pass → multi-corpus RAG retrieval (conversation-memory + KIWIX + notes, top-K=5 each) → LLM synthesis pass → post-processor (style/length cap) → UI stream + TTS**. The synthesis pass is always local even when the optional Claude boost supplies substance — the *voice* must stay local.

Three vector corpora live in one ObjectBox DB: `zim-wikipedia-de`, `notes`, `conversation-memory`. KIWIX ZIMs ship with pre-built sidecar `.vecdb` files (indexing on-device would take hours). Conversation memory uses **supersedes-not-update** semantics (mem0 pattern) for contradictions, and `score = cosine * exp(-age / 90 days)` for decay.

## Hardware-Adaptive Model Selection

`SPEC.md` §7 defines four tiers (Tiny/Small/Medium/Large) and a traffic-light recommender. Default for the target device (HONOR Magic V2, 16 GB) is **Qwen2.5 7B Q4_K_M + bge-small-en-v1.5 INT8 + ObjectBox HNSW**. The picker auto-selects the largest GREEN tier; RED requires an explicit modal confirmation; OOM auto-falls-back to the next-smaller GREEN. A manual GGUF override lets the user load any local file.

When implementing this, the budget heuristic is `availRamGB * 0.25 + 1.5` (the +1.5 GB is KV-cache headroom). Don't change it without re-running the math against the tier table.

## Build & Release Pipeline

The full `.github/workflows/build-and-release.yml` is dictated in `SPEC.md` §5 — copy it verbatim. Key properties:

- Triggered on push to `main` (and `workflow_dispatch`). Targets ≤ 30 min.
- `release-please` (release-type: simple) drives versioning from Conventional Commits → `version.txt` + `CHANGELOG.md`.
- Build job is gated on `needs.release-please.outputs.release_created == 'true'` — pushes that don't bump the version don't produce a build.
- Caches: Gradle, Android SDK, llama.cpp models (`~/.cache/llm-models`, key `hashFiles('scripts/download-models.sh')`).
- After signing, the workflow runs `apksigner verify --verbose` and `--print-certs | grep SHA-256` as a smoke test before attaching to the release.

**Use Conventional Commits** (`feat:`, `fix:`, `chore:`, `docs:`, etc.) — release-please depends on this.

## Scripts (in `scripts/`)

| Script | When |
|---|---|
| `init-keystore.sh` | **One-time, manual** — the *only* required manual step. Refuses to overwrite an existing keystore (would break updates). |
| `bootstrap-soul.sh` | One-time, manual, optional. Uses Claude API + prompt-caching on `soul.md` to generate `knowledge/initial-memory.json` (~80 persona facts, 100 Q&A pairs, tag taxonomy). Loaded into ObjectBox on first launch. |
| `download-models.sh` | Every CI build. Idempotent, checksummed, populates `~/.cache/llm-models/` with the embedding model + a small smoke-test LLM. Larger models are downloaded on-device at runtime. |
| `install-dev.sh` | Optional dev convenience — pulls latest GitHub Release and `adb install`s it on connected devices. |
| `release.sh` | Local helper to trigger a release-please PR. |

## Common Commands

These don't exist yet (Phase 1). Once the Android module is in place:

```bash
cd android
./gradlew :app:assembleDebug                 # local debug build
./gradlew :app:assembleRelease               # signed release (uses keystore/ci.keystore)
./gradlew :core-llm:test                     # unit tests for one module
./gradlew test                               # all unit tests
./gradlew connectedAndroidTest               # instrumentation tests (needs device/emulator)
./gradlew :app:installDebug                  # build + install on connected device
```

When invoked manually (outside CI), the release build needs `versionCode`/`versionName` Gradle properties or it falls back to `1` / `0.0.0-dev`:

```bash
./gradlew :app:assembleRelease \
  -PversionCode=10001 -PversionName=0.1.0
```

Single-test invocation:

```bash
./gradlew :core-llm:test --tests "com.somi.llm.PromptBuilderTest.buildsSoulPrefix"
```

## Tool Layer Conventions (`SPEC.md` §9)

12 tools, all defined as JSON Schema (and converted to GBNF for llama.cpp's constrained decoding). Routing is a 3-stage cascade with rough hit-rate targets — don't reorder them:

1. **Regex direct dispatch** (~40%) — cheap, deterministic, must come first.
2. **Embedding cosine ≥ 0.78** against tool-description vectors (~35%).
3. **LLM plan-pass fallback** (~25%) — only when the first two miss.

The Claude online-boost escalation (when an API key is present) fires on: `query_tokens > 6000` OR `estimated_complexity > 0.8` OR explicit `@claude` flag OR (`tool_choice == "uncertain"` AND `retries >= 2`). Even when Claude answers, the response is run through a **local persona synthesis pass** before display — and the UI shows a small cyan "via Claude" chip. There is a hard monthly budget cap.

## Phase Plan

`SPEC.md` §12 enumerates Phases 0–5 with explicit acceptance criteria per phase. Always check which phase the repo is currently in (look for the artifacts each phase produces) before starting work, and **do not skip ahead** — Phase 2's smoke test depends on Phase 1's pipeline producing an installable APK first.

## Language

`SPEC.md` and `soul.md` are in German because the user is German-speaking and So-Mi's primary language is German with English/Korean codeswitching. Code, identifiers, and code comments are in English. UI strings are German (i18n is not in scope for v1).
