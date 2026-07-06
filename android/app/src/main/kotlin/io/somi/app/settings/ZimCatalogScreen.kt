package io.somi.app.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.somi.app.LocalSongbirdColors
import io.somi.app.components.SongbirdButton
import io.somi.app.components.SongbirdButtonKind
import io.somi.app.components.SectionCard
import io.somi.app.components.SongbirdTopBar
import io.somi.data.StorageRoots
import io.somi.data.ZimCatalog
import io.somi.data.ZimManifest
import io.somi.data.download.ZimDownloadWorker
import kotlinx.coroutines.Dispatchers
import java.io.File

@Composable
fun ZimCatalogScreen(onBack: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SongbirdTopBar(title = "Offline-Lexikon", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Wörterbücher und Nachschlagewerke für die Offline-Nutzung. So-Mi verwendet diese automatisch bei passenden Anfragen.",
            color = songbird.glass,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(ZimCatalog.ALL, key = { it.id }) { manifest ->
                ZimRow(manifest = manifest, context = context)
            }
        }
    }
}

@Composable
private fun ZimRow(manifest: ZimManifest, context: Context) {
    val songbird = LocalSongbirdColors.current
    val workManager = remember { WorkManager.getInstance(context) }
    val tag = ZimDownloadWorker.workTag(manifest.id)

    var isInstalled by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    LaunchedEffect(manifest.id) {
        // Check installation status
        withContext(Dispatchers.IO) {
            isInstalled = File(StorageRoots.zim(context), manifest.filename).exists()
        }

        // Observe WorkManager progress
        workManager.getWorkInfosByTagFlow(tag).collect { infos ->
            val info = infos.firstOrNull()
            when (info?.state) {
                WorkInfo.State.RUNNING -> {
                    val done = info.progress.getLong(ZimDownloadWorker.KEY_BYTES_DONE, 0L)
                    val total = info.progress.getLong(ZimDownloadWorker.KEY_BYTES_TOTAL, 0L)
                    isVerifying = info.progress.getBoolean(ZimDownloadWorker.KEY_VERIFYING, false)
                    downloadProgress = if (total > 0L) done.toFloat() / total else null
                }
                WorkInfo.State.SUCCEEDED -> {
                    downloadProgress = null
                    isVerifying = false
                    withContext(Dispatchers.IO) {
                        isInstalled = File(StorageRoots.zim(context), manifest.filename).exists()
                    }
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    downloadProgress = null
                    isVerifying = false
                }
                else -> {}
            }
        }
    }

    SectionCard(title = manifest.displayName) {
        Text(
            text = manifest.description,
            color = songbird.glass,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Größe: %.1f GB".format(manifest.sizeBytes / 1_073_741_824.0),
            color = songbird.bone.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(10.dp))

        when {
            isInstalled -> {
                Text(
                    text = "✓ Installiert — So-Mi nutzt dieses Lexikon automatisch.",
                    color = songbird.signal,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                SongbirdButton(
                    label = "Neu installieren",
                    kind = SongbirdButtonKind.Ghost,
                    onClick = { reinstallZim(context, manifest) },
                )
            }
            isVerifying -> {
                Text(
                    text = "Wird überprüft…",
                    color = songbird.glass,
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = songbird.signal,
                    trackColor = songbird.bubbleBorder,
                )
            }
            downloadProgress != null -> {
                val pct = ((downloadProgress ?: 0f) * 100).toInt()
                Text(
                    text = "Herunterladen… $pct%",
                    color = songbird.glass,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = songbird.crimson,
                    trackColor = songbird.bubbleBorder,
                )
                Spacer(Modifier.height(8.dp))
                SongbirdButton(
                    label = "Abbrechen",
                    kind = SongbirdButtonKind.Ghost,
                    onClick = { workManager.cancelUniqueWork(tag) },
                )
            }
            else -> {
                Text(
                    text = "Nicht installiert. Download benötigt WLAN empfohlen (~1.2 GB).",
                    color = songbird.glass,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                SongbirdButton(
                    label = "Herunterladen",
                    kind = SongbirdButtonKind.Primary,
                    onClick = { ZimDownloadWorker.enqueue(context, manifest.id) },
                )
            }
        }
    }
}

private fun reinstallZim(context: Context, manifest: ZimManifest) {
    val file = File(StorageRoots.zim(context), manifest.filename)
    file.delete()
    ZimDownloadWorker.enqueue(context, manifest.id)
}
