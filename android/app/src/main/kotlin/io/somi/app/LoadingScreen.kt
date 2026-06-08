package io.somi.app

import androidx.compose.animation.core.EaseInQuad
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Phase-2.5 / v0.13.0 LoadingScreen.
 *
 * Shown while the model file is mmap'd into the native context and
 * `setSystemPrompt(soul)` runs the soul prompt through llama_decode
 * once. On Magic V2 + 7B Q4_K_M with the 4096 KV cache that's roughly
 * 15–45 seconds on cold filesystem, ~10–30 s on warm.
 *
 * v0.13.0 polish layers (single rememberInfiniteTransition shares the
 * frame clock, no extra battery cost):
 *   1. Asymmetric breath cadence on the avatar alpha — slow inhale,
 *      brief hold, faster exhale, brief hold. Reads as a lung, not a
 *      metronome.
 *   2. Crimson glow ring around the avatar — radial gradient pulsing
 *      on the same breath clock.
 *   3. Static crimson radial vignette under everything — subtle
 *      cinematic ground.
 *   4. Typewriter reveal of title then subtitle on first appearance —
 *      one-shot, not looping.
 *   5. TalkBack: the title is a LiveRegion so screen-reader users get
 *      announced the loading state instead of waiting in silence for
 *      30+ seconds.
 *
 * Avoided deliberately: glitch displacement (reads as broken), full
 * scan-line sweep (reserved for chat surfaces), CRT chromatic
 * aberration (raster-tint not structurally clean on Image), blinking
 * caret (third motion source = busy).
 */
@Composable
internal fun LoadingScreen() {
    val songbird = LocalSongbirdColors.current
    val density = LocalDensity.current

    // Asymmetric breath: 1.4s inhale to 1.0, 0.2s hold, 0.8s exhale to
    // 0.62, 0.2s hold. Total cycle 2.6s.
    val transition = rememberInfiniteTransition(label = "breath")
    val breath by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2600
                0.62f at 0 using EaseInQuad
                1.0f at 1400 using LinearEasing       // hold high
                1.0f at 1600 using EaseOutQuad
                0.62f at 2400 using LinearEasing      // hold low
                0.62f at 2600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "breath-curve",
    )

    val title = stringResource(R.string.loading_title)
    val subtitle = stringResource(R.string.loading_subtitle)

    // Typewriter — one-shot, advances character-by-character.
    var titleChars by remember { mutableIntStateOf(0) }
    var subtitleChars by remember { mutableIntStateOf(0) }
    LaunchedEffect(title, subtitle) {
        // Re-init if strings change at runtime (locale flip etc.).
        titleChars = 0
        subtitleChars = 0
        // 28 ms per char on title.
        for (i in 1..title.length) {
            titleChars = i
            delay(28)
        }
        delay(320)
        // 24 ms per char on subtitle.
        for (i in 1..subtitle.length) {
            subtitleChars = i
            delay(24)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Layer 1: subtle radial vignette across the whole screen.
        // Crimson at 4% alpha at center, fades to obsidian at 60%
        // radius. Adds a cinematic warmth without competing with the
        // avatar.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxR = minOf(size.width, size.height) * 0.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to songbird.crimson.copy(alpha = 0.06f),
                        1f to Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = maxR,
                ),
                radius = maxR,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Layer 2: avatar inside a glow ring. The Box hosts both
            // — Canvas first (drawn behind), Image on top.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp),
            ) {
                // Glow ring — radial gradient signal-red, animated
                // radius + alpha with the breath.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val glowAlpha = 0.18f + (breath - 0.62f) / (1.0f - 0.62f) * 0.24f
                    val glowR = with(density) { (88.dp + (16.dp * breath)).toPx() } / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to songbird.signal.copy(alpha = glowAlpha * 0.7f),
                                0.6f to songbird.signal.copy(alpha = glowAlpha * 0.3f),
                                1f to Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = glowR,
                        ),
                        radius = glowR,
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                }
                // Avatar — alpha-pulses with the breath (62% → 100%).
                Image(
                    painter = painterResource(id = R.drawable.somi_avatar),
                    contentDescription = stringResource(R.string.avatar_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .alpha(breath)
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            width = 1.dp,
                            color = songbird.bubbleBorder,
                            shape = RoundedCornerShape(20.dp),
                        ),
                )
            }
            Spacer(Modifier.height(20.dp))
            // Layer 3: typewriter title. LiveRegion so TalkBack reads
            // it once when the loading state begins, instead of
            // leaving the user in silence for 30+ seconds.
            Text(
                text = title.take(titleChars),
                color = songbird.bone,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics {
                    contentDescription = title
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle.take(subtitleChars),
                color = songbird.glass,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
