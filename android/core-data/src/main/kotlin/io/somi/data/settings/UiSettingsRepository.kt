package io.somi.data.settings

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val rootDir: File by lazy {
        io.somi.data.StorageRoots.settings(context)
    }

    private val file: File get() = File(rootDir, "ui.json")

    private val _state = MutableStateFlow(UiSettings.DEFAULTS)
    val state: StateFlow<UiSettings> = _state.asStateFlow()

    init {
        loadFromDisk()
    }

    suspend fun setImmersive(value: Boolean) = save(_state.value.copy(immersive = value))

    suspend fun setGreetingMode(mode: GreetingMode) = save(_state.value.copy(greetingMode = mode))

    suspend fun setToolMode(mode: ToolMode) = save(_state.value.copy(toolMode = mode))

    suspend fun setAutoTts(enabled: Boolean) = save(_state.value.copy(autoTts = enabled))

    suspend fun setPiperSpeechRate(rate: Float) = save(_state.value.copy(piperSpeechRate = rate.coerceIn(0.5f, 2.0f)))

    suspend fun setSelectedModelId(id: String?) = save(_state.value.copy(selectedModelId = id))

    suspend fun setSelectedPiperVoice(voiceName: String?) = save(_state.value.copy(selectedPiperVoice = voiceName))

    suspend fun save(settings: UiSettings) = withContext(Dispatchers.IO) {
        _state.value = settings
        try {
            val json = JSONObject().apply {
                put("immersive", settings.immersive)
                put("greetingMode", settings.greetingMode.name)
                put("toolMode", settings.toolMode.name)
                put("autoTts", settings.autoTts)
                put("piperSpeechRate", settings.piperSpeechRate.toDouble())
                if (settings.selectedModelId != null)
                    put("selectedModelId", settings.selectedModelId)
                else
                    put("selectedModelId", org.json.JSONObject.NULL)
                if (settings.selectedPiperVoice != null)
                    put("selectedPiperVoice", settings.selectedPiperVoice)
                else
                    put("selectedPiperVoice", org.json.JSONObject.NULL)
                // ttsPitch/ttsSpeechRate entfernt in v0.58.0 — gespeicherte Werte werden ignoriert
            }
            file.writeText(json.toString())
        } catch (t: Throwable) {
            Log.e(TAG, "save failed", t)
        }
    }

    private fun loadFromDisk() {
        if (!file.exists()) {
            _state.value = UiSettings.DEFAULTS
            return
        }
        try {
            val json = JSONObject(file.readText())
            _state.value = UiSettings(
                immersive = json.optBoolean("immersive", UiSettings.DEFAULTS.immersive),
                greetingMode = runCatching {
                    GreetingMode.valueOf(json.optString("greetingMode", UiSettings.DEFAULTS.greetingMode.name))
                }.getOrDefault(UiSettings.DEFAULTS.greetingMode),
                toolMode = runCatching {
                    ToolMode.valueOf(json.optString("toolMode", UiSettings.DEFAULTS.toolMode.name))
                }.getOrDefault(UiSettings.DEFAULTS.toolMode),
                autoTts = json.optBoolean("autoTts", false),
                piperSpeechRate = json.optDouble("piperSpeechRate", 1.0).toFloat().coerceIn(0.5f, 2.0f),
                selectedModelId = json.optString("selectedModelId", null)
                    ?.takeIf { it.isNotBlank() },
                selectedPiperVoice = json.optString("selectedPiperVoice", null)
                    ?.takeIf { it.isNotBlank() },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "load failed; using defaults", t)
            _state.value = UiSettings.DEFAULTS
        }
    }

    private companion object {
        const val TAG = "UiSettingsRepo"
    }
}

data class UiSettings(
    val immersive: Boolean = true,
    val greetingMode: GreetingMode = GreetingMode.COLD_START,
    val toolMode: ToolMode = ToolMode.COMPACT,
    val autoTts: Boolean = false,
    val piperSpeechRate: Float = 1.0f,
    val selectedModelId: String? = null,
    val selectedPiperVoice: String? = null,  // PiperTtsEngine.Voice.name
) {
    companion object {
        val DEFAULTS = UiSettings()
    }
}

enum class GreetingMode {
    FULL,
    COLD_START,
    NONE,
}

enum class ToolMode {
    COMPACT,
    SYSTEM_PROMPT,
}
