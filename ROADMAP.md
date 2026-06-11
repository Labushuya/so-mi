# ROADMAP — so-mi

**Single source of truth für Phasen-Stand, User-Vereinbarungen aus Sitzungen, und den nächsten Push.**

`SPEC.md` §12 ist der ursprüngliche Plan. Diese Datei hält IST-Stand und alle nicht-SPEC-User-Vereinbarungen aus Chat-Sitzungen fest, sodass nichts mehr in Memory-Notes oder Chat-Verlauf verloren geht.

**Pflege-Pflicht:** Jede Sitzung beginnt mit einem Blick hier. Tasks im Backlog werden gegen diese Datei abgeglichen, nicht nur gegen SPEC.md. Wenn eine User-Vereinbarung eine SPEC-Abweichung darstellt, wird sie hier dokumentiert mit Begründung — nicht stillschweigend ignoriert.

---

## Aktueller Stand (2026-06-10)

| Release | Stand | Inhalt |
|---------|-------|--------|
| v0.19.1 | ✅ stable | M8 Recall: Fakten werden vor jeder Antwort als Kontext injiziert. Fakt-Normalisierung. |
| v0.19.0 | ✅ live (Build-Bug KDoc) | M8 Recall erster Entwurf |
| v0.18.5 | ✅ live | Memory-Persistence-Fix: mkdirs(), Save auch ohne Embedder, MagicOS ADJUST_PAN |
| v0.18.4 | ✅ live | Keyboard-Gap (imePadding), mehr Triggerwörter, Memory-Browser zeigt Fakten |
| v0.18.3 | ✅ live | Echter Memory-Browser (liest .md), imePadding statt windowInsetsPadding |
| v0.18.2 | ✅ live | 14B-Ampel-Fix (ramMinGB Korrektur) |
| v0.18.1 | ✅ live | XLARGE-Tier when-Branch in tierLabel |
| v0.18.0 | ✅ live | 14B-Ampel gelb, unvollständige Shards vervollständigen, Pause-Button |
| v0.17.1 | ✅ live | Settings Akkordeon, Tastatur adjustResize, Modelle & Speicher gemergt |
| v0.16.x | ✅ live | Download-UI, LLM-Katalog, Status-Badges, diverse Crash-Fixes |
| v0.15.1 | ✅ live | Build-Fix: EmbeddingModelDownloadWorker internal-Klasse |
| v0.14.3 | ✅ live | Embedder-Enqueue, Keyboard-Spacing, CHANGELOG |

---

## Phasen-Status gegen SPEC.md §12

### ✅ Phase 0 — Repo-Bootstrap
Komplett.

### ✅ Phase 1 — Skeleton + Pipeline
Komplett. Multi-Module-Build, release-please, signierte APKs.

### ✅ Phase 2 — Lokales LLM + Chat-UI
Komplett. llama.cpp, Hardware-Picker, soul.md-Prefix, streaming Chat.
**Drift gegen SPEC §3:** AGP 8.5.2 + Kotlin 2.0.21, Compose BOM 2024.10.01 — siehe CLAUDE.md.

### 🟡 Phase 3 — RAG + Persona-Memory (TEILWEISE GELIEFERT)

| Deliverable | Stand | Notiz |
|---|---|---|
| ObjectBox + ONNX Embedder | ✅ v0.14.0+ | paraphrase-multilingual-MiniLM-L12-v2, 384-dim |
| Explicit-Trigger (M5+M6) | ✅ v0.14.0 | "merk dir" → save in notes.md + ObjectBox |
| Recall / RAG-Inject (M8) | ✅ v0.19.1 | .md-Fakten als Kontext vor jeder Generation |
| Fakt-Normalisierung | ✅ v0.19.1 | führende Konjunktionen ("dass") werden gestrippt |
| Memory-Browser (M7) | 🟡 basic | liest .md; CRUD, Verschieben, Kategorien fehlen noch |
| TopicClassifier (M9) | ❌ offen | alles landet derzeit in NOTES |
| Auto-Toggle (M10) | ❌ offen | — |
| Multi-Fakt-Extraktion | ❌ offen | "Ich heiße X und bin Y alt" → 2 Fakten |
| KIWIX-AAR + search_kiwix | ❌ offen | — |
| **SPEC §12 Akzeptanztest** | 🟡 teilweise | Schreiben ✅, Recall ✅ (text-basiert), KIWIX ❌ |

