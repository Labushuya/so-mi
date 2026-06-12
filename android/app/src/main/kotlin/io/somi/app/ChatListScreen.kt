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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.somi.app.components.SongbirdButton
import io.somi.app.components.SongbirdButtonKind
import io.somi.app.components.SongbirdDialog
import io.somi.app.components.SongbirdDialogAction
import io.somi.app.components.SongbirdDialogTone
import io.somi.data.db.ConversationEntity
import io.somi.ui.chat.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.37.0 — Chat-Übersicht. Zeigt alle Gespräche, ermöglicht:
 * - Neues Gespräch anlegen (FAB unten rechts)
 * - Gespräch antippen → öffnet Chat
 * - Long-Press → Umbenennen / Löschen
 */
@Composable
internal fun ChatListScreen(
    viewModel: ChatViewModel,
    onOpenChat: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var deleteTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Gespräche",
                    color = songbird.bone,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(songbird.bubbleBorder)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚙", color = songbird.glass, style = MaterialTheme.typography.titleSmall)
                }
            }

            if (conversations.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Noch keine Gespräche.", color = songbird.glass, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        SongbirdButton(
                            label = "+ Neues Gespräch",
                            kind = SongbirdButtonKind.Primary,
                            onClick = {
                                scope.launch {
                                    val id = viewModel.createNewConversation()
                                    onOpenChat(id)
                                }
                            },
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        ConversationRow(
                            conv = conv,
                            isActive = conv.id == viewModel.currentConversationId,
                            onClick = { onOpenChat(conv.id) },
                            onRename = { renameTarget = conv },
                            onDelete = { deleteTarget = conv },
                        )
                    }
                }
            }
        }

        // FAB — Neues Gespräch
        if (conversations.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(songbird.crimson)
                    .clickable {
                        scope.launch {
                            val id = viewModel.createNewConversation()
                            onOpenChat(id)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = songbird.bone, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
    }

    deleteTarget?.let { conv ->
        SongbirdDialog(
            onDismissRequest = { deleteTarget = null },
            title = "Gespräch löschen?",
            message = "\"${conv.title}\" und alle Nachrichten werden gelöscht.",
            tone = SongbirdDialogTone.Destructive,
            confirm = SongbirdDialogAction("Löschen", { viewModel.deleteConversation(conv.id); deleteTarget = null }, SongbirdDialogAction.Kind.Destructive),
            dismiss = SongbirdDialogAction("Abbrechen", { deleteTarget = null }),
        )
    }

    renameTarget?.let { conv ->
        var newTitle by remember(conv.id) { mutableStateOf(conv.title) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Umbenennen", color = songbird.bone, fontWeight = FontWeight.Bold) },
            text = {
                androidx.compose.foundation.text.BasicTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = songbird.bone),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(songbird.crimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(songbird.obsidian)
                        .border(1.dp, songbird.crimson, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                )
            },
            confirmButton = {
                SongbirdButton("Speichern", onClick = {
                    if (newTitle.isNotBlank()) viewModel.renameConversation(conv.id, newTitle.trim())
                    renameTarget = null
                }, kind = SongbirdButtonKind.Primary)
            },
            dismissButton = { SongbirdButton("Abbrechen", onClick = { renameTarget = null }, kind = SongbirdButtonKind.Ghost) },
            containerColor = songbird.aiBubble,
            titleContentColor = songbird.bone,
        )
    }
}

@Composable
private fun ConversationRow(
    conv: ConversationEntity,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    var showMenu by remember { mutableStateOf(false) }
    val dateStr = remember(conv.updatedAt) {
        SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN).format(Date(conv.updatedAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) songbird.crimson.copy(alpha = 0.15f) else songbird.aiBubble)
                        .border(
                            1.dp,
                            if (isActive) songbird.crimson.copy(alpha = 0.5f) else songbird.bubbleBorder,
                            RoundedCornerShape(10.dp),
                        )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                conv.title,
                color = songbird.bone,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(dateStr, color = songbird.glass, style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SongbirdButton("✎", onClick = onRename, kind = SongbirdButtonKind.Ghost, minHeight = 28.dp)
            SongbirdButton("✕", onClick = onDelete, kind = SongbirdButtonKind.Destructive, minHeight = 28.dp)
        }
    }
}
