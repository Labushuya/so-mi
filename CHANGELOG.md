# Changelog

## [0.46.11](https://github.com/Labushuya/so-mi/compare/v0.46.10...v0.46.11) (2026-06-23)


### Bug Fixes

* Alarm als Foreground-Worker — Ringtone + Vibration auch bei gesperrtem Display ([#212](https://github.com/Labushuya/so-mi/issues/212)) ([a2e0d46](https://github.com/Labushuya/so-mi/commit/a2e0d46abf554e28d745fc560cd61c5a328d2034))

## [0.46.10](https://github.com/Labushuya/so-mi/compare/v0.46.9...v0.46.10) (2026-06-23)


### Bug Fixes

* build — hilt-work Dependency für AlarmWorker ([#210](https://github.com/Labushuya/so-mi/issues/210)) ([a4e3004](https://github.com/Labushuya/so-mi/commit/a4e300443ed76866660deed825c12970b1262417))

## [0.46.9](https://github.com/Labushuya/so-mi/compare/v0.46.8...v0.46.9) (2026-06-23)


### Bug Fixes

* Alarm auf WorkManager, Exchange Rate Currency-Map statt 3-Letter-Regex ([#208](https://github.com/Labushuya/so-mi/issues/208)) ([c840d7b](https://github.com/Labushuya/so-mi/commit/c840d7b1c48721e0e856a26ad40f951a2352f79c))

## [0.46.8](https://github.com/Labushuya/so-mi/compare/v0.46.7...v0.46.8) (2026-06-23)


### Bug Fixes

* build — Notification-Sound Typ-Fehler behoben ([#202](https://github.com/Labushuya/so-mi/issues/202)) ([6f8758f](https://github.com/Labushuya/so-mi/commit/6f8758fa26620c5796bd3fa6b736961b16c6ad65))
* Stage-2-Embedding deaktiviert — verhindert ONNX+LLM-Concurrent-Crash ([#206](https://github.com/Labushuya/so-mi/issues/206)) ([b4567b6](https://github.com/Labushuya/so-mi/commit/b4567b6f6d6f661c861faff44b80b60e74e157b6))
* Tool-History-Filter bei deaktiviertem Tool, matchesAnyToolPattern für Regex-Check ([#205](https://github.com/Labushuya/so-mi/issues/205)) ([2caccd4](https://github.com/Labushuya/so-mi/commit/2caccd4aaa235f965f9b5c314ba76d0c944b58ff))

## [0.46.7](https://github.com/Labushuya/so-mi/compare/v0.46.6...v0.46.7) (2026-06-23)


### Bug Fixes

* build — Notification-Sound Typ-Fehler behoben ([#202](https://github.com/Labushuya/so-mi/issues/202)) ([6f8758f](https://github.com/Labushuya/so-mi/commit/6f8758fa26620c5796bd3fa6b736961b16c6ad65))
* Tool-History gefiltert, Zahlwörter im Alarm, Ton+Vibration bei Alarm ([#200](https://github.com/Labushuya/so-mi/issues/200)) ([1a00750](https://github.com/Labushuya/so-mi/commit/1a00750d6393952d37c5ac3301e851811a2ebcc2))
* Tool-History-Filter bei deaktiviertem Tool, matchesAnyToolPattern für Regex-Check ([#205](https://github.com/Labushuya/so-mi/issues/205)) ([2caccd4](https://github.com/Labushuya/so-mi/commit/2caccd4aaa235f965f9b5c314ba76d0c944b58ff))

## [0.46.6](https://github.com/Labushuya/so-mi/compare/v0.46.5...v0.46.6) (2026-06-23)


### Bug Fixes

* build — Notification-Sound Typ-Fehler behoben ([#202](https://github.com/Labushuya/so-mi/issues/202)) ([6f8758f](https://github.com/Labushuya/so-mi/commit/6f8758fa26620c5796bd3fa6b736961b16c6ad65))
* Tool-History gefiltert, Zahlwörter im Alarm, Ton+Vibration bei Alarm ([#200](https://github.com/Labushuya/so-mi/issues/200)) ([1a00750](https://github.com/Labushuya/so-mi/commit/1a00750d6393952d37c5ac3301e851811a2ebcc2))
* USE_EXACT_ALARM auto-granted, Alarm-Fallback, Tool-History-Blöcke herausgefiltert ([#198](https://github.com/Labushuya/so-mi/issues/198)) ([2db8402](https://github.com/Labushuya/so-mi/commit/2db8402d03a088ae8f2b55cd12860ac9976de92f))

## [0.46.5](https://github.com/Labushuya/so-mi/compare/v0.46.4...v0.46.5) (2026-06-19)


### Bug Fixes

* Stage-3-Crash deaktiviert, create_reminder→set_alarm, Alarm-Label + Hinweis ([#196](https://github.com/Labushuya/so-mi/issues/196)) ([c725e22](https://github.com/Labushuya/so-mi/commit/c725e22b3270befe6ed6572ac9ea46b77c02d50c))

## [0.46.4](https://github.com/Labushuya/so-mi/compare/v0.46.3...v0.46.4) (2026-06-17)


### Bug Fixes

* build — collectAsState Import hinzugefügt ([#194](https://github.com/Labushuya/so-mi/issues/194)) ([562d92b](https://github.com/Labushuya/so-mi/commit/562d92bd72f685c938294c59059f844c81968253))

## [0.46.3](https://github.com/Labushuya/so-mi/compare/v0.46.2...v0.46.3) (2026-06-17)


### Bug Fixes

* build — collectAsState statt collectAsStateWithLifecycle für plain Flow ([#192](https://github.com/Labushuya/so-mi/issues/192)) ([fc195c8](https://github.com/Labushuya/so-mi/commit/fc195c8485dca4800d82b2d9c673e07959741e55))

## [0.46.2](https://github.com/Labushuya/so-mi/compare/v0.46.1...v0.46.2) (2026-06-17)


### Bug Fixes

* build — ToolModeSection Aufruf mit toolEnabled + onToolToggle ([#190](https://github.com/Labushuya/so-mi/issues/190)) ([b746a67](https://github.com/Labushuya/so-mi/commit/b746a67dea671d7ce53cd0680f2f757cc35fd6e3))

## [0.46.1](https://github.com/Labushuya/so-mi/compare/v0.46.0...v0.46.1) (2026-06-17)


### Bug Fixes

* build — ToolDefinition Import-Pfad ([#188](https://github.com/Labushuya/so-mi/issues/188)) ([c3540a5](https://github.com/Labushuya/so-mi/commit/c3540a547d02115dea7c603e279c4c97076a1bd4))

## [0.46.0](https://github.com/Labushuya/so-mi/compare/v0.45.1...v0.46.0) (2026-06-17)


### Features

* v0.46.0 — per-Tool-Toggle, create_reminder, get_exchange_rate, news_briefing ([#186](https://github.com/Labushuya/so-mi/issues/186)) ([589d92c](https://github.com/Labushuya/so-mi/commit/589d92c2a165cf1db73439e2e3ca1c17e588ec49))

## [0.45.1](https://github.com/Labushuya/so-mi/compare/v0.45.0...v0.45.1) (2026-06-17)


### Bug Fixes

* Kategorie-Suche matcht echte Dateinamen — Custom-Kategorien mit Emoji funktionieren ([#184](https://github.com/Labushuya/so-mi/issues/184)) ([cbdfbc8](https://github.com/Labushuya/so-mi/commit/cbdfbc817de022cdbb5911d4c72122ec2035f8b5))

## [0.45.0](https://github.com/Labushuya/so-mi/compare/v0.44.3...v0.45.0) (2026-06-17)


### Features

* Wetter versteht 'in N Tagen'/Wochenende/Wochentag, Erinnerungen nach Kategorie abrufbar ([#182](https://github.com/Labushuya/so-mi/issues/182)) ([64e81c1](https://github.com/Labushuya/so-mi/commit/64e81c1ed6fb693f0a4697dce13a9d697e81be41))

## [0.44.3](https://github.com/Labushuya/so-mi/compare/v0.44.2...v0.44.3) (2026-06-16)


### Bug Fixes

* Wetter versteht 'morgen'/'heute', History bei jedem Tool-Call unterdrückt ([#180](https://github.com/Labushuya/so-mi/issues/180)) ([7572156](https://github.com/Labushuya/so-mi/commit/7572156d2e171e8ff67cc3c5e93b9d2864e523ec))

## [0.44.2](https://github.com/Labushuya/so-mi/compare/v0.44.1...v0.44.2) (2026-06-16)


### Bug Fixes

* Web-Consent entfernt, History bei Tool-Calls unterdrückt, Prompt-Framing vereinfacht ([#178](https://github.com/Labushuya/so-mi/issues/178)) ([c7b0a9a](https://github.com/Labushuya/so-mi/commit/c7b0a9af741c8763daa584d61ae02ca8238974f1))

## [0.44.1](https://github.com/Labushuya/so-mi/compare/v0.44.0...v0.44.1) (2026-06-16)


### Bug Fixes

* 7 Tool-Bugs — State-Leak, falscher Dispatcher, Timeout, leere Params, Web-Stripping ([#176](https://github.com/Labushuya/so-mi/issues/176)) ([56a9200](https://github.com/Labushuya/so-mi/commit/56a92006181a5fe345cc4d334afa91919f3df6ff))

## [0.44.0](https://github.com/Labushuya/so-mi/compare/v0.43.3...v0.44.0) (2026-06-16)


### Features

* Tool-Modus-Toggle in Settings — Kompakt vs. System-Prompt (experimentell) ([#174](https://github.com/Labushuya/so-mi/issues/174)) ([d1a6040](https://github.com/Labushuya/so-mi/commit/d1a6040a91eccd6a2e23df94bb423b3f81b7027c))

## [0.43.3](https://github.com/Labushuya/so-mi/compare/v0.43.2...v0.43.3) (2026-06-15)


### Bug Fixes

* Tool-Fehler — Timeout 15s, Fehler-Strings als error, [@erinnerung](https://github.com/erinnerung) kein ONNX ([#172](https://github.com/Labushuya/so-mi/issues/172)) ([a5d743d](https://github.com/Labushuya/so-mi/commit/a5d743de96fbb51122fbc0e5eaff13e1d9b2aa07))

## [0.43.2](https://github.com/Labushuya/so-mi/compare/v0.43.1...v0.43.2) (2026-06-15)


### Bug Fixes

* Tool-Ergebnisse werden jetzt genutzt — Kontext-Framing, Tool-Chip, Logging ([#170](https://github.com/Labushuya/so-mi/issues/170)) ([458dbe7](https://github.com/Labushuya/so-mi/commit/458dbe780c02ad63de530363538cdf49ef1bac33))

## [0.43.1](https://github.com/Labushuya/so-mi/compare/v0.43.0...v0.43.1) (2026-06-13)


### Bug Fixes

* build — LlmCaller Interface statt Lambda-Injection (Hilt-Typlöschung) ([#168](https://github.com/Labushuya/so-mi/issues/168)) ([0bd8cf6](https://github.com/Labushuya/so-mi/commit/0bd8cf61effda348ef5ef72b554d8a0b8ff0a159))

## [0.43.0](https://github.com/Labushuya/so-mi/compare/v0.42.1...v0.43.0) (2026-06-13)


### Features

* Phase 4 Tool-System — ToolRouter (3-Stufen), get_weather, search_web, search_memory ([#166](https://github.com/Labushuya/so-mi/issues/166)) ([457b4d1](https://github.com/Labushuya/so-mi/commit/457b4d1032d7878fcfecef419243cbd44e4c4060))

## [0.42.1](https://github.com/Labushuya/so-mi/compare/v0.42.0...v0.42.1) (2026-06-13)


### Bug Fixes

* build — EmbeddingBackfillWorker internal entfernt ([#164](https://github.com/Labushuya/so-mi/issues/164)) ([2fd040d](https://github.com/Labushuya/so-mi/commit/2fd040dc61e37dcb26c3af1a75fad229dd9a4514))

## [0.42.0](https://github.com/Labushuya/so-mi/compare/v0.41.0...v0.42.0) (2026-06-13)


### Features

* Erinnerungs-Rückmeldung mit Kategorie, Backfill-Worker, Embedder-Hinweis korrigiert ([#162](https://github.com/Labushuya/so-mi/issues/162)) ([5de21fc](https://github.com/Labushuya/so-mi/commit/5de21fcda0b5a4e456d57dcacaf1719c83657622))

## [0.41.0](https://github.com/Labushuya/so-mi/compare/v0.40.3...v0.41.0) (2026-06-13)


### Features

* HNSW-Recall — ObjectBox nearestNeighbors wenn Embedder aktiv, .md-Scan als Fallback ([#160](https://github.com/Labushuya/so-mi/issues/160)) ([4398d30](https://github.com/Labushuya/so-mi/commit/4398d30c22c62445468f9a0e8359e60426d9b553))

## [0.40.3](https://github.com/Labushuya/so-mi/compare/v0.40.2...v0.40.3) (2026-06-13)


### Bug Fixes

* Backup-Fehler — WAL-Checkpoint entfernt, alle drei DB-Dateien werden zusammen kopiert ([#158](https://github.com/Labushuya/so-mi/issues/158)) ([b378bc3](https://github.com/Labushuya/so-mi/commit/b378bc32cc5bd2681aeca7eeac75273ed76181b0))

## [0.40.2](https://github.com/Labushuya/so-mi/compare/v0.40.1...v0.40.2) (2026-06-13)


### Bug Fixes

* build — SoMiDatabase nicht in core-ui, WAL-Checkpoint über ChatRepository.checkpointWal() ([#156](https://github.com/Labushuya/so-mi/issues/156)) ([6d630db](https://github.com/Labushuya/so-mi/commit/6d630dbd5f39eaeaa54590127c5dab4f55e69bb6))

## [0.40.1](https://github.com/Labushuya/so-mi/compare/v0.40.0...v0.40.1) (2026-06-13)


### Bug Fixes

* build — LlamaContext aus core-rag entfernt, LLM-Klassifizierung in ChatViewModel ([#154](https://github.com/Labushuya/so-mi/issues/154)) ([6e298d2](https://github.com/Labushuya/so-mi/commit/6e298d219e23ebe95e0f08768ee785685121befa))

## [0.40.0](https://github.com/Labushuya/so-mi/compare/v0.39.2...v0.40.0) (2026-06-12)


### Features

* LLM-Klassifizierer für Erinnerungen + Gesprächskontext als Sliding-Window ([#152](https://github.com/Labushuya/so-mi/issues/152)) ([d5a643b](https://github.com/Labushuya/so-mi/commit/d5a643b681b3d4f0330bb312c2f5a388c41d7cb3))

## [0.39.2](https://github.com/Labushuya/so-mi/compare/v0.39.1...v0.39.2) (2026-06-12)


### Bug Fixes

* Backup enthält somi.db — WAL-Checkpoint vor Kopieren, SoMiDatabase injiziert ([#150](https://github.com/Labushuya/so-mi/issues/150)) ([95fdcd2](https://github.com/Labushuya/so-mi/commit/95fdcd2a1682276b188559ad94cd2497d58a2916))

## [0.39.1](https://github.com/Labushuya/so-mi/compare/v0.39.0...v0.39.1) (2026-06-12)


### Bug Fixes

* build — verwaiste Zeilen in ChatViewModel.submit entfernt ([#148](https://github.com/Labushuya/so-mi/issues/148)) ([0d8a677](https://github.com/Labushuya/so-mi/commit/0d8a677225e5d9e1460005c5735118fa14df7bf4))

## [0.39.0](https://github.com/Labushuya/so-mi/compare/v0.38.1...v0.39.0) (2026-06-12)


### Features

* Backup mit Chat-Verlauf, Import-Bestätigung, Chat-Commands /search /clear /rename /archive ([#146](https://github.com/Labushuya/so-mi/issues/146)) ([f251edc](https://github.com/Labushuya/so-mi/commit/f251edc324a9224f86de7e9d05d9e3f8c7800666))

## [0.38.1](https://github.com/Labushuya/so-mi/compare/v0.38.0...v0.38.1) (2026-06-12)


### Bug Fixes

* build — clearAll() in ChatRepository wiederhergestellt ([#144](https://github.com/Labushuya/so-mi/issues/144)) ([d1dd515](https://github.com/Labushuya/so-mi/commit/d1dd515340b5431cd381bbe78d0b0a89372bb7a5))

## [0.38.0](https://github.com/Labushuya/so-mi/compare/v0.37.2...v0.38.0) (2026-06-12)


### Features

* Chat-Suche, OOM-Crash-Banner, Empty-State-Fix ([#142](https://github.com/Labushuya/so-mi/issues/142)) ([76d030c](https://github.com/Labushuya/so-mi/commit/76d030ca1ff5a1ff0d05e0f591535128736d8cb4))

## [0.37.2](https://github.com/Labushuya/so-mi/compare/v0.37.1...v0.37.2) (2026-06-12)


### Bug Fixes

* neuer Chat zeigt nur eigene Nachrichten, letzter Chat löschbar ([#140](https://github.com/Labushuya/so-mi/issues/140)) ([e4374c8](https://github.com/Labushuya/so-mi/commit/e4374c84029d30275e0086b0234227554461ed82))

## [0.37.1](https://github.com/Labushuya/so-mi/compare/v0.37.0...v0.37.1) (2026-06-12)


### Bug Fixes

* build — doppelter Room.databaseBuilder entfernt ([#138](https://github.com/Labushuya/so-mi/issues/138)) ([c4275fb](https://github.com/Labushuya/so-mi/commit/c4275fb604fb9b83921eb8309f17e519fa3c2449))

## [0.37.0](https://github.com/Labushuya/so-mi/compare/v0.36.2...v0.37.0) (2026-06-12)


### Features

* Multi-Chat — Gespräche anlegen, wechseln, löschen, umbenennen ([#136](https://github.com/Labushuya/so-mi/issues/136)) ([82fe024](https://github.com/Labushuya/so-mi/commit/82fe024440ea669d98621e5a96a641aa03fbf551))

## [0.36.2](https://github.com/Labushuya/so-mi/compare/v0.36.1...v0.36.2) (2026-06-12)


### Bug Fixes

* Pausieren-Button-Layout, Embedder löschen ohne Re-Download, klarere Button-Labels ([#133](https://github.com/Labushuya/so-mi/issues/133)) ([6f07320](https://github.com/Labushuya/so-mi/commit/6f0732072856200e9a45e9cd52886e7fa6559930))

## [0.36.1](https://github.com/Labushuya/so-mi/compare/v0.36.0...v0.36.1) (2026-06-12)


### Bug Fixes

* build — Mistral-Nemo vor ALL deklariert ([#131](https://github.com/Labushuya/so-mi/issues/131)) ([ff60642](https://github.com/Labushuya/so-mi/commit/ff6064262133ba2ded5eb74c20c7a8d4da419269))

## [0.36.0](https://github.com/Labushuya/so-mi/compare/v0.35.0...v0.36.0) (2026-06-12)


### Features

* Embedder-Band für alle Status, Mistral-Nemo 12B Q3+Q4 im Katalog ([#129](https://github.com/Labushuya/so-mi/issues/129)) ([4926b00](https://github.com/Labushuya/so-mi/commit/4926b00c6271fcbfcdf65dd48eaa0fd8073bce0a))

## [0.35.0](https://github.com/Labushuya/so-mi/compare/v0.34.0...v0.35.0) (2026-06-12)


### Features

* FAQ mit Suche, Embedder-Status-Fix nach Löschen ([#127](https://github.com/Labushuya/so-mi/issues/127)) ([d17488a](https://github.com/Labushuya/so-mi/commit/d17488a7649ec6be5adb52f5ffb1761b00e287a6))

## [0.34.0](https://github.com/Labushuya/so-mi/compare/v0.33.0...v0.34.0) (2026-06-12)


### Features

* Backup-Import, Setup-Guard-Banner wenn Embedder fehlt, ROADMAP aktualisiert ([#125](https://github.com/Labushuya/so-mi/issues/125)) ([3241e5d](https://github.com/Labushuya/so-mi/commit/3241e5d2483a754baae64e23203d32bbba1a086b))

## [0.33.0](https://github.com/Labushuya/so-mi/compare/v0.32.1...v0.33.0) (2026-06-12)


### Features

* Slash-Command-Popup mit Autocomplete und /-Button, Band auto-dismiss nach 5s ([#123](https://github.com/Labushuya/so-mi/issues/123)) ([da9c8ee](https://github.com/Labushuya/so-mi/commit/da9c8eed677aead18c66c0b3212587ec513b4925))

## [0.32.1](https://github.com/Labushuya/so-mi/compare/v0.32.0...v0.32.1) (2026-06-12)


### Bug Fixes

* build — @Composable auf enum class entfernt ([#121](https://github.com/Labushuya/so-mi/issues/121)) ([cdf248a](https://github.com/Labushuya/so-mi/commit/cdf248a464b2cad7394e078f050d310512e2dde0))

## [0.32.0](https://github.com/Labushuya/so-mi/compare/v0.31.2...v0.32.0) (2026-06-12)


### Features

* Test-Commands für Chat-Band, 4 Band-Typen, 12B im Staging-Catalog ([#119](https://github.com/Labushuya/so-mi/issues/119)) ([3572da0](https://github.com/Labushuya/so-mi/commit/3572da0934a62c6adfd6e7de7f7f21459ee737f5))

## [0.31.2](https://github.com/Labushuya/so-mi/compare/v0.31.1...v0.31.2) (2026-06-12)


### Bug Fixes

* build — überzählige geschweifte Klammer entfernt ([#117](https://github.com/Labushuya/so-mi/issues/117)) ([326dd2c](https://github.com/Labushuya/so-mi/commit/326dd2cf2863aa8013621273a14ce9080fb2b5a4))

## [0.31.1](https://github.com/Labushuya/so-mi/compare/v0.31.0...v0.31.1) (2026-06-12)


### Bug Fixes

* build — doppeltes @Composable auf ErrorBanner ([#115](https://github.com/Labushuya/so-mi/issues/115)) ([018f321](https://github.com/Labushuya/so-mi/commit/018f321d3ea9dbabc2f6a32a7591a93a56fb96f7))

## [0.31.0](https://github.com/Labushuya/so-mi/compare/v0.30.1...v0.31.0) (2026-06-12)


### Features

* Scroll via IME-Inset-State, System-Meldungen als Band, Keyword-Suche optisch verbessert ([#113](https://github.com/Labushuya/so-mi/issues/113)) ([3557a43](https://github.com/Labushuya/so-mi/commit/3557a4328c62e94c55371bd2977d4834045582cc))

## [0.30.1](https://github.com/Labushuya/so-mi/compare/v0.30.0...v0.30.1) (2026-06-12)


### Bug Fixes

* build — snapshotFlow Import und expliziter Typ ([#111](https://github.com/Labushuya/so-mi/issues/111)) ([2504c16](https://github.com/Labushuya/so-mi/commit/2504c161d20f32f05cbdbe6a59c73314e23fcb0d))

## [0.30.0](https://github.com/Labushuya/so-mi/compare/v0.29.3...v0.30.0) (2026-06-12)


### Features

* nur eine Antwort nach Trigger, Scroll-Fix mit snapshotFlow, Keywords ein-/ausklappbar mit Suche ([#109](https://github.com/Labushuya/so-mi/issues/109)) ([d5646c6](https://github.com/Labushuya/so-mi/commit/d5646c6d66257dfe3df81577c47caa9836379197))

## [0.29.3](https://github.com/Labushuya/so-mi/compare/v0.29.2...v0.29.3) (2026-06-12)


### Bug Fixes

* App-Crash — ViewCompat-Listener entfernt, Viewport-Änderung steuert Scroll ([#107](https://github.com/Labushuya/so-mi/issues/107)) ([d2d7515](https://github.com/Labushuya/so-mi/commit/d2d75155cd38a41f3b2c06dba84f0399a18928d2))

## [0.29.2](https://github.com/Labushuya/so-mi/compare/v0.29.1...v0.29.2) (2026-06-12)


### Bug Fixes

* Scroll-bei-Tastatur via ViewCompat, besserer Antwort-Kontext nach Trigger ([#105](https://github.com/Labushuya/so-mi/issues/105)) ([10d73b9](https://github.com/Labushuya/so-mi/commit/10d73b9d1410bf2e4ed98c7c124c07e56e290fa6))

## [0.29.1](https://github.com/Labushuya/so-mi/compare/v0.29.0...v0.29.1) (2026-06-11)


### Bug Fixes

* build — WindowInsets.ime.getBottom statt asPaddingValues ([#103](https://github.com/Labushuya/so-mi/issues/103)) ([f5b3dfc](https://github.com/Labushuya/so-mi/commit/f5b3dfc419d1766a6582eacc7ee3b612101b487b))

## [0.29.0](https://github.com/Labushuya/so-mi/compare/v0.28.0...v0.29.0) (2026-06-11)


### Features

* Emojis in Kategorienamen, Scroll-bei-Tastatur, Duplikat-Erkennung ([#101](https://github.com/Labushuya/so-mi/issues/101)) ([b3c4dd4](https://github.com/Labushuya/so-mi/commit/b3c4dd4c9ae4e3d07136c22f62929e5e23775617))

## [0.28.0](https://github.com/Labushuya/so-mi/compare/v0.27.0...v0.28.0) (2026-06-11)


### Features

* Keywords editieren und verschieben, deutlich getrennte UI-Sektionen ([#99](https://github.com/Labushuya/so-mi/issues/99)) ([f637a9f](https://github.com/Labushuya/so-mi/commit/f637a9f69870ccb354e6c9587fd6e154f50125d1))

## [0.27.0](https://github.com/Labushuya/so-mi/compare/v0.26.0...v0.27.0) (2026-06-11)


### Features

* Keywords für Kategorien direkt in den Erinnerungen pflegen ([#97](https://github.com/Labushuya/so-mi/issues/97)) ([35e6b33](https://github.com/Labushuya/so-mi/commit/35e6b3393e2773398d3024c18f0c567d28044808))

## [0.26.0](https://github.com/Labushuya/so-mi/compare/v0.25.1...v0.26.0) (2026-06-11)


### Features

* Keywords für eigene Kategorien selbst festlegen — per Ansage oder Settings ([#95](https://github.com/Labushuya/so-mi/issues/95)) ([20fb25b](https://github.com/Labushuya/so-mi/commit/20fb25be49af01169360b31293b40b9d8076c34c))

## [0.25.1](https://github.com/Labushuya/so-mi/compare/v0.25.0...v0.25.1) (2026-06-11)


### Bug Fixes

* eigene Kategorien haben Vorrang, Synonym-Erkennung für Beruf und mehr ([#93](https://github.com/Labushuya/so-mi/issues/93)) ([202e488](https://github.com/Labushuya/so-mi/commit/202e4887e80c41cfe0d251b6e5592aa4505e0452))

## [0.25.0](https://github.com/Labushuya/so-mi/compare/v0.24.2...v0.25.0) (2026-06-11)


### Features

* Kategorien umbenennen und löschen, eigene Kategorien bei Trigger erkannt, Backup erstellen ([#91](https://github.com/Labushuya/so-mi/issues/91)) ([948cb59](https://github.com/Labushuya/so-mi/commit/948cb597955b64f3cd8bb3908661574fea74bad7))

## [0.24.2](https://github.com/Labushuya/so-mi/compare/v0.24.1...v0.24.2) (2026-06-11)


### Bug Fixes

* build — doppeltes @Composable in TextInputDialog ([#89](https://github.com/Labushuya/so-mi/issues/89)) ([99eb19e](https://github.com/Labushuya/so-mi/commit/99eb19ebfa0fbaa694394d0edd255a76b58c9f4e))

## [0.24.1](https://github.com/Labushuya/so-mi/compare/v0.24.0...v0.24.1) (2026-06-11)


### Bug Fixes

* So-Mi erinnert sich auch an eigene Kategorien, Textfeld sichtbar, & in Kategoriename erlaubt ([#87](https://github.com/Labushuya/so-mi/issues/87)) ([4be10dd](https://github.com/Labushuya/so-mi/commit/4be10dd5202bcc34c22d0e8d93d62dae57f1b1f0))

## [0.24.0](https://github.com/Labushuya/so-mi/compare/v0.23.2...v0.24.0) (2026-06-11)


### Features

* Erinnerungen editieren und manuell anlegen ([#85](https://github.com/Labushuya/so-mi/issues/85)) ([7382044](https://github.com/Labushuya/so-mi/commit/73820444c7a70b88e04c608b9d6642ca2dbea1b5))

## [0.23.2](https://github.com/Labushuya/so-mi/compare/v0.23.1...v0.23.2) (2026-06-11)


### Bug Fixes

* alle Fakten einzeln absichern, zweiten Fakt nicht durch ersten Fehler verlieren ([#83](https://github.com/Labushuya/so-mi/issues/83)) ([f9686d3](https://github.com/Labushuya/so-mi/commit/f9686d3a1a7ec11b17ae088fdba85dd517c091f5))

## [0.23.1](https://github.com/Labushuya/so-mi/compare/v0.23.0...v0.23.1) (2026-06-10)


### Bug Fixes

* build — SongbirdButton mit benannten Argumenten ([#81](https://github.com/Labushuya/so-mi/issues/81)) ([f3f622b](https://github.com/Labushuya/so-mi/commit/f3f622b16025378c31297c8943db02bc2a06a5c7))

## [0.23.0](https://github.com/Labushuya/so-mi/compare/v0.22.0...v0.23.0) (2026-06-10)


### Features

* Fakten-Split korrigiert, eigene Kategorien anlegen, Settings zugeklappt ([#79](https://github.com/Labushuya/so-mi/issues/79)) ([aa7396a](https://github.com/Labushuya/so-mi/commit/aa7396ae4aa6b266011b09eb3c674fd354b5425f))

## [0.22.0](https://github.com/Labushuya/so-mi/compare/v0.21.2...v0.22.0) (2026-06-10)


### Features

* mehrere Fakten aus einem Satz extrahieren, automatisch nach Thema sortieren ([#77](https://github.com/Labushuya/so-mi/issues/77)) ([0837e74](https://github.com/Labushuya/so-mi/commit/0837e742a8a2929eb09a91e5beb9effcf49ed069))

## [0.21.2](https://github.com/Labushuya/so-mi/compare/v0.21.1...v0.21.2) (2026-06-10)


### Bug Fixes

* build — BasicAlertDialog durch AlertDialog ersetzt ([#75](https://github.com/Labushuya/so-mi/issues/75)) ([e49af89](https://github.com/Labushuya/so-mi/commit/e49af891b15c528f1842529a48d0670259e19da2))

## [0.21.1](https://github.com/Labushuya/so-mi/compare/v0.21.0...v0.21.1) (2026-06-10)


### Bug Fixes

* build — doppelter KDoc-Kommentar in HardwareDetector ([#73](https://github.com/Labushuya/so-mi/issues/73)) ([37a1c00](https://github.com/Labushuya/so-mi/commit/37a1c00f1aca28484818b04cf2451f4759a5ce66))

## [0.21.0](https://github.com/Labushuya/so-mi/compare/v0.20.0...v0.21.0) (2026-06-10)


### Features

* Erinnerungen löschen und verschieben, 14B-Ampel nutzt echten Gerätespeicher ([#71](https://github.com/Labushuya/so-mi/issues/71)) ([86961a6](https://github.com/Labushuya/so-mi/commit/86961a6a550addb8155487dac950669fbbef18a8))

## [0.20.0](https://github.com/Labushuya/so-mi/compare/v0.19.1...v0.20.0) (2026-06-10)


### Features

* Settings-Reihenfolge, Back-Navigation, Erinnerungen-Zähler, 14B-Ampel-Fix ([#69](https://github.com/Labushuya/so-mi/issues/69)) ([a0474a9](https://github.com/Labushuya/so-mi/commit/a0474a99e91e7ee3f116eb8925d98669f0811871))

## [0.19.1](https://github.com/Labushuya/so-mi/compare/v0.19.0...v0.19.1) (2026-06-10)


### Bug Fixes

* build — KDoc-Kommentar in RagOrchestrator korrekt geschlossen ([#67](https://github.com/Labushuya/so-mi/issues/67)) ([5dfc88e](https://github.com/Labushuya/so-mi/commit/5dfc88eba77e86686cdd5db6a5210791fb0acde2))

## [0.19.0](https://github.com/Labushuya/so-mi/compare/v0.18.5...v0.19.0) (2026-06-10)


### Features

* So-Mi erinnert sich — Fakten werden bei jeder Antwort als Kontext mitgegeben ([#65](https://github.com/Labushuya/so-mi/issues/65)) ([723c77a](https://github.com/Labushuya/so-mi/commit/723c77a6ec7fabbdf59d6cf953801ee9ef42f47e))

## [0.18.5](https://github.com/Labushuya/so-mi/compare/v0.18.4...v0.18.5) (2026-06-10)


### Bug Fixes

* Erinnerungen ohne installierten Embedder speichern, MagicOS ADJUST_PAN überschreiben ([#63](https://github.com/Labushuya/so-mi/issues/63)) ([0b908c2](https://github.com/Labushuya/so-mi/commit/0b908c20c30087d6c233581d1897d960a09bf366))

## [0.18.4](https://github.com/Labushuya/so-mi/compare/v0.18.3...v0.18.4) (2026-06-10)


### Bug Fixes

* Tastatur-Gap, Erinnerungen werden persistent gespeichert, mehr Triggerwörter ([#61](https://github.com/Labushuya/so-mi/issues/61)) ([3c41a08](https://github.com/Labushuya/so-mi/commit/3c41a087bc4e09a9d1a01c659d8fd4af120afa0b))

## [0.18.3](https://github.com/Labushuya/so-mi/compare/v0.18.2...v0.18.3) (2026-06-10)


### Bug Fixes

* Tastatur-Layout, Erinnerungen-Browser zeigt gespeicherte Fakten ([#59](https://github.com/Labushuya/so-mi/issues/59)) ([a90bbab](https://github.com/Labushuya/so-mi/commit/a90bbab02f3d059273039f719764c6f18c5c8c48))

## [0.18.2](https://github.com/Labushuya/so-mi/compare/v0.18.1...v0.18.2) (2026-06-10)


### Bug Fixes

* 14B-Ampel gelb statt rot — XLARGE ramMinGB auf 7.5 GB korrigiert ([#57](https://github.com/Labushuya/so-mi/issues/57)) ([4b8f1d7](https://github.com/Labushuya/so-mi/commit/4b8f1d7e89bf1014be73aaa23ba3803bfc33175b))

## [0.18.1](https://github.com/Labushuya/so-mi/compare/v0.18.0...v0.18.1) (2026-06-09)


### Bug Fixes

* build — XLARGE-Branch in tierLabel when-Ausdruck ergänzt ([#55](https://github.com/Labushuya/so-mi/issues/55)) ([fb88409](https://github.com/Labushuya/so-mi/commit/fb884095dda516873384e284b2b6b0022e059c8b))

## [0.18.0](https://github.com/Labushuya/so-mi/compare/v0.17.1...v0.18.0) (2026-06-09)


### Features

* 14B-Ampel korrekt gelb, unvollständige Teile ergänzen, Download pausieren ([#53](https://github.com/Labushuya/so-mi/issues/53)) ([6512b2f](https://github.com/Labushuya/so-mi/commit/6512b2f6e93b8f7b2740768e2716499e7d377c42))

## [0.17.1](https://github.com/Labushuya/so-mi/compare/v0.17.0...v0.17.1) (2026-06-09)


### Bug Fixes

* build — SongbirdDialog-Signatur korrigiert (kein actions-Parameter) ([#51](https://github.com/Labushuya/so-mi/issues/51)) ([dc01c8a](https://github.com/Labushuya/so-mi/commit/dc01c8acff12334958cf7fd3315006ce58fb24d1))

## [0.17.0](https://github.com/Labushuya/so-mi/compare/v0.16.8...v0.17.0) (2026-06-09)


### Features

* Tastatur dockt nahtlos an Chat an, Settings mit Akkordeon-Sektionen ([#49](https://github.com/Labushuya/so-mi/issues/49)) ([7fa659c](https://github.com/Labushuya/so-mi/commit/7fa659cd45db539d9b4a110369c63e1eb64129f8))

## [0.16.8](https://github.com/Labushuya/so-mi/compare/v0.16.7...v0.16.8) (2026-06-09)


### Bug Fixes

* Tastatur überlappt Chat, Download abbrechen, LLM-Status korrekt ([#47](https://github.com/Labushuya/so-mi/issues/47)) ([083f8c9](https://github.com/Labushuya/so-mi/commit/083f8c972c35c3627e740dafc16779fd4942f778))

## [0.16.7](https://github.com/Labushuya/so-mi/compare/v0.16.6...v0.16.7) (2026-06-09)


### Bug Fixes

* Keyboard-Spacing, Download-Button im Katalog, Modell-Naming, Modell-Reset nach Löschen ([#45](https://github.com/Labushuya/so-mi/issues/45)) ([5a3d1dd](https://github.com/Labushuya/so-mi/commit/5a3d1ddceb14a96f735b0496531f68c955826aae))

## [0.16.6](https://github.com/Labushuya/so-mi/compare/v0.16.5...v0.16.6) (2026-06-09)


### Bug Fixes

* Downloads in Settings ganz nach oben, Leerraum im Chat entfernt ([#43](https://github.com/Labushuya/so-mi/issues/43)) ([8dc57ea](https://github.com/Labushuya/so-mi/commit/8dc57eab2ca2537febb4aaf96920d66a726d2458))

## [0.16.5](https://github.com/Labushuya/so-mi/compare/v0.16.4...v0.16.5) (2026-06-09)


### Bug Fixes

* App-Crash — _modelStatuses vor init-Block deklariert (NPE durch Kotlin-Initialisierungsreihenfolge) ([#41](https://github.com/Labushuya/so-mi/issues/41)) ([cfd42ea](https://github.com/Labushuya/so-mi/commit/cfd42ea3f632dbfd43f8eda2cf904a82a2e8c651))

## [0.16.4](https://github.com/Labushuya/so-mi/compare/v0.16.3...v0.16.4) (2026-06-09)


### Bug Fixes

* App-Crash — AnimatedContent entfernt, fehlende compose-animation-Dep verursacht ClassNotFoundException ([#39](https://github.com/Labushuya/so-mi/issues/39)) ([8a8082f](https://github.com/Labushuya/so-mi/commit/8a8082facf28ec9c55f76da562c39b5779091eb4))

## [0.16.3](https://github.com/Labushuya/so-mi/compare/v0.16.2...v0.16.3) (2026-06-09)


### Bug Fixes

* App-Crash — LazyListState.Saver ist internal, durch rememberLazyListState() ersetzt ([#37](https://github.com/Labushuya/so-mi/issues/37)) ([2985fd5](https://github.com/Labushuya/so-mi/commit/2985fd5d2f8cecaa42a9edbc32d059f4b3a2eebe))

## [0.16.2](https://github.com/Labushuya/so-mi/compare/v0.16.1...v0.16.2) (2026-06-09)


### Bug Fixes

* build — typografische Anführungszeichen in Kotlin-Strings ersetzt ([#35](https://github.com/Labushuya/so-mi/issues/35)) ([b83cd83](https://github.com/Labushuya/so-mi/commit/b83cd839d0ad7f0defe4fcc338f9642e95f6bb64))

## [0.16.1](https://github.com/Labushuya/so-mi/compare/v0.16.0...v0.16.1) (2026-06-09)


### Bug Fixes

* typografische Anführungszeichen in SettingsScreen durch ASCII ersetzt ([4183e22](https://github.com/Labushuya/so-mi/commit/4183e22a803eb866a12b85d074967a03217c87c9))
* typografische Anführungszeichen in SettingsScreen durch ASCII ersetzt ([eabbfea](https://github.com/Labushuya/so-mi/commit/eabbfea8464e344de02a8ee28d26d9145d792bef))

## [0.16.0](https://github.com/Labushuya/so-mi/compare/v0.15.1...v0.16.0) (2026-06-09)


### Features

* WLAN-Toggle, Katalog-Download-UI, Ladeüberblendung und 14B-Freischaltung (v0.16.0) ([f502b7d](https://github.com/Labushuya/so-mi/commit/f502b7dfaeac068685ca547f53f371c84a16f855))
* WLAN-Toggle, Katalog-Download-UI, Ladeüberblendung und 14B-Freischaltung (v0.16.0) ([5c2395b](https://github.com/Labushuya/so-mi/commit/5c2395b3d2715e6990599d6e467415415e94e354))

## [0.15.1](https://github.com/Labushuya/so-mi/compare/v0.15.0...v0.15.1) (2026-06-08)


### Bug Fixes

* build — embedder-worker-name aus rag-fassade exposen statt direkt referenzieren ([340698a](https://github.com/Labushuya/so-mi/commit/340698a3fc98b0711550d40e770018c5b74f1b8f))
* build v0.15.0-Compile-Fehler ([30374a9](https://github.com/Labushuya/so-mi/commit/30374a9c93d9c5df30bf6ca15003f82dad514799))

## [0.15.0](https://github.com/Labushuya/so-mi/compare/v0.14.3...v0.15.0) (2026-06-08)


### Features

* 14B-Modelle Q3 und Q4 als Staging im Modell-Katalog (SHA-Verify in v0.15.1) ([a798233](https://github.com/Labushuya/so-mi/commit/a798233146335e722390639b9ffb710944ce392d))
* alle Daten unter sichtbarem SoMi/-Verzeichnis bündeln ([b748d1b](https://github.com/Labushuya/so-mi/commit/b748d1bc3f98b09b9100040ec07d23fbb7bcbdab))
* Begrüßung beim App-Start, Downloads-Sichtbarkeit und Modell-Wechsel im laufenden Betrieb ([1b83d51](https://github.com/Labushuya/so-mi/commit/1b83d516837bf83b39247e1bf90c2f0450ca2ec9))
* Embedder-Mirror auf GitHub-Release als Failover, mit App-internem Asset-Pfad ([b865c4e](https://github.com/Labushuya/so-mi/commit/b865c4e48767aeb0798fbac121830cc362b74745))
* in-App Daten-Browser unter Einstellungen, zeigt SoMi/-Verzeichnis und teilt Dateien ([d07e711](https://github.com/Labushuya/so-mi/commit/d07e711fbc91c8429a184753241898c9675c82e5))
* Vollbild-Modus mit Toggle in Einstellungen, Notch und Tastatur sauber respektiert ([fe5dfa2](https://github.com/Labushuya/so-mi/commit/fe5dfa2f2a81216bee3e132b53d9d5c4d968a283))

## [0.14.3](https://github.com/Labushuya/so-mi/compare/v0.14.2...v0.14.3) (2026-06-08)


### Bug Fixes

* erinnerungs-modell wird endlich heruntergeladen, tastatur-spacing endgültig weg ([cd5cdb0](https://github.com/Labushuya/so-mi/commit/cd5cdb0b29771079d7e2f70570096b9398e059ff))

## [0.14.2](https://github.com/Labushuya/so-mi/compare/v0.14.1...v0.14.2) (2026-06-08)


### Bug Fixes

* build — ObjectBox-Task vom Configuration-Cache ausgenommen ([7080eb5](https://github.com/Labushuya/so-mi/commit/7080eb53979a669322607a14277d8bbf46300bef))

## [0.14.1](https://github.com/Labushuya/so-mi/compare/v0.14.0...v0.14.1) (2026-06-08)


### Bug Fixes

* build — objectbox-plugin korrekt verkabelt ([8d83786](https://github.com/Labushuya/so-mi/commit/8d83786be712afa33d64f23b577ade5c0965a2bb))

## [0.14.0](https://github.com/Labushuya/so-mi/compare/v0.13.0...v0.14.0) (2026-06-08)


### Features

* so-mi merkt sich was — 'merk dir' speichert fakten persistent ([e260018](https://github.com/Labushuya/so-mi/commit/e260018af04e4090229be3872d31475b7137c1ce))
* speicher-fundament gelegt — datenbank für so-mi's gedächtnis ist da, noch ohne sichtbare funktion ([363950e](https://github.com/Labushuya/so-mi/commit/363950ebf1e6ea77809db14417c654cede2a053a))

## [0.13.0](https://github.com/Labushuya/so-mi/compare/v0.12.1...v0.13.0) (2026-06-08)


### Features

* vier User-Bugs behoben — Erst-Setup-Loop, Tastatur-Spacing, System-Bars und Lade-Animation aufgehübscht ([b03ac75](https://github.com/Labushuya/so-mi/commit/b03ac754c86d27e33271744efc41bab3a39b4e07))

## [0.12.1](https://github.com/Labushuya/so-mi/compare/v0.12.0...v0.12.1) (2026-06-08)


### Bug Fixes

* build — release-compile-fehler durch eindeutige JNI-funktion behoben ([7dd6594](https://github.com/Labushuya/so-mi/commit/7dd6594b4eeaaec16689a911b78837de707bf64c))

## [0.12.0](https://github.com/Labushuya/so-mi/compare/v0.11.3...v0.12.0) (2026-06-08)


### Features

* einstellungen-refaktorierung — kategorisiert, songbird-stil, persönlichkeit editierbar, modell-verhalten regelbar ([5603ddb](https://github.com/Labushuya/so-mi/commit/5603ddb303be06ecef1c82aacae935c1206bd23d))

## [0.11.3](https://github.com/Labushuya/so-mi/compare/v0.11.2...v0.11.3) (2026-06-07)


### Bug Fixes

* build — logging.h-Include-Pfad nach lokaler ai_chat.cpp-Kopie ergänzt ([4137980](https://github.com/Labushuya/so-mi/commit/413798007f14663b845fed132231b0ee62d63b96))

## [0.11.2](https://github.com/Labushuya/so-mi/compare/v0.11.1...v0.11.2) (2026-06-07)


### Bug Fixes

* stabilität-refaktorierung — freeze beim aufwärmen, mobile-daten-download, init-flicker, kvaesitso-icon ([e92c2bf](https://github.com/Labushuya/so-mi/commit/e92c2bf14550f57570efb2f9f263c50edb1add44))

## [0.11.1](https://github.com/Labushuya/so-mi/compare/v0.11.0...v0.11.1) (2026-06-07)


### Bug Fixes

* vollständige refaktorierung — modell-erkennung, composer, icon und löschen funktionieren wieder ([58b891e](https://github.com/Labushuya/so-mi/commit/58b891e4201763d1b52f6aba39d1c6de8bfc8022))

## [0.11.0](https://github.com/Labushuya/so-mi/compare/v0.10.1...v0.11.0) (2026-06-07)


### Features

* speicher-screen — alle modell-kopien sichtbar, duplikate löschbar ([b1f8152](https://github.com/Labushuya/so-mi/commit/b1f8152773bbb79378dc2da26d1bbe0c80652166))


### Bug Fixes

* app sucht modell in allen historischen pfaden statt erneuten 4-gb-download zu fordern ([3a0b627](https://github.com/Labushuya/so-mi/commit/3a0b6277d8ed1527c88749c6da176d3ad2e8cfb9))

## [0.10.2] (2026-06-07)


### Bug Fixes

* **App fragt nicht mehr nach erneutem 4-GB-Download wenn das Modell schon da ist.** Die App durchsucht jetzt alle historischen Storage-Pfade (v0.7.x externalFilesDir, v0.10.0 Documents, internal fallback) und nutzt das Modell wo immer es liegt — kein Migrate, kein Copy.

## [0.10.1](https://github.com/Labushuya/so-mi/compare/v0.10.0...v0.10.1) (2026-06-07)


### Bug Fixes

* App startet wieder — der `/sdcard/Documents/`-Modell-Pfad aus 0.10.0 brauchte eine Permission die nicht da war. Pfad zurück auf app-private external storage; Modell-Datei bleibt nach Update erhalten, geht bei Uninstall verloren.
* App-Icon-Setup massiv vereinfacht — alle adaptive-icon-Spezifika entfernt, jetzt nur ein PNG pro Density mit Obsidian-Hintergrund. Wenn das Icon trotzdem nicht im Drawer erscheint: App force-stoppen oder Phone neu starten (MagicOS-Launcher-Cache).
* rollback v0.10.0 storage + icon disasters ([cfecb99](https://github.com/Labushuya/so-mi/commit/cfecb9948b9a8b2ed48040e7befaee31275cb7bc))

## [0.10.0](https://github.com/Labushuya/so-mi/compare/v0.9.1...v0.10.0) (2026-06-07)

> ⚠️ **Diese Version startet auf manchen Geräten nicht.** Nutze v0.10.1 (Rollback).

### Features

* Chat-Verlauf bleibt nach App-Neustart erhalten (Room-DB unter `filesDir/somi.db`).
* **Chat-Persistenz + Icon-Hintergrund:** Chat-History wird in Room-DB persistiert; App-Icon-Hintergrund von weiß auf Obsidian-Schwarz umgestellt ([c5afa9d](https://github.com/Labushuya/so-mi/commit/c5afa9df1bde0c82b7e0aee537d9c63f5aabf1c5))

### ⚠ Bekannte Probleme

* Modell-Pfad auf `/sdcard/Documents/SoMi-Models/` umgestellt — funktioniert ohne MANAGE_EXTERNAL_STORAGE-Permission nicht. App wirft "Modell konnte nicht geladen werden". Behoben in 0.10.1.
* App-Icon weiterhin nicht im Drawer sichtbar trotz adaptive-icon-Inset-Versuch. Behoben in 0.10.1.

## [0.9.1](https://github.com/Labushuya/so-mi/compare/v0.9.0...v0.9.1) (2026-06-07)


### Bug Fixes

* So-Mi nennt User korrekt beim Vornamen statt "Songbird" — soul.md gekürzt + Aliase explizit erklärt.
* App stirbt nicht mehr nach erster Antwort (neue Foreground-Service `LlamaSessionService` pinned den Process bei MagicOS).
* Adaptive-Icon `<monochrome>`-Layer entfernt — Material-You-Themed-Icons-Modus zeigte den Songbird-Outline auf hellem Hintergrund (sah weiß aus).
* persona/branding/lifecycle bundle (icon bg, condensed soul, FGS pin) ([2b3bd44](https://github.com/Labushuya/so-mi/commit/2b3bd447fd89989fe256a3d66f2984854ebfdb2d))

## [0.9.0](https://github.com/Labushuya/so-mi/compare/v0.8.1...v0.9.0) (2026-06-07)


### Features

* Brand-Icon: Crimson-Eye-im-Octagon-Design für Launcher + Notifications.
* **branding:** Songbird launcher icon + notification icon ([532a60c](https://github.com/Labushuya/so-mi/commit/532a60c661c1dfc86ac06829172cbaf497857796))


### Bug Fixes

* Modell-Lade-Hang behoben — `setSystemPrompt(soul.md)`-Prefill auf 600 Zeichen begrenzt (volle 4 KB ergaben 5–15 Min Hang auf CPU 7B).
* Modelle landen unter `/sdcard/Documents/SoMi-Models/` — überleben App-Daten-Löschen + Uninstall (siehe v0.10.0 für Probleme).
* 3-Min-Timeout um Lade-Pfad — bei Hang erscheint Error-Banner statt unendlicher Spinner.
* **load:** drop warm-pass + truncate system prompt + persistent model storage ([79b9325](https://github.com/Labushuya/so-mi/commit/79b9325ea87cf64ac8991286ca34f67cad3d3712))

## [0.8.1](https://github.com/Labushuya/so-mi/compare/v0.8.0...v0.8.1) (2026-06-07)


### Bug Fixes

* **download:** rescue stuck-after-download via disk-truth-first + polling fallback ([0bc6b65](https://github.com/Labushuya/so-mi/commit/0bc6b65c4f4f819d5aafdee5e5a2abf9a51d4dcc))

## [0.8.0](https://github.com/Labushuya/so-mi/compare/v0.7.0...v0.8.0) (2026-06-06)


### Features

* **Erste lauffähige Chat-Session:** End-to-end-Chat — Erst-Start-Flow mit Modell-Picker, soul.md als System-Prompt, Token-für-Token live-tippende Antworten ([39d2942](https://github.com/Labushuya/so-mi/commit/39d294200cf90076e20248c12cdd2ce38bb1e9ef))

## [0.7.0](https://github.com/Labushuya/so-mi/compare/v0.6.0...v0.7.0) (2026-06-06)


### Features

* **Modell-Download-Manager:** WorkManager + OkHttp mit Resume-Support und SHA-256-Verifikation für GGUF-Downloads ([71ead78](https://github.com/Labushuya/so-mi/commit/71ead78c29d61549c1a2a92d6afe21ef823a57f0))

## [0.6.0](https://github.com/Labushuya/so-mi/compare/v0.5.0...v0.6.0) (2026-06-06)


### Features

* **llama.cpp eingebaut:** Native Inferenz-Engine als Submodul vendored, libai-chat.so per NDK r27 für arm64 gebaut ([2fe5274](https://github.com/Labushuya/so-mi/commit/2fe527488028851b9a7555274af60c362fab6e4f))

## [0.5.0](https://github.com/Labushuya/so-mi/compare/v0.4.0...v0.5.0) (2026-06-06)


### Features

* **Hardware-Erkennung + Modell-Empfehlung:** App erkennt RAM, Speicher und GPU des Geräts, schlägt passendes Modell-Tier vor (Tiny/Small/Medium/Large) ([6f266d0](https://github.com/Labushuya/so-mi/commit/6f266d085f1bf0fda1dca87d293c10869ceb0da3))

## [0.4.0](https://github.com/Labushuya/so-mi/compare/v0.3.1...v0.4.0) (2026-06-06)


### Features

* **Hilt-Grundgerüst + Chat-ViewModel:** Dependency-Injection-Framework eingebaut, ChatViewModel und Modul-Verkabelung als Basis für die folgenden Inkremente ([d190177](https://github.com/Labushuya/so-mi/commit/d190177cfd1d8c325f48ac3e1940559304005ebd))

## [0.3.1](https://github.com/Labushuya/so-mi/compare/v0.3.0...v0.3.1) (2026-06-06)


### Bug Fixes

* **ci:** mark gradlew + scripts/*.sh executable in git index ([360c742](https://github.com/Labushuya/so-mi/commit/360c7421d38036be9aedc30c4529b821ec331b8d))

## [0.3.0](https://github.com/Labushuya/so-mi/compare/v0.2.0...v0.3.0) (2026-06-06)


### Features

* songbird rebrand + chat shell port from odysseus ([4daf15a](https://github.com/Labushuya/so-mi/commit/4daf15a504dcd48e79aea0ea251bfae89912ddfe))

## [0.2.0](https://github.com/Labushuya/so-mi/compare/v0.1.0...v0.2.0) (2026-06-06)


### Features

* initial Phase 1 skeleton (build pipeline + hello-world APK) ([a039453](https://github.com/Labushuya/so-mi/commit/a039453f4bfb77201f0e0f8c5f5f2908b43ed003))

## Changelog
