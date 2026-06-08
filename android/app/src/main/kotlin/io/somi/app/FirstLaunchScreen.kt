package io.somi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.somi.common.chat.ChatState
import io.somi.common.chat.ChatState.Companion.banner
import io.somi.common.chat.ChatState.Companion.unwrap
import io.somi.data.Light
import io.somi.data.ModelCatalog
import io.somi.data.ModelManifest
import io.somi.data.Tier
import io.somi.ui.chat.ChatViewModel

/**
 * Phase-2.5 first-launch flow.
 *
 * Two surfaces in one Composable, switched by ChatState:
 *  - NoModelInstalled → Hardware-Ampel + model picker + download CTA
 *  - DownloadingModel → progress bar + cancel button
 *
 * Layout follows SPEC §7's mockup, reskinned to Songbird:
 *  - Obsidian full-bleed ground
 *  - Bone type, Glass meta
 *  - Crimson left bar marks the auto-picked row
 *  - Signal red on the primary action button + error states
 */
@Composable
internal fun FirstLaunchScreen(
    state: ChatState,
    boot: ChatViewModel.BootSnapshot?,
    selected: ModelManifest?,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onSelect: (ModelManifest) -> Unit,
    onStartDownload: (wifiOnly: Boolean) -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current

    // v0.11.2: confirm-dialog when the user disables WLAN-only — a
    // 4 GB download over mobile data is the kind of slip that costs
    // real money. The dialog only appears when the toggle was just
    // turned off (wifiOnly false) AND a download is being requested.
    var pendingMobileDownload by remember { mutableStateOf<ModelManifest?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            // v0.13.0: enableEdgeToEdge in MainActivity flips the window
            // into edge-to-edge on Android 11/12, so we now reserve the
            // status/nav-bar inset explicitly here. Background extends
            // behind the bars; content is inset.
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        // Header with settings entry
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.first_launch_title),
                color = songbird.bone,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenSettings() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⋮",
                    color = songbird.glass,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.first_launch_subtitle),
            color = songbird.glass,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))

        // Hardware ampel
        if (boot != null) {
            HardwareSummary(boot)
            Spacer(Modifier.height(20.dp))
        }

        // List of models — scrolls if needed
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val recommended = boot?.recommendation?.auto?.let { ModelCatalog.forTier(it) }
            if (recommended != null) {
                Text(
                    text = stringResource(R.string.section_recommended).uppercase(),
                    color = songbird.crimson,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                ModelRow(
                    manifest = recommended,
                    light = boot.recommendation.lights[recommended.tier] ?: Light.RED,
                    isSelected = selected?.id == recommended.id,
                    isRecommended = true,
                    onClick = { onSelect(recommended) },
                )
                Spacer(Modifier.height(16.dp))
            }

            val others = ModelCatalog.ALL.filter { it.id != recommended?.id }
            if (others.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.section_others).uppercase(),
                    color = songbird.glass,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                others.forEach { manifest ->
                    val light = boot?.recommendation?.lights?.get(manifest.tier) ?: Light.RED
                    ModelRow(
                        manifest = manifest,
                        light = light,
                        isSelected = selected?.id == manifest.id,
                        isRecommended = false,
                        onClick = { onSelect(manifest) },
                    )
                }
            }
        }

        // Bottom action area — depends on state. Errors are now banner
        // overlays via ChatState.WithBanner, so we render the ErrorRow
        // when a banner is present AND route on the unwrapped inner
        // lifecycle for the action surface (so a banner over Downloading
        // still shows the progress bar, not the download CTA).
        Spacer(Modifier.height(16.dp))
        val bannerOverlay = state.banner()
        if (bannerOverlay != null) {
            ErrorRow(message = bannerOverlay.message, onRetry = onRetry)
            Spacer(Modifier.height(12.dp))
        }
        when (val core = state.unwrap()) {
            is ChatState.Booting -> {
                // Routed-around defensively. MainActivity.SoMiAppRoot
                // routes Booting straight to BootingSplash before
                // FirstLaunchScreen is composed, so this branch is
                // reached only if a future routing change drops that
                // guard. Render nothing rather than the wrong-state CTA.
                Unit
            }
            is ChatState.DownloadingModel -> {
                DownloadProgress(state = core, onCancel = onCancelDownload)
            }
            else -> {
                // NoModelInstalled / Idle / Generating / LoadingModel —
                // show the download CTA when a model is selected. Idle
                // and Generating shouldn't normally route here, but the
                // CTA is harmless if they do.
                if (selected != null) {
                    DownloadActionRow(
                        manifest = selected,
                        wifiOnly = wifiOnly,
                        onWifiOnlyChange = onWifiOnlyChange,
                        onStart = {
                            // If WLAN-only is OFF, force a confirm step
                            // before triggering the actual download —
                            // mobile data + 4 GB is too expensive for a
                            // silent toggle.
                            if (!wifiOnly) {
                                pendingMobileDownload = selected
                            } else {
                                onStartDownload(wifiOnly)
                            }
                        },
                    )
                }
            }
        }
    }

    // Confirm dialog overlay — shown when the user tries to download
    // with WLAN-only OFF.
    pendingMobileDownload?.let { manifest ->
        MobileDataConfirmDialog(
            manifest = manifest,
            onConfirm = {
                pendingMobileDownload = null
                onStartDownload(false)
            },
            onDismiss = { pendingMobileDownload = null },
        )
    }
}

