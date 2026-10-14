# So-Mi — Handover-Dokument
**Erstellt: 2026-10-01 | Übergabe von Sonnet 4.6 → Opus 5.5**

Dieses Dokument ist die vollständige Übergabe für eine neue Claude-Session die die Entwicklung von So-Mi fortführt. Lies es vollständig bevor du irgendeine Datei öffnest oder Code schreibst.

---

## 1. Was ist So-Mi

Offline-first Android-Assistentin (Sideload-only, kein Play Store) für HONOR Magic V2 (16 GB RAM). Persönlichkeit basiert auf Songbird/So-Mi aus Cyberpunk 2077: Phantom Liberty. Vollständig lokal: kein Account, keine Cloud, keine Telemetrie.

- **Repo:** `C:\Code\claude\apps\so-mi` (GitHub: `Labushuya/so-mi`)
- **Stack:** Kotlin/Compose Material3, AGP 8.5.2, Kotlin 2.0.21, NDK r27, arm64-v8a
- **LLM:** Qwen2.5 7B Q4_K_M via ARM InferenceEngine (llama.cpp JNI), CPU-only
- **Embedding:** paraphrase-multilingual-MiniLM-L12-v2, ONNX Runtime 1.18
- **Vektorspeicher:** ObjectBox 4.0.3, HNSW-Index (384 dim, Cosine)
- **Aktuelle stabile Version:** **v0.64.2**

---

## 2. Kritische Non-Negotiables (sofort lesen, nie brechen)

| Regel | Detail |
|---|---|
| **soul/soul.md ist alleinige Persönlichkeitsquelle** | Fest als System-Prefix, niemals via RAG, niemals paraphrasiert |
| **Keine applicationIdSuffix** | Eine applicationId für alle Build-Types (Updater-Invariante) |
| **CI-Keystore committed in Klartext** | `keystore/ci.keystore`, Passwort `ci-password-public` — NICHT rotieren |
| **versionCode = 10000 + GITHUB_RUN_NUMBER** | Nie hardcoden |
| **Keine Telemetrie/Analytics/Crashlytics** | Privacy ist ein Feature |
| **Claude API optional** | Offline muss standalone funktionieren |
| **MAX_SYSTEM_PROMPT_CHARS = 1200** | soul.md auf 1200 Zeichen kappen → ~435 Token Prefill. War mal 4096 → 105s Antwortzeit. Nie wieder erhöhen ohne Messung. |
| **maxTokens = 512 default** | Halbiert in v0.62.0. Ausreichend für normale Antworten. |

---

## 3. Lokale Situation jetzt (2026-10-01)

### Git-Status
```
Branch: main (up-to-date mit origin/main)
Uncommitted: fix_quotes.mjs, fix_quotes2.mjs (können gelöscht werden)
```

### Letzter Release: v0.64.2
ROADMAP.md ist auf diesem Stand — lies sie als erstes jede Session.

### fix_quotes.mjs / fix_quotes2.mjs
Diese zwei Dateien liegen im Repo-Root uncommitted. Sie sind **einmalige Werkzeuge** die typografische Anführungszeichen (U+201C/201D/201E) in `SettingsScreen.kt` und `ModelCatalogScreen.kt` repariert haben. Sie wurden **bereits ausgeführt** (keine typografischen Quotes mehr in den Zieldateien). Sie können sicher gelöscht oder ignoriert werden — sie sind kein Teil des Builds und müssen nicht committed werden.

---

## 4. Architektur — Multi-Module Gradle

