package io.somi.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.somi.common.chat.ChatState
import io.somi.data.DeviceInfo
import io.somi.data.HardwareDetector
import io.somi.data.Recommendation
import io.somi.data.recommendModelTier
import io.somi.llm.LlamaContext
import io.somi.llm.SoulPromptLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase-2.2 ChatViewModel.
 *
 * Probes hardware on init and computes the recommended model tier per
 * SPEC §7. The lifecycle [state] still defaults to LoadingModel; the
 * hardware snapshot + tier go on a separate [boot] StateFlow so the
 * diagnostic banner can render both simultaneously.
 *
 * The hardware probe runs on Dispatchers.Default (the GLES renderer
 * roundtrip costs 10-50 ms — safe for the main thread but sloppy).
 *
 * Phase 2.6 will turn [submit] into a real generation pipeline. For now
 * the ViewModel only surfaces facts, which is enough to verify the
 * core-data wiring on-device.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    @Suppress("unused") private val llama: LlamaContext,
    @Suppress("unused") private val soulPromptLoader: SoulPromptLoader,
    private val hardwareDetector: HardwareDetector,
) : ViewModel() {

    private val _state = MutableStateFlow<ChatState>(ChatState.LoadingModel)
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _boot = MutableStateFlow<BootSnapshot?>(null)
    val boot: StateFlow<BootSnapshot?> = _boot.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val info = hardwareDetector.snapshot()
            val rec = recommendModelTier(info)
            _boot.value = BootSnapshot(deviceInfo = info, recommendation = rec)
        }
    }

    /**
     * Hardware fingerprint + recommendation, captured once at startup.
     * Null until the background probe completes.
     */
    data class BootSnapshot(
        val deviceInfo: DeviceInfo,
        val recommendation: Recommendation,
    )
}
