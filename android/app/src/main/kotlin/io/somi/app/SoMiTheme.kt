package io.somi.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Songbird palette — Edition 01 Visual Identity & Color System (v1.0).
// Hex values are taken verbatim from the brand guide; do not nudge them.
// ---------------------------------------------------------------------------

// Primary axis (red spectrum + Obsidian ground)
val Obsidian = Color(0xFF0A0203)    // dominant ground / full-bleed bg
val Oxblood = Color(0xFF2A0102)     // deep red support
val Blood = Color(0xFF670003)       // mid red support
val Crimson = Color(0xFFA60008)     // principal brand color / accent
val Signal = Color(0xFFE00817)      // CTA / interactive states / the i-dot
val Ember = Color(0xFFFF2D3F)       // bright red emphasis
val HotPink = Color(0xFFF35C93)     // highlight points only — never a fill
val PearlGlow = Color(0xFFFCDEF5)   // luminous near-white glow — accents only

// Secondary / neutrals
val Rust = Color(0xFF7A2A1F)
val Plum = Color(0xFF3D0A1E)
val Ash = Color(0xFF3C2A2D)
val Smoke = Color(0xFF5A4448)
val Glass = Color(0xFFA8888C)
val RoseDust = Color(0xFFC9929A)
val Bone = Color(0xFFF4ECEC)        // dominant light ground
val Graphite = Color(0xFF1A1416)    // body text on Bone

// ---------------------------------------------------------------------------
// Material3 color schemes.
// Phase-1 default is dark — Songbird is a low-key cinematic system, the dark
// surface is the canonical ground. Light scheme is wired so a future setting
// can toggle it, but the app currently launches dark-locked.
// ---------------------------------------------------------------------------

private val SongbirdDarkScheme = darkColorScheme(
    primary = Crimson,
    onPrimary = Bone,
    primaryContainer = Oxblood,
    onPrimaryContainer = Bone,
    secondary = Signal,           // high-energy companion / CTAs
    onSecondary = Bone,
    tertiary = Ember,
    onTertiary = Obsidian,
    background = Obsidian,
    onBackground = Bone,
    surface = Obsidian,
    onSurface = Bone,
    surfaceVariant = Ash,         // raised surfaces (input bar, AI bubble)
    onSurfaceVariant = Glass,
    outline = Smoke,              // borders
    outlineVariant = Ash,
    error = Ember,
    onError = Bone,
)

private val SongbirdLightScheme = lightColorScheme(
    primary = Crimson,
    onPrimary = Bone,
    primaryContainer = RoseDust,
    onPrimaryContainer = Graphite,
    secondary = Signal,
    onSecondary = Bone,
    tertiary = Blood,
    onTertiary = Bone,
    background = Bone,
    onBackground = Graphite,
    surface = Bone,
    onSurface = Graphite,
    surfaceVariant = Color(0xFFEADCDC),
    onSurfaceVariant = Smoke,
    outline = Glass,
    outlineVariant = RoseDust,
    error = Crimson,
    onError = Bone,
)

// ---------------------------------------------------------------------------
// SongbirdColors — tokens Material3 doesn't model directly. Exposed via a
// CompositionLocal so chat-shell components can grab brand colors without
// hardcoding hex.
// ---------------------------------------------------------------------------

data class SongbirdColors(
    val obsidian: Color = Obsidian,
    val oxblood: Color = Oxblood,
    val blood: Color = Blood,
    val crimson: Color = Crimson,
    val signal: Color = Signal,
    val ember: Color = Ember,
    val hotPink: Color = HotPink,
    val pearlGlow: Color = PearlGlow,
    val rust: Color = Rust,
    val plum: Color = Plum,
    val ash: Color = Ash,
    val smoke: Color = Smoke,
    val glass: Color = Glass,
    val roseDust: Color = RoseDust,
    val bone: Color = Bone,
    val graphite: Color = Graphite,
    // Composed semantic tokens used by the chat shell.
    val userBubble: Color = Oxblood,             // user message fill
    val aiBubble: Color = Color(0xFF150709),     // AI fill — Obsidian + a hair of warmth
    val bubbleBorder: Color = Color(0xFF2A1B1D), // subtle ash-tinted border
    val composerBg: Color = Color(0xFF120608),
    val composerBorder: Color = Color(0xFF2A1B1D),
    val mutedText: Color = Glass,
)

val LocalSongbirdColors = staticCompositionLocalOf { SongbirdColors() }

// ---------------------------------------------------------------------------
// SongbirdTypography — Inter is the primary face per brand guide; we fall
// back to FontFamily.SansSerif (the Android system geometric sans). Body
// Regular, headlines tighter at 1.1x, display sizes carry slightly negative
// tracking, ALL-CAPS labels carry +5%.
// ---------------------------------------------------------------------------

private val SongbirdFontFamily = FontFamily.SansSerif

val SongbirdTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SongbirdFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.4).sp,    // -1% of 40sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SongbirdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SongbirdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SongbirdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(            // chat body — 11/16 in the guide,
        fontFamily = SongbirdFontFamily, // bumped to 15sp on Android for legibility
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SongbirdFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SongbirdFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SongbirdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(           // ALL CAPS kickers / labels
        fontFamily = SongbirdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.5.sp,       // +5% per guide
    ),
)

// ---------------------------------------------------------------------------
// SoMiTheme — the public entry point.
// ---------------------------------------------------------------------------

@Composable
fun SoMiTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Phase 1 is dark-locked. Songbird's brand-canonical surface is Obsidian;
    // the light scheme is wired in for completeness but not selected.
    val scheme = SongbirdDarkScheme
    CompositionLocalProvider(LocalSongbirdColors provides SongbirdColors()) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SongbirdTypography,
            content = content,
        )
    }
}