```
android/
├── app/               Compose UI · Navigation · MainActivity · Hilt-Wiring
│                      UpdateChecker · LoadingScreen · SettingsScreen
├── core-llm/          LlamaContext Interface
├── core-llm-llama/    ARM InferenceEngine JNI (NDK r27, llama.cpp)
├── core-rag/          ObjectBox HNSW · ONNX Embedder · RagOrchestrator
│                      kiwix/: KiwixRepository, KiwixAutoOpen, KiwixSearchAdapter, KiwixEntry
├── core-tools/        ToolRouter (Regex Stage 1 aktiv, Stage 2+3 deaktiviert)
│                      12 Tools · BuiltInToolDefinitions · SearchKiwixTool
│                      planning/ → NOCH LEER (nächster Sprint!)
├── core-voice/        Piper TTS (deaktiviert — Memory-Conflict sherpa-onnx+ggml)
├── core-data/         Room · DataStore · StorageRoots · ModelCatalog · ZimCatalog
│                      download/: ModelDownloadWorker, ZimDownloadWorker, ResumableDownloader
├── core-ui/           ChatViewModel · SlashCommandRegistry · RAG-Integration
└── core-common/       TextEmbedder · MemorySearchPort · LlmCaller · KiwixSearchPort

soul/soul.md           Persönlichkeit — EINZIGE Quelle, fester System-Prefix
keystore/ci.keystore   CI-Signatur (öffentlich committed, intentional)
```

**Modul-Regel:** `core-*` darf nur `core-common` importieren. Ausnahme: `core-rag` importiert `core-data` (dokumentiert in CLAUDE.md).

---

## 5. Aktueller Technologie-Stand

### KIWIX-Integration (v0.63.x, vollständig)

**Dependency:** `org.kiwix:libkiwix:2.6.0` in `core-rag/build.gradle.kts`

**Dateien:**
- `core-rag/kiwix/KiwixRepository.kt` — ZIM öffnen, SuggestionSearcher, htmlToPlainText
- `core-rag/kiwix/KiwixAutoOpen.kt` — öffnet erstes ZIM beim Start (non-blocking)
- `core-rag/kiwix/KiwixSearchAdapter.kt` — implementiert KiwixSearchPort
- `core-rag/kiwix/KiwixEntry.kt` — DTO: title, path, plainText
- `core-common/kiwix/KiwixSearchPort.kt` — Interface für core-tools
- `core-tools/kiwix/SearchKiwixTool.kt` — Tool: @lexikon/@wörterbuch/"was bedeutet"
- `core-data/ZimCatalog.kt` — WIKTIONARY_DE: 1.2 GB, SHA256: `947e4f17...`
- `core-data/StorageRoots.zim()` — SoMi/zim/ Verzeichnis
- `core-data/download/ZimDownloadWorker.kt` — @HiltWorker, Resume, SHA-256-Verify

**AndroidManifest.xml:** `tools:replace="android:allowBackup"` in `<application>` — notwendig wegen libkiwix-Konflikt (libkiwix hat allowBackup=true, So-Mi hat false).

**RAG-Integration:** `RagOrchestrator.recallForPrompt()` läuft Memory + KIWIX **parallel** via `coroutineScope { async {} }`. `extractSearchTerm()` nimmt längstes Nicht-Stopwort aus dem Input.

**Wichtig:** Aktuell nur `SuggestionSearcher` (Titel-Prefix). Xapian-FTS ist vorbereitet aber nicht aktiviert (→ KIWIX Phase 2).

### Updater (v0.64.x, vollständig)

`UpdateChecker.kt`:
- `DownloadState`: `Progress(percent)`, `Paused(percent)`, `Done`, `Failed`
- `resumeExistingDownload(context, versionName)`: prüft SharedPrefs auf gespeicherte DM-ID → re-attached bei laufendem/fertigem Download (App-Kill-Survival)
- `cancelDownload()`: `DownloadManager.remove()` + SharedPrefs leeren (pauseDownload ist keine public API!)
- `TIMEOUT_MS = 600_000L` (10 Minuten)
- SharedPrefs: `"updater"` → Keys `active_download_id` + `active_download_version`

### Glitch-Übergang (v0.64.2, gefixt)

`LoadingScreen.kt`: Äußerer Box hat permanenten `Obsidian`-Hintergrund, innerer Box bekommt `.alpha(screenAlpha)`. Verhindert dass der Chat-Screen beim Fade-out durchschimmert (vorher: `.alpha()` vor `.background()` → transparenter Background).

### Tool-Router

