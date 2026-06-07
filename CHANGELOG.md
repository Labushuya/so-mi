# Changelog

## [0.10.1](https://github.com/Labushuya/so-mi/compare/v0.10.0...v0.10.1) (2026-06-07)


### Bug Fixes

* App startet wieder — der `/sdcard/Documents/`-Modell-Pfad aus 0.10.0 brauchte eine Permission die nicht da war. Pfad zurück auf app-private external storage; Modell-Datei bleibt nach Update erhalten, geht bei Uninstall verloren.
* App-Icon-Setup massiv vereinfacht — alle adaptive-icon-Spezifika entfernt, jetzt nur ein PNG pro Density mit Obsidian-Hintergrund. Wenn das Icon trotzdem nicht im Drawer erscheint: App force-stoppen oder Phone neu starten (MagicOS-Launcher-Cache).
* rollback v0.10.0 storage + icon disasters ([cfecb99](https://github.com/Labushuya/so-mi/commit/cfecb9948b9a8b2ed48040e7befaee31275cb7bc))

## [0.10.0](https://github.com/Labushuya/so-mi/compare/v0.9.1...v0.10.0) (2026-06-07)

> ⚠️ **Diese Version startet auf manchen Geräten nicht.** Nutze v0.10.1 (Rollback).

### Features

* Chat-Verlauf bleibt nach App-Neustart erhalten (Room-DB unter `filesDir/somi.db`).
* **phase-3a:** chat persistence in Room + icon background fix ([c5afa9d](https://github.com/Labushuya/so-mi/commit/c5afa9df1bde0c82b7e0aee537d9c63f5aabf1c5))

### ⚠ Bekannte Probleme

* Modell-Pfad auf `/sdcard/Documents/SoMi-Models/` umgestellt — funktioniert ohne MANAGE_EXTERNAL_STORAGE-Permission nicht. App wirft "Modell konnte nicht geladen werden". Behoben in 0.10.1.
* App-Icon weiterhin nicht im Drawer sichtbar trotz adaptive-icon-Inset-Versuch. Behoben in 0.10.1.

## [0.9.1](https://github.com/Labushuya/so-mi/compare/v0.9.0...v0.9.1) (2026-06-07)


### Bug Fixes

* So-Mi nennt User korrekt beim Vornamen statt "Songbird" — soul.md gekürzt + Aliase explizit erklärt.
* App stirbt nicht mehr nach erster Antwort (neue Foreground-Service `LlamaSessionService` pinned den Process bei MagicOS).
* Adaptive-Icon `<monochrome>`-Layer entfernt — Material-You-Themed-Icons-Modus zeigte den Songbird-Outline auf hellem Hintergrund (sah weiß aus).
* persona/branding/lifecycle bundle (icon bg, condensed soul, FGS pin) ([2b3bd44](https://github.com/Labushuya/so-mi/commit/2b3bd447fd89989fe256a3d66f2984854ebfdb2d))

## [0.9.0](https://github.com/Labushuya/so-mi/compare/v0.8.1...v0.9.0) (2026-06-07)


### Features

* Brand-Icon: Crimson-Eye-im-Octagon-Design für Launcher + Notifications.
* **branding:** Songbird launcher icon + notification icon ([532a60c](https://github.com/Labushuya/so-mi/commit/532a60c661c1dfc86ac06829172cbaf497857796))


### Bug Fixes

* Modell-Lade-Hang behoben — `setSystemPrompt(soul.md)`-Prefill auf 600 Zeichen begrenzt (volle 4 KB ergaben 5–15 Min Hang auf CPU 7B).
* Modelle landen unter `/sdcard/Documents/SoMi-Models/` — überleben App-Daten-Löschen + Uninstall (siehe v0.10.0 für Probleme).
* 3-Min-Timeout um Lade-Pfad — bei Hang erscheint Error-Banner statt unendlicher Spinner.
* **load:** drop warm-pass + truncate system prompt + persistent model storage ([79b9325](https://github.com/Labushuya/so-mi/commit/79b9325ea87cf64ac8991286ca34f67cad3d3712))

## [0.8.1](https://github.com/Labushuya/so-mi/compare/v0.8.0...v0.8.1) (2026-06-07)


### Bug Fixes

* **download:** rescue stuck-after-download via disk-truth-first + polling fallback ([0bc6b65](https://github.com/Labushuya/so-mi/commit/0bc6b65c4f4f819d5aafdee5e5a2abf9a51d4dcc))

## [0.8.0](https://github.com/Labushuya/so-mi/compare/v0.7.0...v0.8.0) (2026-06-06)


### Features

* **phase-2.5-2.7:** end-to-end chat — first-launch flow + soul.md prefix + live-typing chat ([39d2942](https://github.com/Labushuya/so-mi/commit/39d294200cf90076e20248c12cdd2ce38bb1e9ef))

## [0.7.0](https://github.com/Labushuya/so-mi/compare/v0.6.0...v0.7.0) (2026-06-06)


### Features

* **phase-2.4:** model download manager (WorkManager + OkHttp + resumable + sha256) ([71ead78](https://github.com/Labushuya/so-mi/commit/71ead78c29d61549c1a2a92d6afe21ef823a57f0))

## [0.6.0](https://github.com/Labushuya/so-mi/compare/v0.5.0...v0.6.0) (2026-06-06)


### Features

* **phase-2.3:** vendor llama.cpp + native libai-chat.so ([2fe5274](https://github.com/Labushuya/so-mi/commit/2fe527488028851b9a7555274af60c362fab6e4f))

## [0.5.0](https://github.com/Labushuya/so-mi/compare/v0.4.0...v0.5.0) (2026-06-06)


### Features

* **phase-2.2:** hardware detection + recommendModelTier per SPEC §7 ([6f266d0](https://github.com/Labushuya/so-mi/commit/6f266d085f1bf0fda1dca87d293c10869ceb0da3))

## [0.4.0](https://github.com/Labushuya/so-mi/compare/v0.3.1...v0.4.0) (2026-06-06)


### Features

* **phase-2.1:** hilt skeleton + ChatViewModel + module wiring ([d190177](https://github.com/Labushuya/so-mi/commit/d190177cfd1d8c325f48ac3e1940559304005ebd))

## [0.3.1](https://github.com/Labushuya/so-mi/compare/v0.3.0...v0.3.1) (2026-06-06)


### Bug Fixes

* **ci:** mark gradlew + scripts/*.sh executable in git index ([360c742](https://github.com/Labushuya/so-mi/commit/360c7421d38036be9aedc30c4529b821ec331b8d))

## [0.3.0](https://github.com/Labushuya/so-mi/compare/v0.2.0...v0.3.0) (2026-06-06)


### Features

* songbird rebrand + chat shell port from odysseus ([4daf15a](https://github.com/Labushuya/so-mi/commit/4daf15a504dcd48e79aea0ea251bfae89912ddfe))

## [0.2.0](https://github.com/Labushuya/so-mi/compare/v0.1.0...v0.2.0) (2026-06-06)


### Features

* initial Phase 1 skeleton (build pipeline + hello-world APK) ([a039453](https://github.com/Labushuya/so-mi/commit/a039453f4bfb77201f0e0f8c5f5f2908b43ed003))

## Changelog
