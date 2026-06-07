package io.somi.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.somi.common.chat.Author
import io.somi.common.chat.ChatState
import io.somi.common.chat.Message
import io.somi.ui.chat.ChatViewModel

/**
 * Phase-2.5 / 2.6 / 2.7 — end-to-end shell.
 *
 * Routes between three surfaces driven by `ChatState`:
 *   NoModelInstalled / DownloadingModel  → FirstLaunchScreen (picker + download)
 *   LoadingModel                          → LoadingScreen (warm-up)
 *   Idle / Generating / Error             → ChatShellScreen (live chat)
 *
 * On first launch, requests POST_NOTIFICATIONS so the foreground download
 * notification can appear. Non-blocking — denial is fine; the worker
 * runs without the icon.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Phase 2.10: pin the process at FOREGROUND_SERVICE oom_adj so
        // MagicOS / iaware doesn't reap us mid-session. Idempotent —
        // safe to call on every Activity create. Service stays alive
        // even if MainActivity is destroyed; only an explicit user
        // "Modell entladen" action stops it.
        LlamaSessionService.start(this)

        setContent {
            SoMiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SoMiAppRoot()
                }
            }
        }
    }
}

@Composable
private fun SoMiAppRoot() {
    val viewModel: ChatViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val boot by viewModel.boot.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }

    // Best-effort POST_NOTIFICATIONS request on Android 13+. Fire-and-forget.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { /* result ignored — denial is fine */ }
        LaunchedEffect(Unit) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showSettings) {
        // Re-list on every show — the user just deleted something.
        val instances = remember(showSettings) { viewModel.listAllModelInstances() }
        var refreshKey by remember { mutableStateOf(0) }
        val currentInstances = remember(refreshKey) { viewModel.listAllModelInstances() }
        SettingsScreen(
            instances = currentInstances,
            onDeleteInstance = { inst ->
                viewModel.deleteModelInstance(inst)
                refreshKey++
            },
            onClose = { showSettings = false },
        )
        return
    }

    when (state) {
        is ChatState.NoModelInstalled,
        is ChatState.DownloadingModel,
        -> FirstLaunchScreen(
            state = state,
            boot = boot,
            selected = selectedModel,
            onSelect = viewModel::selectModel,
            onStartDownload = viewModel::startDownload,
            onCancelDownload = viewModel::cancelDownload,
            onRetry = viewModel::retry,
            onOpenSettings = { showSettings = true },
        )
        is ChatState.LoadingModel -> LoadingScreen()
        is ChatState.Idle, is ChatState.Generating -> ChatShellScreen(
            state = state,
            messages = messages,
            boot = boot,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            onSubmit = viewModel::submit,
            onCancelGeneration = viewModel::cancelGeneration,
            onOpenSettings = { showSettings = true },
        )
        is ChatState.Error -> {
            // Error from an Idle session — show the chat with an error
            // banner overlaid. FirstLaunchScreen handles errors during
            // download / load.
            ChatShellScreen(
                state = state,
                messages = messages,
                boot = boot,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                onSubmit = viewModel::submit,
                onCancelGeneration = viewModel::cancelGeneration,
                onRetry = viewModel::retry,
                onOpenSettings = { showSettings = true },
            )
        }
    }
}

@Composable
private fun ChatShellScreen(
    state: ChatState,
    messages: List<Message>,
    boot: ChatViewModel.BootSnapshot?,
    versionName: String,
    versionCode: Int,
    onSubmit: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onOpenSettings: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    val isGenerating = state is ChatState.Generating
    val partial = (state as? ChatState.Generating)?.partialResponse ?: ""
    val partialPromptId = (state as? ChatState.Generating)?.promptId ?: -1L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        ChatTopBar(
            versionName = versionName,
            versionCode = versionCode,
            chatState = state,
            boot = boot,
            messageCount = messages.size,
            onOpenSettings = onOpenSettings,
        )

        // Optional error banner above the message list.
        if (state is ChatState.Error) {
            ErrorBanner(message = state.message, onRetry = onRetry)
        }

        // Message history + live-typing bubble for in-flight generation.
        val listState = rememberLazyListState()
        // Scroll to bottom whenever messages or partial text changes.
        LaunchedEffect(messages.size, partial.length, isGenerating) {
            val target = messages.size + if (isGenerating) 1 else 0
            if (target > 0) listState.animateScrollToItem(target - 1)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty() && !isGenerating) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.empty_chat_hint),
                            color = songbird.glass,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                if (msg.author == Author.USER) UserBubble(text = msg.text)
                else AssistantBubble(text = msg.text)
            }
            if (isGenerating) {
                item(key = "live-$partialPromptId") {
                    AssistantBubble(text = partial.ifEmpty { "…" })
                }
            }
        }

        Composer(
            enabled = state is ChatState.Idle,
            isGenerating = isGenerating,
            onSubmit = onSubmit,
            onStop = onCancelGeneration,
        )
    }
}

