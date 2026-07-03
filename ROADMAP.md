# ROADMAP — so-mi

**Single source of truth für Phasen-Stand, User-Vereinbarungen aus Sitzungen, und den nächsten Push.**

`SPEC.md` §12 ist der ursprüngliche Plan. Diese Datei hält IST-Stand und alle nicht-SPEC-User-Vereinbarungen fest.

**Pflege-Pflicht:** Jede Sitzung beginnt mit einem Blick hier. ROADMAP.md gewinnt bei Konflikten mit SPEC.md.

---

## Aktueller Stand (2026-07-03)

| Release | Stand | Inhalt |
|---------|-------|--------|
| v0.53.0 | ✅ live | OKF-Memory: YAML-Frontmatter, EntityExtractor, RelationIndex, 1-Hop Recall, Supersedes |
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
| KIWIX-AAR | ❌ verschoben auf v1.0 |

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

### ✅ Phase 5 (teilweise) — In-App-Updater
- **UpdateChecker** ✅ v0.51.1 — GitHub Releases API, semantisches Versions-Vergleich, dismissbares Banner
- **Manueller Update-Check** ✅ v0.52.0 — Settings → Diagnose → "Jetzt prüfen" mit Throttle + Fehlerstate
- **Updater-Fix** ✅ v0.53.1 — DownloadManager statt Browser, REQUEST_INSTALL_PACKAGES, sichtbarer Install-Button
- **Voice** 🟡 v0.54.0 — SpeechRecognizer (de-DE), Mikrofon-Button im Composer

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

### v0.53.0 — OKF-Memory (Agentic Memory Flywheel)
**Grundlage:** So-Mi speichert Fakten als Flat-Markdown. OKF ergänzt strukturierte YAML-Frontmatter + verlinkte Entitäten.

Kernkonzept (Patrick / Agentic Memory Flywheel):
- Jede gespeicherte Information bekommt `type`, `title`, `tags`, `relations` im YAML-Header
- Beim Speichern: LLM extrahiert Entitäten + Beziehungen aus dem Fakt automatisch (1-2s overhead)
- Verlinkte Entitäten: `[Christopher](/personen/christopher.md)` statt freier Text
- Beim Recall: Nicht nur semantische Suche, sondern auch Graph-Traversal (verlinkten Entitäten folgen)
- "Supersedes"-Semantik: Widersprüche erkennen → altes Fact als `superseded_by` markieren, neues anlegen

Deliverables:
1. `extractEntitiesFromFact()` im RagOrchestrator — LLM-basiert, 1-Pass, gibt `{entities, relations}` zurück
2. Frontmatter-Writer: beim Speichern YAML-Header in .md einfügen
3. Relation-Index: `_index.json` pro Kategorie mit {factId → linkedEntities}
4. Recall-Erweiterung: wenn semantisch gefundener Fakt auf andere Entität linkt → diese nachladen (1 Hop)
5. Duplikat-Erkennung erweitern: inhaltliche Überschneidung → `superseded_by` statt Duplikat-Abweisung

### v0.54.0 — Voice (Spracheingabe)
- SpeechRecognizer API (Android native, kein Modell-Download)
- Mikrofon-Button im Composer
- Whisper-tiny als Offline-Fallback (sherpa-onnx)

### v1.0 — Abschluss
1. KIWIX-Offline-Lexikon
2. Piper TTS mit so-mi Voice-Profil

### Vorgemerkt — OKF (Open Knowledge Format)
**User-Vereinbarung 2026-06-30** — für spätere Implementierung vorgemerkt.

OKF ersetzt/ergänzt das aktuelle Flat-Markdown-Erinnerungssystem durch strukturierte Markdown-Dateien mit YAML-Frontmatter und verlinkten Entitäten:

```yaml
---
type: Person
title: Christopher
tags: [nutzer]
---
- arbeitet als: SRE bei Delos Cloud
- Beziehungen: [Familie](/personen/familie.md)
```

So-Mi implementiert OKF bereits zu ~70% (Markdown-Dateien pro Kategorie). Fehlend: YAML-Frontmatter, Verlinkungen, Index-Dateien.
Spec: https://cloud.google.com/blog/products/data-analytics/how-the-open-knowledge-format-can-improve-data-sharing

---

## Wie diese Datei zu pflegen ist

- **Jede Sitzung:** ROADMAP zuerst lesen, dann SPEC.md wenn nötig
- **Neue Vereinbarungen:** sofort eintragen mit Datum
- **Nach Release:** "Aktueller Stand" aktualisieren, Pipeline bereinigen
- **Plan-Agents:** bekommen ROADMAP als Kontext, nicht nur SPEC
