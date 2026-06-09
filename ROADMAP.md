# ROADMAP — so-mi

**Single source of truth für Phasen-Stand, User-Vereinbarungen aus Sitzungen, und den nächsten Push.**

`SPEC.md` §12 ist der ursprüngliche Plan. Diese Datei hält IST-Stand und alle nicht-SPEC-User-Vereinbarungen aus Chat-Sitzungen fest, sodass nichts mehr in Memory-Notes oder Chat-Verlauf verloren geht.

**Pflege-Pflicht:** Jede Sitzung beginnt mit einem Blick hier. Tasks im Backlog werden gegen diese Datei abgeglichen, nicht nur gegen SPEC.md. Wenn eine User-Vereinbarung eine SPEC-Abweichung darstellt, wird sie hier dokumentiert mit Begründung — nicht stillschweigend ignoriert.

---

## Aktueller Stand (2026-06-09)

| Release | Stand | Inhalt |
|---------|-------|--------|
| v0.16.0 | 🔜 pending release-please | WLAN-Toggle zurück, Settings-Position gemerkt, Katalog-Download-UI (Fortschrittsbalken + Herunterladen-Button), Ladeüberblendung (350 ms), Embedder-Ampel, 14B SHA-geprüft + freigeschaltet |
| v0.15.1 | ✅ live | Build-Fix: EmbeddingModelDownloadWorker.WORK_NAME aus RagBootstrap exposen statt internal-Klasse direkt referenzieren |
| v0.15.0 | ✅ live (kein APK — Build-Bug) | Downloads-Section + 14B-Catalog + ModelCatalogScreen + DataBrowser + Greeting-Feature + Vollbild-Modus + SoMi/-Wurzel-Storage + Embedder-Mirror |
| v0.14.3 | ✅ live | RagBootstrap.scheduleEmbedderDownload + Keyboard-Spacing-Fix + CHANGELOG-Aufräumen |
| v0.14.2 | ✅ live | M1-M6 Lern-RAG-Schreibe-Pfad ("merk dir" → "Hab ich.") |
| v0.13.0 | ✅ live | Settings-Refactor + Songbird-Dialog-Stil + LLM-Parameter + Persönlichkeits-Editor |
| v0.12.x | ✅ live | Sprachdrift-Fix + Settings-Refactor (war ursprünglich als 14B-Release geplant — siehe "Verschoben") |
| v0.11.x | ✅ live | Composer-Bug + System-Bars + Erst-Setup-Loop-Fix |

---

## Phasen-Status gegen SPEC.md §12

### ✅ Phase 0 — Repo-Bootstrap
Komplett. Repo, Keystore, CI alles up.

### ✅ Phase 1 — Skeleton + Pipeline
Komplett. Multi-Module-Build, release-please, signierte APKs.

### ✅ Phase 2 — Lokales LLM + Chat-UI
Komplett. llama.cpp via Submodul, Hardware-Picker, soul.md-Prefix, streaming Chat. **Drift gegen SPEC §3:** AGP 8.5.2 + Kotlin 2.0.21 statt der inkonsistenten SPEC-Triple, Compose BOM 2024.10.01 in lockstep — siehe CLAUDE.md.

### 🟡 Phase 3 — RAG + Persona-Memory (TEILWEISE GELIEFERT)
| Deliverable | Stand | Notiz |
|---|---|---|
| ObjectBox + bge-Embeddings (ONNX) | ✅ M1+M2 in v0.14.0+ | paraphrase-multilingual-MiniLM-L12-v2 statt bge-small (User-Vereinbarung 2026-06-08, WordPiece-Tokenizer) |
| libkiwix-AAR + `search_kiwix` | ❌ offen | Verschoben nach v0.15+ |
| Notes-Corpus + Watcher | ❌ offen | Verschoben nach v0.15+ |
| Conversation-Memory: explicit Trigger | ✅ M5+M6 in v0.14.0 | "merk dir, ..." → save in `notes.md` |
| Conversation-Memory: Background-Fact-Extractor | ❌ offen | M9 (TopicClassifier) + M10 (Auto-Toggle) nicht geliefert |
| Decay (`cosine * exp(-age/90d)`), Supersedes | 🟡 teilweise | Implementiert in MemoryStore.topK aber nicht via HNSW-Live-Query (M8 nicht geliefert) |
| `bootstrap-soul.sh` + `initial-memory.json`-Loader | ❌ offen | Phase-3-Bootstrap |
| **SPEC §12 Akzeptanztest** | ❌ noch nicht erfüllt | "So-Mi erinnert sich an Fakten" — Schreibe ja, **Recall nein**; KIWIX-Quellen nein |