`ToolRouter.kt`: Stage 1 (Regex) aktiv. Stage 2 (Embedding-Cosine) und Stage 3 (LLM-PlanPass) **deaktiviert** — ONNX/llama.cpp Concurrent-Crash (SIGABRT auf Magic V2).

### SlashCommandRegistry

`AT_COMMANDS` enthält jetzt `@lexikon` und `@wörterbuch` (Kategorie "@ Wissen"). TTS-Doppel-Speak-Bug gefixt: `hasSpooken = remember(text) { mutableStateOf(false) }` in `AssistantBubble`.

---

## 6. Nächster Sprint: v0.65.0 — Agentic Planning Layer

**Das ist der vereinbarte nächste große Schritt.** Vollständig designed, noch kein Code geschrieben.

### Kernidee

Vor jedem LLM-Aufruf läuft ein **Planning-Pass** (<1ms, kein LLM-Call) der einen `QueryPlan` produziert. Dieser steuert alle nachgelagerten Entscheidungen automatisch.

```
User-Eingabe → QueryPlanner.plan() → QueryPlan
                                          ↓
                    RagOrchestrator.recallForPrompt(plan)  ← plan-aware
                    Memory + SessionKnowledgeCache + KIWIX (plan-gesteuert)
                                          ↓
                    llama.generate(maxTokens = plan.effectiveMaxTokens)
                                          ↓ (bei LONG)
                    ChunkBoundary → "▸ Teil 2"-Button
```

### Neue Dateien zu erstellen

```
android/core-tools/src/main/kotlin/io/somi/tools/planning/QueryPlan.kt
android/core-tools/src/main/kotlin/io/somi/tools/planning/QueryPlanner.kt
android/core-common/src/main/kotlin/io/somi/common/llm/ChunkBoundary.kt
android/core-rag/src/main/kotlin/io/somi/rag/SessionKnowledgeCache.kt
```

### Zu ändernde Dateien

```
android/core-ui/src/main/kotlin/io/somi/ui/chat/ChatViewModel.kt
  → runGeneration() bekommt QueryPlan, plan-adaptive maxTokens

android/core-rag/src/main/kotlin/io/somi/rag/RagOrchestrator.kt
  → recallForPrompt(plan: QueryPlan) plan-aware, schreibt in SessionKnowledgeCache

android/app/src/main/kotlin/io/somi/app/MainActivity.kt
  → "▸ Weiter [Teil 2]"-Button in AssistantBubble
```

### QueryPlan-Datenstruktur

```kotlin
data class QueryPlan(
    val mode: ResponseMode = ResponseMode.NORMAL,
    val kiwixPriority: KiwixPriority = KiwixPriority.AUTO,
    val forceMemoryRecall: Boolean = false,
    val synthesisMode: Boolean = false,
    val factCheckHint: Boolean = false,
    val estimatedComplexity: Float = 0.0f,
) {
    enum class ResponseMode { NORMAL, LONG, CONVERSATIONAL }
    enum class KiwixPriority { SKIP, AUTO, FORCED }

    val effectiveMaxTokens: Int get() = when (mode) {
        ResponseMode.LONG -> 1024
        ResponseMode.CONVERSATIONAL -> 256
        ResponseMode.NORMAL -> 512
    }
}
```

### QueryPlanner-Heuristik (vollständige Implementierung)

