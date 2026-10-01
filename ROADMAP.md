# ROADMAP — so-mi

**Single source of truth für Phasen-Stand, User-Vereinbarungen aus Sitzungen, und den nächsten Push.**

`SPEC.md` §12 ist der ursprüngliche Plan. Diese Datei hält IST-Stand und alle nicht-SPEC-User-Vereinbarungen fest.

**Pflege-Pflicht:** Jede Sitzung beginnt mit einem Blick hier. ROADMAP.md gewinnt bei Konflikten mit SPEC.md.

---

## Aktueller Stand (2026-10-01)

| Release | Stand | Inhalt |
|---------|-------|--------|
| v0.64.2 | ✅ **stable** | Glitch-Übergang in Brand-Farben (Obsidian-Hintergrund-Fix), Display-aus-Hinweis im Update-Banner |
| v0.64.1 | ✅ stable | Updater: App-Kill-Survival (SharedPrefs persist), cancelDownload, Paused(percent)-State, Timeout 3→10min |
| v0.64.0 | ✅ live | Updater: Pause/Resume/Reattach-Architektur (Basisimplementierung — API-Fix folgte in v0.64.1) |
| v0.63.4 | ✅ stable | @lexikon/@wörterbuch im Command-Dropdown (SlashCommandRegistry.AT_COMMANDS), TTS-Doppel-Speak-Fix (hasSpooken-Flag) |
| v0.63.3 | ✅ stable | KIWIX Wiktionary DE integriert: libkiwix 2.6.0, KiwixRepository, ZimCatalog (SHA256 verifiziert), ZimDownloadWorker, RAG-Inject, search_kiwix Tool, ZimCatalogScreen |
| v0.59.7 | ✅ stable | Piper TTS deaktiviert (Memory-Conflict mit llama.cpp), ForegroundService-Fix |
| v0.59.x | ✅ live | Piper TTS (sherpa-onnx), Stimmen-Auswahl, Crash-Fixes |
| v0.58.x | ✅ live | Piper TTS Integration, Offboarding Android-TTS-Slider |
| v0.57.x | ✅ live | TTS UX-Fixes, Stimme expressiver |
| v0.56.0 | ✅ stable | TTS: 🔊-Button an jeder Antwort + Auto-Vorlesen-Toggle |
| v0.55.9 | ✅ live | Updater Guard-Fix (0% Balken) |
| v0.55.8 | ✅ live | Done-Button nicht klickbar + FAQ Scan-Hinweis |
| v0.55.7 | ✅ live | Download hängt bei 97%: Channel-Bridge BroadcastReceiver + Polling |
| v0.55.x | ✅ live | Updater-Iterationen: Progress-Flow, Installer, Mic-Lag, Debounce |
| v0.54.x | ✅ live | Voice: SpeechRecognizer + Mikrofon-Button + Pre-warm |
| v0.53.x | ✅ live | OKF-Memory: Frontmatter, EntityExtractor, RelationIndex, Supersedes |
| v0.52.0 | ✅ stable | Manueller Update-Check in Einstellungen → Diagnose |
| v0.51.1 | ✅ stable | okhttp-Dep fix für UpdateChecker |
| v0.51.0 | ✅ stable | Greeting in aktiver Konversation + In-App-Update-Banner |
| v0.50.6 | ✅ live | KRITISCH: withContext(IO) Crash entfernt |
| v0.50.5 | ✅ live | Greeting bei App-Resume (onResume-Hook) |
| v0.50.3 | ✅ live | ANR/Performance behoben, /rename 3 Bugs, Greeting-Retry |
| v0.50.0 | ✅ live | Exchange-Rate-Fallback (Frankfurter), Tools gruppiert |
| v0.49.0 | ✅ stable | Uniforme Commands, Smart Autocomplete, #Kategorie Inline-Routing |
| v0.48.0 | ✅ stable | search_notes, save_note, summarize — 11 von 12 Tools |
| v0.47.1 | ✅ stable | Google Kalender unterstützt, Kalender-Name im Ergebnis |
| v0.47.0 | ✅ stable | Kalender-Integration: read_calendar + create_event |
| v0.46.12 | ✅ stable | Absturz behoben (shortService/FGS), deaktiviertes Tool zeigt Hinweis |
| v0.46.x | ✅ stable | Tool-System iterativ stabilisiert: Alarm, Wechselkurs, History-Filter |
| v0.45.x | ✅ stable | Wetter dynamisch, Erinnerungs-Kategorieabfrage, Custom-Kategorien Emoji-safe |
| v0.43–44 | ✅ stable | Tool-System Grundlage: ToolRouter, 8 Tools, per-Tool-Toggle |
| v0.42.1 | ✅ stable | Erinnerungs-Rückmeldung mit Kategorie, Backfill-Worker |
| v0.41.0 | ✅ stable | HNSW-Recall, Sliding-Window Gesprächskontext |
| v0.39.0 | ✅ stable | Backup mit Chat-Verlauf, Chat-Commands |
| v0.37.2 | ✅ stable | Multi-Chat vollständig |

