package io.somi.ui.chat

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.somi.common.chat.ChatState
import io.somi.llm.LlamaContext
import io.somi.llm.SoulPromptLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Phase-2.1 ChatViewModel skeleton.
 *
 * Constructor-injected with [LlamaContext] and [SoulPromptLoader] so the
 * Hilt graph is exercised end-to-end (the smoke screen reads
 * [state.value::class.simpleName] and renders it). No real generation
 * logic yet — that lives in Phase 2.6.
 *
 * State starts at [ChatState.LoadingModel] to match the eventual cold-
 * launch experience: 2.5 will later flip to NoModelInstalled on a fresh
 * install or LoadingModel during the post-download warm pass.
 *
 * Why dependencies are injected now even though they're unused: this is
 * the smallest surface that proves the SingletonComponent provider chain
 * (LlamaModule → NoOpLlamaContext → ChatViewModel) is wired correctly.
 * Adding them in 2.6 instead would let a broken Hilt graph slip past 2.1
 * acceptance.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    @Suppress("unused") private val llama: LlamaContext,
    @Suppress("unused") private val soulPromptLoader: SoulPromptLoader,
) : ViewModel() {

    private val _state = MutableStateFlow<ChatState>(ChatState.LoadingModel)
    val state: StateFlow<ChatState> = _state.asStateFlow()
}
