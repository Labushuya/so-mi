package io.somi.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.somi.app.LocalSongbirdColors

/**
 * v0.11.4 — Section header (ALL-CAPS kicker) used to label
 * SectionCard groupings in the Settings screen.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    val songbird = LocalSongbirdColors.current
    Text(
        text = text.uppercase(),
        color = songbird.glass,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/**
 * v0.11.4 — Card container used to group related Settings rows.
 * Reuses the chat-bubble palette: aiBubble fill, 1.dp bubbleBorder,
 * 12.dp radius.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    Column {
        if (title != null) SectionHeader(title)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(songbird.aiBubble)
                .border(1.dp, songbird.bubbleBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            content()
        }
    }
}

/**
 * Single text row (label + optional value) used as a building block
 * inside SectionCard for read-only diagnostic data.
 */
@Composable
fun SettingsRow(
    label: String,
    value: String? = null,
    modifier: Modifier = Modifier,
) {
    val songbird = LocalSongbirdColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = songbird.bone,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                color = songbird.glass,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