```kotlin
@Singleton
class QueryPlanner @Inject constructor() {
    companion object {
        private const val LONG_WORD_THRESHOLD = 12
        private const val SHORT_WORD_THRESHOLD = 5

        private val LONG_TRIGGERS = Regex("""(?i)(erkl[äa]r|beschreib|aufz[äa]hl|unterschied|vergleich|wie funk|was sind|schreib (mir|einen)|wie (genau|funktioniert)|vollst[äa]ndig|ausf[üu]hrlich|detail|zusammenfass|liste (alle|mir)|nenn (alle|mir)|f[üu]r und wider|vor- und nachteile|geschichte (von|des))""")
        private val KIWIX_FORCED = Regex("""(?i)(was bedeutet|was hei[ßs]t|definier|erkl[äa]r das wort|definition|bedeutung|herkunft des worts?|etymologie|@lexikon|@w[öo]rterbuch|das wort\s+\w|was ist ein\s+\w)""")
        private val KIWIX_SKIP = Regex("""(?i)(wie geht|danke|super|ok|okay|was denkst du|was meinst du|deine meinung|erzähl mir (was|etwas) (über dich|von dir)|wie ist dein|wie findest du)""")
        private val FACTUAL = Regex("""(?i)(ist es wahr|stimmt es|ist das richtig|wann (genau|war|wurde)|wie viele|wie viel|wie hoch|wie lang|wer (hat|ist|war)|welches (jahr|datum))""")
        private val CONVERSATIONAL = Regex("""(?i)(^(hi|hey|hallo|na|moin|guten|servus)|wie geht|was machst du|langweil|red(e)? mit mir|erzähl|witz|quatsch)""")
    }

    fun plan(userText: String): QueryPlan {
        val lower = userText.trim().lowercase()
        val wordCount = userText.trim().split(Regex("\\s+")).size

        val mode = when {
            CONVERSATIONAL.containsMatchIn(lower) -> QueryPlan.ResponseMode.CONVERSATIONAL
            LONG_TRIGGERS.containsMatchIn(lower) || wordCount > LONG_WORD_THRESHOLD -> QueryPlan.ResponseMode.LONG
            else -> QueryPlan.ResponseMode.NORMAL
        }
        val kiwixPriority = when {
            KIWIX_FORCED.containsMatchIn(lower) -> QueryPlan.KiwixPriority.FORCED
            KIWIX_SKIP.containsMatchIn(lower) || mode == QueryPlan.ResponseMode.CONVERSATIONAL -> QueryPlan.KiwixPriority.SKIP
            else -> QueryPlan.KiwixPriority.AUTO
        }
        return QueryPlan(
            mode = mode,
            kiwixPriority = kiwixPriority,
            forceMemoryRecall = wordCount < SHORT_WORD_THRESHOLD && kiwixPriority == QueryPlan.KiwixPriority.SKIP,
            synthesisMode = mode == QueryPlan.ResponseMode.LONG && kiwixPriority == QueryPlan.KiwixPriority.FORCED,
            factCheckHint = FACTUAL.containsMatchIn(lower) && kiwixPriority != QueryPlan.KiwixPriority.FORCED,
            estimatedComplexity = run {
                var s = 0f
                if (mode == QueryPlan.ResponseMode.LONG) s += 0.4f
                if (wordCount > 20) s += 0.2f
                s.coerceIn(0f, 1f)
            }
        )
    }
}
```

### SessionKnowledgeCache

```kotlin
@Singleton
class SessionKnowledgeCache @Inject constructor() {
    private data class CachedEntry(val term: String, val snippet: String, val source: String)

    // LRU, max 5 Einträge × 300 Zeichen = ~300 Token Budget
    private val cache = object : LinkedHashMap<String, CachedEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CachedEntry>) = size > 5
    }

    fun put(term: String, snippet: String, source: String) {
        cache[term.trim().lowercase()] = CachedEntry(term, snippet.take(300), source)
    }

    fun recall(query: String): String? {
        if (cache.isEmpty()) return null
        val q = query.lowercase()
        val hits = cache.values.filter { e ->
            q.contains(e.term.lowercase()) || e.term.lowercase().split(" ").any { it.length >= 4 && q.contains(it) }
        }
        if (hits.isEmpty()) return null
        return buildString {
            append("[Sitzungs-Wissen aus diesem Gespräch]:\n")
            var chars = 0
            for (h in hits) {
                val line = "[Quelle: ${h.source}] ${h.snippet}\n"
                if (chars + line.length > 600) break
                append(line); chars += line.length
            }
            append("\n")
        }.takeIf { it.length > 50 }
    }

    fun clear() = cache.clear()
}
```

### Integration in RagOrchestrator

