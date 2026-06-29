# So-Mi — Offline-First Android-Assistentin

> Eine persönliche KI-Assistentin für Android. Läuft vollständig lokal — kein Account, keine Cloud, keine Telemetrie. Persönlichkeit angelehnt an Songbird (So-Mi) aus Cyberpunk 2077: Phantom Liberty.

**Aktuell: v0.47.1 · Phase 3 abgeschlossen · Phase 4 aktiv**

---

## Was ist So-Mi?

So-Mi ist eine offline-first Android-App die ein lokales Sprachmodell (LLM) direkt auf deinem Gerät ausführt. Alles bleibt auf dem Handy — Gespräche, Erinnerungen, Persönlichkeit. Keine Nutzerdaten verlassen das Gerät, außer du fragst explizit nach Online-Tools wie Web-Suche oder Wetter.

**Zielgerät:** HONOR Magic V2 (16 GB RAM) · **Verteilung:** Sideload via GitHub Releases · **Keine Play-Store-Veröffentlichung geplant.**

---

## Funktionsumfang (v0.47.1)

### Konversation & Persönlichkeit
- Lokales LLM via ARM InferenceEngine (llama.cpp, NDK r27, arm64-v8a)
- Standardmodell: Qwen2.5 7B Q4\_K\_M · weitere Modelle wählbar (bis 12B)
- Persönlichkeit ausschließlich aus `soul/soul.md` — fest als System-Prefix, nie via RAG überschreibbar
- Glitch-Shader-Effekte, immersiver Vollbild-Modus

### Gedächtnis & RAG
- **Erinnerungen speichern:** `"Merk dir, ich liebe Kaffee"` → gespeichert + kategorisiert
- **LLM-Klassifizierer:** nach dem Speichern klassifiziert das Modell den Fakt (Personen / Vorlieben / Termine / Technik / Notizen)
- **HNSW-Vektorsuche:** semantisches Recall via ObjectBox wenn Embedder aktiv; Keyword-Fallback sonst
- **Backfill-Worker:** re-embeddet ältere Fakten automatisch nach Embedder-Installation
- **Kategorien:** eigene Kategorien mit Keywords, Emoji-safe, per UI oder Slash-Command
- Embedder: paraphrase-multilingual-MiniLM-L12-v2 (~470 MB, optional)

### Tool-System (Phase 4)
So-Mi kann Werkzeuge nutzen — sie entscheidet selbst wann ein Tool passt:

| Tool | Beschreibung | Trigger-Beispiele |
|------|-------------|-------------------|
| `get_weather` | Open-Meteo (kein API-Key) | "Wetter morgen in Berlin", "Wetter am Wochenende" |
| `search_web` | SearXNG (kein API-Key) | "@web EU AI Act aktuell" |
| `search_memory` | Eigene Erinnerungen | "@erinnerung Familie", "Was weißt du über mich?" |
| `set_alarm` | Android AlarmManager | "Erinner mich in 20 Minuten" |
| `get_exchange_rate` | Echtzeit-Wechselkurse | "Wie viel sind 100 EUR in USD?" |
| `news_briefing` | RSS-Feeds (Tagesschau, Spiegel, Heise) | "@news", "Aktuelle Nachrichten" |
| `read_calendar` | Google Kalender & Systemkalender | "Zeig meine Termine diese Woche" |
| `create_event` | Kalendertermin anlegen | "Erstelle Termin: Meeting morgen 14 Uhr" |

Jedes Tool ist einzeln de-/aktivierbar in **Settings → So-Mi → Tool-Modus**.

### Chat & UI
- Multi-Chat: mehrere Gespräche anlegen, umbenennen, archivieren
- Slash-Commands: `/search`, `/rename`, `/clear`, `/archive` + Autocomplete-Popup
- Gesprächskontext: letzte 14 Nachrichten als Sliding-Window
- Chat-Band für Status-Hinweise (Error / Warning / Success / Info)

### Backup & Daten
- Backup: Erinnerungen, Persönlichkeit, Chat-Verlauf, Einstellungen → ZIP
- Import mit Bestätigungs-Dialog
- Data-Browser: alle App-Dateien direkt einsehbar
- Einstellungen exportierbar

---

## Technologie-Stack