### ❌ Phase 4 — Tools
Nicht angefangen. Gated nach Phase-3-Akzeptanz.

### ❌ Phase 5 — Voice + Update-Mechanismus
Nicht angefangen. Gated nach Phase 4.

---

## User-Vereinbarungen aus Chat-Sitzungen (nicht in SPEC)

Diese Vereinbarungen wurden in Sitzungen mit Christopher getroffen und sind **bindend** wie SPEC-Anforderungen — auch wenn sie SPEC ergänzen oder leicht abweichen.

### Modell-Auswahl
- **14B-Modelle (Q3 + Q4) hinzufügen** *(2026-06-08, M2-Planungs-Sitzung)*
  - Q3_K_M (~7 GB, GREEN-Tier) und Q4_K_M (~9 GB, YELLOW-Tier) parallel im Catalog
  - Picker zeigt pro Eintrag den Quant-Typ mit Tooltip-Erklärung (Q3 = kleiner+schneller, Q4 = besser+grenzwertig)
  - YELLOW-Tap → Songbird-Confirm-Dialog "Knapper RAM-Budget. OOM-Fallback auf 7B aktiv?"
  - **Kein Auto-Switch bei OOM** — User muss aktiv bestätigen
  - Status: Task #97 offen, Reihenfolge: nach v0.15.0 weil Resilienz-Pfad davor wichtiger
- **7B Q4_K_M bleibt v1-Default** *(CLAUDE.md)*
- **Wi-Fi-Gate für alle großen Downloads** *(Phase-2-Architektur-Memory)*

### Embedder
- **paraphrase-multilingual-MiniLM-L12-v2** *(2026-06-08)* — WordPiece-Tokenizer, 384-dim, ~120 MB. Kein bge-m3 weil SentencePiece-Tokenizer auf Android nicht zuverlässig liefert.
- **Download-on-first-launch via WorkManager** *(2026-06-08)*

### Lern-Trigger
- **Hybrid-Trigger** *(2026-06-08)*: Keyword-Match default-on (`merk dir`, `remember`, `/note`, `vergiss nicht`, `wichtig:`, `speichere`, `erinnere dich`) + Auto-Toggle in Settings default-off
- **mem0-Standard-Widerspruchs-Policy** *(2026-06-08)*: Single-Value supersedes (Name), Multi-Value akkumuliert (Hobbys), Spitznamen als Aliase
- **Topic-Klassifizierung Hybrid** *(2026-06-08)*: vordefinierte Buckets (persons/preferences/dates/technical/notes) + LLM-Klassifizierer + Disambiguation-Frage bei Unsicherheit
- **Disambiguation-Format Default** *(2026-06-08)*: Chat-Bubble; Toggle in Settings auf Sheet-UI als Alternative
- **Save-Bestätigung** *(2026-06-08)*: kurze in-character Chat-Bubble ("Hab ich.")
- **Recall-Anzeige** *(2026-06-08)*: Memory-Chip am Antwort-Bubble, antippbar zum Aufklappen

### Memory-Browser (M7)
- **Liste mit Akkordeon pro Topic** *(2026-06-08)* — ein-/ausklappbar
- **Bullet-weise Lösch + Verschieben in andere `.md`** *(2026-06-08)*

### Multi-Chat
- **Mehrere parallele Gespräche** *(2026-06-09)* — conversationId-Spalte in Room, Chat-Liste als Einstieg vor dem Chat. Ein Floating-Action-Button "Neues Gespräch", Swipe-to-delete. Kommt in v0.16.x nach Recall.

### Anzeige & UX
- **Tatsächlicher Vollbild-Modus per Toggle in Settings** *(2026-06-08)*
- **Breathing-Screen: Fade-Out-Animation beim Übergang in den Chat** *(2026-06-09)* — kein harter Cut. CrossFade-Composable oder AnimatedVisibility mit 300–400 ms.
- **Settings-Scroll-Position: beim Zurück-Navigieren die zuletzt sichtbare Position beibehalten** *(2026-06-09)* — `rememberLazyListState` mit Snapshot-Save.
- **Notch/Punch-Hole respektieren** *(2026-06-08)*
- **Keyboard-Spacing-Fix endgültig** *(2026-06-08)*
- **Begrüßungs-Feature mit 3-Mode-Toggle** *(2026-06-08)*