### ❌ Phase 4 — Tools (12 aus SPEC §9)
Nicht angefangen. Kein Online-Boost (User-Entscheidung). Intent-Router lokal-only.

### ❌ Phase 5 — Voice + In-App-Updater
Nicht angefangen.

---

## User-Vereinbarungen (bindend wie SPEC)

### Erinnerungen / RAG
- **Recall aktiv** *(v0.19.1)* — top-20 Fakten aus .md als Kontext vor jeder Generation
- **Fakt-Normalisierung** *(v0.19.1)* — führende Konjunktionen gestrippt, erste Letter groß
- **Multi-Fakt-Extraktion** *(v0.23.2)* — "und"-Sätze werden gesplittet, per Regex in PERSONS/DATES/PREFERENCES/TECHNICAL/NOTES klassifiziert
- **Erinnerungen speichern ohne Embedder** *(v0.18.5)* — .md immer beschrieben; ObjectBox mit Null-Vektor bis Embedder verfügbar
- **Save-Bestätigung** *(v0.14.0)* — kurze in-character Chat-Bubble ("Hab ich.")
- **⚠️ TO BE IMPROVED — Komplexe Fakt-Extraktion** *(2026-06-11)* — Regex-Heuristik versagt bei komplexen Sätzen mit mehreren Fakten. Geplante Lösung: LLM-Pass für Extraktion (M9.1). Aufgeschoben nach v0.25.0.
- **⚠️ TO BE IMPROVED — Recall-Qualität** *(2026-06-11)* — Alle top-20 Fakten werden pauschal injiziert ohne semantische Sortierung. Verbessert sich wenn Embedder-Modell geladen (HNSW-Suche wird dann aktiv).

### Memory-Browser CRUD *(2026-06-10 teilweise, v0.24.0 rest)*
- **Kategorien anlegen** *(v0.23.1)* — benutzerdefinierte .md-Dateien
- **Fakten löschen** *(v0.21.2)* — mit Bestätigung
- **Fakten verschieben** *(v0.21.2)* — zwischen Kategorien
- **Fakten editieren** — Text direkt änderbar → **v0.24.0**
- **Fakten manuell anlegen** — ohne Trigger-Phrase → **v0.24.0**

### Settings-Struktur *(2026-06-10)*
Neue Reihenfolge der Akkordeon-Sektionen:
1. **Diagnose** (immer offen — Gerät-Info, Version, Ampel)
2. **So-Mi** (Erinnerungen GANZ OBEN, dann Persönlichkeit, Verhalten, Begrüßung, Lernen)
3. **Modelle & Abhängigkeiten** (umbenennen von "Modelle & Speicher" — LLM + Gedächtnis-Modell + künftige Pakete)
4. **Anzeige & Daten** (Vollbild, Backup & Import — So-Mis Daten sicherbar/importierbar)

- **"Erinnerungen ansehen (leer)"-Label**: muss dynamisch sein: "Erinnerungen ansehen (N gespeichert)" oder "Keine Erinnerungen"
- **So-Mi-Untermenü**: Erinnerungen erscheinen als erste Sektion, dann Persönlichkeit usw.

### Navigation *(2026-06-10)*
- **Hardware-Back-Button** navigiert intern (wie ← oben links), schließt die App nicht; nur auf dem Root-Screen (Chat) schließt Back die App

### Modell-Ampel *(aufgeschoben — kein Blocker)*
- **StorageRedline-Formel und StatFs-Quelle** — mehrfach angepasst, 14B zeigt weiterhin rot. Root-Cause unklar ohne Device-Debug-Build. Da die 14B-Modelle trotz roter Ampel funktionieren und auswählbar sind, ist das ein kosmetisches Problem. Aufgeschoben auf v0.23.x wenn ein Debug-Build mit Logcat-Zugriff möglich ist.

