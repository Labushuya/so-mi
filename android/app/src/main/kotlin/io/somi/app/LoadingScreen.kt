package io.somi.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Phase-2.5 LoadingScreen.
 *
 * Shown while the model file is mmap'd into the native context and
 * `setSystemPrompt(soul)` runs the soul prompt through llama_decode
 * once. On Magic V2 + 7B Q4_K_M with the 4096 KV cache that's roughly
 * 15–45 seconds on cold filesystem, ~10–30 s on warm. The avatar
 * breathes (alpha 0.55 ↔ 1.0) so the UI looks alive while the native
 * thread is blocked.
 *
 * The loader text comes from strings.xml in So-Mi's voice — see
 * v0.11.2 wording decision.
 */
@Composable
internal fun LoadingScreen() {
    val songbird = LocalSongbirdColors.current

    val transition = rememberInfiniteTransition(label = "warm-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha-pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.somi_avatar),
                contentDescription = stringResource(R.string.avatar_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .alpha(pulse)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 1.dp,
                        color = songbird.bubbleBorder,
                        shape = RoundedCornerShape(20.dp),
                    ),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.loading_title),
                color = songbird.bone,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.loading_subtitle),
                color = songbird.glass,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
