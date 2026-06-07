package io.somi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Phase v0.11.2 — minimal boot splash.
 *
 * Painted during [io.somi.common.chat.ChatState.Booting] — i.e. while the
 * ChatViewModel's init coroutine is running its hardware probe, soul.md
 * cache, and on-disk model scan. Typical duration is 100–500 ms; we
 * deliberately render NOTHING but a solid Obsidian background:
 *
 *  - No spinner: at this latency a spinner reads as glitch, not progress.
 *  - No text: the user hasn't engaged with the app yet, anything we
 *    show would be premature.
 *  - No avatar: the LoadingScreen earns the avatar reveal once a real
 *    load is happening.
 *
 * This deliberate emptiness fixes the v0.11.1 "App läuft initial eher
 * instabil" report — earlier versions used the LoadingScreen ("Modell
 * wird vorgewärmt…") as the initial state, which flickered visibly
 * for users who hadn't installed any model yet.
 */
@Composable
internal fun BootingSplash() {
    val songbird = LocalSongbirdColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian),
    )
}
