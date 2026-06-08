package io.somi.app.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.somi.app.LocalSongbirdColors
import io.somi.app.components.SectionCard
import io.somi.app.components.SongbirdTopBar
import io.somi.data.StorageRoots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * v0.15.0 — Daten-Browser für SoMi/-Wurzel.
 *
 * Liest read-only aus dem App-eigenen externalFilesDir/SoMi/-Baum.
 * Zeigt Größen + erlaubt Sharing einzelner Dateien per FileProvider.
 *
 * **Threading:** alle Datei-I/O-Aufrufe (listFiles, walkTopDown für
 * Größen-Aufsummierung, length()) laufen in einem LaunchedEffect auf
 * Dispatchers.IO. Der State ist ein vorberechneter Snapshot. Damit
 * stockt das Scrollen nicht, auch wenn FUSE auf externen App-Dirs
 * langsam ist.
 *
 * Kein ADB nötig — die Datei-Sharing-Intent reicht aus, um eine Datei
 * in einen anderen App-Container (Files, Drive, Email) zu schicken.
 */
@Composable
fun DataBrowserScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val songbird = LocalSongbirdColors.current
    val root = remember { StorageRoots.root(context) }
    var current by remember { mutableStateOf(root) }
    var snapshot by remember { mutableStateOf<DirSnapshot?>(null) }

    // System back: erst Verzeichnishierarchie hochwandern, dann erst raus.
    BackHandler(enabled = current != root) {
        current = current.parentFile ?: root
    }

    // I/O off main: bei jedem current-Wechsel neu snapshotten.
    LaunchedEffect(current) {
        snapshot = withContext(Dispatchers.IO) { computeSnapshot(current, root) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SongbirdTopBar(
            title = "Dateien",
            onBack = {
                if (current == root) onBack()
                else current = current.parentFile ?: root
            },
        )
        Spacer(Modifier.height(8.dp))

        SectionCard {
            Text(
                text = "Pfad: ${snapshot?.relativePath ?: "/"}",
                color = songbird.glass,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (snapshot == null) "lädt…"
                else "${snapshot!!.entries.size} Einträge · ${formatSize(snapshot!!.totalBytes)}",
                color = songbird.bone,
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            val entries = snapshot?.entries.orEmpty()
            items(entries, key = { it.absolutePath }) { e ->
                FileRow(
                    entry = e,
                    onClick = {
                        if (e.isDirectory) current = File(e.absolutePath)
                        else shareFile(context, File(e.absolutePath))
                    },
                )
            }
            if (snapshot != null && entries.isEmpty()) {
                item {
                    Text(
                        text = "Leer.",
                        color = songbird.glass,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: Entry, onClick: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    val sizeText = if (entry.isDirectory) "${entry.childCount} Einträge" else formatSize(entry.sizeBytes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (entry.isDirectory) "📁" else "📄",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.padding(horizontal = 6.dp))
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                text = entry.name,
                color = songbird.bone,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = sizeText,
                color = songbird.glass,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private data class Entry(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val childCount: Int,
)

private data class DirSnapshot(
    val relativePath: String,
    val totalBytes: Long,
    val entries: List<Entry>,
)

private fun computeSnapshot(dir: File, root: File): DirSnapshot {
    val children = runCatching { dir.listFiles()?.toList()?.sortedBy { it.name }.orEmpty() }
        .getOrDefault(emptyList())
    val entries = children.map { f ->
        Entry(
            name = f.name,
            absolutePath = f.absolutePath,
            isDirectory = f.isDirectory,
            sizeBytes = if (f.isFile) runCatching { f.length() }.getOrDefault(0L) else 0L,
            childCount = if (f.isDirectory) runCatching { f.list()?.size ?: 0 }.getOrDefault(0) else 0,
        )
    }
    val total = runCatching {
        if (!dir.isDirectory) dir.length()
        else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)
    val rel = "/" + dir.absolutePath.removePrefix(root.absolutePath).trimStart('/').ifEmpty { "" }
    return DirSnapshot(relativePath = rel, totalBytes = total, entries = entries)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun shareFile(context: android.content.Context, file: File) {
    if (!file.exists() || !file.isFile) return
    try {
        val authority = context.packageName + ".fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Datei teilen").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (t: Throwable) {
        // FileProvider.getUriForFile wirft IllegalArgumentException auf
        // exotischen Storage-Layouts (Work-Profiles, manche OEM-Sandboxen).
        // Ein Toast ist freundlicher als ein Crash.
        Toast.makeText(context, "Datei kann auf diesem Gerät nicht geteilt werden.", Toast.LENGTH_SHORT).show()
    }
}
