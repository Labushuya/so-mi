# ROADMAP — so-mi

**Single source of truth für Phasen-Stand, User-Vereinbarungen aus Sitzungen, und den nächsten Push.**

`SPEC.md` §12 ist der ursprüngliche Plan. Diese Datei hält IST-Stand und alle nicht-SPEC-User-Vereinbarungen fest.

**Pflege-Pflicht:** Jede Sitzung beginnt mit einem Blick hier. ROADMAP.md gewinnt bei Konflikten mit SPEC.md.

---

## Aktueller Stand (2026-06-17)

| Release | Stand | Inhalt |
|---------|-------|--------|
| v0.46.5 | ✅ live | Stage-3-Crash, set_alarm Umbenennung + Alarm-Hinweis |
| v0.46.4 | ✅ stable | per-Tool-Toggle, set_alarm, get_exchange_rate, news_briefing |
| v0.45.1 | ✅ stable | Custom-Kategorien mit Emoji via Dateiname-Matching |
| v0.45.0 | ✅ stable | Wetter dynamisch (Wochenende/Wochentag/in N Tagen), Erinnerungen Kategorie |
| v0.44.x | ✅ stable | Tool-System vollständig funktional: Wetter, Web-Suche, Erinnerungen |
| v0.43.1 | ✅ stable | Phase 4: Tool-System — ToolRouter (3-Stufen), get_weather, search_web, search_memory |
| v0.42.1 | ✅ stable | Erinnerungs-Rückmeldung mit Kategorie, Backfill-Worker, Embedder-Hinweis |
| v0.41.0 | ✅ stable | HNSW-Recall — semantische Suche wenn Embedder aktiv, .md-Scan als Fallback |
| v0.40.2 | ✅ stable | LLM-Klassifizierer + Sliding-Window Gesprächskontext |
| v0.39.0 | ✅ stable | Backup mit Chat-Verlauf, Import-Bestätigung, /search /clear /rename /archive |
| v0.38.1 | ✅ stable | Chat-Suche, OOM-Crash-Banner, Empty-State-Fix |
| v0.37.2 | ✅ stable | Multi-Chat Nachrichten-Isolation, letzter Chat löschbar |
| v0.36.2 | ✅ stable | Pausieren-Button-Fix, Embedder löschen, klarere Labels |
| v0.33.0 | ✅ stable | Slash-Command-Popup, Band auto-dismiss |

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

### 🟡 Phase 4 — Tools (3 von 12 stabil, ab v0.43.1)

| Tool | Stand |
|---|---|
| `get_weather` (Open-Meteo, kein Key) | ✅ v0.43.1+ — dynamische Datumsangaben, stabil |
| `search_web` (SearXNG) | ✅ v0.43.1+ — stabil, 15s Timeout |
| `search_memory` (Keyword + Kategorie) | ✅ v0.45.1 — Custom-Kategorien, Emoji-safe |
| Tool-Modus-Toggle (Kompakt / System-Prompt) | ✅ v0.44.0 |
| Restliche 9 Tools (SPEC §9) | ❌ v0.46.0+ |
| Settings → Tools: per-Tool-Toggle | ❌ v0.46.0 |
| GBNF-Wiring (Stage-3 Constrained Decoding) | ❌ deferred |

### ❌ Phase 5 — Voice + In-App-Updater

---

## User-Vereinbarungen (bindend wie SPEC)

### Anzeige & UX
- **⚠️ TO BE IMPROVED — Scroll-to-Bottom bei Tastatur** *(2026-06-12)* — mehrere Ansätze auf MagicOS gescheitert. Aufgeschoben.
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

### v0.46.0 — Settings → Tools + weitere Tools
- Settings → So-Mi → Tools: per-Tool-Toggle (de-/aktivierbar)
- `create_reminder` — Android AlarmManager + Notification
- `get_exchange_rate` — exchangerate.host (kein Key)
- `news_briefing` — lokale RSS-Feeds

### v0.47.0 — Kalender-Integration
- `read_calendar` — CalendarContract (READ_CALENDAR Permission)
- `create_event` — CalendarContract (WRITE_CALENDAR Permission)

### v1.0 — Phase-3/4-Abschluss
1. KIWIX-Offline-Lexikon
2. Tool-System vollständig (alle 12 SPEC §9 Tools)
3. Phase-3/4-Akzeptanztest erfüllt

---

## Wie diese Datei zu pflegen ist

- **Jede Sitzung:** ROADMAP zuerst lesen, dann SPEC.md wenn nötig
- **Neue Vereinbarungen:** sofort eintragen mit Datum
- **Nach Release:** "Aktueller Stand" aktualisieren, Pipeline bereinigen
- **Plan-Agents:** bekommen ROADMAP als Kontext, nicht nur SPEC