@Composable
private fun ChatTopBar(
    versionName: String,
    versionCode: Int,
    chatState: ChatState,
    boot: ChatViewModel.BootSnapshot?,
    messageCount: Int,
    onOpenSettings: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(songbird.obsidian)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Title row centered, settings gear at the end.
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.top_bar_session_title),
                    color = songbird.bone.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "·",
                    color = songbird.glass,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.top_bar_msg_count, messageCount),
                    color = songbird.glass,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
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
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildDiagnosticLine(versionName, versionCode, chatState, boot),
            color = songbird.glass.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(songbird.bubbleBorder),
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: (() -> Unit)?) {
    val songbird = LocalSongbirdColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(songbird.signal.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = songbird.bone,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(songbird.signal)
                    .clickable { onRetry() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_retry),
                    color = songbird.bone,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    val songbird = LocalSongbirdColors.current
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 0.dp,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = painterResource(id = R.drawable.somi_avatar),
            contentDescription = stringResource(R.string.avatar_cd),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, songbird.bubbleBorder, RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(shape)
                .background(songbird.aiBubble)
                .border(1.dp, songbird.bubbleBorder, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(songbird.signal),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.assistant_name),
                    color = songbird.bone,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                color = songbird.bone.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val songbird = LocalSongbirdColors.current
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 0.dp,
        bottomStart = 18.dp,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(shape)
                .background(songbird.userBubble)
                .border(1.dp, songbird.bubbleBorder, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = stringResource(R.string.user_name),
                color = songbird.bone.copy(alpha = 0.6f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                color = songbird.bone,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun Composer(
    enabled: Boolean,
    isGenerating: Boolean,
    onSubmit: (String) -> Unit,
    onStop: () -> Unit,
) {
    val songbird = LocalSongbirdColors.current
    var input by remember { mutableStateOf(TextFieldValue("")) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .imePadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 800.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(songbird.composerBg)
                .border(1.dp, songbird.composerBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = { if (enabled) input = it },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp, max = 140.dp),
                textStyle = LocalTextStyle.current.copy(
                    color = if (enabled) songbird.bone else songbird.glass,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                cursorBrush = SolidColor(songbird.signal),
                maxLines = 6,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (input.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.composer_placeholder),
                                color = songbird.glass.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        inner()
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isGenerating) {
                    StopButton(onClick = onStop)
                } else {
                    SendButton(
                        enabled = enabled && input.text.isNotBlank(),
                        onClick = {
                            val text = input.text
                            input = TextFieldValue("")
                            onSubmit(text)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(songbird.signal.copy(alpha = alpha))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "↑",
            color = songbird.bone,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(songbird.signal)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        // Stop glyph (filled square)
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(songbird.bone),
        )
    }
}

// ---------------------------------------------------------------------------
// Diagnostic banner — same idea as Phase 2.1/2.2, retained.
// ---------------------------------------------------------------------------

private fun buildDiagnosticLine(
    versionName: String,
    versionCode: Int,
    chatState: ChatState,
    boot: ChatViewModel.BootSnapshot?,
): String = buildString {
    append("v").append(versionName)
    append(" · build ").append(versionCode)
    append(" · ").append(chatStateLabel(chatState))
    if (boot != null) {
        append(" · ")
        append("%.0f".format(boot.deviceInfo.totalRamGB))
        append("GB")
        append(" · ")
        append(boot.recommendation.auto.name.lowercase())
        append(lightGlyph(boot.recommendation.lights[boot.recommendation.auto]))
    }
}

private fun lightGlyph(light: io.somi.data.Light?): String = when (light) {
    io.somi.data.Light.GREEN -> "🟢"
    io.somi.data.Light.YELLOW -> "🟡"
    io.somi.data.Light.RED -> "🔴"
    null -> ""
}

private fun chatStateLabel(state: ChatState): String = when (state) {
    is ChatState.Idle -> "idle"
    is ChatState.LoadingModel -> "loading"
    is ChatState.NoModelInstalled -> "no-model"
    is ChatState.DownloadingModel -> "downloading"
    is ChatState.Generating -> "generating"
    is ChatState.Error -> "error"
}
