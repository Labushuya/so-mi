# Changelog

## [0.16.4](https://github.com/Labushuya/so-mi/compare/v0.16.3...v0.16.4) (2026-06-09)


### Bug Fixes

* App-Crash — AnimatedContent entfernt, fehlende compose-animation-Dep verursacht ClassNotFoundException ([#39](https://github.com/Labushuya/so-mi/issues/39)) ([8a8082f](https://github.com/Labushuya/so-mi/commit/8a8082facf28ec9c55f76da562c39b5779091eb4))

## [0.16.3](https://github.com/Labushuya/so-mi/compare/v0.16.2...v0.16.3) (2026-06-09)


### Bug Fixes

* App-Crash — LazyListState.Saver ist internal, durch rememberLazyListState() ersetzt ([#37](https://github.com/Labushuya/so-mi/issues/37)) ([2985fd5](https://github.com/Labushuya/so-mi/commit/2985fd5d2f8cecaa42a9edbc32d059f4b3a2eebe))

## [0.16.2](https://github.com/Labushuya/so-mi/compare/v0.16.1...v0.16.2) (2026-06-09)


### Bug Fixes

* build — typografische Anführungszeichen in Kotlin-Strings ersetzt ([#35](https://github.com/Labushuya/so-mi/issues/35)) ([b83cd83](https://github.com/Labushuya/so-mi/commit/b83cd839d0ad7f0defe4fcc338f9642e95f6bb64))

## [0.16.1](https://github.com/Labushuya/so-mi/compare/v0.16.0...v0.16.1) (2026-06-09)


### Bug Fixes

* typografische Anführungszeichen in SettingsScreen durch ASCII ersetzt ([4183e22](https://github.com/Labushuya/so-mi/commit/4183e22a803eb866a12b85d074967a03217c87c9))
* typografische Anführungszeichen in SettingsScreen durch ASCII ersetzt ([eabbfea](https://github.com/Labushuya/so-mi/commit/eabbfea8464e344de02a8ee28d26d9145d792bef))

## [0.16.0](https://github.com/Labushuya/so-mi/compare/v0.15.1...v0.16.0) (2026-06-09)


### Features

* WLAN-Toggle, Katalog-Download-UI, Ladeüberblendung und 14B-Freischaltung (v0.16.0) ([f502b7d](https://github.com/Labushuya/so-mi/commit/f502b7dfaeac068685ca547f53f371c84a16f855))
* WLAN-Toggle, Katalog-Download-UI, Ladeüberblendung und 14B-Freischaltung (v0.16.0) ([5c2395b](https://github.com/Labushuya/so-mi/commit/5c2395b3d2715e6990599d6e467415415e94e354))

## [0.15.1](https://github.com/Labushuya/so-mi/compare/v0.15.0...v0.15.1) (2026-06-08)


### Bug Fixes

* build — embedder-worker-name aus rag-fassade exposen statt direkt referenzieren ([340698a](https://github.com/Labushuya/so-mi/commit/340698a3fc98b0711550d40e770018c5b74f1b8f))
* build v0.15.0-Compile-Fehler ([30374a9](https://github.com/Labushuya/so-mi/commit/30374a9c93d9c5df30bf6ca15003f82dad514799))

## [0.15.0](https://github.com/Labushuya/so-mi/compare/v0.14.3...v0.15.0) (2026-06-08)


### Features

* 14B-Modelle Q3 und Q4 als Staging im Modell-Katalog (SHA-Verify in v0.15.1) ([a798233](https://github.com/Labushuya/so-mi/commit/a798233146335e722390639b9ffb710944ce392d))
* alle Daten unter sichtbarem SoMi/-Verzeichnis bündeln ([b748d1b](https://github.com/Labushuya/so-mi/commit/b748d1bc3f98b09b9100040ec07d23fbb7bcbdab))
* Begrüßung beim App-Start, Downloads-Sichtbarkeit und Modell-Wechsel im laufenden Betrieb ([1b83d51](https://github.com/Labushuya/so-mi/commit/1b83d516837bf83b39247e1bf90c2f0450ca2ec9))
* Embedder-Mirror auf GitHub-Release als Failover, mit App-internem Asset-Pfad ([b865c4e](https://github.com/Labushuya/so-mi/commit/b865c4e48767aeb0798fbac121830cc362b74745))
* in-App Daten-Browser unter Einstellungen, zeigt SoMi/-Verzeichnis und teilt Dateien ([d07e711](https://github.com/Labushuya/so-mi/commit/d07e711fbc91c8429a184753241898c9675c82e5))
* Vollbild-Modus mit Toggle in Einstellungen, Notch und Tastatur sauber respektiert ([fe5dfa2](https://github.com/Labushuya/so-mi/commit/fe5dfa2f2a81216bee3e132b53d9d5c4d968a283))

## [0.14.3](https://github.com/Labushuya/so-mi/compare/v0.14.2...v0.14.3) (2026-06-08)


### Bug Fixes

* erinnerungs-modell wird endlich heruntergeladen, tastatur-spacing endgültig weg ([cd5cdb0](https://github.com/Labushuya/so-mi/commit/cd5cdb0b29771079d7e2f70570096b9398e059ff))

## [0.14.2](https://github.com/Labushuya/so-mi/compare/v0.14.1...v0.14.2) (2026-06-08)


### Bug Fixes

* build — ObjectBox-Task vom Configuration-Cache ausgenommen ([7080eb5](https://github.com/Labushuya/so-mi/commit/7080eb53979a669322607a14277d8bbf46300bef))

## [0.14.1](https://github.com/Labushuya/so-mi/compare/v0.14.0...v0.14.1) (2026-06-08)


### Bug Fixes

* build — objectbox-plugin korrekt verkabelt ([8d83786](https://github.com/Labushuya/so-mi/commit/8d83786be712afa33d64f23b577ade5c0965a2bb))

## [0.14.0](https://github.com/Labushuya/so-mi/compare/v0.13.0...v0.14.0) (2026-06-08)


### Features

* so-mi merkt sich was — 'merk dir' speichert fakten persistent ([e260018](https://github.com/Labushuya/so-mi/commit/e260018af04e4090229be3872d31475b7137c1ce))
* speicher-fundament gelegt — datenbank für so-mi's gedächtnis ist da, noch ohne sichtbare funktion ([363950e](https://github.com/Labushuya/so-mi/commit/363950ebf1e6ea77809db14417c654cede2a053a))

## [0.13.0](https://github.com/Labushuya/so-mi/compare/v0.12.1...v0.13.0) (2026-06-08)


### Features

* vier User-Bugs behoben — Erst-Setup-Loop, Tastatur-Spacing, System-Bars und Lade-Animation aufgehübscht ([b03ac75](https://github.com/Labushuya/so-mi/commit/b03ac754c86d27e33271744efc41bab3a39b4e07))

## [0.12.1](https://github.com/Labushuya/so-mi/compare/v0.12.0...v0.12.1) (2026-06-08)


### Bug Fixes

* build — release-compile-fehler durch eindeutige JNI-funktion behoben ([7dd6594](https://github.com/Labushuya/so-mi/commit/7dd6594b4eeaaec16689a911b78837de707bf64c))

## [0.12.0](https://github.com/Labushuya/so-mi/compare/v0.11.3...v0.12.0) (2026-06-08)


### Features

* einstellungen-refaktorierung — kategorisiert, songbird-stil, persönlichkeit editierbar, modell-verhalten regelbar ([5603ddb](https://github.com/Labushuya/so-mi/commit/5603ddb303be06ecef1c82aacae935c1206bd23d))

## [0.11.3](https://github.com/Labushuya/so-mi/compare/v0.11.2...v0.11.3) (2026-06-07)


### Bug Fixes

* build — logging.h-Include-Pfad nach lokaler ai_chat.cpp-Kopie ergänzt ([4137980](https://github.com/Labushuya/so-mi/commit/413798007f14663b845fed132231b0ee62d63b96))

## [0.11.2](https://github.com/Labushuya/so-mi/compare/v0.11.1...v0.11.2) (2026-06-07)


### Bug Fixes

* stabilität-refaktorierung — freeze beim aufwärmen, mobile-daten-download, init-flicker, kvaesitso-icon ([e92c2bf](https://github.com/Labushuya/so-mi/commit/e92c2bf14550f57570efb2f9f263c50edb1add44))

## [0.11.1](https://github.com/Labushuya/so-mi/compare/v0.11.0...v0.11.1) (2026-06-07)


### Bug Fixes

* vollständige refaktorierung — modell-erkennung, composer, icon und löschen funktionieren wieder ([58b891e](https://github.com/Labushuya/so-mi/commit/58b891e4201763d1b52f6aba39d1c6de8bfc8022))

## [0.11.0](https://github.com/Labushuya/so-mi/compare/v0.10.1...v0.11.0) (2026-06-07)


### Features

* speicher-screen — alle modell-kopien sichtbar, duplikate löschbar ([b1f8152](https://github.com/Labushuya/so-mi/commit/b1f8152773bbb79378dc2da26d1bbe0c80652166))


### Bug Fixes

* app sucht modell in allen historischen pfaden statt erneuten 4-gb-download zu fordern ([3a0b627](https://github.com/Labushuya/so-mi/commit/3a0b6277d8ed1527c88749c6da176d3ad2e8cfb9))

## [0.10.2] (2026-06-07)


### Bug Fixes

* **App fragt nicht mehr nach erneutem 4-GB-Download wenn das Modell schon da ist.** Die App durchsucht jetzt alle historischen Storage-Pfade (v0.7.x externalFilesDir, v0.10.0 Documents, internal fallback) und nutzt das Modell wo immer es liegt — kein Migrate, kein Copy.

## [0.10.1](https://github.com/Labushuya/so-mi/compare/v0.10.0...v0.10.1) (2026-06-07)


### Bug Fixes

* App startet wieder — der `/sdcard/Documents/`-Modell-Pfad aus 0.10.0 brauchte eine Permission die nicht da war. Pfad zurück auf app-private external storage; Modell-Datei bleibt nach Update erhalten, geht bei Uninstall verloren.
* App-Icon-Setup massiv vereinfacht — alle adaptive-icon-Spezifika entfernt, jetzt nur ein PNG pro Density mit Obsidian-Hintergrund. Wenn das Icon trotzdem nicht im Drawer erscheint: App force-stoppen oder Phone neu starten (MagicOS-Launcher-Cache).
* rollback v0.10.0 storage + icon disasters ([cfecb99](https://github.com/Labushuya/so-mi/commit/cfecb9948b9a8b2ed48040e7befaee31275cb7bc))

## [0.10.0](https://github.com/Labushuya/so-mi/compare/v0.9.1...v0.10.0) (2026-06-07)

> ⚠️ **Diese Version startet auf manchen Geräten nicht.** Nutze v0.10.1 (Rollback).

### Features

* Chat-Verlauf bleibt nach App-Neustart erhalten (Room-DB unter `filesDir/somi.db`).
* **Chat-Persistenz + Icon-Hintergrund:** Chat-History wird in Room-DB persistiert; App-Icon-Hintergrund von weiß auf Obsidian-Schwarz umgestellt ([c5afa9d](https://github.com/Labushuya/so-mi/commit/c5afa9df1bde0c82b7e0aee537d9c63f5aabf1c5))

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

* **Erste lauffähige Chat-Session:** End-to-end-Chat — Erst-Start-Flow mit Modell-Picker, soul.md als System-Prompt, Token-für-Token live-tippende Antworten ([39d2942](https://github.com/Labushuya/so-mi/commit/39d294200cf90076e20248c12cdd2ce38bb1e9ef))

## [0.7.0](https://github.com/Labushuya/so-mi/compare/v0.6.0...v0.7.0) (2026-06-06)


### Features

* **Modell-Download-Manager:** WorkManager + OkHttp mit Resume-Support und SHA-256-Verifikation für GGUF-Downloads ([71ead78](https://github.com/Labushuya/so-mi/commit/71ead78c29d61549c1a2a92d6afe21ef823a57f0))

## [0.6.0](https://github.com/Labushuya/so-mi/compare/v0.5.0...v0.6.0) (2026-06-06)


### Features

* **llama.cpp eingebaut:** Native Inferenz-Engine als Submodul vendored, libai-chat.so per NDK r27 für arm64 gebaut ([2fe5274](https://github.com/Labushuya/so-mi/commit/2fe527488028851b9a7555274af60c362fab6e4f))

## [0.5.0](https://github.com/Labushuya/so-mi/compare/v0.4.0...v0.5.0) (2026-06-06)


### Features

* **Hardware-Erkennung + Modell-Empfehlung:** App erkennt RAM, Speicher und GPU des Geräts, schlägt passendes Modell-Tier vor (Tiny/Small/Medium/Large) ([6f266d0](https://github.com/Labushuya/so-mi/commit/6f266d085f1bf0fda1dca87d293c10869ceb0da3))

## [0.4.0](https://github.com/Labushuya/so-mi/compare/v0.3.1...v0.4.0) (2026-06-06)


### Features

* **Hilt-Grundgerüst + Chat-ViewModel:** Dependency-Injection-Framework eingebaut, ChatViewModel und Modul-Verkabelung als Basis für die folgenden Inkremente ([d190177](https://github.com/Labushuya/so-mi/commit/d190177cfd1d8c325f48ac3e1940559304005ebd))

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
