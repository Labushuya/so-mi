# So-Mi (Songbird)

Persönliche, offline-first Android-Assistentin: lokales LLM + KIWIX-RAG + Conversation-Memory + Tool-Calling. Single-User, Sideload-only über GitHub Releases. Persönlichkeit angelehnt an Songbird (So-Mi) aus Cyberpunk 2077: Phantom Liberty.

> Vollständige Architektur und Phasenplan: siehe **[SPEC.md](SPEC.md)**.
> Repo-Konventionen für Claude-Sessions: **[CLAUDE.md](CLAUDE.md)**.

---

## Status: Phase 1 (Skeleton + Pipeline)

Phase 1 produziert ein minimales "Hello-Songbird"-APK, das die komplette Build- und Release-Pipeline beweist. Phase 2 (lokales LLM + Chat-UI) baut darauf auf.

## Setup (10 Minuten)

### Voraussetzungen

- Lokale Toolchain für **Phase 0**: `git`, `keytool` (kommt mit JDK 17/21), `bash`.
- Für **CI**: nur ein GitHub-Repo. Pipeline läuft auf `ubuntu-latest`.
- Für **Sideload-Tests**: `adb` und ein Android-14+-Gerät (Ziel: HONOR Magic V2).
- Optional: GitHub CLI `gh` (für `scripts/install-dev.sh` ohne Auth-Flickerei).

### Phase 0 — einmalige manuelle Schritte

```bash
git clone <dein-repo>
cd so-mi

# 1. CI-Keystore generieren und committen.
#    Wichtig: dieser Keystore liegt BEWUSST im Klartext im Repo
#    (Sideload-only, Update-Kontinuität — siehe scripts/init-keystore.sh
#    und SPEC §5 für die Begründung).
bash scripts/init-keystore.sh
git commit -m "chore: add CI keystore (sideload-only)"

# 2. (Optional) ANTHROPIC_API_KEY als Repo-Secret hinterlegen,
#    falls du später bootstrap-soul.sh nutzen willst.
```

### Erster Push und Akzeptanztest

```bash
# Conventional-Commits-Nachricht — release-please reagiert nur auf feat/fix/perf.
git commit --allow-empty -m "feat: initial Phase 1 skeleton"
git push origin main
```

Was passiert:

1. `release-please-action` öffnet einen Release-PR. Mit `bump-minor-pre-major: false` (Default für Pre-1.0) bumpt der erste `feat:` von **0.1.0 → 0.2.0** (Titel: `chore(main): release 0.2.0`). Der PR aktualisiert `version.txt` und `CHANGELOG.md`.
2. Du **mergst** den Release-PR. Das tagged `v0.2.0` und triggert den `build`-Job.
3. Der Build-Job kompiliert `:app:assembleRelease` mit `versionCode = 10000 + GITHUB_RUN_NUMBER`, signiert mit dem CI-Keystore, prüft die Signatur per `apksigner`, und hängt das APK ans Release.

> **Versions-Bumping-Regel pre-1.0:** `feat:` → MINOR (0.1.0 → 0.2.0), `fix:` → PATCH (0.2.0 → 0.2.1). Falls du lieber `feat:` zu PATCH bumpen willst, setze in `.release-please-config.json` `"bump-minor-pre-major": true`.

Phase-1-Akzeptanz erfüllt, sobald:

- [ ] Push auf `main` produziert ein signiertes APK in **GitHub → Releases** (≤ 30 Min Pipeline-Laufzeit).
- [ ] APK installiert sich auf dem Magic V2 (per `adb install -r` oder `bash scripts/install-dev.sh`).
- [ ] Eine **Folge-Version** installiert sich **drüber**, ohne `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- [ ] App zeigt "So-Mi", den `versionName` und den `versionCode`.

## Lokal bauen (ohne Push)

```bash
cd android
./gradlew :app:assembleDebug                  # debug build, signiert mit Release-Keystore
./gradlew :app:assembleRelease \
  -PversionCode=10001 -PversionName=0.1.1     # release build mit expliziten Versionen
```

`-PversionCode` und `-PversionName` sind optional; ohne sie nimmt der Build `1` / `0.0.0-dev`.

## Verzeichnisstruktur

Vollständig in [SPEC.md §4](SPEC.md). Kurzfassung:

```
android/                       8-Modul-Gradle-Build (app + 7 core-*)
soul/soul.md                   Persönlichkeit — fester System-Prefix, niemals via RAG
knowledge/                     ZIMs, Notizen, Seeds für die RAG-Korpora
keystore/ci.keystore           CI-Signatur (committed, public — siehe SPEC §5)
scripts/                       init-keystore, download-models, install-dev, release, bootstrap-soul
.github/workflows/             build-and-release.yml (release-please + signed APK)
version.txt + CHANGELOG.md     release-please-managed
```

## Phasenplan

Siehe [SPEC.md §12](SPEC.md). Reihenfolge ist nicht verhandelbar — Phase 2 setzt den grünen Phase-1-Akzeptanztest voraus.

- **Phase 0** — Repo-Bootstrap (manuell, einmalig).
- **Phase 1** — Skeleton + Pipeline. *Hier sind wir.*
- **Phase 2** — Lokales LLM + Chat-UI.
- **Phase 3** — RAG + Persona-Memory.
- **Phase 4** — Tool-Layer (12 Tools).
- **Phase 5** — Voice + In-App-Update.
