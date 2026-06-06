package io.somi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.somi.common.chat.ChatState
import io.somi.ui.chat.ChatViewModel

/**
 * Phase-1 acceptance screen — Songbird-reskinned port of the Odysseus chat
 * surface (https://github.com/pewdiepie-archdaemon/odysseus, static/).
 *
 * Faithfully ported from the Odysseus DOM:
 *   - .chat-top-bar  → ChatTopBar (centered session meta + msg count + version)
 *   - #chat-history  → LazyColumn of message rows
 *   - .msg.msg-ai    → AssistantMessage (left-aligned, rounded except bottom-left)
 *   - .msg.msg-user  → UserMessage (right-aligned, rounded except bottom-right)
 *   - .chat-input-bar → ChatInputBar (rounded panel, max-width 800dp, centered,
 *                       textarea + mode-toggle pill + square send button)
 *
 * Deliberate Phase-1 deviations (out of scope for the hello-world):
 *   - Sidebar / icon-rail / model picker / overflow tools / attach strip
 *     all omitted. Visible UI surface = top bar + chat history + composer.
 *   - Welcome screen (.welcome-active overlay) skipped — we ship a single
 *     pre-rendered AI message instead.
 *   - Composer is non-interactive: no LLM is wired up yet, so the textarea
 *     is rendered as a styled placeholder Text, the send button is dimmed,
 *     and the mode toggle is inert.
 *   - The assistant bubble is preceded by a 40dp rounded-square avatar
 *     (RoundedCornerShape(14.dp)) painted from somi_avatar.png. Odysseus
 *     has no per-message avatars but the brief explicitly asks for this
 *     and the avatar carries So-Mi's visual identity in the absence of a
 *     working model.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SoMiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Phase 2.1: hilt-injected ChatViewModel exposes a
                    // StateFlow<ChatState>. The chat shell renders the
                    // state's class-name as a tiny diagnostic line in
                    // the top bar — that's the smoke signal proving the
                    // SingletonComponent → ViewModel → Compose graph is
                    // wired correctly. Phase 2.6 turns this into the
                    // real chat lifecycle UI.
                    val viewModel: ChatViewModel = hiltViewModel()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val boot by viewModel.boot.collectAsStateWithLifecycle()

                    ChatShellScreen(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                        chatState = state,
                        boot = boot,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top-level layout: top bar + scrolling chat history + composer pinned to
// bottom. Mirrors the Odysseus main.chat-container flex column.
// ---------------------------------------------------------------------------

@Composable
private fun ChatShellScreen(
    versionName: String,
    versionCode: Int,
    chatState: ChatState,
    boot: ChatViewModel.BootSnapshot?,
) {
    val songbird = LocalSongbirdColors.current
    val welcomeText = stringResource(R.string.welcome_message)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        ChatTopBar(
            sessionTitle = stringResource(R.string.top_bar_session_title),
            msgCount = stringResource(R.string.top_bar_msg_count),
            versionName = versionName,
            versionCode = versionCode,
            chatState = chatState,
            boot = boot,
        )

        // chat-history — flex:1, scrollable. One assistant message for now.
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AssistantMessage(
                    authorLabel = stringResource(R.string.assistant_name),
                    body = welcomeText,
                )
            }
        }

        ChatInputBar()
    }
}

// ---------------------------------------------------------------------------
// ChatTopBar — Odysseus's .chat-top-bar.
// Centered session title + msg count, with the version string tucked under
// as a subtitle. Hairline ash-tinted border below.
// ---------------------------------------------------------------------------

@Composable
private fun ChatTopBar(
    sessionTitle: String,
    msgCount: String,
    versionName: String,
    versionCode: Int,
    chatState: ChatState,
    boot: ChatViewModel.BootSnapshot?,
) {
    val songbird = LocalSongbirdColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(songbird.obsidian)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = sessionTitle,
                color = songbird.bone.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "·",
                color = songbird.glass,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = msgCount,
                color = songbird.glass,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(2.dp))
        // Version subtitle — subtle, but visible enough for Phase-1 acceptance.
        Text(
            text = buildDiagnosticLine(versionName, versionCode, chatState, boot),
            color = songbird.glass.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(6.dp))
        // Hairline border below the bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(songbird.bubbleBorder),
        )
    }
}

// ---------------------------------------------------------------------------
// AssistantMessage — .msg.msg-ai.
// Odysseus rules: align-self:flex-start; max-width clamp; border-radius
// 18 18 18 0 (flat bottom-left = visual tail anchor); subtle border + AI fill.
// Per user instruction: prefix with a 40dp rounded-square avatar (R=14dp).
// ---------------------------------------------------------------------------

@Composable
private fun AssistantMessage(authorLabel: String, body: String) {
    val songbird = LocalSongbirdColors.current
    val aiShape = RoundedCornerShape(
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
        // Avatar — square with rounded corners, leading the bubble.
        Image(
            painter = painterResource(id = R.drawable.somi_avatar),
            contentDescription = stringResource(R.string.avatar_cd),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = songbird.bubbleBorder,
                    shape = RoundedCornerShape(14.dp),
                ),
        )
        Spacer(Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(aiShape)
                .background(songbird.aiBubble)
                .border(width = 1.dp, color = songbird.bubbleBorder, shape = aiShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            // Role label row — Odysseus paints a small dot before the label;
            // we use Signal red per Songbird (the i-dot signal motif).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(songbird.signal),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = authorLabel,
                    color = songbird.bone,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                color = songbird.bone.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// UserMessage — .msg.msg-user. Right-aligned, flat bottom-right corner,
// Oxblood fill. Currently unused in Phase 1 (only one AI turn is shown) but
// the component is here so Phase 2 can drop user messages straight in.
// ---------------------------------------------------------------------------

@Suppress("unused")
@Composable
private fun UserMessage(body: String) {
    val songbird = LocalSongbirdColors.current
    val userShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 0.dp,
        bottomStart = 18.dp,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(userShape)
                .background(songbird.userBubble)
                .border(width = 1.dp, color = songbird.bubbleBorder, shape = userShape)
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
                text = body,
                color = songbird.bone,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ChatInputBar — .chat-input-bar.
// Rounded panel (R=16), max-width 800dp, centered. Top row: textarea (here
// rendered as a non-interactive placeholder, since the LLM is offline).
// Bottom row: mode-toggle pill on the left, square send button on the right.
// Send button is the Songbird Signal red rendered at 50% opacity to honestly
// signal "disabled".
// ---------------------------------------------------------------------------

@Composable
private fun ChatInputBar() {
    val songbird = LocalSongbirdColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 800.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(songbird.composerBg)
                .border(
                    width = 1.dp,
                    color = songbird.composerBorder,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top row — textarea placeholder. Disabled-state styling: muted
            // text in the Glass tone, no caret, no input handlers wired.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.input_placeholder_disabled),
                    color = songbird.glass.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            // Bottom row — mode-toggle on the left, send button on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeTogglePill()
                SendButton()
            }
        }
    }
}

@Composable
private fun ModeTogglePill() {
    val songbird = LocalSongbirdColors.current
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = songbird.bubbleBorder,
                shape = RoundedCornerShape(10.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Active pill side — Agent.
        Box(
            modifier = Modifier
                .height(28.dp)
                .background(songbird.bone.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.mode_agent),
                color = songbird.bone,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        // Inactive — Chat.
        Box(
            modifier = Modifier
                .height(28.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.mode_chat),
                color = songbird.bone.copy(alpha = 0.40f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SendButton() {
    val songbird = LocalSongbirdColors.current
    // 32dp square, R=8. Signal red rendered at 50% opacity to indicate the
    // disabled Phase-1 state (no LLM behind it). Up-arrow glyph is a Unicode
    // codepoint rather than a material-icons-extended dependency.
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(songbird.signal.copy(alpha = 0.50f)),
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

// ---------------------------------------------------------------------------
// Phase-2.1 / 2.2 helpers
// ---------------------------------------------------------------------------

/**
 * Phase-2 diagnostic banner string.
 *
 * Format: `v<version> · build <code> · <state>[ · <ramGB>GB · <tier><light>]`
 *
 * The leading three components are the Phase-1 acceptance signal (proves
 * release-please + versionCode injection work). The trailing two appear
 * once HardwareDetector finishes its background probe — they're the
 * Phase-2.2 acceptance signal (proves the snapshot ran and SPEC §7's
 * recommendModelTier returned a sane verdict for this hardware).
 */
private fun buildDiagnosticLine(
    versionName: String,
    versionCode: Int,
    chatState: ChatState,
    boot: io.somi.ui.chat.ChatViewModel.BootSnapshot?,
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

/**
 * One-word lower-case label for the current chat lifecycle state. Shown
 * in the top-bar subtitle as the Phase-2.1 smoke signal — proves the
 * Hilt graph is wired (ChatViewModel actually injects + emits state).
 * Phase 2.6 retires this in favor of richer per-state UI surfaces.
 */
private fun chatStateLabel(state: ChatState): String = when (state) {
    ChatState.Idle -> "idle"
    ChatState.LoadingModel -> "loading"
    ChatState.NoModelInstalled -> "no-model"
    is ChatState.DownloadingModel -> "downloading"
    is ChatState.Generating -> "generating"
    is ChatState.Error -> "error"
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF0A0203)
@Composable
private fun ChatShellPreview() {
    SoMiTheme {
        ChatShellScreen(
            versionName = "0.1.0",
            versionCode = 10001,
            chatState = ChatState.LoadingModel,
            boot = null,
        )
    }
}
