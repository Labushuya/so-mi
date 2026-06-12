# ROADMAP — so-mi

**Single source of truth für Phasen-Stand, User-Vereinbarungen aus Sitzungen, und den nächsten Push.**

`SPEC.md` §12 ist der ursprüngliche Plan. Diese Datei hält IST-Stand und alle nicht-SPEC-User-Vereinbarungen fest.

**Pflege-Pflicht:** Jede Sitzung beginnt mit einem Blick hier. ROADMAP.md gewinnt bei Konflikten mit SPEC.md.

---

## Aktueller Stand (2026-06-12)

| Release | Stand | Inhalt |
|---------|-------|--------|
| v0.33.0 | ✅ stable | Slash-Command-Popup + Autocomplete + /-Button, Band auto-dismiss 5s |
| v0.32.1 | ✅ live | Test-Commands für Chat-Band, 4 Band-Typen, 12B Staging |
| v0.31.2 | ✅ live | Keyword-Suche UI, Band-Style WhatsApp |
| v0.30.1 | ✅ live | Einfache Antwort nach Trigger (kein doppelter LLM-Call) |
| v0.29.1 | ✅ live | Emojis in Kategorienamen, Duplikat-Erkennung (Levenshtein) |
| v0.28.0 | ✅ live | Keyword-CRUD (edit/move/delete), getrennte UI-Sektionen |
| v0.27.0 | ✅ live | Keywords per UI + /-Button pflegen |
| v0.25.1 | ✅ live | Eigene Kategorien-Erkennung mit Keywords, Backup-Export |
| v0.24.2 | ✅ live | Erinnerungen editieren + manuell anlegen |
| v0.23.2 | ✅ live | Multi-Fakt-Split, Duplikat-Fix per Kategorie |
| v0.22.0 | ✅ live | TopicClassifier (Regex), eigene Kategorien |
| v0.21.2 | ✅ live | Memory-Browser CRUD |
| v0.20.0 | ✅ live | Settings-Akkordeon, Navigation, WLAN-Toggle |
| v0.19.1 | ✅ live | M8 Recall, Fakten als Kontext vor Generation |

---

## Phasen-Status gegen SPEC.md §12

### ✅ Phase 0–2 — Bootstrap, Pipeline, LLM + Chat
Komplett.

### 🟡 Phase 3 — RAG + Persona-Memory (TEILWEISE)

| Deliverable | Stand | Notiz |
|---|---|---|
| Explicit-Trigger + Save | ✅ v0.14+ | "merk dir" → .md + ObjectBox |
| Recall / RAG-Inject | ✅ v0.19.1 | top-20 Fakten als Kontext |
| Memory-Browser CRUD | ✅ v0.24.2+ | lesen, editieren, löschen, verschieben |
| TopicClassifier (Heuristik) | ✅ v0.22.0 | Regex-basiert |
| Custom Kategorien + Keywords | ✅ v0.27.0+ | per UI + per Slash-Command |
| Setup-Guard | ❌ offen | v0.34.0 |
| Backup Import | ❌ offen | Export fertig, Import fehlt |
| Multi-Chat | ❌ offen | v0.35.0 |
| M9 LLM-Klassifizierer | ❌ aufgeschoben | nach v0.35 |
| KIWIX-AAR | ❌ Phase-3-Abschluss | |

### ❌ Phase 4 — Tools (12 aus SPEC §9)
Nicht angefangen.

### ❌ Phase 5 — Voice + In-App-Updater
Nicht angefangen.

---

## User-Vereinbarungen (bindend wie SPEC)

