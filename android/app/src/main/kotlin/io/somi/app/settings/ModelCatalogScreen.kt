package io.somi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.somi.app.LocalSongbirdColors
import io.somi.app.ModelRow
import io.somi.app.components.SectionCard
import io.somi.app.components.SongbirdTopBar
import io.somi.data.Light
import io.somi.data.ModelCatalog
import io.somi.ui.chat.ChatViewModel

/**
 * v0.15.0 — Model-Katalog ab Settings → Downloads → "Anderes Modell laden".
 *
 * Spiegelt FirstLaunchScreen.ModelGrid: same Light-Farbcodes,
 * same ModelRow-Komponente. Unterschiede:
 *  - kein "Weiter"-Button (User pickt → ChatViewModel.selectModel feuert
 *    sofort und übernimmt download/load).
 *  - Aufruf jederzeit, nicht nur First-Launch.
 *
 * Reuses [ModelRow] (promoted from `private` to `internal` in v0.15.0).
 */
@Composable
fun ModelCatalogScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    val boot by viewModel.boot.collectAsStateWithLifecycle()
    val selected by viewModel.selectedModel.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val installedIds = remember(instances) { instances.filter { it.isComplete }.map { it.manifestId }.toSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SongbirdTopBar(title = "Modell laden", onBack = onBack)
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SectionCard {
                    Text(
                        text = "Pick' eines aus. Die Ampel zeigt, ob Dein Gerät es trägt — Grün = locker, Gelb = knapp, Rot = wahrscheinlich OOM.",
                        color = songbird.glass,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Bei „qwen-research“-Lizenz: rein für Dich, nicht für kommerzielle Nutzung. So-Mi distribuiert das Modell nicht weiter — Du lädst direkt von Hugging Face.",
                        color = songbird.glass,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            items(ModelCatalog.ALL, key = { it.id }) { manifest ->
                val light = boot?.recommendation?.lights?.get(manifest.tier) ?: Light.RED
                val isRecommended = boot?.recommendation?.auto == manifest.tier
                val isSelected = selected?.id == manifest.id
                val isInstalled = installedIds.contains(manifest.id)
                Column {
                    ModelRow(
                        manifest = manifest,
                        light = light,
                        isSelected = isSelected,
                        isRecommended = isRecommended,
                        onClick = { viewModel.selectModel(manifest) },
                    )
                    if (isInstalled) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Bereits installiert.",
                            color = songbird.glass,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