@Composable
private fun HardwareSummary(boot: ChatViewModel.BootSnapshot) {
    val songbird = LocalSongbirdColors.current
    val device = boot.deviceInfo
    val capabilities = buildList {
        if (device.hasVulkan11) add(stringResource(R.string.capability_vulkan))
        if (device.hasOpenCL) add(stringResource(R.string.capability_opencl))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(songbird.aiBubble)
            .border(1.dp, songbird.bubbleBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            text = "%.0f GB · %s".format(device.totalRamGB, device.gpuRenderer),
            color = songbird.bone,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        if (capabilities.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = capabilities.joinToString(" · "),
                color = songbird.glass,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ModelRow(
    manifest: ModelManifest,
    light: Light,
    isSelected: Boolean,
    isRecommended: Boolean,
    onClick: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    val rowAlpha = if (isRecommended || isSelected) 1.0f else 0.65f
    val borderColor = if (isSelected) songbird.crimson else songbird.bubbleBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .clip(RoundedCornerShape(12.dp))
            .background(songbird.aiBubble)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Light glyph (colored disc)
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(lightColor(light, songbird)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${tierLabel(manifest.tier)} · ${manifest.displayName}",
                color = songbird.bone,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatSizeGB(manifest.totalSizeBytes) + " · " + manifest.license,
                color = songbird.glass,
                style = MaterialTheme.typography.labelSmall,
            )
            if (manifest.license == "qwen-research") {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.license_warning_research),
                    color = songbird.roseDust,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DownloadActionRow(
    manifest: ModelManifest,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        // Wi-Fi-only toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.wifi_only_label),
                color = songbird.bone,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = wifiOnly,
                onCheckedChange = onWifiOnlyChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = songbird.bone,
                    checkedTrackColor = songbird.crimson,
                    uncheckedThumbColor = songbird.glass,
                    uncheckedTrackColor = songbird.ash,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        // Primary action — Crimson rectangle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(songbird.crimson)
                .clickable { onStart() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.action_download, formatSizeGB(manifest.totalSizeBytes)),
                color = songbird.bone,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DownloadProgress(state: ChatState.DownloadingModel, onCancel: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    val progress = state.progress ?: 0f
    val pct = (progress * 100).toInt()
    val isFinalising = state.bytesTotal > 0 && state.bytesDownloaded >= state.bytesTotal

    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = songbird.crimson,
            trackColor = songbird.ash,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isFinalising) {
                "Wird verifiziert und installiert…"
            } else {
                "$pct % · ${formatBytes(state.bytesDownloaded)} / ${formatBytes(state.bytesTotal)}"
            },
            color = songbird.glass,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, songbird.glass, RoundedCornerShape(8.dp))
                .clickable { onCancel() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.action_cancel),
                color = songbird.glass,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun ErrorRow(message: String, onRetry: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF330004))
            .border(1.dp, songbird.signal, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(
            text = message,
            color = songbird.bone,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(songbird.signal)
                .clickable { onRetry() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.action_retry),
                color = songbird.bone,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun lightColor(light: Light, songbird: SongbirdColors): Color = when (light) {
    Light.GREEN -> Color(0xFF4ADE80)   // brand green for go-state
    Light.YELLOW -> songbird.ember
    Light.RED -> songbird.signal
}

/**
 * Mobile-data confirm dialog. Triggered when the user taps the download
 * CTA with the "Nur über WLAN laden" toggle turned OFF. v0.11.4 migrated
 * to the unified SongbirdDialog component instead of a hand-rolled
 * Material3 AlertDialog.
 */
@Composable
private fun MobileDataConfirmDialog(
    manifest: ModelManifest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val gb = "%.1f GB".format(manifest.totalSizeBytes.toDouble() / 1_073_741_824.0)
    io.somi.app.components.SongbirdDialog(
        onDismissRequest = onDismiss,
        title = "Mobilfunk?",
        message = "Du bist gerade nicht im WLAN. $gb über mobile Daten — wirklich?",
        tone = io.somi.app.components.SongbirdDialogTone.Destructive,
        confirm = io.somi.app.components.SongbirdDialogAction(
            label = "Ja, $gb laden",
            kind = io.somi.app.components.SongbirdDialogAction.Kind.Destructive,
            onClick = onConfirm,
        ),
        dismiss = io.somi.app.components.SongbirdDialogAction(
            label = "Abbrechen",
            kind = io.somi.app.components.SongbirdDialogAction.Kind.Ghost,
            onClick = onDismiss,
        ),
    )
}

@Composable
private fun tierLabel(tier: Tier): String = when (tier) {
    Tier.TINY -> stringResource(R.string.tier_tiny)
    Tier.SMALL -> stringResource(R.string.tier_small)
    Tier.MEDIUM -> stringResource(R.string.tier_medium)
    Tier.LARGE -> stringResource(R.string.tier_large)
}

private fun formatSizeGB(bytes: Long): String =
    "%.1f GB".format(bytes.toDouble() / 1_073_741_824.0)

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb < 1024.0) "%.0f MB".format(mb) else "%.2f GB".format(mb / 1024.0)
}