### Anzeige & UX
- **⚠️ TO BE IMPROVED — Scroll-to-Bottom bei Tastatur** *(2026-06-12)* — mehrere Ansätze auf MagicOS gescheitert. Aufgeschoben bis Debug-APK möglich.
- **⚠️ TO BE IMPROVED — 14B-Ampel zeigt rot** *(2026-06-12)* — StatFs auf MagicOS gibt Quota statt physischen Speicher. Aufgeschoben.
- **Chat-Band** *(v0.33.0)* — 4 Typen (Error/Warning/Success/Info), 5s Auto-Dismiss, WhatsApp-Style Pill
- **Slash-Command-Popup** *(v0.33.0)* — Autocomplete beim `/`-Tippen + /-Button; Klick fügt Command ein

### Erinnerungen / RAG
- **Recall aktiv** *(v0.19.1)* — top-20 Fakten als Kontext vor jeder Generation
- **Multi-Fakt + Regex-Classifier** *(v0.22–v0.23)* — "und"-Split, PERSONS/DATES/PREFERENCES/TECHNICAL/NOTES
- **Duplikat-Erkennung** *(v0.29.1)* — exakter Match + Levenshtein ≤ 2
- **Eigene Kategorien + Keywords** *(v0.27.0)* — per UI oder `/note`-Slash; Keywords in `.keywords.json`
- **⚠️ TO BE IMPROVED — Komplexe Fakt-Extraktion** *(2026-06-11)* — LLM-Pass geplant, aufgeschoben nach v0.35
- **⚠️ TO BE IMPROVED — Recall-Qualität** *(2026-06-11)* — HNSW kommt wenn Embedder aktiv

### Modelle & Downloads
- **7B Q4_K_M Default** *(CLAUDE.md)*
- **14B Q3 + Q4 im Katalog** *(v0.15.1)* — qwen-research-Lizenz
- **12B Q3_K_M Staging** *(v0.32.1)* — SHAs pending, ~5.8GB, GREEN auf Magic V2
- **⚠️ TO BE IMPROVED — 14B auf Magic V2** *(2026-06-12)* — crasht (KV-Cache + 9GB > 16GB). 12B als Candidate. OOM-Fallback ausstehend.
- **Wi-Fi-Gate für alle großen Downloads**
- **Kein Auto-OOM-Fallback** — User bestätigt manuell

### Prozess
- **Vollautomatisches Pushen** — PR + release-please ohne User-Klick *(2026-06-09)*
- **tl;dr + Test-Anweisung** nach jedem Push
- **Stable-Einstufung** nach positivem Test-Feedback
- **Workflow + Agents bevorzugen** bei parallelen Tasks

---

## Pipeline — nächste Sprints

### v0.34.0 — Setup-Guard + Backup Import
1. Setup-Guard: FirstLaunchScreen blockiert Chat bis LLM + Embedder installiert; "Ohne Gedächtnis starten"-Option
2. Backup-Import: ZIP zurückspielen (Export seit v0.25.0 fertig)
3. In-App-Hinweis wenn erforderliche Abhängigkeiten fehlen

### v0.35.0 — Multi-Chat
1. conversationId-Spalte in Room + Migration
2. Chat-Liste als App-Einstieg
3. FAB "Neues Gespräch", Swipe-to-delete

### v0.36.0 — 12B + OOM-Fallback
1. Qwen2.5-12B SHAs live verifizieren + in ALL aufnehmen
2. OOM-Detection → Banner "Modell zu groß — auf 7B wechseln?"
3. Tier-Formel für XLARGE neu kalibrieren mit Debug-APK

### v1.0 — Phase-3-Abschluss
1. M9 LLM-Klassifizierer für Fakt-Extraktion
2. KIWIX-AAR + libkiwix
3. Phase-3-Akzeptanztest erfüllt

---

## Wie diese Datei zu pflegen ist

- **Jede Sitzung:** ROADMAP zuerst lesen, dann SPEC.md wenn nötig
- **Neue Vereinbarungen:** sofort eintragen mit Datum
- **Nach Release:** "Aktueller Stand" aktualisieren, Pipeline bereinigen
- **Plan-Agents:** bekommen ROADMAP als Kontext, nicht nur SPEC
