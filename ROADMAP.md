# ROADMAP — so-mi

**Single source of truth für Phasen-Stand, User-Vereinbarungen aus Sitzungen, und den nächsten Push.**

`SPEC.md` §12 ist der ursprüngliche Plan. Diese Datei hält IST-Stand und alle nicht-SPEC-User-Vereinbarungen aus Chat-Sitzungen fest, sodass nichts mehr in Memory-Notes oder Chat-Verlauf verloren geht.

**Pflege-Pflicht:** Jede Sitzung beginnt mit einem Blick hier. Tasks im Backlog werden gegen diese Datei abgeglichen, nicht nur gegen SPEC.md. Wenn eine User-Vereinbarung eine SPEC-Abweichung darstellt, wird sie hier dokumentiert mit Begründung — nicht stillschweigend ignoriert.

---

## Aktueller Stand (2026-06-08)

| Release | Stand | Inhalt |
|---------|-------|--------|
| v0.15.0 | 🟡 in Push | Downloads-Section + 14B-Catalog + ModelCatalogScreen + DataBrowser + Greeting-Feature + Vollbild-Modus + SoMi/-Wurzel-Storage + Embedder-Mirror (HF + GH-Release) + tokenizer-Asset-Seed-Pfad |
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

### Anzeige & UX
- **Tatsächlicher Vollbild-Modus per Toggle in Settings** *(2026-06-08)* — `WindowInsetsControllerCompat.hide(systemBars())` mit `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`; Default EIN; Swipe vom Rand bringt Bars kurz zurück
- **Notch/Punch-Hole respektieren** *(2026-06-08)* — `windowLayoutInDisplayCutoutMode = shortEdges` plus `WindowInsets.systemBars.union(displayCutout)` auf allen Vollbild-Composables
- **Keyboard-Spacing-Fix endgültig** *(2026-06-08)* — LazyColumn `Arrangement.spacedBy(8.dp, Alignment.Bottom)`, contentPadding vertical=4.dp, Composer mit `WindowInsets.ime.union(navigationBars)`
- **Begrüßungs-Feature mit 3-Mode-Toggle** *(2026-06-08)*
  - FULL: bei jedem Aufwachen + Hintergrund-Rückkehr (>5 min)
  - COLD_START: nur beim Kaltstart (Default)
  - NONE: aus
  - Begrüßung kommt OHNE LLM-Generation (Direct-Append in Chat) aus dem Pool `app/src/main/assets/greeting-pool.json`

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

### v0.15.0 — Downloads-Sichtbarkeit + Vollbild + SoMi/-Wurzel + 14B-Catalog (in Push)
1. ✅ Settings → Downloads-Section: zeigt Embedder-Status (Installed/Running/Enqueued/Failed/NotPresent) + LLM-Liste mit "Anderes Modell laden"-CTA
2. ✅ ModelCatalogScreen — Modellwechsel mitten im Betrieb möglich
3. ✅ DataBrowserScreen — In-App File-Tree für SoMi/, Datei-Teilen via FileProvider
4. ✅ Multi-URL-Fallback in EmbeddingModelPart (HF primär, GH-Release sekundär)
5. ✅ GH-Release-Workflow `mirror-embedder-assets.yml` lädt Assets unter Tag `embedder-assets` hoch
6. ✅ Bundled-Asset-Seed-Pfad für tokenizer.json (Datei selbst NICHT eingecheckt — Hinweis in `assets/embedder/README.md` wie nachladen)
7. ✅ Begrüßungs-Feature mit 3-Mode-Toggle und greeting-pool.json
8. ✅ Echter Vollbild-Modus über `WindowInsetsControllerCompat`
9. ✅ SoMi/-Wurzelverzeichnis + StorageMigrator
10. ✅ 14B Q3 + Q4 im ModelCatalog mit verifizierten SHAs
11. ✅ KV-Trample-Fix in selectModel (cancel generationJob vor load)
12. ⏳ Resilienz-Härtung (Fail-Fast 404/410, Self-Heal 416, Cap-Retries 5xx) — verschoben nach v0.15.1
13. ⏳ SAF-Manuell-Import als Tertiär-Pfad — verschoben nach v0.16.0
14. ⏳ Verify-on-Launch (alle 7 Tage SHA-Check) — verschoben nach v0.16.0

### v0.15.1 — Resilienz-Härtung + 14B-Picker-UI
1. ResumableDownloader: Fail-Fast bei 404/410, Self-Heal bei 416, Cap-Retries bei 5xx
2. 14B-Picker-UI mit Quant-Tooltips (Q3 = sicherer, Q4 = besser+knapp)
3. YELLOW-Tier-Songbird-Confirm "Knapper RAM. Wirklich Q4?" mit OOM-Fallback per User-Confirm

### v0.16.0 — Phase-3-Recall (M7+M8+M9)
1. M7 — Memory-Browser-UI in Settings (Akkordeon, Bullet-Edit, Verschieben)
2. M8 — Recall + RAG-Injection + Memory-Chip am Antwort-Bubble
3. M9 — TopicClassifier (LLM-Pass mit Disambiguation)
4. SAF-Manuell-Import als Tertiär-Download-Pfad
5. Verify-on-Launch (7-Tage-SHA-Check)
6. SPEC §7 Tier-Formel-Recalibration
7. ObjectBox unter SoMi/-Wurzel verschieben (mit FUSE-Risk-Verifizierung)

### v0.17.0+ — Phase-3-Rest
1. M10 — Auto-Toggle in Settings
2. KIWIX-AAR + libkiwix
3. Notes-Corpus + Watcher
4. `bootstrap-soul.sh` + `initial-memory.json`-Loader
5. **Phase-3-Akzeptanztest erfüllen** ("So-Mi erinnert sich" + "KIWIX zitiert Quelle")

### Phase 4 — neu zu konzipieren
Online-Boost ist out (User-Entscheidung). Tools (12 aus SPEC §9) bleiben auf der Liste. Intent-Router lokal-only.

### Phase 5 — Voice + In-App-Updater
Wie SPEC.

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