### Modell-Auswahl
- **7B Q4_K_M Default** *(CLAUDE.md)*
- **14B Q3 + Q4 aktiv** *(v0.18.1)* — im Katalog, SHAs verifiziert, XLARGE-Tier (YELLOW auf Magic V2)
- **Wi-Fi-Gate für alle großen Downloads**
- **Kein Auto-OOM-Fallback** — User bestätigt manuell

### Downloads & Setup
- **Embedder** *(v0.14.3+)* — paraphrase-multilingual-MiniLM-L12-v2, ~470 MB, WLAN-only, WorkManager
- **Setup-Guard** *(2026-06-10)* — Chat erst nach LLM + Embedder; Option "Ohne Gedächtnis starten"
- **In-App-Banner für alle Systemfehler** *(2026-06-10)* — anklickbar, Popup mit Erklärung + Handlung

### Backup & Import *(2026-06-10)*
- **So-Mis Daten exportierbar**: soul.md, memory/*.md, settings/*.json als ZIP
- **Importierbar**: gleiche Struktur; UI unter Anzeige & Daten

### Multi-Chat *(2026-06-09)*
- conversationId in Room, Chat-Liste als Einstieg, FAB "Neues Gespräch", Swipe-to-delete

### Prozess
- **Automatisches Pushen** *(2026-06-09)* — vollständig ohne User-Klick
- **tl;dr + Test-Anweisung** nach jedem Push
- **Stable-Einstufung** fragen nach positivem Test-Feedback

---

## Pipeline — nächste Sprints

### v0.20.0 — Settings-Überarbeitung + Navigation + Ampel-Fix (unmittelbar)
1. Settings-Reihenfolge: Diagnose → So-Mi → Modelle & Abhängigkeiten → Anzeige & Daten
2. "Erinnerungen ansehen (N gespeichert)"-Label dynamisch
3. Hardware-Back-Button intern navigieren (kein App-Close)
4. Ampel-Fix: `storageRedline = storageMinGB * 1.1` statt `* 1.5`; StatFs auf gesamten Geräte-Speicher umstellen
5. Settings-Sektion umbenennen zu "Modelle & Abhängigkeiten"

### v0.21.0 — Memory-Browser CRUD
1. Erinnerungen editieren (Inline-Edit)
2. Erinnerungen verschieben (zwischen Kategorien)
3. Kategorien anlegen/umbenennen/löschen
4. Reihenfolge per Drag (optional: Long-press + Reorder)

### v0.22.0 — Multi-Fakt-Extraktion + TopicClassifier (M9)
1. LLM-Pass extrahiert mehrere Fakten aus einem Satz
2. TopicClassifier kategorisiert in PERSONS/DATES/PREFERENCES/TECHNICAL/NOTES
3. Disambiguation-Chat-Bubble bei Unsicherheit

### v0.23.0 — Setup-Guard + Backup & Import
1. Setup-Guard: Chat gesperrt bis LLM + Embedder OK
2. Backup-Export: SoMi-Daten als ZIP
3. Import: ZIP zurückspielen

### v0.24.0 — Multi-Chat
1. conversationId in Room
2. Chat-Liste als Einstieg
3. FAB "Neues Gespräch", Swipe-to-delete

### v1.0 — Phase-3-Abschluss
1. KIWIX-AAR + libkiwix
2. Notes-Corpus + Watcher
3. Phase-3-Akzeptanztest erfüllt

---

## Wie diese Datei zu pflegen ist

- **Jede Sitzung:** ROADMAP zuerst lesen, dann SPEC.md wenn nötig
- **Neue Vereinbarungen:** sofort in "User-Vereinbarungen" eintragen mit Datum
- **Nach Release:** "Aktueller Stand" aktualisieren, Pipeline bereinigen
- **Plan-Agents:** bekommen ROADMAP als Kontext, nicht nur SPEC