---

## Phasen-Status gegen SPEC.md §12

### ✅ Phase 0–2 — Bootstrap, Pipeline, LLM + Chat
Komplett.

### ✅ Phase 3 — RAG + Persona-Memory (ABGESCHLOSSEN ohne KIWIX)

| Deliverable | Stand |
|---|---|
| Explicit-Trigger + Save | ✅ v0.14+ |
| Recall / RAG-Inject | ✅ v0.19.1 |
| Memory-Browser CRUD | ✅ v0.24.2+ |
| TopicClassifier Heuristik + LLM | ✅ v0.22.0 / v0.40.2 |
| Custom Kategorien + Keywords | ✅ v0.27.0+ |
| Backup Export + Import (inkl. DB) | ✅ v0.25.0 / v0.39.0 |
| Setup-Guard | ✅ v0.34.0 |
| FAQ | ✅ v0.35.0 |
| Multi-Chat | ✅ v0.37.0+ |
| HNSW-Recall + Backfill | ✅ v0.41.0 / v0.42.1 |
| KIWIX-AAR | ✅ v0.63.3 (libkiwix 2.6.0, Wiktionary DE) |

### 🟡 Phase 4 — Tools (11 von 12 implementiert, v0.43–v0.49)

| Tool | Stand |
|---|---|
| `get_weather` (Open-Meteo) | ✅ stabil — dynamische Datumsangaben |
| `search_web` (SearXNG) | ✅ stabil — 15s Timeout |
| `search_memory` (Keyword + Kategorie) | ✅ stabil — Custom-Kategorien, Emoji-safe |
| `set_alarm` (WorkManager) | ✅ stabil — Ton + Vibration |
| `get_exchange_rate` | ✅ stabil — Currency-Map |
| `news_briefing` (RSS) | ✅ stabil — Tagesschau, Spiegel, Heise |
| `read_calendar` (CalendarContract) | ✅ stabil — Google Kalender bevorzugt |
| `create_event` (CalendarContract) | ✅ stabil |
| `search_notes` / `save_note` | ✅ v0.48.0 |
| `summarize` | ✅ v0.48.0 |
| Uniforme Commands + Smart Autocomplete | ✅ v0.49.0 |
| `#Kategorie kw1,kw2` Inline-Routing | ✅ v0.49.0 |
| Settings → per-Tool-Toggle | ✅ v0.46.4 |
| Stage-2-Embedding | ❌ deferred — SharedMutex ONNX/llama.cpp nötig, Crash-Risiko |
| Settings → Tools UX (Gruppierung, Status) | ❌ v0.50.0 |
| GBNF Stage-3 Constrained Decoding | ❌ deferred |

### ✅ Phase 5 (nahezu vollständig)
- **UpdateChecker** ✅ — GitHub Releases API, Progress-Flow, Benachrichtigung für Install
- **Manueller Update-Check** ✅ — Settings → Diagnose
- **Voice-Input** ✅ v0.54.x — SpeechRecognizer, Pre-warm, Mikrofon-Button
- **TTS** ✅ v0.56.0 — 🔊-Button + Auto-Vorlesen, Android TTS, kein Modell-Download

---

## User-Vereinbarungen (bindend wie SPEC)

### Anzeige & UX
- **⚠️ TO BE IMPROVED — Scroll-to-Bottom bei Tastatur** *(2026-06-12)* — mehrere Ansätze auf MagicOS gescheitert. Neuer Ansatz: viewport-Größe als Keyboard-Signal (v0.50.2). Noch nicht verifiziert.
- **⚠️ TO BE IMPROVED — 14B/12B-Ampel-Farbe** *(2026-06-12)* — StatFs auf MagicOS gibt App-Quota statt physischen Speicher. Aufgeschoben.
- **Chat-Band** *(v0.33.0)* — 4 Typen, 5s Auto-Dismiss, WhatsApp-Style Pill
- **Slash-Command-Popup** *(v0.33.0)* — Autocomplete + /-Button

