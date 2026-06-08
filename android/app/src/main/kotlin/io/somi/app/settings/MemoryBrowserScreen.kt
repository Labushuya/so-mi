package io.somi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.somi.app.LocalSongbirdColors
import io.somi.app.components.SectionCard
import io.somi.app.components.SongbirdTopBar

/**
 * v0.11.4 — placeholder for the v0.13.0 Memory-Browser.
 *
 * Real content comes when the hybrid-Lern-RAG lands (Trigger-Detector,
 * Topic-Classifier, MemoryStore). Skeleton today so the navigation
 * surface is final and the Settings → Lernen entry point is wired.
 */
@Composable
fun MemoryBrowserScreen(onBack: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            // v0.15.0: union of systemBars + displayCutout — see FirstLaunchScreen.
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(16.dp),
    ) {
        SongbirdTopBar(title = "Erinnerungen", onBack = onBack)
        Spacer(Modifier.height(16.dp))
        SectionCard {
            Text(
                text = "Hier wird So-Mi Dir später zeigen, was sie sich gemerkt hat — gruppiert nach Personen, Vorlieben, Terminen, Notizen.",
                color = songbird.bone,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Kommt mit v0.13. Solange ist diese Seite leer.",
                color = songbird.glass,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
