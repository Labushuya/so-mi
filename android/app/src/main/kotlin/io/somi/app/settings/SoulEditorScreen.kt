package io.somi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.somi.app.LocalSongbirdColors
import io.somi.app.components.SectionCard
import io.somi.app.components.SectionHeader
import io.somi.app.components.SongbirdButton
import io.somi.app.components.SongbirdButtonKind
import io.somi.app.components.SongbirdDialog
import io.somi.app.components.SongbirdDialogAction
import io.somi.app.components.SongbirdDialogTone
import io.somi.app.components.SongbirdTopBar
import io.somi.data.soul.SoulBackup
import io.somi.data.soul.SoulRepository
import io.somi.llm.SoulPromptLoader
import io.somi.ui.chat.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.11.4 — Soul-Editor screen.
 *
 * Lets the user edit `soul_condensed.md` in-app with backup-on-save and
 * live-reload (no app restart required). Hard-cap at 1200 chars matches
 * MAX_SYSTEM_PROMPT_CHARS in ChatViewModel — typing beyond the cap is
 * blocked at the TextField input level.
 *
 * Save flow:
 *   1. SoulRepository.save(text) rotates the previous override into
 *      backups/, writes the new file, prunes to last 10 backups.
 *   2. ChatViewModel.reloadSoul() invalidates the loader cache and
 *      re-runs setSystemPrompt on the engine.
 *   3. UI flips back to the read state with a "gespeichert" toast.
 *
 * Reset flow:
 *   - Songbird-styled confirm dialog before the reset triggers, so a
 *     user doesn't wipe their edits with a misclick.
 */
