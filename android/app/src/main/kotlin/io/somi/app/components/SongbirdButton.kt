package io.somi.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.somi.app.LocalSongbirdColors

enum class SongbirdButtonKind { Primary, Destructive, Ghost }

/**
 * v0.11.4 — typed Songbird button.
 *
 * Replaces the ad-hoc `DeleteButton` / 'Abbrechen' Box patterns sprinkled
 * around the codebase. Three kinds: Primary (crimson fill), Destructive
 * (signal fill), Ghost (transparent + glass outline). All share the
 * 36.dp height + RoundedCornerShape(6.dp) geometry.
 */
@Composable
fun SongbirdButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: SongbirdButtonKind = SongbirdButtonKind.Primary,
    enabled: Boolean = true,
    minHeight: Dp = 36.dp,
) {
    val songbird = LocalSongbirdColors.current
    val (bg, fg, border) = when (kind) {
        SongbirdButtonKind.Primary -> Triple(songbird.crimson, songbird.bone, null)
        SongbirdButtonKind.Destructive -> Triple(songbird.signal, songbird.bone, null)
        SongbirdButtonKind.Ghost -> Triple(Color.Transparent, songbird.glass, songbird.glass)
    }
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .height(minHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(bg.copy(alpha = bg.alpha * alpha))
            .let { if (border != null) it.border(1.dp, border.copy(alpha = alpha), RoundedCornerShape(6.dp)) else it }
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg.copy(alpha = alpha),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
