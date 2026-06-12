package io.somi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.somi.app.LocalSongbirdColors
import io.somi.app.ModelRow
import io.somi.app.SongbirdColors
import io.somi.app.components.SectionCard
import io.somi.app.components.SongbirdTopBar
import io.somi.data.Light
import io.somi.data.ModelCatalog
import io.somi.data.ModelStatus
import io.somi.ui.chat.ChatViewModel

/**
 * v0.15.0 — Model-Katalog ab Settings → Downloads → "Anderes Modell laden".
 *
 * v0.15.1 — Download-Button per Modell:
 *  - Not installed → "Herunterladen"-Button.
 *  - Downloading/Verifying → LinearProgressIndicator mit Prozent.
 *  - Installed → "Installiert ✓"-Label + "Aktivieren"-Button.
 *  - Currently selected model: crimson border (via ModelRow).
 */
@Composable
fun ModelCatalogScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    val boot by viewModel.boot.collectAsStateWithLifecycle()
    val selected by viewModel.selectedModel.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val modelStatuses by viewModel.modelStatuses.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SongbirdTopBar(title = "LLM auswählen", onBack = onBack)
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SectionCard {
                    Text(
                        text = "Pick' eines aus. Die Ampel zeigt, ob Dein Gerät es trägt — Grün = locker, Gelb = knapp, Rot = wahrscheinlich OOM.",
                        color = songbird.glass,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Bei \"qwen-research\"-Lizenz: rein für Dich, nicht für kommerzielle Nutzung. So-Mi distribuiert das Modell nicht weiter — Du lädst direkt von Hugging Face.",
                        color = songbird.glass,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            items(ModelCatalog.ALL, key = { it.id }) { manifest ->
                val light = boot?.recommendation?.lights?.get(manifest.tier) ?: Light.RED
                val isRecommended = boot?.recommendation?.auto == manifest.tier
                val isSelected = selected?.id == manifest.id
                val diskInstance = instances.firstOrNull { it.manifestId == manifest.id }
                val isCompleteOnDisk = diskInstance?.isComplete == true
                val isPartialOnDisk = diskInstance != null && !isCompleteOnDisk
                val rawStatus = modelStatuses[manifest.id]
                // Disk truth wins. NotInstalled only if no bytes on disk at all.
                val effectiveStatus: ModelStatus = when {
                    isCompleteOnDisk -> ModelStatus.Installed(java.io.File(""))
                    rawStatus is ModelStatus.Downloading && rawStatus.bytesDownloaded > 0 -> rawStatus
                    rawStatus is ModelStatus.Verifying -> rawStatus
                    rawStatus is ModelStatus.Failed -> rawStatus
                    isPartialOnDisk -> ModelStatus.NotInstalled // partial but not actively downloading
                    else -> ModelStatus.NotInstalled
                }

                Column {
                    ModelRow(
                        manifest = manifest,
                        light = light,
                        isSelected = isSelected,
                        isRecommended = isRecommended,
                        onClick = { if (isCompleteOnDisk) viewModel.selectModel(manifest) },
                    )
                    if (isPartialOnDisk && effectiveStatus !is ModelStatus.Downloading && effectiveStatus !is ModelStatus.Verifying) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Unvollständig: ${diskInstance!!.filesPresent.size}/${manifest.parts.size} Teile",
                                color = songbird.roseDust,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { viewModel.downloadModel(manifest, wifiOnly) },
                                colors = ButtonDefaults.buttonColors(containerColor = songbird.crimson),
                            ) { Text("Vervollständigen") }
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        ModelActionRow(
                            status = effectiveStatus,
                            isSelected = isSelected,
                            onActivate = { viewModel.selectModel(manifest) },
                            onDownload = { viewModel.downloadModel(manifest, wifiOnly) },
                            onCancel = { viewModel.cancelModelDownload(manifest) },
                            songbirdColors = songbird,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelActionRow(
    status: ModelStatus?,
    isSelected: Boolean,
    onActivate: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    songbirdColors: SongbirdColors,
) {
    when (status) {
        is ModelStatus.Installed -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Installiert ✓",
                    color = songbirdColors.glass,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!isSelected) {
                    OutlinedButton(
                        onClick = onActivate,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = songbirdColors.crimson,
                        ),
                    ) {
                        Text("Aktivieren")
                    }
                }
            }
        }

        is ModelStatus.Downloading -> {
            val pct = (status.progress * 100).toInt()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Lädt … $pct %",
                        color = songbirdColors.glass,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.widthIn(min = 96.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = songbirdColors.roseDust,
                        ),
                    ) {
                        Text("Pausieren", maxLines = 1)
                    }
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = songbirdColors.crimson,
                    trackColor = songbirdColors.bubbleBorder,
                )
            }
        }

        is ModelStatus.Verifying -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "Prüfe Datei …",
                    color = songbirdColors.glass,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = songbirdColors.crimson,
                    trackColor = songbirdColors.bubbleBorder,
                )
            }
        }

        is ModelStatus.Failed -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = status.message,
                    color = songbirdColors.roseDust,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = songbirdColors.crimson,
                    ),
                ) {
                    Text("Nochmal")
                }
            }
        }

        is ModelStatus.NotInstalled, null -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = songbirdColors.crimson,
                    ),
                ) {
                    Text("Herunterladen")
                }
            }
        }
    }
}
