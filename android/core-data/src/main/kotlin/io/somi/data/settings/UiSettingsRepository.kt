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

/**
 * v0.15.0 — UI-Settings persistence (immersive fullscreen + greeting mode).
 *
 * Two flat fields, kept in JSON because that's the pattern already
 * established by SamplerSettingsRepository. DataStore would be
 * cleaner architecturally but we'd burn one whole release introducing
 * the dependency just for two booleans + an enum.
 *
 *   $filesDir/settings/ui.json
 *
 * Schema:
 *   {
 *     "immersive": true|false,                    // hide system bars
 *     "greetingMode": "FULL" | "COLD_START" | "NONE"
 *   }
 *
 * Defaults: immersive=true (user explicitly asked for true fullscreen
 * in v0.15.0), greetingMode=COLD_START (user-locked default per
 * v0.15.0 planning session).
 */
@Singleton
class UiSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val rootDir: File by lazy {
        // v0.15.0: lives under SoMi/settings/ together with sampler.json.
        io.somi.data.StorageRoots.settings(context)
    }

    private val file: File get() = File(rootDir, "ui.json")

    private val _state = MutableStateFlow(UiSettings.DEFAULTS)
    val state: StateFlow<UiSettings> = _state.asStateFlow()

    init {
        // v0.15.0 — load synchronously. ui.json is one tiny JSON blob
        // (two booleans + an enum), and BOTH consumers (MainActivity's
        // immersive-apply in onCreate, ChatViewModel's greeting hook on
        // first Lifecycle.Ready ~10 ms after Hilt creates the VM) read
        // `state.value` before any async load could finish. Pushing
        // this to a background coroutine makes the DEFAULTS sticky on
        // first paint — visible bar-flash for a user with immersive=
        // false saved, and a wrong-mode greeting for greetingMode=NONE.
        // Cost on cold start: a single readText of <200 bytes.
        loadFromDisk()
    }

    suspend fun setImmersive(value: Boolean) = save(_state.value.copy(immersive = value))

    suspend fun setGreetingMode(mode: GreetingMode) = save(_state.value.copy(greetingMode = mode))

    suspend fun save(settings: UiSettings) = withContext(Dispatchers.IO) {
        _state.value = settings
        try {
            val json = JSONObject().apply {
                put("immersive", settings.immersive)
                put("greetingMode", settings.greetingMode.name)
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

/**
 * Immutable UI-settings snapshot.
 */
data class UiSettings(
    val immersive: Boolean = true,
    val greetingMode: GreetingMode = GreetingMode.COLD_START,
) {
    companion object {
        val DEFAULTS = UiSettings()
    }
}

/**
 * Three-mode greeting toggle. Default COLD_START per user-locked decision.
 */
enum class GreetingMode {
    /** Greet on every Activity-resume after >= GREETING_THRESHOLD_MS background gap. */
    FULL,

    /** Greet only on cold process start (empty chat or first launch). */
    COLD_START,

    /** Never greet. */
    NONE,
}