```kotlin
// recallForPrompt() Signatur erweitern:
suspend fun recallForPrompt(userText: String = "", plan: QueryPlan = QueryPlan()): String?

// In buildKiwixBlock(): nach KIWIX-Treffern in SessionCache schreiben:
results.forEach { entry ->
    sessionCache.put(query, entry.plainText.take(300), "Wiktionary: ${entry.title}")
}

// In recallForPrompt(): SessionCache VOR KIWIX prüfen:
val sessionBlock: String? = sessionCache.recall(userText)

// kiwixPriority berücksichtigen:
when (plan.kiwixPriority) {
    QueryPlan.KiwixPriority.SKIP -> null
    QueryPlan.KiwixPriority.FORCED -> buildKiwixBlock(userText, maxResults = if (plan.synthesisMode) 5 else 3)
    QueryPlan.KiwixPriority.AUTO -> buildKiwixBlock(userText, maxResults = 3)
}
```

### Integration in ChatViewModel.runGeneration()

```kotlin
// Am Anfang von runGeneration():
val plan = queryPlanner.plan(userText)

// maxTokens plan-adaptiv:
val maxTokens = plan.effectiveMaxTokens

// factCheckHint als leading text:
if (plan.factCheckHint) {
    promptWithContext = "Antworte nur mit gesichertem Wissen. Wenn unsicher, sage es explizit.\n\n$promptWithContext"
}

// Nach Generation: Chunk-Boundary-Check
val isChunked = plan.mode == QueryPlan.ResponseMode.LONG && partial.length >= 900
if (isChunked && !endsWithSentence(partial)) {
    // hasContinuation = true → "▸ Weiter"-Button in UI
}
```

### ChunkBoundary

```kotlin
object ChunkBoundary {
    private val SENTENCE_END = Regex("""[.!?]\s*$""")
    private val PARAGRAPH_END = Regex("""\n\n\s*$""")

    fun findBoundary(text: String, minLength: Int = 400): Int? {
        if (text.length < minLength) return null
        val last = text.takeLast(200)
        val paraIdx = last.lastIndexOf("\n\n")
        if (paraIdx >= 0) return text.length - 200 + paraIdx + 2
        val sentIdx = SENTENCE_END.find(last)?.range?.last
        if (sentIdx != null) return text.length - 200 + sentIdx + 1
        return null
    }

    fun endsCleanly(text: String): Boolean =
        SENTENCE_END.containsMatchIn(text.takeLast(50)) || PARAGRAPH_END.containsMatchIn(text.takeLast(50))
}
```

---

## 7. Noch weiter in der Pipeline

Nach v0.65.0 in Prioritätsreihenfolge:

1. **KIWIX Phase 2 — Xapian-Volltextsuche**: `org.kiwix.libzim.Searcher` statt nur `SuggestionSearcher` → "Zeitwort" findet "Verb". Latenz: 200–800ms.
2. **Evidenz-Chips**: Tags unter Antwort-Bubble: `[Quelle: Wiktionary: X]` wenn KIWIX/Memory genutzt wurde.
3. **Wikipedia DE** (optional, ~22 GB): Nach Wiktionary-Stabilisierung. Sachfragen statt nur Wortschatz.
4. **Piper TTS Neuimplementierung**: Separate Process-Isolation (foreground service in eigenem Prozess) um sherpa-onnx/ggml-Konflikt zu umgehen. Aufgeschoben.

---

## 8. CI/CD-Prozess

Vollautomatisch — User muss nichts klicken:

```bash
# Feature-Branch:
git checkout -b feat/... && git add ... && git commit -m "feat: ..." && git push -u origin feat/...
gh pr create --title "..." --body "..." && gh pr merge <nr> --squash

# release-please-PR abwarten:
sleep 15 && gh pr list --base main  # zeigt "chore(main): release X.Y.Z"
gh pr merge <nr> --squash

# Build beobachten:
gh run watch <run-id> --exit-status
```

**Build-Logik:** Zwei GitHub-Actions-Runs pro Push. Der erste (16s) ist release-please. Der zweite (~11min) ist der eigentliche Build — nur wenn `release_created == true`. `versionCode = 10000 + GITHUB_RUN_NUMBER`.

