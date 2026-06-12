package io.somi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.somi.app.components.SectionCard
import io.somi.app.components.SectionHeader
import io.somi.app.components.SettingsRow
import io.somi.app.components.SongbirdButton
import io.somi.app.components.SongbirdButtonKind
import io.somi.app.components.SongbirdDialog
import io.somi.app.components.SongbirdDialogAction
import io.somi.app.components.SongbirdDialogTone
import io.somi.app.components.SongbirdSlider
import io.somi.app.components.SongbirdTopBar
import io.somi.common.llm.SamplerParams
import io.somi.data.Light
import io.somi.data.ModelStorage
import io.somi.ui.chat.ChatViewModel
import kotlinx.coroutines.launch

/**
 * v0.11.4 — Settings refactor.
 *
 * Sections (top → bottom, destructive last):
 *   1. Persönlichkeit — soul.md preview, "Bearbeiten" → SoulEditorScreen
 *   2. Verhalten      — LLM sampler sliders (temp / top_p / repeat_penalty / max_tokens) with in-character explanations
 *   3. Lernen         — placeholder for v0.13 Memory-Browser
 *   4. Diagnose       — boot.deviceInfo + recommendation tier traffic-light + version + model-load timings
 *   5. Speicher       — model instances list + delete (destructive, always last)
 *
 * Single outer LazyColumn so all sections scroll together.
 */