### Keyboard
- **Tastatur überlappt Chat, kein Layout-Hochschieben** *(2026-06-09)* — `adjustNothing` im Manifest. Composer liegt immer am Bildschirmrand. Tastatur schiebt sich über den Chat-Bereich. User scrollt hoch für ältere Nachrichten.

### Downloads & Modelle
- **Wi-Fi-Toggle muss zurück in die Downloads-Sektion** *(2026-06-09)* — war in v0.14.x; in v0.15.0 versehentlich entfernt. Steuert ALLE großen Downloads (LLM + Embedder). Default: WLAN. Ohne Toggle: Soft-Lock wenn kein WLAN.
- **ModelCatalogScreen braucht Download-Button** *(2026-06-09)* — Tippen auf ein Modell schlägt das Modell vor, aber löst keinen Download aus wenn das Modell noch nicht installiert ist. "Herunterladen"-Button pro Zeile (greyed-out wenn bereits installiert/lädt).
- **Embedder-Status in der Downloads-Sektion sichtbar als Badge** *(2026-06-09)* — "Installiert ✓", "Lädt X%" Fortschrittsbalken, "Fehler — Erneut laden". Aktuell ist der Status-Text da aber ohne visuelles Gewicht.
- **Optionale Downloads werden als Pflicht kommuniziert** *(2026-06-09)* — Embedder (RAG-Modell) und später Sprachpakete sollen beim Start automatisch angestoßen + mit in-App-Hinweis "Wird geladen…" signalisiert werden. Foreground-Notification bleibt als OS-Pflicht erhalten, aber kein extra Popup.
- **RAG-Sektion in Downloads-Sektion getrennt von Sprachmodellen** *(2026-06-09)* — unter "Sprachmodelle" gehört nur der LLM; Embedder hat seine eigene Untersektion "Gedächtnis-Modell".

### Notifications
- **Download-Notifications reduzieren auf OS-Minimum** *(2026-06-09)* — Foreground-Service-Notification bleibt (OS-Pflicht), aber kein separates "Download abgeschlossen"-Pop. In-App-Status in der Downloads-Sektion ersetzt alle Nachrichteninhalte.

### RAG / Memory
- **Recall-Funktion (M8) ist noch nicht implementiert** *(Stand 2026-06-09)* — "merk dir" schreibt, Recall liest nicht zurück. Das ist erwartetes Verhalten und kein Bug — M8 kommt in v0.16.0. So-Mi vergisst zwischen Sessions, weil kein RAG-Inject in den Prompt passiert.

### Prozess
- **Eine User-sichtbare Bullet pro Commit** *(Memory)*
- **Deutsche User-Prosa als Commit-Subjects**
- **Automatisches Pushen ohne User-Aktion** *(2026-06-09)* — PR öffnen, mergen, release-please-PR mergen, alles vollautomatisch. User muss nichts mehr klicken.
- **Workflow + Agents bevorzugen** *(2026-06-09)* — bei nicht-trivialen Bundles immer Workflow-Tool nutzen.

### Storage
- **`$externalFilesDir/memory/<topic>.md`** *(2026-06-08)* — User-readable Mirror
- **`$filesDir/objectbox/so-mi-rag/`** *(2026-06-08)* — binärer Authority-Store
- **`SoMi/`-Sichtbares-Wurzelverzeichnis unter `externalFilesDir`** *(2026-06-08)* — alle user-sichtbaren Daten konsolidiert: `SoMi/llm`, `SoMi/memory`, `SoMi/soul`, `SoMi/db`, `SoMi/settings`. `StorageMigrator` migriert Alt-Pfade beim ersten Start (sentinel-gated, idempotent). Embedder + ObjectBox bleiben unter `filesDir` (deferred bis v0.16.0)
- **In-App Daten-Browser unter Settings → Daten** *(2026-06-08)* — read-only File-Tree-Viewer für `SoMi/`-Wurzel, mit Datei-Teilen via FileProvider; ersetzt ADB komplett

### Online-Boost
- **Komplett verworfen** *(2026-06-09 oder so)* — User nutzt keine Claude-API. Stattdessen lokales Lernen via RAG. Phase 4 wird ohne Online-Boost-Komponente neu konzipiert wenn sie dran ist.

### Persönlichkeit
- **soul.md editierbar in Settings mit Backup + Live-Reload** *(2026-06-08)*
- **Sprach-Anker: immer Deutsch** — explizit in soul_condensed.md kodifiziert nach Code-Switching-Bug (v0.11.4)

