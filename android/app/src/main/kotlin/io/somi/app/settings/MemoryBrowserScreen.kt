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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.somi.app.LocalSongbirdColors
import io.somi.app.components.SongbirdTopBar
import io.somi.data.StorageRoots
import io.somi.rag.memory.MemoryTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * v0.18.3 — echter Memory-Browser: zeigt die .md-Dateien unter SoMi/memory/
 * pro Thema als Akkordeon. Liest direkt von Disk (kein ViewModel nötig da
 * read-only). Bullets werden aus dem Markdown extrahiert (Zeilen mit "- ").
 */
@Composable
fun MemoryBrowserScreen(onBack: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    val context = LocalContext.current
    var topicData by remember { mutableStateOf<Map<MemoryTopic, List<String>>>(emptyMap()) }
    var expandedTopic by remember { mutableStateOf<MemoryTopic?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val root = StorageRoots.memory(context)
            val data = MemoryTopic.entries.associateWith { topic ->
                val file = File(root, "${topic.id}.md")
                if (!file.exists()) return@associateWith emptyList()
                file.readLines()
                    .filter { it.trimStart().startsWith("- ") }
                    .map { it.trimStart().removePrefix("- ").trim() }
            }
            topicData = data
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

        val totalCount = topicData.values.sumOf { it.size }
        Text(
            text = if (totalCount == 0) "Noch keine Erinnerungen gespeichert."
                   else "$totalCount gespeicherte Fakten",
            color = songbird.glass,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(MemoryTopic.entries, key = { it.id }) { topic ->
                val facts = topicData[topic].orEmpty()
                TopicAccordion(
                    topic = topic,
                    facts = facts,
                    expanded = expandedTopic == topic,
                    onToggle = { expandedTopic = if (expandedTopic == topic) null else topic },
                )
            }
        }
    }
}

@Composable
private fun TopicAccordion(
    topic: MemoryTopic,
    facts: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
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
                text = "${facts.size} ${if (expanded) "▲" else "▼"}",
                color = songbird.glass,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (expanded) {
            HorizontalDivider(color = songbird.bubbleBorder)
            if (facts.isEmpty()) {
                Text(
                    text = "Noch nichts in dieser Kategorie.",
                    color = songbird.glass,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else {
                facts.forEach { fact ->
                    // Strip the timestamp suffix "  _(gespeichert: ...)_" for display
                    val displayFact = fact.replace(Regex("  _\\(gespeichert:.*\\)_$"), "").trim()
                    Text(
                        text = "· $displayFact",
                        color = songbird.bone,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
