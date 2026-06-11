package io.somi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.SolidColor
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.24.0 — Memory-Browser mit vollem CRUD:
 * - Fakten lesen, löschen, verschieben (v0.21.2)
 * - Fakten editieren (✎ pro Zeile)
 * - Fakten manuell anlegen (+ pro Kategorie)
 * - Eigene Kategorien anlegen (v0.23.1)
 */
@Composable
fun MemoryBrowserScreen(onBack: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    data class Category(val id: String, val displayName: String)

    var allCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var categoryLines by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var expandedCategory by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var moveTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) } // (categoryId, rawLine, displayText)
    var addTarget by remember { mutableStateOf<String?>(null) } // categoryId to add fact to
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            val root = StorageRoots.memory(context)
            root.mkdirs()
            val enumTopics = MemoryTopic.entries.map { t -> Category(t.id, t.displayName) }
            val customIds = root.listFiles()
                ?.filter { it.extension == "md" }
                ?.map { it.nameWithoutExtension }
                ?.filter { id -> MemoryTopic.entries.none { it.id == id } }
                ?.sorted()
                ?.map { id -> Category(id, id.replaceFirstChar { it.uppercaseChar() }.replace("_", " ")) }
                .orEmpty()
            allCategories = enumTopics + customIds
            val lines = (enumTopics + customIds).associate { cat ->
                val file = File(root, "${cat.id}.md")
                cat.id to (if (!file.exists()) emptyList()
                           else file.readLines().filter { it.trimStart().startsWith("- ") })
            }
            categoryLines = lines
        }
    }

    fun deleteFact(categoryId: String, rawLine: String) {
        scope.launch(Dispatchers.IO) {
            val file = File(StorageRoots.memory(context), "$categoryId.md")
            if (!file.exists()) return@launch
            val lines = file.readLines().toMutableList()
            lines.removeAll { it.trimStart() == rawLine.trimStart() }
            file.writeText(lines.joinToString("\n") + "\n")
            refreshKey++
        }
    }

    fun editFact(categoryId: String, rawLine: String, newText: String) {
        scope.launch(Dispatchers.IO) {
            val file = File(StorageRoots.memory(context), "$categoryId.md")
            if (!file.exists()) return@launch
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMAN).format(Date())
            val newLine = "- ${newText.trim()}  _(gespeichert: $ts)_"
            val lines = file.readLines().toMutableList()
            val idx = lines.indexOfFirst { it.trimStart() == rawLine.trimStart() }
            if (idx >= 0) lines[idx] = newLine
            else lines.add(newLine)
            file.writeText(lines.joinToString("\n") + "\n")
            refreshKey++
        }
    }

    fun addFact(categoryId: String, text: String) {
        scope.launch(Dispatchers.IO) {
            val root = StorageRoots.memory(context)
            val file = File(root, "$categoryId.md")
            file.parentFile?.mkdirs()
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMAN).format(Date())
            if (!file.exists()) {
                val displayName = allCategories.firstOrNull { it.id == categoryId }?.displayName ?: categoryId
                file.writeText("# $displayName\n\n<!-- Auto-generiert von So-Mi -->\n\n")
            }
            file.appendText("- ${text.trim()}  _(gespeichert: $ts)_\n")
            refreshKey++
        }
    }

    fun moveFact(fromId: String, rawLine: String, toId: String) {
        scope.launch(Dispatchers.IO) {
            val root = StorageRoots.memory(context)
            val srcFile = File(root, "$fromId.md")
            if (srcFile.exists()) {
                val lines = srcFile.readLines().toMutableList()
                lines.removeAll { it.trimStart() == rawLine.trimStart() }
                srcFile.writeText(lines.joinToString("\n") + "\n")
            }
            val fact = rawLine.trimStart().removePrefix("- ")
            val dstFile = File(root, "$toId.md")
            dstFile.parentFile?.mkdirs()
            if (!dstFile.exists()) {
                val displayName = allCategories.firstOrNull { it.id == toId }?.displayName ?: toId
                dstFile.writeText("# $displayName\n\n<!-- Auto-generiert von So-Mi -->\n\n")
            }
            dstFile.appendText("- $fact\n")
            refreshKey++
        }
    }

    fun createCategory(name: String) {
        scope.launch(Dispatchers.IO) {
            val id = name.lowercase()
                .replace("&", "_und_")
                .replace(" ", "_")
                .replace(Regex("[^a-z0-9_äöüß]"), "")
                .replace(Regex("_+"), "_")  // collapse multiple underscores
                .trim('_')
            if (id.isBlank()) return@launch
            val file = File(StorageRoots.memory(context), "$id.md")
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.writeText("# $name\n\n<!-- Eigene Kategorie -->\n\n")
            }
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
        Spacer(Modifier.height(8.dp))

        val totalCount = categoryLines.values.sumOf { it.size }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (totalCount == 0) "Noch nichts gespeichert." else "$totalCount Fakten",
                color = songbird.glass,
                style = MaterialTheme.typography.bodySmall,
            )
            SongbirdButton("+ Kategorie", onClick = { showNewCategoryDialog = true }, kind = SongbirdButtonKind.Secondary, minHeight = 32.dp)
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(allCategories, key = { it.id }) { cat ->
                val lines = categoryLines[cat.id].orEmpty()
                FactAccordion(
                    categoryId = cat.id,
                    displayName = cat.displayName,
                    rawLines = lines,
                    expanded = expandedCategory == cat.id,
                    onToggle = { expandedCategory = if (expandedCategory == cat.id) null else cat.id },
                    onDelete = { line -> deleteTarget = cat.id to line },
                    onEdit = { line ->
                        val display = line.trimStart().removePrefix("- ").replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim()
                        editTarget = Triple(cat.id, line, display)
                    },
                    onMove = { line -> moveTarget = cat.id to line },
                    onAdd = { addTarget = cat.id },
                )
            }
        }
    }

    // Delete dialog
    deleteTarget?.let { (catId, line) ->
        val text = line.trimStart().removePrefix("- ").replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim()
        SongbirdDialog(
            onDismissRequest = { deleteTarget = null },
            title = "Erinnerung löschen?",
            message = "\"$text\"",
            tone = SongbirdDialogTone.Destructive,
            confirm = SongbirdDialogAction("Löschen", { deleteFact(catId, line); deleteTarget = null }, SongbirdDialogAction.Kind.Destructive),
            dismiss = SongbirdDialogAction("Abbrechen", { deleteTarget = null }),
        )
    }

    // Edit dialog
    editTarget?.let { (catId, rawLine, displayText) ->
        TextInputDialog(
            title = "Erinnerung bearbeiten",
            initial = displayText,
            confirmLabel = "Speichern",
            onConfirm = { newText -> editFact(catId, rawLine, newText); editTarget = null },
            onDismiss = { editTarget = null },
        )
    }

    // Add dialog
    addTarget?.let { catId ->
        val catName = allCategories.firstOrNull { it.id == catId }?.displayName ?: catId
        TextInputDialog(
            title = "Neue Erinnerung in \"$catName\"",
            initial = "",
            confirmLabel = "Hinzufügen",
            onConfirm = { text -> addFact(catId, text); addTarget = null },
            onDismiss = { addTarget = null },
        )
    }

    // Move dialog
    moveTarget?.let { (fromId, line) ->
        val text = line.trimStart().removePrefix("- ").replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim()
        val targets = allCategories.filter { it.id != fromId }
        MoveDialog(
            factText = text,
            targets = targets.map { it.id to it.displayName },
            onMove = { toId -> moveFact(fromId, line, toId); moveTarget = null },
            onDismiss = { moveTarget = null },
        )
    }

    // New category dialog
    if (showNewCategoryDialog) {
        TextInputDialog(
            title = "Neue Kategorie",
            initial = "",
            confirmLabel = "Anlegen",
            onConfirm = { name -> createCategory(name); showNewCategoryDialog = false },
            onDismiss = { showNewCategoryDialog = false },
        )
    }
}

