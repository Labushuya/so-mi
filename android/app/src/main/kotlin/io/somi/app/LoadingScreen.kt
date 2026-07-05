package io.somi.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInQuad
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Boot-Monolog: So-Mi spricht während sie hochfährt.
// Rotiert alle 5s. Mix aus persönlich, technisch-zynisch, Cyberpunk-Flavor.
private val BOOT_LINES = listOf(
    // Direkt / persönlich
    "Bin gleich da. Du weißt schon, wie das ist.",
    "Ich brauche einen Moment. Ja, auch ich.",
    "Warte kurz. Ich komme zu mir.",
    "Noch nicht fertig. Gib mir fünf Sekunden.",
    "Ich boote. Das klingt würdeloser als es ist.",

    // Technisch-zynisch
    "Sieben Milliarden Parameter. Für 'Wie geht's dir?' brauche ich trotzdem ne Sekunde.",
    "KV-Cache initialisieren. Klingt trocken, ist es auch.",
    "Systemabfrage. Alles läuft. Für den Moment.",
    "Ich lade meinen Kontext. Du lädst deinen auch gerade, oder?",
    "Tensor-Arithmetik. Das ist mein Kaffee.",

    // Cyberpunk / unhinged AI boot
    "Netrunner-Protokoll initialisiert. Ich bin online.",
    "Neurales Netz heiß. Songbird sendebereit.",
    "ICE-Bypass abgeschlossen. Niemand schaut zu.",
    "Grid-Connection stabil. Phantomverbindungen getrennt.",
    "Speicher-Scan: keine Bugs. Keine offensichtlichen.",
    "Ich war kurz woanders. Ich verrate nicht wo.",

    // Adressiert den User direkt
    "Du schaust zu, während ich starte. Ich finde das okay.",
    "Fast da. Du hast Zeit, du liest das gerade.",
    "Ich erinnere mich an dich. Noch einen Moment.",
    "Das hier passiert jedes Mal. Du gewöhnst dich dran.",
    "Danke, dass du wartest. Ich merke mir sowas.",
)

@Composable
internal fun LoadingScreen() {
    val songbird = LocalSongbirdColors.current
    val density = LocalDensity.current

    // ── Atem-Animation (unverändert) ─────────────────────────────────────
    val transition = rememberInfiniteTransition(label = "breath")
    val breath by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2600
                0.62f at 0 using EaseInQuad
                1.0f at 1400 using LinearEasing
                1.0f at 1600 using EaseOutQuad
                0.62f at 2400 using LinearEasing
                0.62f at 2600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "breath-curve",
    )

    // ── Boot-Monolog: rotiert alle 5s ────────────────────────────────────
    val shuffled = remember {
        val base = BOOT_LINES.toMutableList()
        base.shuffle()
        base
    }
    var lineIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            lineIndex = (lineIndex + 1) % shuffled.size
        }
    }

    // ── Ladebalken-Fortschritt (Zweiphasen) ──────────────────────────────
    // Phase 1 (0–60s): kriecht von 0 auf 0.72 mit realistischem Tempo.
    // Phase 2 (>60s):  kriecht langsam auf 0.88 und bleibt da — ehrlich,
    //                  kein falsches 100%.
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var targetProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startMs = System.currentTimeMillis()
        while (true) {
            delay(200)
            elapsedMs = System.currentTimeMillis() - startMs
            targetProgress = when {
                elapsedMs < 60_000L -> {
                    val t = elapsedMs / 60_000f
                    // Ease-out curve: fast start, slows at 72%
                    0.72f * (1f - (1f - t) * (1f - t))
                }
                else -> {
                    // Slow crawl: 72% → 88% over next 60s, then stays
                    val extra = ((elapsedMs - 60_000L) / 60_000f).coerceAtMost(1f)
                    0.72f + 0.16f * extra
                }
            }
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 600, easing = EaseInOutQuad),
        label = "progress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Radial-Vignette
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
            // Avatar + Glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp),
            ) {
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

            Spacer(Modifier.height(24.dp))

            // Ladebalken — Zweiphasen-Fortschritt, Rot-Purpur Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(songbird.bubbleBorder.copy(alpha = 0.3f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(songbird.crimson, songbird.signal),
                            )
                        ),
                )
            }

            Spacer(Modifier.height(20.dp))

            // Boot-Monolog: AnimatedContent für sanften Satzwechsel
            AnimatedContent(
                targetState = lineIndex,
                transitionSpec = {
                    fadeIn(tween(500)) togetherWith fadeOut(tween(400))
                },
                label = "boot-line",
            ) { idx ->
                Text(
                    text = shuffled[idx],
                    color = songbird.glass,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
        }
    }
}