@Composable
internal fun SettingsScreen(
    viewModel: ChatViewModel,
    onClose: () -> Unit,
    onOpenSoulEditor: () -> Unit,
    onOpenMemoryBrowser: () -> Unit,
    onOpenModelCatalog: () -> Unit,
    onOpenDataBrowser: () -> Unit,
    onOpenFaq: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val sampler by viewModel.samplerParams.collectAsStateWithLifecycle()
    val boot by viewModel.boot.collectAsStateWithLifecycle()
    val embedderStatus by viewModel.embedderStatus.collectAsStateWithLifecycle()
    val uiSettings by viewModel.uiSettings.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Akkordeon-State: welche Sektion ist gerade aufgeklappt
    var expandedSection by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SongbirdTopBar(title = "Einstellungen", onBack = onClose)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // 1. Diagnose — immer sichtbar, kein Akkordeon
            item {
                DiagnosticsSection(
                    boot = boot,
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                )
            }
            // 2. So-Mi — Erinnerungen ZUERST, dann Persönlichkeit, Verhalten, Begrüßung, Lernen
            item {
                AccordionSection(
                    title = "So-Mi",
                    expanded = expandedSection == "somi",
                    onToggle = { expandedSection = if (expandedSection == "somi") null else "somi" },
                ) {
                    LearningSection(onOpenMemoryBrowser = onOpenMemoryBrowser)
                    Spacer(Modifier.height(16.dp))
                    PersonalitySection(onOpenSoulEditor = onOpenSoulEditor)
                    Spacer(Modifier.height(16.dp))
                    BehaviourSection(
                        sampler = sampler,
                        onSamplerChange = { viewModel.applySamplerParams(it) },
                    )
                    Spacer(Modifier.height(16.dp))
                    GreetingSection(
                        mode = uiSettings.greetingMode,
                        onModeChange = { m ->
                            coroutineScope.launch { viewModel.uiSettings.setGreetingMode(m) }
                        },
                    )
                }
            }
            // 3. Modelle & Abhängigkeiten (LLM + Gedächtnis-Modell + künftige Pakete)
            item {
                val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
                AccordionSection(
                    title = "Modelle & Abhängigkeiten",
                    expanded = expandedSection == "modelle",
                    onToggle = { expandedSection = if (expandedSection == "modelle") null else "modelle" },
                ) {
                    ModelStorageSection(
                        instances = instances,
                        embedderStatus = embedderStatus,
                        wifiOnly = wifiOnly,
                        onWifiOnlyChange = { viewModel.setWifiOnly(it) },
                        onOpenModelCatalog = onOpenModelCatalog,
                        onRetryEmbedder = { viewModel.manualEnqueueEmbedder() },
                        onReinstallEmbedder = { viewModel.reinstallEmbedder() },
                        onDeleteEmbedder = { viewModel.deleteEmbedderOnly() },
                        onDeleteInstance = { viewModel.deleteModelInstance(it) },
                    )
                }
            }
            // 4. Anzeige & Daten
            item {
                AccordionSection(
                    title = "Anzeige & Daten",
                    expanded = expandedSection == "anzeige",
                    onToggle = { expandedSection = if (expandedSection == "anzeige") null else "anzeige" },
                ) {
                    DisplaySection(
                        immersive = uiSettings.immersive,
                        onImmersiveChange = { v ->
                            coroutineScope.launch { viewModel.uiSettings.setImmersive(v) }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    DataSection(viewModel = viewModel, onOpenDataBrowser = onOpenDataBrowser, onOpenFaq = onOpenFaq)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Persönlichkeit
// ---------------------------------------------------------------------------

@Composable
private fun PersonalitySection(onOpenSoulEditor: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    SectionCard(title = "Persönlichkeit") {
        Text(
            text = "So-Mis Stimme. Wird bei jeder Antwort als System-Prompt mitgegeben.",
            color = songbird.glass,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        SongbirdButton(
            label = "Persönlichkeit bearbeiten",
            kind = SongbirdButtonKind.Primary,
            onClick = onOpenSoulEditor,
        )
    }
}

// ---------------------------------------------------------------------------
// Akkordeon-Wrapper
// ---------------------------------------------------------------------------

@Composable
private fun AccordionSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(songbird.aiBubble)
            .border(1.dp, songbird.bubbleBorder, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                color = songbird.bone,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (expanded) "▲" else "▼",
                color = songbird.glass,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (expanded) {
            androidx.compose.material3.HorizontalDivider(color = songbird.bubbleBorder)
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Modelle & Speicher (merged)
// ---------------------------------------------------------------------------

@Composable
private fun ModelStorageSection(
    instances: List<io.somi.data.ModelStorage.ModelInstance>,
    embedderStatus: io.somi.ui.chat.ChatViewModel.EmbedderStatus,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onOpenModelCatalog: () -> Unit,
    onRetryEmbedder: () -> Unit,
    onReinstallEmbedder: () -> Unit,
    onDeleteEmbedder: () -> Unit,
    onDeleteInstance: (io.somi.data.ModelStorage.ModelInstance) -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    val coroutineScope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<io.somi.data.ModelStorage.ModelInstance?>(null) }

    // Wi-Fi Toggle
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Nur per WLAN",
            color = songbird.bone,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Switch(
            checked = wifiOnly,
            onCheckedChange = onWifiOnlyChange,
        )
    }
    if (!wifiOnly) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "WLAN aus: Downloads laufen auch über mobile Daten.",
            color = songbird.signal,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Spacer(Modifier.height(16.dp))

    // Gedächtnis-Modell
    Text("Gedächtnis-Modell (Embedder)", color = songbird.bone, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    EmbedderStatusBadge(status = embedderStatus)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // "Download starten" wenn nicht installiert/fehlgeschlagen, sonst "Erneut versuchen"
        SongbirdButton(
            label = if (embedderStatus == io.somi.ui.chat.ChatViewModel.EmbedderStatus.Installed) "Erneut herunterladen" else "Herunterladen",
            kind = SongbirdButtonKind.Secondary,
            onClick = onRetryEmbedder,
        )
        if (embedderStatus == io.somi.ui.chat.ChatViewModel.EmbedderStatus.Installed) {
            SongbirdButton(label = "Löschen", kind = SongbirdButtonKind.Destructive, onClick = onDeleteEmbedder)
        }
    }

    Spacer(Modifier.height(20.dp))

    // LLM
    Text("LLM", color = songbird.bone, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    if (instances.isEmpty()) {
        Text("Kein Modell installiert.", color = songbird.glass, style = MaterialTheme.typography.bodySmall)
    } else {
        instances.forEach { instance ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(instance.manifestId, color = songbird.bone, style = MaterialTheme.typography.bodySmall)
                    Text(formatGB(instance.sizeBytes), color = songbird.glass, style = MaterialTheme.typography.labelSmall)
                }
                SongbirdButton(
                    label = "Löschen",
                    kind = SongbirdButtonKind.Destructive,
                    onClick = { confirmDelete = instance },
                    minHeight = 32.dp,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SongbirdButton(
        label = "Anderes LLM laden",
        kind = SongbirdButtonKind.Primary,
        onClick = onOpenModelCatalog,
    )

    if (confirmDelete != null) {
        val instance = confirmDelete!!
        SongbirdDialog(
            onDismissRequest = { confirmDelete = null },
            title = "Modell löschen?",
            message = "${instance.manifestId} wird von Disk entfernt. Download nötig wenn Du es wieder willst.",
            tone = SongbirdDialogTone.Destructive,
            confirm = SongbirdDialogAction(
                label = "Löschen",
                onClick = { onDeleteInstance(instance); confirmDelete = null },
                kind = SongbirdDialogAction.Kind.Destructive,
            ),
            dismiss = SongbirdDialogAction(
                label = "Abbrechen",
                onClick = { confirmDelete = null },
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Verhalten — LLM sampler sliders
// ---------------------------------------------------------------------------

@Composable
private fun BehaviourSection(
    sampler: SamplerParams,
    onSamplerChange: (SamplerParams) -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    val scope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }

    // Local mirror so dragging the slider feels responsive; commit
    // happens on slide-end (debounce-by-gesture).
    var local by remember(sampler) { mutableStateOf(sampler) }

    SectionCard(title = "Verhalten") {
        Text(
            text = "Schraub an den Reglern, um So-Mis Stimme nachzujustieren. Defaults sind sicher; Extreme produzieren Quatsch.",
            color = songbird.glass,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))

        SongbirdSlider(
            label = "Temperatur",
            value = local.temperature,
            valueRange = SamplerParams.TEMPERATURE_RANGE,
            valueText = "%.2f".format(local.temperature),
            explanation = "Höher = So-Mi wird wilder, kreativer, halluziniert mehr. Niedriger = nüchterner, vorhersehbar. Default 0.30.",
            onValueChange = { local = local.copy(temperature = it) },
            onValueChangeFinished = { onSamplerChange(local) },
        )
        SongbirdSlider(
            label = "Top-P",
            value = local.topP,
            valueRange = SamplerParams.TOP_P_RANGE,
            valueText = "%.2f".format(local.topP),
            explanation = "Wie groß die Kandidaten-Menge ist, aus der jedes Token kommt. Höher = mehr Wortauswahl, lockerer Stil. Default 0.90.",
            onValueChange = { local = local.copy(topP = it) },
            onValueChangeFinished = { onSamplerChange(local) },
        )
        SongbirdSlider(
            label = "Wiederholungs-Bremse",
            value = local.repeatPenalty,
            valueRange = SamplerParams.REPEAT_PENALTY_RANGE,
            valueText = "%.2f".format(local.repeatPenalty),
            explanation = "Höher = So-Mi wiederholt sich seltener, klingt dafür angestrengter. Default 1.10.",
            onValueChange = { local = local.copy(repeatPenalty = it) },
            onValueChangeFinished = { onSamplerChange(local) },
        )
        SongbirdSlider(
            label = "Top-K",
            value = local.topK.toFloat(),
            valueRange = SamplerParams.TOP_K_RANGE.first.toFloat()..SamplerParams.TOP_K_RANGE.last.toFloat(),
            valueText = "${local.topK}",
            explanation = "Wie viele Token-Kandidaten pro Schritt erlaubt sind. Niedriger = präzisere Sätze. Default 40.",
            onValueChange = { local = local.copy(topK = it.toInt()) },
            onValueChangeFinished = { onSamplerChange(local) },
            steps = (SamplerParams.TOP_K_RANGE.last - SamplerParams.TOP_K_RANGE.first - 1).coerceAtLeast(0),
        )
        SongbirdSlider(
            label = "Max-Tokens pro Antwort",
            value = local.maxTokens.toFloat(),
            valueRange = SamplerParams.MAX_TOKENS_RANGE.first.toFloat()..SamplerParams.MAX_TOKENS_RANGE.last.toFloat(),
            valueText = "${local.maxTokens}",
            explanation = "Hartes Limit, wie lang So-Mi pro Antwort werden darf. Default 1024.",
            onValueChange = { local = local.copy(maxTokens = (it.toInt() / 64) * 64) },
            onValueChangeFinished = { onSamplerChange(local) },
            steps = ((SamplerParams.MAX_TOKENS_RANGE.last - SamplerParams.MAX_TOKENS_RANGE.first) / 64 - 1).coerceAtLeast(0),
        )

        Spacer(Modifier.height(8.dp))
        SongbirdButton(
            label = "Auf Default zurücksetzen",
            kind = SongbirdButtonKind.Ghost,
            onClick = { showResetConfirm = true },
        )
    }

    if (showResetConfirm) {
        SongbirdDialog(
            onDismissRequest = { showResetConfirm = false },
            title = "Defaults wiederherstellen?",
            message = "Setzt alle Verhalten-Regler auf die mitgelieferten Werte zurück.",
            tone = SongbirdDialogTone.Neutral,
            confirm = SongbirdDialogAction(
                label = "Zurücksetzen",
                kind = SongbirdDialogAction.Kind.Primary,
                onClick = {
                    showResetConfirm = false
                    onSamplerChange(SamplerParams.DEFAULTS)
                },
            ),
            dismiss = SongbirdDialogAction(
                label = "Abbrechen",
                kind = SongbirdDialogAction.Kind.Ghost,
                onClick = { showResetConfirm = false },
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Lernen
// ---------------------------------------------------------------------------

@Composable
private fun LearningSection(onOpenMemoryBrowser: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var memoryCount by remember { mutableStateOf(-1) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val root = io.somi.data.StorageRoots.memory(context)
            val count = io.somi.rag.memory.MemoryTopic.entries.sumOf { topic ->
                val file = java.io.File(root, "${topic.id}.md")
                if (!file.exists()) 0
                else file.readLines().count { it.trimStart().startsWith("- ") }
            }
            memoryCount = count
        }
    }

    val buttonLabel = when {
        memoryCount < 0 -> "Erinnerungen ansehen"
        memoryCount == 0 -> "Erinnerungen ansehen (keine gespeichert)"
        else -> "Erinnerungen ansehen ($memoryCount gespeichert)"
    }

    SectionCard(title = "Lernen") {
        Text(
            text = "So-Mi merkt sich, was Du ihr sagst. Sag 'Merke dir, ...' oder 'Vergiss nicht, ...' — So-Mi speichert den Fakt und erinnert sich bei zukünftigen Antworten daran.",
            color = songbird.glass,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        SongbirdButton(
            label = buttonLabel,
            kind = SongbirdButtonKind.Ghost,
            onClick = onOpenMemoryBrowser,
        )
    }
}

// ---------------------------------------------------------------------------
// Diagnose
// ---------------------------------------------------------------------------

@Composable
private fun DiagnosticsSection(
    boot: ChatViewModel.BootSnapshot?,
    versionName: String,
    versionCode: Int,
) {
    SectionCard(title = "Diagnose") {
        SettingsRow(label = "App-Version", value = "v$versionName ($versionCode)")
        if (boot != null) {
            val device = boot.deviceInfo
            SettingsRow(label = "RAM", value = "%.1f GB".format(device.totalRamGB))
            SettingsRow(label = "Verfügbar", value = "%.1f GB".format(device.availRamGB))
            SettingsRow(label = "GPU", value = device.gpuRenderer)
            val auto = boot.recommendation.auto
            val light = boot.recommendation.lights[auto]
            val lightGlyph = when (light) {
                Light.GREEN -> "🟢"
                Light.YELLOW -> "🟡"
                Light.RED -> "🔴"
                null -> ""
            }
            SettingsRow(label = "Empfohlene Modell-Klasse", value = "$lightGlyph ${auto.name.lowercase()}")
        } else {
            SettingsRow(label = "Hardware-Probe", value = "läuft…")
        }
    }
}

// ---------------------------------------------------------------------------
// Speicher (model instances)
// ---------------------------------------------------------------------------

@Composable
private fun StorageSection(
    instances: List<ModelStorage.ModelInstance>,
    onDeleteInstance: (ModelStorage.ModelInstance) -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    SectionCard(title = "Speicher") {
        if (instances.isEmpty()) {
            Text(
                text = "Keine Modelle auf dem Gerät.",
                color = songbird.glass,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@SectionCard
        }
        val totalBytes = instances.sumOf { it.sizeBytes }
        Text(
            text = "Insgesamt belegt: ${formatGB(totalBytes)}",
            color = songbird.bone,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(12.dp))
        instances.forEach { instance ->
            InstanceRow(instance = instance, onDelete = { onDeleteInstance(instance) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InstanceRow(
    instance: ModelStorage.ModelInstance,
    onDelete: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(songbird.composerBg)
            .border(1.dp, songbird.composerBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = instance.displayName,
                color = songbird.bone,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (instance.filesMissing.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(songbird.signal.copy(alpha = 0.2f))
                        .border(1.dp, songbird.signal, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "UNVOLLSTÄNDIG",
                        color = songbird.signal,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val statusLabel = if (instance.filesMissing.isNotEmpty()) {
            val total = instance.filesPresent.size + instance.filesMissing.size
            "${formatGB(instance.sizeBytes)} · ${instance.filesPresent.size}/$total Shards · ${instance.filesMissing.size} fehlen"
        } else {
            formatGB(instance.sizeBytes)
        }
        Text(
            text = statusLabel,
            color = songbird.glass,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        if (!confirmDelete) {
            SongbirdButton(
                label = "Modell löschen",
                kind = SongbirdButtonKind.Destructive,
                onClick = { confirmDelete = true },
            )
        } else {
            Text(
                text = "Wirklich löschen? Du musst danach das Modell neu laden.",
                color = songbird.bone,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SongbirdButton(
                    label = "Ja, löschen",
                    kind = SongbirdButtonKind.Destructive,
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                )
                SongbirdButton(
                    label = "Abbrechen",
                    kind = SongbirdButtonKind.Ghost,
                    onClick = { confirmDelete = false },
                )
            }
        }
    }
}

private fun formatGB(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    return if (gb >= 1.0) "%.2f GB".format(gb)
    else "%.0f MB".format(bytes / 1_048_576.0)
}

// ---------------------------------------------------------------------------
// v0.15.0 — Downloads
// ---------------------------------------------------------------------------

@Composable
private fun DownloadsSection(
    embedderStatus: ChatViewModel.EmbedderStatus,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onOpenModelCatalog: () -> Unit,
    onRetryEmbedder: () -> Unit,
    onReinstallEmbedder: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    SectionCard(title = "Downloads") {
        Text(
            text = "Was So-Mi gerade lädt — und wie Du selbst Hand anlegst, falls etwas hängt.",
            color = songbird.glass,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))

        // Wi-Fi-Toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Nur per WLAN",
                color = songbird.bone,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Switch(
                checked = wifiOnly,
                onCheckedChange = onWifiOnlyChange,
            )
        }
        if (!wifiOnly) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "WLAN aus: Downloads laufen auch über mobile Daten. Kann Kosten verursachen.",
                color = songbird.signal,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(16.dp))

        // Embedder
        Text("Gedächtnis-Modell (Embedder)", color = songbird.bone, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        EmbedderStatusBadge(status = embedderStatus)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SongbirdButton(
                label = "Erneut laden",
                kind = SongbirdButtonKind.Secondary,
                onClick = onRetryEmbedder,
            )
            if (embedderStatus == ChatViewModel.EmbedderStatus.Installed) {
                SongbirdButton(
                    label = "Neu installieren",
                    kind = SongbirdButtonKind.Ghost,
                    onClick = onReinstallEmbedder,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // LLMs
        Text("LLM (Sprachmodell)", color = songbird.bone, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Pick' eines aus dem Katalog — vom 0.5B-Notnagel bis zum 14B-Schwergewicht. Aktuell installierte Modelle siehst Du unten unter \"Speicher\".",
            color = songbird.glass,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        SongbirdButton(
            label = "Anderes Modell laden",
            kind = SongbirdButtonKind.Primary,
            onClick = onOpenModelCatalog,
        )
    }
}

@Composable
private fun EmbedderStatusBadge(status: ChatViewModel.EmbedderStatus) {
    val songbird = LocalSongbirdColors.current
    val dotColor = when (status) {
        ChatViewModel.EmbedderStatus.Installed -> Color(0xFF4CAF50)
        ChatViewModel.EmbedderStatus.Running -> Color(0xFF4CAF50)
        ChatViewModel.EmbedderStatus.Enqueued -> Color(0xFFFFCC00)
        ChatViewModel.EmbedderStatus.Failed -> songbird.roseDust
        ChatViewModel.EmbedderStatus.NotPresent -> songbird.glass
    }
    val label = when (status) {
        ChatViewModel.EmbedderStatus.Installed -> "Installiert"
        ChatViewModel.EmbedderStatus.Running -> "Lädt…"
        ChatViewModel.EmbedderStatus.Enqueued -> "Wartet auf WLAN"
        ChatViewModel.EmbedderStatus.Failed -> "Fehler"
        ChatViewModel.EmbedderStatus.NotPresent -> "Nicht installiert"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == ChatViewModel.EmbedderStatus.Running) {
            CircularProgressIndicator(
                modifier = Modifier.size(8.dp),
                color = dotColor,
                strokeWidth = 1.5.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = dotColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---------------------------------------------------------------------------
// v0.15.0 — Anzeige (Immersive Fullscreen)
// ---------------------------------------------------------------------------

@Composable
private fun DisplaySection(
    immersive: Boolean,
    onImmersiveChange: (Boolean) -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    SectionCard(title = "Anzeige") {
        Text(
            text = "Vollbild blendet die Status- und Navigations-Leisten aus. Ein Wisch vom Rand bringt sie kurz zurück.",
            color = songbird.glass,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Vollbild",
                color = songbird.bone,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Switch(
                checked = immersive,
                onCheckedChange = onImmersiveChange,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// v0.15.0 — Begrüßung
// ---------------------------------------------------------------------------

@Composable
private fun GreetingSection(
    mode: io.somi.data.settings.GreetingMode,
    onModeChange: (io.somi.data.settings.GreetingMode) -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    SectionCard(title = "Begrüßung") {
        Text(
            text = "So-Mi sagt Hallo, wenn Du sie aufweckst. Wähl, wann.",
            color = songbird.glass,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))

        GreetingRadioRow(
            label = "Vollständig",
            description = "Bei jedem Öffnen + Rückkehr aus dem Hintergrund.",
            selected = mode == io.somi.data.settings.GreetingMode.FULL,
            onSelect = { onModeChange(io.somi.data.settings.GreetingMode.FULL) },
        )
        GreetingRadioRow(
            label = "Nur beim Start",
            description = "Nur beim Kaltstart der App. Standard.",
            selected = mode == io.somi.data.settings.GreetingMode.COLD_START,
            onSelect = { onModeChange(io.somi.data.settings.GreetingMode.COLD_START) },
        )
        GreetingRadioRow(
            label = "Aus",
            description = "Keine Begrüßung. Nur Stille bis Du etwas tippst.",
            selected = mode == io.somi.data.settings.GreetingMode.NONE,
            onSelect = { onModeChange(io.somi.data.settings.GreetingMode.NONE) },
        )
    }
}

@Composable
private fun GreetingRadioRow(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onSelect,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = songbird.bone, style = MaterialTheme.typography.titleSmall)
            Text(description, color = songbird.glass, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---------------------------------------------------------------------------
// v0.15.0 — Daten (in-app file viewer entry point)
// ---------------------------------------------------------------------------

@Composable
private fun DataSection(viewModel: ChatViewModel, onOpenDataBrowser: () -> Unit, onOpenFaq: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var backupStatus by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Import launcher — opens file picker for ZIP files
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Show confirmation before overwriting
        pendingImportUri = uri
    }

    // Import confirmation dialog
    pendingImportUri?.let { uri ->
        SongbirdDialog(
            onDismissRequest = { pendingImportUri = null },
            title = "Backup importieren?",
            message = "Bestehende Daten (Erinnerungen, Persönlichkeit, Chat-Verlauf, Einstellungen) werden überschrieben. Fortfahren?",
            tone = SongbirdDialogTone.Warning,
            confirm = SongbirdDialogAction("Importieren", {
                pendingImportUri = null
                scope.launch {
                    backupStatus = "Importiere…"
                    try {
                        val count = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val tmpFile = java.io.File(context.cacheDir, "import_backup.zip")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tmpFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            io.somi.data.BackupManager(context).importBackup(tmpFile)
                        }
                        backupStatus = "Import abgeschlossen: $count Dateien wiederhergestellt."
                    } catch (t: Throwable) {
                        backupStatus = "Import fehlgeschlagen: ${t.message}"
                    }
                }
            }),
            dismiss = SongbirdDialogAction("Abbrechen", { pendingImportUri = null }),
        )
    }
    SectionCard(title = "Daten") {
        Text(
            text = "Schau Dir an, was So-Mi auf Dein Gerät schreibt — Modelle, Erinnerungen, Persönlichkeit, Datenbank. Alles unter \"SoMi/\".",
            color = songbird.glass,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        SongbirdButton(
            label = "Dateien anzeigen",
            kind = SongbirdButtonKind.Secondary,
            onClick = onOpenDataBrowser,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SongbirdButton(
                label = "Backup erstellen",
                kind = SongbirdButtonKind.Ghost,
                onClick = {
                    scope.launch {
                        backupStatus = "Erstelle Backup…"
                        try {
                            val zipFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                io.somi.data.BackupManager(context, viewModel.databaseOpenHelper).createBackup()
                            }
                            backupStatus = "Backup: ${zipFile.name}"
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, context.packageName + ".fileprovider", zipFile,
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Backup teilen").apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (t: Throwable) {
                            backupStatus = "Fehler: ${t.message}"
                        }
                    }
                },
            )
            SongbirdButton(
                label = "Backup importieren",
                kind = SongbirdButtonKind.Ghost,
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            )
        }
        if (backupStatus.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(backupStatus, color = songbird.glass, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
        SongbirdButton(
            label = "❓ Häufige Fragen (FAQ)",
            kind = SongbirdButtonKind.Ghost,
            onClick = onOpenFaq,
        )
    }
}
