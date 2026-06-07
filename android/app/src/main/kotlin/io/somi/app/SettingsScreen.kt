package io.somi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.somi.data.ModelStorage

/**
 * Settings screen — for v0.10.x focused on storage / model cleanup.
 *
 * Lists every on-disk model instance the app can find across all
 * historical storage paths, with size + completeness + canonical flag.
 * Lets the user delete duplicates without leaving the app.
 */
@Composable
internal fun SettingsScreen(
    instances: List<ModelStorage.ModelInstance>,
    onDeleteInstance: (ModelStorage.ModelInstance) -> Unit,
    onClose: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .padding(16.dp),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClose() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "←",
                    color = songbird.bone,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Speicher",
                color = songbird.bone,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))

        if (instances.isEmpty()) {
            Text(
                text = "Keine Modelle gefunden.",
                color = songbird.glass,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        // Total size summary
        val totalBytes = instances.sumOf { it.sizeBytes }
        Text(
            text = "Insgesamt belegt: ${formatGB(totalBytes)}",
            color = songbird.bone,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        if (instances.size > 1) {
            Text(
                text = "${instances.size} Kopien gefunden — du kannst Duplikate löschen.",
                color = songbird.glass,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(16.dp))

        // List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            instances.forEach { inst ->
                InstanceCard(
                    instance = inst,
                    onDelete = { onDeleteInstance(inst) },
                )
            }
        }
    }
}

@Composable
private fun InstanceCard(
    instance: ModelStorage.ModelInstance,
    onDelete: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(songbird.aiBubble)
            .border(
                width = if (instance.isCanonical) 1.dp else 1.dp,
                color = if (instance.isCanonical) songbird.crimson else songbird.bubbleBorder,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
    ) {
        // Title + canonical/duplicate badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = instance.displayName,
                color = songbird.bone,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (instance.isCanonical) {
                BadgePill(label = "AKTIV", color = songbird.crimson)
            } else {
                BadgePill(label = "DUPLIKAT", color = songbird.signal)
            }
        }
        Spacer(Modifier.height(4.dp))

        // Size + completeness
        val statusLabel = when {
            !instance.isComplete -> "${formatGB(instance.sizeBytes)} · unvollständig"
            else -> formatGB(instance.sizeBytes)
        }
        Text(
            text = statusLabel,
            color = songbird.glass,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))

        // Path
        Text(
            text = pathHint(instance.rootPath.absolutePath),
            color = songbird.glass.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = instance.rootPath.absolutePath,
            color = songbird.glass.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
        )

        // Files (compact)
        if (instance.filesPresent.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = instance.filesPresent.joinToString(" · "),
                color = songbird.glass.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Delete action
        if (!confirmDelete) {
            DeleteButton(
                label = if (instance.isCanonical) "Modell löschen" else "Duplikat löschen",
                onClick = { confirmDelete = true },
                isWarning = instance.isCanonical,
            )
        } else {
            Column {
                Text(
                    text = if (instance.isCanonical) {
                        "Wirklich löschen? Du musst danach das Modell neu laden (4 GB)."
                    } else {
                        "Wirklich diese Kopie löschen?"
                    },
                    color = songbird.bone,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeleteButton(
                        label = "Ja, löschen",
                        onClick = {
                            confirmDelete = false
                            onDelete()
                        },
                        isWarning = true,
                    )
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, songbird.glass, RoundedCornerShape(6.dp))
                            .clickable { confirmDelete = false }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Abbrechen",
                            color = songbird.glass,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgePill(label: String, color: androidx.compose.ui.graphics.Color) {
    val songbird = LocalSongbirdColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DeleteButton(label: String, onClick: () -> Unit, isWarning: Boolean) {
    val songbird = LocalSongbirdColors.current
    val color = if (isWarning) songbird.signal else songbird.glass
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = songbird.bone,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatGB(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    return if (gb >= 1.0) "%.2f GB".format(gb)
    else "%.0f MB".format(bytes / 1_048_576.0)
}

private fun pathHint(absPath: String): String = when {
    absPath.contains("/Android/data/") -> "App-privater Speicher (geht bei Uninstall verloren)"
    absPath.contains("/Documents/SoMi-Models") -> "Öffentlicher Documents-Ordner (überlebt Uninstall)"
    absPath.contains("/data/data/") -> "App-interner Speicher (geht bei Uninstall verloren)"
    else -> "Pfad"
}
