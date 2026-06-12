# ROADMAP — so-mi

**Single source of truth für Phasen-Stand, User-Vereinbarungen aus Sitzungen, und den nächsten Push.**

`SPEC.md` §12 ist der ursprüngliche Plan. Diese Datei hält IST-Stand und alle nicht-SPEC-User-Vereinbarungen fest.

**Pflege-Pflicht:** Jede Sitzung beginnt mit einem Blick hier. ROADMAP.md gewinnt bei Konflikten mit SPEC.md.

---

## Aktueller Stand (2026-06-13)

| Release | Stand | Inhalt |
|---------|-------|--------|
| v0.39.1 | ✅ live | Build-Fix: verwaiste Zeilen ChatViewModel |
| v0.39.0 | ✅ live | Backup mit Chat-Verlauf, Import-Bestätigung, /search /clear /rename /archive |
| v0.38.1 | ✅ stable | Chat-Suche, OOM-Crash-Banner, Empty-State-Fix |
| v0.37.2 | ✅ stable | Multi-Chat Nachrichten-Isolation, letzter Chat löschbar |
| v0.37.1 | ✅ live | Multi-Chat Bug-Fix Room.databaseBuilder |
| v0.37.0 | ✅ live | Multi-Chat v1: Chat-Liste, Gespräche anlegen/löschen/umbenennen |
| v0.36.2 | ✅ stable | Pausieren-Button-Fix, Embedder löschen, klarere Labels |
| v0.36.1 | ✅ live | Mistral-Nemo 12B Q3+Q4 im Katalog (Apache 2.0, ungated) |
| v0.35.0 | ✅ live | FAQ mit Suche, Embedder-Status-Observer-Fix |
| v0.34.0 | ✅ live | Backup-Import, Setup-Guard-Banner |
| v0.33.0 | ✅ stable | Slash-Command-Popup, Band auto-dismiss |
| v0.32.1 | ✅ live | Test-Commands für Chat-Band (/trigger_error etc.), 4 Band-Typen |
| v0.31.2 | ✅ live | Keyword-Suche UI, Band-Style WhatsApp |
| v0.30.1 | ✅ live | Einfache Antwort nach Trigger (kein doppelter LLM-Call) |
| v0.29.1 | ✅ live | Emojis in Kategorienamen, Duplikat-Erkennung (Levenshtein) |
| v0.28.0 | ✅ live | Keyword-CRUD (edit/move/delete), getrennte UI-Sektionen |
| v0.27.0 | ✅ live | Keywords per UI + /-Button pflegen |
| v0.25.1 | ✅ live | Eigene Kategorien-Erkennung mit Keywords, Backup-Export |
| v0.24.2 | ✅ live | Erinnerungen editieren + manuell anlegen |
| v0.23.2 | ✅ live | Multi-Fakt-Split, Duplikat-Fix |
| v0.22.0 | ✅ live | TopicClassifier (Regex), eigene Kategorien |
| v0.21.2 | ✅ live | Memory-Browser CRUD |
| v0.20.0 | ✅ live | Settings-Akkordeon, Navigation |
| v0.19.1 | ✅ live | M8 Recall — Fakten als Kontext |

---

## Phasen-Status gegen SPEC.md §12

### ✅ Phase 0–2 — Bootstrap, Pipeline, LLM + Chat
Komplett.

### 🟡 Phase 3 — RAG + Persona-Memory (TEILWEISE)

| Deliverable | Stand |
|---|---|
| Explicit-Trigger + Save | ✅ v0.14+ |
| Recall / RAG-Inject | ✅ v0.19.1 |
| Memory-Browser CRUD | ✅ v0.24.2+ |
| TopicClassifier (Heuristik) | ✅ v0.22.0 |
| Custom Kategorien + Keywords | ✅ v0.27.0+ |
| Backup Export + Import | ✅ v0.25.0 / v0.34.0 |
| Setup-Guard | ✅ v0.34.0 (Banner, non-blocking) |
| FAQ | ✅ v0.35.0 |
| Multi-Chat | ❌ offen — v0.37.0 |
| M9 LLM-Klassifizierer | ❌ aufgeschoben nach v0.40 |
| KIWIX-AAR | ❌ Phase-3-Abschluss |

### ❌ Phase 4 — Tools (12 aus SPEC §9)
### ❌ Phase 5 — Voice + In-App-Updater

---

## User-Vereinbarungen (bindend wie SPEC)

### Anzeige & UX
- **⚠️ TO BE IMPROVED — Scroll-to-Bottom bei Tastatur** *(2026-06-12)* — mehrere Ansätze auf MagicOS gescheitert. Aufgeschoben bis Debug-APK möglich.
- **⚠️ TO BE IMPROVED — 14B/12B-Ampel-Farbe** *(2026-06-12)* — StatFs auf MagicOS gibt App-Quota statt physischen Speicher. Aufgeschoben.
- **Chat-Band** *(v0.33.0)* — 4 Typen (Error/Warning/Success/Info), 5s Auto-Dismiss, WhatsApp-Style Pill
- **Slash-Command-Popup** *(v0.33.0)* — Autocomplete + /-Button; Klick fügt Command ein

