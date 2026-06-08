package io.somi.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.somi.app.LocalSongbirdColors
import io.somi.app.SongbirdColors

/**
 * v0.11.4 unified Songbird-styled dialog.
 *
 * Replaces ad-hoc Material3 [androidx.compose.material3.AlertDialog]
 * uses sprinkled across the app (the v0.11.2 mobile-data confirm in
 * FirstLaunchScreen was the first occurrence; the Settings refactor
 * adds several more). Bringing them under one roof means:
 *  - Tone (Neutral / Warning / Destructive) drives the accent color,
 *    so a destructive confirm always reads as red without callers
 *    hand-rolling the geometry.
 *  - Buttons are typed (Primary / Destructive / Ghost) rather than
 *    hand-rolled Box+clickable trees.
 *  - Containers match the chat-bubble visual language: aiBubble fill,
 *    1.dp glass hairline border, RoundedCornerShape(12.dp).
 *
 * Single-button info dialogs (no dismiss) work by passing only
 * [confirm] and leaving [dismiss] null.
 */
data class SongbirdDialogAction(
    val label: String,
    val onClick: () -> Unit,
    val kind: Kind = Kind.Primary,
) {
    enum class Kind { Primary, Destructive, Ghost }
}

enum class SongbirdDialogTone { Neutral, Warning, Destructive }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongbirdDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirm: SongbirdDialogAction,
    dismiss: SongbirdDialogAction? = null,
    tone: SongbirdDialogTone = SongbirdDialogTone.Neutral,
    properties: DialogProperties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
    ),
) {
    val songbird = LocalSongbirdColors.current
    val accent: Color = when (tone) {
        SongbirdDialogTone.Neutral -> songbird.crimson
        SongbirdDialogTone.Warning -> songbird.ember
        SongbirdDialogTone.Destructive -> songbird.signal
    }

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(songbird.aiBubble)
                .border(1.dp, songbird.bubbleBorder, RoundedCornerShape(12.dp))
                .padding(20.dp),
        ) {
            // Tone strip — a slim accent line above the title to make
            // destructive vs neutral readable at a glance.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                color = songbird.bone,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = songbird.glass,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismiss != null) {
                    SongbirdDialogButton(action = dismiss, songbird = songbird)
                    Spacer(Modifier.width(8.dp))
                }
                SongbirdDialogButton(action = confirm, songbird = songbird)
            }
        }
    }
}

@Composable
private fun SongbirdDialogButton(
    action: SongbirdDialogAction,
    songbird: SongbirdColors,
) {
    val (bg, fg, border) = when (action.kind) {
        SongbirdDialogAction.Kind.Primary -> Triple(songbird.crimson, songbird.bone, null)
        SongbirdDialogAction.Kind.Destructive -> Triple(songbird.signal, songbird.bone, null)
        SongbirdDialogAction.Kind.Ghost -> Triple(Color.Transparent, songbird.glass, songbird.glass)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .let { if (border != null) it.border(1.dp, border, RoundedCornerShape(8.dp)) else it }
            .clickable { action.onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = action.label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
