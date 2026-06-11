package io.somi.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
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
import io.somi.common.chat.ChatState.Companion.banner
import io.somi.common.chat.ChatState.Companion.unwrap
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

    @Inject lateinit var uiSettingsRepository: io.somi.data.settings.UiSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // v0.13.0: explicit edge-to-edge with Songbird-Obsidian scrim
        // hint. On targetSdk 35 (Android 15+) the deprecated theme
        // attrs android:statusBarColor / navigationBarColor are no-ops;
        // some OEMs (HONOR Magic V2 / MagicOS) apply a default scrim
        // unless the app declares its own SystemBarStyle. The .dark()
        // variant draws light icons on the obsidian ground.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(SONGBIRD_OBSIDIAN_ARGB),
            navigationBarStyle = SystemBarStyle.dark(SONGBIRD_OBSIDIAN_ARGB),
        )

        // v0.18.4: MagicOS overrides windowSoftInputMode to ADJUST_PAN
        // (confirmed via `dumpsys input_method`). ADJUST_PAN shifts the
        // entire window up leaving a black gap at the bottom. Setting
        // SOFT_INPUT_ADJUST_RESIZE programmatically overrides the OEM
        // behaviour so the layout shrinks instead of panning.
        @Suppress("DEPRECATION")
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        // v0.15.0: true fullscreen / immersive mode. User explicitly
        // asked for status bar + nav bar HIDDEN, drawing the chat to
        // every pixel. Toggle persisted in UiSettingsRepository;
        // default is true. Re-applied imperatively via the controller
        // because edge-to-edge alone leaves both bars visible.
        // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE lets the user reveal
        // them by swiping from the screen edges — no escape hatch
        // is otherwise reachable on a phone with no hardware keys.
        applyImmersive(uiSettingsRepository.state.value.immersive)

        // Re-apply on lifecycle resume — Android temporarily un-hides
        // bars when the user switches apps; without this re-apply
        // they stay visible after coming back.
        lifecycleScope.launch {
            uiSettingsRepository.state.collect { s ->
                applyImmersive(s.immersive)
            }
        }

        // Phase 2.10 / v0.11.2: the eager FGS start now lives in
        // SoMiApp.onCreate (only when a model is on disk), so it wins
        // the race against Android 14's 5 s ForegroundServiceDidNotStartInTime
        // guard during a 7B prefill. We do NOT call LlamaSessionService.start
        // here unconditionally anymore — that posted a misleading
        // "Modell läuft" notification before any model was installed.
        // Mid-session promotion (after a download completes) happens
        // through ChatViewModel reacting to the lifecycle transition.

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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-apply immersive whenever the window regains focus —
            // dialogs, IME, transient overlays all reset the bar state.
            applyImmersive(uiSettingsRepository.state.value.immersive)
        }
    }

    private fun applyImmersive(enabled: Boolean) {
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        /** ARGB for Songbird Obsidian — must mirror res/values/colors.xml::songbird_obsidian. */
        const val SONGBIRD_OBSIDIAN_ARGB: Int = 0xFF0A0203.toInt()
    }
}

