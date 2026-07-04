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

    suspend fun save(settings: UiSettings) = withContext(Dispatchers.IO) {
        _state.value = settings
        try {
            val json = JSONObject().apply {
                put("immersive", settings.immersive)
                put("greetingMode", settings.greetingMode.name)
                put("toolMode", settings.toolMode.name)
                put("autoTts", settings.autoTts)
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