### Erinnerungen / RAG
- **Recall aktiv** *(v0.19.1)* — HNSW semantisch wenn Embedder aktiv, .md-Fallback sonst
- **Multi-Fakt + LLM-Classifier** *(v0.22–v0.40.2)* — "und"-Split, LLM-Klassifizierung + Regex-Fallback
- **Duplikat-Erkennung** *(v0.29.1)* — exakter Match + Levenshtein ≤ 2
- **Eigene Kategorien + Keywords** *(v0.27.0)* — per UI oder Slash-Command; Keywords in `.keywords.json`
- **Backfill-Worker** *(v0.42.1)* — re-embeddet ältere Fakten automatisch nach Embedder-Install
- **Kategorie-Abfrage** *(v0.45.0)* — `@erinnerung personen/familie/etc.` ruft Kategorie-Datei direkt ab

### Tools / Phase 4
- **Internet-Zugang via search_web** *(2026-06-13)* — SearXNG (kein Key), Datenschutz-Hinweis im Ergebnis
- **Tool-Modus** *(v0.44.0)* — Kompakt (Standard, stabil) vs. System-Prompt (experimentell, langsam)
- **Kein Web-Consent-Dialog** *(v0.44.2)* — Datenschutz-Hinweis im Ergebnisblock; kein blocking-Dialog
- **History bei Tool-Calls unterdrückt** *(v0.44.2)* — verhindert Vermischung von Gesprächsverlauf und Tool-Daten

### Modelle & Downloads
- **7B Q4_K_M Default** *(CLAUDE.md)*
- **14B Q3 + Q4 im Katalog** *(v0.15.1)* — qwen-research-Lizenz
- **Mistral-Nemo 12B Q3+Q4** *(v0.36.1)* — Apache 2.0, ungated, SHA verifiziert; LARGE_PLUS-Tier
- **⚠️ TO BE IMPROVED — 14B auf Magic V2** *(2026-06-12)* — crasht (KV-Cache + 9GB > 16GB). Aufgeschoben.
- **Wi-Fi-Gate für Downloads**; kein Auto-OOM-Fallback

### Prozess
- **Vollautomatisches Pushen** — PR + release-please ohne User-Klick
- **tl;dr + Test-Anweisung** nach jedem Push
- **Stable-Einstufung** nach positivem Test-Feedback
- **Workflow + Agents** bei parallelen Tasks

---

## Pipeline — nächste Sprints (Priorität absteigend)

## Pipeline — nächste Sprints (Priorität absteigend)

### v0.63.0–v0.63.4 — KIWIX Offline-Lexikon + Fixes
**✅ Live als v0.63.4 stable.** Vollständige KIWIX-Integration:
- `org.kiwix:libkiwix:2.6.0` (Maven Central, GPLv3, arm64-v8a, kein ONNX-Konflikt)
- `KiwixRepository` in core-rag: openZim/search/getEntry, single-thread Dispatcher + Mutex
- `KiwixAutoOpen` öffnet erstes installiertes ZIM beim Start (non-blocking launch{})
- `ZimCatalog.WIKTIONARY_DE`: wiktionary_de_all_nopic_2026-04.zim, 1.2 GB, SHA256 live verifiziert: `947e4f17...`
- `ZimDownloadWorker` in core-data: Resume, SHA-256-Verify, WorkManager KEEP-Policy
- `RagOrchestrator.recallForPrompt()`: Memory + KIWIX parallel via coroutineScope { async {} }
- `search_kiwix` Tool: @lexikon / @wörterbuch / "was bedeutet" Trigger — in SlashCommandRegistry.AT_COMMANDS unter Kategorie "@ Wissen"
- `ZimCatalogScreen`: Einstellungen → Modelle → "Offline-Lexikon verwalten"
- TTS-Doppel-Speak-Fix: `hasSpooken`-Flag in AssistantBubble (LaunchedEffect-Recompose-Bug)
- tools:replace="android:allowBackup" in AndroidManifest (libkiwix Manifest-Conflict)