@Composable
private fun SoMiAppRoot() {
    val viewModel: ChatViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val boot by viewModel.boot.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current

    // v0.11.2: promote LlamaSessionService the instant we transition
    // into a state where the engine matters (LoadingModel or further).
    // SoMiApp.onCreate handles the cold-start case where a model is
    // already on disk; this LaunchedEffect handles the post-download
    // case where a fresh user just finished their first download and
    // is about to enter Loading. Idempotent — start() can be called
    // multiple times safely.
    val unwrapped = state.unwrap()
    LaunchedEffect(unwrapped::class) {
        when (unwrapped) {
            is ChatState.LoadingModel,
            is ChatState.Idle,
            is ChatState.Generating,
            -> LlamaSessionService.start(context)
            else -> Unit
        }
    }

    // Best-effort POST_NOTIFICATIONS request on Android 13+. Fire-and-forget.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { /* result ignored — denial is fine */ }
        LaunchedEffect(Unit) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()

    // v0.11.4: Settings now has sub-screens. Single in-VM enum cheaper
    // than nav-compose for three destinations and matches the rest of
    // the app's "explicit boolean state" pattern.
    var settingsRoute by remember { mutableStateOf(SettingsRoute.Hidden) }
    val soulRepository = viewModel.soulRepository
    val soulPromptLoader = viewModel.soulPromptLoader

    // v0.20.0: Hardware-Back-Button navigiert Settings-intern statt App zu schließen.
    // Auf Root-Screen (Chat, settingsRoute=Hidden) schließt Back die App normal.
    BackHandler(enabled = settingsRoute != SettingsRoute.Hidden) {
        settingsRoute = if (settingsRoute == SettingsRoute.Root) SettingsRoute.Hidden
                        else SettingsRoute.Root
    }

    when (settingsRoute) {
        SettingsRoute.Hidden -> Unit
        SettingsRoute.Root -> {
            SettingsScreen(
                viewModel = viewModel,
                onClose = { settingsRoute = SettingsRoute.Hidden },
                onOpenSoulEditor = { settingsRoute = SettingsRoute.SoulEditor },
                onOpenMemoryBrowser = { settingsRoute = SettingsRoute.MemoryBrowser },
                onOpenModelCatalog = { settingsRoute = SettingsRoute.ModelCatalog },
                onOpenDataBrowser = { settingsRoute = SettingsRoute.DataBrowser },
            )
            return
        }
        SettingsRoute.SoulEditor -> {
            io.somi.app.settings.SoulEditorScreen(
                viewModel = viewModel,
                soulRepository = soulRepository,
                soulPromptLoader = soulPromptLoader,
                onBack = { settingsRoute = SettingsRoute.Root },
            )
            return
        }
        SettingsRoute.MemoryBrowser -> {
            io.somi.app.settings.MemoryBrowserScreen(
                onBack = { settingsRoute = SettingsRoute.Root },
            )
            return
        }
        SettingsRoute.ModelCatalog -> {
            io.somi.app.settings.ModelCatalogScreen(
                viewModel = viewModel,
                onBack = { settingsRoute = SettingsRoute.Root },
            )
            return
        }
        SettingsRoute.DataBrowser -> {
            io.somi.app.settings.DataBrowserScreen(
                onBack = { settingsRoute = SettingsRoute.Root },
            )
            return
        }
    }

    // Route on the underlying lifecycle — a WithBanner overlay must not
    // change which surface the user sees, only paint a banner on top.
    when (state.unwrap()) {
        is ChatState.Booting -> BootingSplash()
        is ChatState.NoModelInstalled,
        is ChatState.DownloadingModel,
        -> FirstLaunchScreen(
            state = state,
            boot = boot,
            selected = selectedModel,
            wifiOnly = wifiOnly,
            onWifiOnlyChange = viewModel::setWifiOnly,
            onSelect = viewModel::selectModel,
            onStartDownload = viewModel::startDownload,
            onCancelDownload = viewModel::cancelDownload,
            onRetry = viewModel::retry,
            onOpenSettings = { settingsRoute = SettingsRoute.Root },
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
            onRetry = viewModel::retry,
            onOpenSettings = { settingsRoute = SettingsRoute.Root },
        )
        is ChatState.WithBanner -> Unit // unreachable: unwrap() never returns WithBanner
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
    // WithBanner over Generating must still drive the live-typing bubble,
    // so every type-check inspects the unwrapped inner state.
    val inner = state.unwrap()
    val isGenerating = inner is ChatState.Generating
    val partial = (inner as? ChatState.Generating)?.partialResponse ?: ""
    val partialPromptId = (inner as? ChatState.Generating)?.promptId ?: -1L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            // Top inset: status bar + notch.
            // imePadding() handles the keyboard — it's a dedicated
            // Modifier that listens to IME WindowInsetsAnimation and
            // animates the padding as the keyboard slides in/out.
            // Using windowInsetsPadding(WindowInsets.ime) is static;
            // imePadding() is dynamic and works with enableEdgeToEdge.
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Top)
                    .union(WindowInsets.displayCutout),
            )
            .imePadding(),
    ) {
        ChatTopBar(
            versionName = versionName,
            versionCode = versionCode,
            chatState = state,
            boot = boot,
            messageCount = messages.size,
            onOpenSettings = onOpenSettings,
        )

        // Optional error banner above the message list, sourced from the
        // WithBanner overlay (companion helper). The banner sits above
        // whatever lifecycle is rendering — Idle, Generating, anything.
        val bannerOverlay = state.banner()
        if (bannerOverlay != null) {
            ErrorBanner(
                message = bannerOverlay.message,
                onRetry = if (bannerOverlay.retryable) onRetry else null,
            )
        }

        // Message history + live-typing bubble for in-flight generation.
        val listState = rememberLazyListState()
        // Scroll to bottom when messages change or partial text grows.
        LaunchedEffect(messages.size, partial.length, isGenerating) {
            val target = messages.size + if (isGenerating) 1 else 0
            if (target > 0) listState.animateScrollToItem(target - 1)
        }
        // Scroll to bottom when keyboard opens — but ONLY if already at bottom.
        // If the user has scrolled up to read older messages, don't interrupt.
        val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime
            .asPaddingValues()
            .calculateBottomPadding() > 0.dp
        LaunchedEffect(imeVisible) {
            if (!imeVisible) return@LaunchedEffect
            val lastIndex = messages.size + (if (isGenerating) 1 else 0) - 1
            if (lastIndex < 0) return@LaunchedEffect
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = layoutInfo.totalItemsCount
            // Only scroll if we're already near the bottom (within 2 items)
            if (lastVisible >= totalItems - 2) {
                listState.animateScrollToItem(lastIndex)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // v0.15.0: tighter contentPadding (4dp top/bottom instead
            // of 16dp) — the gap was perceived as unintentional. Plus
            // Arrangement.spacedBy(8.dp, Alignment.Bottom): the Bottom
            // alignment glues the message stack to the lower edge of
            // the viewport, so a short chat doesn't leave a vast
            // empty area between the last bubble and the Composer.
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            if (messages.isEmpty() && !isGenerating) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
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
            // SendButton-enabled gates on the inner lifecycle so a banner
            // overlay never disables the send action over an Idle screen.
            enabled = inner is ChatState.Idle,
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
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 0.dp),
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
                // Typing is never blocked. Even when the SendButton is
                // disabled (loading / generating / banner) the user can
                // keep drafting their next message — that's the whole
                // point of the WithBanner refactor (ChatState.kt:36).
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp, max = 140.dp),
                textStyle = LocalTextStyle.current.copy(
                    color = songbird.bone,
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

private fun chatStateLabel(state: ChatState): String {
    val hasBanner = state.banner() != null
    val label = when (state.unwrap()) {
        is ChatState.Booting -> "booting"
        is ChatState.Idle -> "idle"
        is ChatState.LoadingModel -> "loading"
        is ChatState.NoModelInstalled -> "no-model"
        is ChatState.DownloadingModel -> "downloading"
        is ChatState.Generating -> "generating"
        is ChatState.WithBanner -> "unknown" // unreachable after unwrap()
    }
    return if (hasBanner) "$label!" else label
}

/**
 * v0.11.4 — Settings sub-routes. We don't pull in nav-compose for
 * three destinations; an enum + LaunchedEffect-aware boolean is enough.
 */
internal enum class SettingsRoute { Hidden, Root, SoulEditor, MemoryBrowser, ModelCatalog, DataBrowser }
