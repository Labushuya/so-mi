package io.somi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.somi.app.LocalSongbirdColors
import io.somi.app.components.SongbirdButton
import io.somi.app.components.SongbirdButtonKind
import io.somi.app.components.SongbirdDialog
import io.somi.app.components.SongbirdDialogAction
import io.somi.app.components.SongbirdDialogTone
import io.somi.app.components.SongbirdTopBar
import io.somi.data.StorageRoots
import io.somi.rag.memory.MemoryTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * v0.21.0 — Memory-Browser mit CRUD:
 * - Fakten pro Thema als Akkordeon (lesen)
 * - Einzelne Fakten löschen (Long-Press → Bestätigungs-Dialog)
 * - Einzelne Fakten in andere Kategorie verschieben
 * Schreibt direkt in die .md-Dateien (Disk-Truth).
 */
@Composable
fun MemoryBrowserScreen(onBack: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Map topic → list of raw lines (WITH timestamp) for correct delete/move
    var topicLines by remember { mutableStateOf<Map<MemoryTopic, List<String>>>(emptyMap()) }
    var expandedTopic by remember { mutableStateOf<MemoryTopic?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<MemoryTopic, String>?>(null) }
    var moveTarget by remember { mutableStateOf<Pair<MemoryTopic, String>?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            val root = StorageRoots.memory(context)
            val data = MemoryTopic.entries.associateWith { topic ->
                val file = File(root, "${topic.id}.md")
                if (!file.exists()) emptyList()
                else file.readLines().filter { it.trimStart().startsWith("- ") }
            }
            topicLines = data
        }
    }

    fun deleteFact(topic: MemoryTopic, rawLine: String) {
        scope.launch(Dispatchers.IO) {
            val root = StorageRoots.memory(context)
            val file = File(root, "${topic.id}.md")
            if (!file.exists()) return@launch
            val lines = file.readLines().toMutableList()
            lines.removeAll { it.trimStart() == rawLine.trimStart() }
            file.writeText(lines.joinToString("\n") + "\n")
            refreshKey++
        }
    }

    fun moveFact(fromTopic: MemoryTopic, rawLine: String, toTopic: MemoryTopic) {
        scope.launch(Dispatchers.IO) {
            val root = StorageRoots.memory(context)
            // Remove from source
            val srcFile = File(root, "${fromTopic.id}.md")
            if (srcFile.exists()) {
                val lines = srcFile.readLines().toMutableList()
                lines.removeAll { it.trimStart() == rawLine.trimStart() }
                srcFile.writeText(lines.joinToString("\n") + "\n")
            }
            // Append to target (strip old bullet prefix, re-add)
            val fact = rawLine.trimStart().removePrefix("- ")
            val dstFile = File(root, "${toTopic.id}.md")
            dstFile.parentFile?.mkdirs()
            if (!dstFile.exists()) {
                dstFile.writeText("# ${toTopic.displayName}\n\n<!-- Auto-generiert von So-Mi -->\n\n")
            }
            dstFile.appendText("- $fact\n")
            refreshKey++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(16.dp),
    ) {
        SongbirdTopBar(title = "Erinnerungen", onBack = onBack)
        Spacer(Modifier.height(12.dp))

        val totalCount = topicLines.values.sumOf { it.size }
        Text(
            text = if (totalCount == 0) "Noch keine Erinnerungen gespeichert."
                   else "$totalCount gespeicherte Fakten · Antippen um aufzuklappen · Lang drücken zum Löschen/Verschieben",
            color = songbird.glass,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(MemoryTopic.entries, key = { it.id }) { topic ->
                val lines = topicLines[topic].orEmpty()
                TopicAccordion(
                    topic = topic,
                    rawLines = lines,
                    expanded = expandedTopic == topic,
                    onToggle = { expandedTopic = if (expandedTopic == topic) null else topic },
                    onDeleteFact = { line -> deleteTarget = topic to line },
                    onMoveFact = { line -> moveTarget = topic to line },
                )
            }
        }
    }

    // Delete confirmation dialog
    deleteTarget?.let { (topic, line) ->
        val displayText = line.trimStart().removePrefix("- ")
            .replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim()
        SongbirdDialog(
            onDismissRequest = { deleteTarget = null },
            title = "Erinnerung löschen?",
            message = "\"$displayText\" wird dauerhaft entfernt.",
            tone = SongbirdDialogTone.Destructive,
            confirm = SongbirdDialogAction(
                label = "Löschen",
                onClick = { deleteFact(topic, line); deleteTarget = null },
                kind = SongbirdDialogAction.Kind.Destructive,
            ),
            dismiss = SongbirdDialogAction(
                label = "Abbrechen",
                onClick = { deleteTarget = null },
            ),
        )
    }

    // Move dialog
    moveTarget?.let { (fromTopic, line) ->
        val displayText = line.trimStart().removePrefix("- ")
            .replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim()
        val otherTopics = MemoryTopic.entries.filter { it != fromTopic }
        MoveDialog(
            factText = displayText,
            targets = otherTopics,
            onMove = { to -> moveFact(fromTopic, line, to); moveTarget = null },
            onDismiss = { moveTarget = null },
        )
    }
}

@Composable
private fun TopicAccordion(
    topic: MemoryTopic,
    rawLines: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDeleteFact: (String) -> Unit,
    onMoveFact: (String) -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(songbird.aiBubble),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = topic.displayName,
                color = songbird.bone,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${rawLines.size} ${if (expanded) "▲" else "▼"}",
                color = songbird.glass,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (expanded) {
            HorizontalDivider(color = songbird.bubbleBorder)
            if (rawLines.isEmpty()) {
                Text(
                    text = "Noch nichts in dieser Kategorie.",
                    color = songbird.glass,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else {
                rawLines.forEach { rawLine ->
                    val displayFact = rawLine.trimStart().removePrefix("- ")
                        .replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "· $displayFact",
                            color = songbird.bone,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SongbirdButton(
                                label = "↗",
                                kind = SongbirdButtonKind.Ghost,
                                onClick = { onMoveFact(rawLine) },
                                minHeight = 28.dp,
                            )
                            SongbirdButton(
                                label = "✕",
                                kind = SongbirdButtonKind.Destructive,
                                onClick = { onDeleteFact(rawLine) },
                                minHeight = 28.dp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MoveDialog(
    factText: String,
    targets: List<MemoryTopic>,
    onMove: (MemoryTopic) -> Unit,
    onDismiss: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Wohin verschieben?", color = songbird.bone, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("\"$factText\"", color = songbird.glass, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                targets.forEach { topic ->
                    Text(
                        text = topic.displayName,
                        color = songbird.crimson,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMove(topic) }
                            .padding(vertical = 10.dp),
                    )
                    HorizontalDivider(color = songbird.bubbleBorder)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            SongbirdButton(label = "Abbrechen", kind = SongbirdButtonKind.Ghost, onClick = onDismiss)
        },
        containerColor = songbird.aiBubble,
        titleContentColor = songbird.bone,
    )
}