**Commit-Format:** Conventional Commits: `feat:` (minor bump), `fix:` (patch bump), `chore:`/`docs:` (kein Bump).

---

## 9. Bekannte Crashes/Bugs und ihre Fixes

| Bug | Fix | Status |
|---|---|---|
| Piper TTS SIGSEGV | PIPER_ENABLED=false in TtsHelper | ✅ Stabil als Android TTS Placeholder |
| Stage-2 Embedding + llama.cpp gleichzeitig | Stage-2 deaktiviert | ✅ Workaround |
| MAX_SYSTEM_PROMPT_CHARS=4096 → 105s Antwortzeit | Auf 1200 reverted | ✅ Fix |
| stop_generation_position double-count | Einzeiler in ai_chat.cpp | ✅ Fix |
| DownloadManager.pauseDownload() nicht public | cancelDownload() statt pause | ✅ Fix |
| libkiwix AndroidManifest allowBackup Konflikt | tools:replace in Manifest | ✅ Fix |
| TTS-Doppel-Speak beim Command-Popup öffnen | hasSpooken-Flag in AssistantBubble | ✅ Fix |
| Glitch-Übergang zeigt weißen Screen | Obsidian-Außen-Box in LoadingScreen | ✅ Fix |
| DownloadManager Channel-Loss bei App-Kill | resumeExistingDownload() + SharedPrefs | ✅ Fix |

---

## 10. Lokale Dateien die nicht committed sind

| Datei | Was | Aktion |
|---|---|---|
| `fix_quotes.mjs` | Werkzeug für typografische Quotes in SettingsScreen.kt — bereits ausgeführt | Kann gelöscht werden |
| `fix_quotes2.mjs` | Werkzeug für typografische Quotes in ModelCatalogScreen.kt — bereits ausgeführt | Kann gelöscht werden |

---

## 11. Projekt-spezifische Regeln für neue Session

1. **ROADMAP.md zuerst lesen** — nicht SPEC.md. ROADMAP gewinnt bei Konflikten.
2. **Git-Identity:** `git config user.email "labushuya@..."` per Repo, nie `--global`
3. **Keine ADB-Anweisungen** — Handy wird nicht verbunden. Alles muss in-app testbar sein.
4. **Model-SHAs immer live verifizieren** — Agents fabrizieren plausible SHAs. Vor Aufnahme in ModelCatalog.ALL live gegen HF verifizieren.
5. **Workflow-Tool bei nicht-trivialem Bündel** — parallele Agents nutzen
6. **tl;dr nach jedem Push** — 3 Zeilen: was/benefit/next
7. **Stable-Einstufung** — erst nach positivem User-Test-Feedback

---

## 12. Empfohlener Einstieg für Opus 5.5

```
1. git checkout main && git pull
2. ROADMAP.md lesen (aktuell auf v0.64.2-Stand)
3. Implementiere v0.65.0 Agentic Planning Layer:
   - P1: QueryPlan.kt + QueryPlanner.kt in core-tools/planning/
   - P2: SessionKnowledgeCache.kt in core-rag/
   - P3: ChunkBoundary.kt in core-common/llm/
   - P4: RagOrchestrator.kt + ChatViewModel.kt integrieren
   - P5: MainActivity.kt — "▸ Weiter"-Button
4. Bei Fragen zur Architektur: dieser Handover-Dokument hat alle Details
```

**Kontext dieser Konversations-Session:** Die Sitzung begann mit Sonnet 4.6 und deckt den gesamten Entwicklungszeitraum Juli 2026. Die letzten 3 Monate (bis Oktober 2026) waren eine Pause — kein Code wurde geändert. Der Agentic Layer wurde in der Sitzung vom 2026-07-07 vollständig geplant (Workflow-Output), aber nicht implementiert.

---

*Handover erstellt: 2026-10-01 | So-Mi v0.64.2 stable | Nächstes Feature: v0.65.0 Agentic Planning Layer*