@Composable
fun SoulEditorScreen(
    viewModel: ChatViewModel,
    soulRepository: SoulRepository,
    soulPromptLoader: SoulPromptLoader,
    onBack: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    val scope = rememberCoroutineScope()
    val backups by soulRepository.backups.collectAsState(initial = emptyList())

    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var initialised by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var savedToast by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<SoulBackup?>(null) }

    // Initial load: pull the current soul (override or asset fallback).
    LaunchedEffect(Unit) {
        if (!initialised) {
            val text = runCatching { soulPromptLoader.load() }.getOrNull().orEmpty()
            draft = TextFieldValue(text.take(MAX_CHARS))
            initialised = true
            soulRepository.refreshBackupList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            // v0.15.0: union of systemBars + displayCutout. imePadding stays — Editor needs to clear the keyboard.
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .imePadding()
            .padding(16.dp),
    ) {
        SongbirdTopBar(title = "Persönlichkeit", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                SectionCard(title = "Editor") {
                    val charsUsed = draft.text.length
                    val nearCap = charsUsed >= MAX_CHARS - 50
                    Text(
                        text = "So-Mis Persönlichkeit. Wird bei jeder Antwort als System-Prompt mitgegeben. Max $MAX_CHARS Zeichen.",
                        color = songbird.glass,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 420.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(songbird.composerBg)
                            .border(1.dp, songbird.composerBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { new ->
                                // Hard cap: drop the input if it would push past MAX_CHARS.
                                if (new.text.length <= MAX_CHARS) draft = new
                            },
                            textStyle = LocalTextStyle.current.copy(
                                color = songbird.bone,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            ),
                            cursorBrush = SolidColor(songbird.signal),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$charsUsed / $MAX_CHARS",
                            color = if (nearCap) songbird.signal else songbird.glass,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        SongbirdButton(
                            label = "Speichern",
                            kind = SongbirdButtonKind.Primary,
                            onClick = {
                                scope.launch {
                                    val result = soulRepository.save(draft.text)
                                    if (result != null) {
                                        viewModel.reloadSoul()
                                        savedToast = "Gespeichert. So-Mi liest die neue Version sofort."
                                    } else {
                                        savedToast = "Speichern fehlgeschlagen. Logcat checken."
                                    }
                                }
                            },
                            enabled = draft.text.isNotBlank() && initialised,
                        )
                    }
                    if (savedToast != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = savedToast!!,
                            color = songbird.glass,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Auf Werkseinstellung zurücksetzen") {
                    Text(
                        text = "Verwirft deine eigene Version und nutzt wieder die mitgelieferte Persönlichkeit. Die aktuelle Version wird vorher als Backup gesichert.",
                        color = songbird.glass,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    SongbirdButton(
                        label = "Zurücksetzen",
                        kind = SongbirdButtonKind.Destructive,
                        onClick = { showResetConfirm = true },
                    )
                }
            }

            item {
                SectionHeader("Backups (${backups.size})")
            }
            if (backups.isEmpty()) {
                item {
                    SectionCard {
                        Text(
                            text = "Noch keine Backups. Werden bei jedem Speichern angelegt — letzte 10 bleiben.",
                            color = songbird.glass,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                items(backups, key = { it.timestamp }) { backup ->
                    BackupRow(
                        backup = backup,
                        onRestore = { pendingRestore = backup },
                    )
                }
            }
        }
    }

    if (showResetConfirm) {
        SongbirdDialog(
            onDismissRequest = { showResetConfirm = false },
            title = "Wirklich zurücksetzen?",
            message = "Die aktuelle Version wird als Backup gesichert. Du kannst sie aus der Liste wiederherstellen.",
            tone = SongbirdDialogTone.Destructive,
            confirm = SongbirdDialogAction(
                label = "Zurücksetzen",
                kind = SongbirdDialogAction.Kind.Destructive,
                onClick = {
                    showResetConfirm = false
                    scope.launch {
                        if (soulRepository.reset()) {
                            viewModel.reloadSoul()
                            val text = runCatching { soulPromptLoader.load() }.getOrNull().orEmpty()
                            draft = TextFieldValue(text.take(MAX_CHARS))
                            savedToast = "Zurückgesetzt. So-Mi nutzt jetzt wieder die Werkseinstellung."
                        }
                    }
                },
            ),
            dismiss = SongbirdDialogAction(
                label = "Abbrechen",
                kind = SongbirdDialogAction.Kind.Ghost,
                onClick = { showResetConfirm = false },
            ),
        )
    }

    pendingRestore?.let { backup ->
        SongbirdDialog(
            onDismissRequest = { pendingRestore = null },
            title = "Backup wiederherstellen?",
            message = "Die aktuelle Version wird zuerst als neues Backup gesichert.",
            tone = SongbirdDialogTone.Warning,
            confirm = SongbirdDialogAction(
                label = "Wiederherstellen",
                kind = SongbirdDialogAction.Kind.Primary,
                onClick = {
                    pendingRestore = null
                    scope.launch {
                        if (soulRepository.restore(backup)) {
                            viewModel.reloadSoul()
                            val text = runCatching { soulPromptLoader.load() }.getOrNull().orEmpty()
                            draft = TextFieldValue(text.take(MAX_CHARS))
                            savedToast = "Wiederhergestellt."
                        }
                    }
                },
            ),
            dismiss = SongbirdDialogAction(
                label = "Abbrechen",
                kind = SongbirdDialogAction.Kind.Ghost,
                onClick = { pendingRestore = null },
            ),
        )
    }
}

@Composable
private fun BackupRow(
    backup: SoulBackup,
    onRestore: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTimestamp(backup.timestamp),
                    color = songbird.bone,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${backup.lengthBytes} Zeichen",
                    color = songbird.glass,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            SongbirdButton(
                label = "Wiederherstellen",
                kind = SongbirdButtonKind.Ghost,
                onClick = onRestore,
            )
        }
    }
}

private val DATE_FORMAT = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)

private fun formatTimestamp(epochMillis: Long): String =
    DATE_FORMAT.format(Date(epochMillis))

private const val MAX_CHARS = 1200