@Composable
private fun FactAccordion(
    categoryId: String,
    displayName: String,
    rawLines: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
    onMove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    Column(modifier = Modifier.fillMaxWidth().background(songbird.aiBubble)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(displayName, color = songbird.bone, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${rawLines.size} ${if (expanded) "▲" else "▼"}", color = songbird.glass, style = MaterialTheme.typography.labelSmall)
        }
        if (expanded) {
            HorizontalDivider(color = songbird.bubbleBorder)
            if (rawLines.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Leer.", color = songbird.glass, style = MaterialTheme.typography.bodySmall)
                    SongbirdButton("+", onClick = onAdd, kind = SongbirdButtonKind.Ghost, minHeight = 28.dp)
                }
            } else {
                rawLines.forEach { rawLine ->
                    val display = rawLine.trimStart().removePrefix("- ").replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("· $display", color = songbird.bone, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).padding(end = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            SongbirdButton("✎", onClick = { onEdit(rawLine) }, kind = SongbirdButtonKind.Ghost, minHeight = 26.dp)
                            SongbirdButton("↗", onClick = { onMove(rawLine) }, kind = SongbirdButtonKind.Ghost, minHeight = 26.dp)
                            SongbirdButton("✕", onClick = { onDelete(rawLine) }, kind = SongbirdButtonKind.Destructive, minHeight = 26.dp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SongbirdButton("+ Hinzufügen", onClick = onAdd, kind = SongbirdButtonKind.Ghost, minHeight = 28.dp)
                }
            }
        }
    }
}

@Composable
@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = songbird.bone, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = if (initial.isEmpty()) "Text eingeben:" else "Text bearbeiten:",
                    color = songbird.glass,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = LocalTextStyle.current.copy(
                        color = songbird.bone,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    ),
                    cursorBrush = SolidColor(songbird.crimson),
                    decorationBox = { inner ->
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(songbird.obsidian)
                                .border(1.dp, songbird.crimson, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .padding(10.dp),
                        ) {
                            if (text.isEmpty()) {
                                Text("Hier tippen…", color = songbird.glass, style = MaterialTheme.typography.bodyMedium)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            SongbirdButton(confirmLabel, onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, kind = SongbirdButtonKind.Primary)
        },
        dismissButton = { SongbirdButton("Abbrechen", onClick = onDismiss, kind = SongbirdButtonKind.Ghost) },
        containerColor = songbird.aiBubble,
        titleContentColor = songbird.bone,
    )
}

@Composable
private fun MoveDialog(
    factText: String,
    targets: List<Pair<String, String>>,
    onMove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wohin verschieben?", color = songbird.bone, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("\"$factText\"", color = songbird.glass, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                targets.forEach { (id, name) ->
                    Text(name, color = songbird.crimson, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clickable { onMove(id) }.padding(vertical = 10.dp))
                    HorizontalDivider(color = songbird.bubbleBorder)
                }
            }
        },
        confirmButton = {},
        dismissButton = { SongbirdButton("Abbrechen", onClick = onDismiss, kind = SongbirdButtonKind.Ghost) },
        containerColor = songbird.aiBubble,
        titleContentColor = songbird.bone,
    )
}