### App-Verhalten
- **FGS-Pin via LlamaSessionService** *(Phase-2.10)* — verhindert MagicOS-Process-Reaping
- **Edge-to-edge mit explizitem Songbird-Obsidian-Scrim** *(v0.13.0)*
- **enableEdgeToEdge in MainActivity** statt deprecated Theme-Attrs *(v0.13.0)*
- **Kein ADB-Reden mit User** *(2026-06-09)* — User nutzt das nicht; alle Inspektions-Pfade müssen in-app sein

### Resilienz
- **GH-Releases-Mirror für Embedder-Files (~487 MB) bei jedem Release** *(2026-06-09)* — User-Frau-Frischinstall-Szenario
- **Multi-URL-Fallback (HF primär, GH-Releases sekundär, SAF tertiär)** *(2026-06-09)*
- **Downloads-Section in Settings** zeigt was geladen ist / lädt / fehlt / wartet auf WLAN *(2026-06-09)*

### Prozess
- **Eine User-sichtbare Bullet pro Commit** *(Memory: changelog-must-be-user-readable)*
- **Deutsche User-Prosa als Commit-Subjects** — release-please rendert verbatim ins CHANGELOG
- **Keine phase-Codes mehr im Changelog** — retroaktiv aufgeräumt v0.14.3
- **Kein versions-genannter Task-Name** — Tasks tragen Feature-Scope, Versionen kommen von release-please
- **Adversarial Verify-Pass vor jedem nicht-trivialen Push** *(Memory: capability-honest-before-big-pushes)*
- **Bei "unknown" im Verify-Pass: nicht pushen, sondern verifizieren** — Lehre aus zwei Build-Bricks bei v0.14.0

---

## Pipeline (offene Vereinbarungen mit Reihenfolge)

### v0.16.0 — Settings-Overhaul + Downloads-Fix + Breathing-Fade (nächster Sprint)
Alle Punkte aus dem v0.15.1-Test die sofort adressiert werden müssen:

1. **Wi-Fi-Toggle** zurück in Downloads-Sektion (Soft-Lock-Fix)
2. **ModelCatalogScreen**: Download-Button pro Zeile + Download-Status-Badge (Fortschrittsbalken)
3. **Embedder-Status-Badge** in Downloads-Sektion mit visuellem Gewicht (Fortschrittsbalken)
4. **Breathing-Screen Fade-Out** → sanfter Übergang in Chat (300–400 ms CrossFade)
5. **Settings-Scroll-Position** beim Zurück beibehalten (`rememberLazyListState`)
6. **Settings-Struktur überarbeiten** — alle Sektionen korrekt beschriftet, RAG getrennt von LLM, keine fehlerhaften Abhängigkeiten
7. **Automatisches Push** (PR öffnen + mergen ohne User-Aktion)
8. **14B-SHAs live verifizieren** und in `ALL` überführen

### v0.17.0 — Phase-3-Recall (M7+M8+M9)
1. M7 — Memory-Browser-UI in Settings
2. **M8 — Recall + RAG-Inject in den Prompt** ← das ist was Christopher als "So-Mi vergisst" erlebt
3. M9 — TopicClassifier mit Disambiguation

### v0.18.0+ — Multi-Chat + Phase-3-Rest
1. Multi-Chat: conversationId-Spalte, Chat-Liste, Swipe-to-delete
2. KIWIX-AAR + libkiwix
3. Notes-Corpus + Watcher

---

## Wie diese Datei zu pflegen ist

**Bei jeder Sitzung am Anfang:**
1. ROADMAP.md lesen
2. Tasks im Backlog gegen "Pipeline"-Sektion abgleichen — keine Drift dulden
3. Wenn User in Sitzung neue Vereinbarungen trifft, **am Ende der Sitzung** in "User-Vereinbarungen" eintragen mit Datum

**Bei Plan-Workflows:**
- Plan-Agents bekommen ROADMAP.md als zusätzlichen Context — nicht nur SPEC.md
- Tasks die User-Vereinbarungen zuwiderlaufen, müssen explizit gerechtfertigt werden, nicht stillschweigend übernommen

**Bei Release:**
- Nach v0.X.Y-Merge: "Aktueller Stand"-Tabelle aktualisieren
- "Pipeline"-Sektion: erledigte Items rausnehmen, neue Vereinbarungen einsortieren