### v0.64.0–v0.64.2 — Updater-Verbesserungen + Brand-Fixes
**✅ Live als v0.64.2 stable.**
- `resumeExistingDownload()`: DownloadManager-ID wird in SharedPrefs persistiert → App-Kill überlebt Download
- `cancelDownload()`: Download abbrechen (DownloadManager.pauseDownload ist keine public API)
- `DownloadState.Paused(percent)`: system-paused Zustand mit letztem bekannten Prozentsatz
- TIMEOUT_MS: 3 min → 10 min
- Hinweistext im Banner: "Display aus ist kein Problem"
- Glitch-Übergang-Fix: äußerer Box mit permanentem Obsidian-Hintergrund → kein weißes Durchscheinen
- fix_quotes.mjs / fix_quotes2.mjs: einmalige Skripte für typografische Anführungszeichen in Kotlin-Dateien wurden ausgeführt (Dateien bleiben uncommitted als Werkzeug-Artefakt)

### 🔴 Nächster Sprint: v0.65.0 — Agentic Planning Layer
**Vollständig designed, noch nicht implementiert.** Architektur aus Session 2026-07-07:

**Neue Dateien:**
- `android/core-tools/src/main/kotlin/io/somi/tools/planning/QueryPlan.kt`
- `android/core-tools/src/main/kotlin/io/somi/tools/planning/QueryPlanner.kt`
- `android/core-common/src/main/kotlin/io/somi/common/llm/ChunkBoundary.kt`
- `android/core-rag/src/main/kotlin/io/somi/rag/SessionKnowledgeCache.kt`

**Geänderte Dateien:**
- `ChatViewModel.kt` — runGeneration() bekommt QueryPlan, plan-adaptive maxTokens
- `RagOrchestrator.kt` — recallForPrompt() plan-aware, schreibt in SessionKnowledgeCache
- `GenerationStream` — hasContinuation + continuationPrompt für Chunking
- `MainActivity.kt` — "▸ Weiter [Teil 2]"-Button

**Was QueryPlanner macht (kein LLM-Call, <1ms):**
- LONG_RESPONSE_MODE: "erkläre", "beschreibe", >12 Wörter → maxTokens 512→1024 + Chunk-Boundary
- KIWIX_FIRST: "was bedeutet", "Definition", "@lexikon" → KIWIX erzwingen
- KIWIX_SKIP: Begrüßungen, Meinungsfragen → KIWIX überspringen (spart 200–800ms)
- MEMORY_INJECT: <5 Wörter ohne Tool-Pattern → Memory-Recall erzwingen
- SYNTHESIS_MODE: LONG + KIWIX_FIRST → bis zu 5 KIWIX-Einträge statt 3
- FACT_CHECK_HINT: "ist es wahr", "wann war" → Vorbehalt-Satz vor Antwort
- CONVERSATIONAL: "hi/hallo/hey" → maxTokens halbiert (256), kein KIWIX

**SessionKnowledgeCache:**
- In-Memory (Hilt @Singleton), nicht persistent
- Max 5 Einträge × 300 Zeichen (LRU)
- Wenn KIWIX "Infinitiv" nachgeschlagen → nächste Frage "bilde Sätze im Infinitiv" nutzt Cache statt neuem KIWIX-Call

**Evidenz-Chips:**
- Antworten mit KIWIX/Memory-Quellen bekommen Tags unter der Bubble: [Quelle: Wiktionary: X]

### 🔴 KIWIX Phase 2 — Volltextsuche (nach v0.65.0)
Aktuell nur `SuggestionSearcher` (Titel-Prefix). Xapian-FTS-Searcher (`org.kiwix.libzim.Searcher`) würde "Zeitwort" → "Verb" finden (Volltext statt Titel-Match).

### ❌ Aufgeschoben — Piper TTS Neuimplementierung
Memory-Conflict (sherpa-onnx + ggml teilen native Arenen → SIGSEGV). Android TTS als stabiler Platzhalter. Neuimplementierung mit Prozess-Isolation aufgeschoben bis nach Agentic Layer.

### ❌ Aufgeschoben — So-Mi Originalstimme (Cyberpunk 2077)
**User-Vereinbarung 2026-07-05** — Feature aufgeschoben bis Audiomaterial vorliegt.

---

## Wie diese Datei zu pflegen ist

- **Jede Sitzung:** ROADMAP zuerst lesen, dann SPEC.md wenn nötig
- **Neue Vereinbarungen:** sofort eintragen mit Datum
- **Nach Release:** "Aktueller Stand" aktualisieren, Pipeline bereinigen
- **Plan-Agents:** bekommen ROADMAP als Kontext, nicht nur SPEC