| Komponente | Technologie |
|-----------|-------------|
| LLM-Engine | llama.cpp via ARM InferenceEngine (vendored, com.arm.aichat) |
| Standardmodell | Qwen2.5 7B Q4_K_M (GGUF) |
| Embedding | paraphrase-multilingual-MiniLM-L12-v2 (ONNX Runtime 1.18) |
| Vektordatenbank | ObjectBox 4.0.3 mit HNSW-Index (384 Dimensionen, Cosine) |
| Chat-Persistenz | Room 2.7 (SQLite, WAL-Mode) |
| UI | Jetpack Compose, Material3, Glitch-Shader |
| DI | Hilt 2.52 |
| Build | AGP 8.5.2, Kotlin 2.0.21, NDK r27 |
| Verteilung | release-please → signierte APK → GitHub Releases |

---

## Architektur

```
android/
├── app/                   Compose UI, Navigation, ViewModels, Hilt-Wiring
├── core-llm/              LlamaContext Interface
├── core-llm-llama/        ARM InferenceEngine JNI-Wrapper (NDK, llama.cpp)
├── core-rag/              ObjectBox HNSW, ONNX Embedder, RagOrchestrator
├── core-tools/            Tool-System: Router (Regex+Embedding), 8 Tools
├── core-data/             Room, DataStore, BackupManager, StorageRoots
├── core-ui/               ChatViewModel, Slash-Commands, RAG-Integration
├── core-common/           Shared Interfaces (TextEmbedder, MemorySearchPort, LlmCaller)
soul/soul.md               Persönlichkeit — einzige Quelle, nie via RAG
keystore/ci.keystore       CI-Signatur (im Klartext committed — Sideload-only, Update-Kontinuität)
```

**Modul-Regel:** `core-*` Module dürfen nur `core-common` importieren. Cross-Modul-Abhängigkeiten gehen über Interfaces in `core-common` oder durch `app/`-Wiring.

---

## Datenschutz & Sicherheit

- **Offline-first:** alle KI-Verarbeitung lokal, keine Nutzerdaten an externe Server
- **Optional-Online:** Web-Suche, Wetter, Wechselkurs verlassen das Gerät — mit Hinweis im Ergebnis
- **Kein API-Key erforderlich** für Basisfunktionen
- **Keine Telemetrie, kein Analytics, kein Crashlytics**
- Claude API optional: wenn hinterlegt, hinter BiometricPrompt in EncryptedSharedPreferences

---

## Installation

1. [**GitHub Releases**](https://github.com/Labushuya/so-mi/releases/latest) → `app-release.apk` herunterladen
2. Auf Android: *Unbekannte Quellen erlauben* → APK tippen → installieren
3. Beim ersten Start: Benachrichtigungs- und Kalender-Berechtigung erlauben (optional)
4. Modell herunterladen: Settings → Modelle → Qwen2.5 7B (Standard, ~4.5 GB, WLAN empfohlen)

**Updates** installieren sich über die vorherige Version (gleicher Signing-Key, gleiche `applicationId`).

---

## Lokal bauen

```bash
git clone https://github.com/Labushuya/so-mi
cd so-mi/android

# Android SDK-Pfad setzen
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# Debug-Build
./gradlew :app:assembleDebug

# Release-Build (versionCode muss höher als installierte Version sein)
./gradlew :app:assembleRelease -PversionCode=99999 -PversionName=local
```

Der Release-Build verwendet `keystore/ci.keystore` mit dem öffentlichen Passwort `ci-password-public`. Das ist bewusst so — Sideload-only, Update-Kontinuität ist wichtiger als Signing-Geheimhaltung.

---

## Roadmap

| Phase | Stand |
|-------|-------|
| Phase 0–2: Bootstrap, Pipeline, LLM + Chat | ✅ Abgeschlossen |
| Phase 3: RAG + Persona-Memory | ✅ Abgeschlossen |
| Phase 4: Tool-System | 🟡 8 von 12 Tools implementiert |
| Phase 5: Voice + In-App-Updater | ❌ Geplant |

Detaillierter Fortschritt: **[ROADMAP.md](ROADMAP.md)**

---

*So-Mi ist ein persönliches Sideload-Projekt. Kein Play-Store, kein Support-Versprechen.*