### Erinnerungen / RAG
- **Recall aktiv** *(v0.19.1)* — top-20 Fakten als Kontext vor jeder Generation
- **Multi-Fakt + Regex-Classifier** *(v0.22–v0.23)* — "und"-Split, PERSONS/DATES/PREFERENCES/TECHNICAL/NOTES
- **Duplikat-Erkennung** *(v0.29.1)* — exakter Match + Levenshtein ≤ 2
- **Eigene Kategorien + Keywords** *(v0.27.0)* — per UI oder Slash-Command; Keywords in `.keywords.json`
- **⚠️ TO BE IMPROVED — Komplexe Fakt-Extraktion** *(2026-06-11)* — LLM-Pass geplant nach v0.40
- **⚠️ TO BE IMPROVED — Recall-Qualität** *(2026-06-11)* — HNSW kommt wenn Embedder aktiv

### Modelle & Downloads
- **7B Q4_K_M Default** *(CLAUDE.md)*
- **14B Q3 + Q4 im Katalog** *(v0.15.1)* — qwen-research-Lizenz
- **Mistral-Nemo 12B Q3+Q4** *(v0.36.1)* — Apache 2.0, ungated, SHA verifiziert 2026-06-12; LARGE_PLUS-Tier
- **Qwen 12B** — HF-gated, nicht integrierbar ohne User-HF-Token; zurückgestellt
- **⚠️ TO BE IMPROVED — 14B auf Magic V2** *(2026-06-12)* — crasht (KV-Cache + 9GB > 16GB). OOM-Fallback ausstehend (v0.38.0).
- **Wi-Fi-Gate für Downloads**; kein Auto-OOM-Fallback (User bestätigt manuell)

### Prozess
- **Vollautomatisches Pushen** — PR + release-please ohne User-Klick
- **tl;dr + Test-Anweisung** nach jedem Push
- **Stable-Einstufung** nach positivem Test-Feedback
- **Workflow + Agents** bei parallelen Tasks

---

## Pipeline — nächste Sprints (Priorität absteigend)

### v0.39.0 — Backup vollständig + Chat Phase 2 (unmittelbar)
1. Room-DB (`somi.db`) ins Backup aufnehmen — Chat-Verlauf exportierbar
2. Import-Bestätigung wenn vorhandene Daten überschrieben werden
3. Nachrichten-Suche innerhalb eines Gesprächs (Suchfeld im Chat)
4. Chat umbenennen direkt aus dem Chat heraus
5. Chat-Archivierung (ausblenden ohne löschen)

### v0.40.0 — M9 LLM-Klassifizierer
1. Kurzer LLM-Pass nach Trigger-Save: strukturierte Fakten-Extraktion
2. Bessere Kategorisierung ohne Regex-Grenzen

### v0.41.0 — Kontext-Qualität
1. Sliding-Window bei langen Gesprächen (KV-Cache-Schutz)
2. Besseres Recall-Ranking wenn Embedder aktiv (HNSW)

### v0.42.0 — Phase 4: Tool-System (Grundlage für Internet + Plugins)
**Internet-Zugang** *(User-Vereinbarung 2026-06-13)*:
- `search_web`-Tool: Suchanfragen an ungated API (Brave Search / SearXNG)
- Ergebnisse als Kontext injiziert; Verarbeitung bleibt lokal
- Nur bei Bedarf: So-Mi entscheidet ob sie sucht, User kann Tool deaktivieren
- Datenschutz-Hinweis: Suchanfragen verlassen das Gerät

**Modul/Plugin-System** *(User-Vereinbarung 2026-06-13)*:
- 12 Tools aus SPEC §9: search_web, search_kiwix, get_weather, read_calendar, write_calendar, send_message, run_shell, + weitere
- Jedes Tool individuell de-/aktivierbar in Settings → So-Mi → Tools
- JSON-Schema → GBNF für constrained decoding
- Erweiterbar durch eigene Tool-Definitionen

### v1.0 — Phase-3/4-Abschluss
1. KIWIX-Offline-Lexikon
2. Tool-System vollständig
3. Phase-3-Akzeptanztest erfüllt

---

## Wie diese Datei zu pflegen ist

- **Jede Sitzung:** ROADMAP zuerst lesen, dann SPEC.md wenn nötig
- **Neue Vereinbarungen:** sofort eintragen mit Datum
- **Nach Release:** "Aktueller Stand" aktualisieren, Pipeline bereinigen
- **Plan-Agents:** bekommen ROADMAP als Kontext, nicht nur SPEC
