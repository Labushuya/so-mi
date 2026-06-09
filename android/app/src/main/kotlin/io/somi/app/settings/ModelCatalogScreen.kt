package io.somi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    // instances gives us the on-disk truth — modelStatuses alone
    // can show Downloading(0%) for models that were never started
    // because ModelManager.observe() treats an empty WorkInfo list
    // as "just enqueued". We override with NotInstalled when there
    // is no on-disk instance and no active WorkManager work running.
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val installedIds = instances.filter { it.isComplete }.map { it.manifestId }.toSet()
    val activeDownloadIds = modelStatuses
        .filter { (_, s) -> s is ModelStatus.Downloading && (s as ModelStatus.Downloading).bytesDownloaded > 0 }
        .keys.toSet()

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
                // Derive effective status: disk truth wins over WorkManager state.
                // Models that were never downloaded show NotInstalled (not Downloading 0%).
                val effectiveStatus: ModelStatus = when {
                    installedIds.contains(manifest.id) -> modelStatuses[manifest.id]
                        ?: ModelStatus.NotInstalled
                    activeDownloadIds.contains(manifest.id) -> modelStatuses[manifest.id]
                        ?: ModelStatus.NotInstalled
                    modelStatuses[manifest.id] is ModelStatus.Failed -> modelStatuses[manifest.id]!!
                    else -> ModelStatus.NotInstalled
                }

                Column {
                    ModelRow(
                        manifest = manifest,
                        light = light,
                        isSelected = isSelected,
                        isRecommended = isRecommended,
                        onClick = { if (effectiveStatus is ModelStatus.Installed) viewModel.selectModel(manifest) },
                    )
                    Spacer(Modifier.height(6.dp))
                    ModelActionRow(
                        status = effectiveStatus,
                        isSelected = isSelected,
                        onActivate = { viewModel.selectModel(manifest) },
                        onDownload = { viewModel.downloadModel(manifest, wifiOnly) },
                        songbirdColors = songbird,
                    )
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
                Text(
                    text = "Lade herunter … $pct %",
                    color = songbirdColors.glass,
                    style = MaterialTheme.typography.labelSmall,
                )
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
