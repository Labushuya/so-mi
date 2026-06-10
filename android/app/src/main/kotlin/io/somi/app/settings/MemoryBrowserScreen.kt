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

/**
 * v0.22.1 — Memory-Browser mit CRUD + eigenen Kategorien.
 *
 * Liest ALLE .md-Dateien aus SoMi/memory/ — nicht nur Enum-Topics.
 * Damit können User eigene Kategorien anlegen (z.B. "Arbeit", "Sport").
 * Die .md-Datei selbst ist die Persistenz der Kategorie.
 */
@Composable
fun MemoryBrowserScreen(onBack: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    data class Category(val id: String, val displayName: String, val isCustom: Boolean)

    var allCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var categoryLines by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var moveTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            val root = StorageRoots.memory(context)
            root.mkdirs()
            // Enum topics first (fixed order), then any custom .md files
            val enumTopics = MemoryTopic.entries.map { t ->
                Category(t.id, t.displayName, isCustom = false)
            }
            val customIds = root.listFiles()
                ?.filter { it.extension == "md" }
                ?.map { it.nameWithoutExtension }
                ?.filter { id -> MemoryTopic.entries.none { it.id == id } }
                ?.sorted()
                ?.map { id -> Category(id, id.replaceFirstChar { it.uppercaseChar() }, isCustom = true) }
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
            val id = name.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_äöü]"), "")
            if (id.isBlank()) return@launch
            val file = File(StorageRoots.memory(context), "$id.md")
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.writeText("# $name\n\n<!-- Eigene Kategorie, erstellt von Dir -->\n\n")
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
                text = if (totalCount == 0) "Noch nichts gespeichert."
                       else "$totalCount Fakten",
                color = songbird.glass,
                style = MaterialTheme.typography.bodySmall,
            )
            SongbirdButton(
                label = "+ Kategorie",
                kind = SongbirdButtonKind.Secondary,
                onClick = { showNewCategoryDialog = true },
                minHeight = 32.dp,
            )
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
                    onMove = { line -> moveTarget = cat.id to line },
                )
            }
        }
    }

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

    if (showNewCategoryDialog) {
        NewCategoryDialog(
            onCreate = { name -> createCategory(name); showNewCategoryDialog = false },
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
    onMove: (String) -> Unit,
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
                Text("Leer.", color = songbird.glass, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            } else {
                rawLines.forEach { rawLine ->
                    val display = rawLine.trimStart().removePrefix("- ").replace(Regex("\\s+_\\(gespeichert:.*?\\)_\\s*$"), "").trim()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("· $display", color = songbird.bone, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SongbirdButton("↗", onClick = { onMove(rawLine) }, kind = SongbirdButtonKind.Ghost, minHeight = 28.dp)
                            SongbirdButton("✕", onClick = { onDelete(rawLine) }, kind = SongbirdButtonKind.Destructive, minHeight = 28.dp)
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

@Composable
private fun NewCategoryDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Kategorie", color = songbird.bone, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Name der neuen Kategorie:", color = songbird.glass, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = LocalTextStyle.current.copy(color = songbird.bone),
                    cursorBrush = SolidColor(songbird.crimson),
                    modifier = Modifier.fillMaxWidth().background(songbird.obsidian).padding(8.dp),
                )
            }
        },
        confirmButton = {
            SongbirdButton("Anlegen", onClick = { if (name.isNotBlank()) onCreate(name.trim()) }, kind = SongbirdButtonKind.Primary)
        },
        dismissButton = { SongbirdButton("Abbrechen", onClick = onDismiss, kind = SongbirdButtonKind.Ghost) },
        containerColor = songbird.aiBubble,
        titleContentColor = songbird.bone,
    )
}
