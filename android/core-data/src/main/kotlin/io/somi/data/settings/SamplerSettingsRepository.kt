package io.somi.data.settings

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.somi.common.llm.SamplerParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.11.4 — sampler-param persistence.
 *
 * Stores [SamplerParams] as a tiny JSON blob at
 *   `$filesDir/settings/sampler.json`
 *
 * No DataStore: introducing a whole new persistence layer for ~5 fields
 * isn't worth it; one file matches the soul.md philosophy. Single
 * writer (SamplerSettingsViewModel) means we don't need transactional
 * guarantees. Reads return DEFAULTS on missing/corrupt file so a
 * clobbered file doesn't brick startup.
 */
@Singleton
class SamplerSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val rootDir: File by lazy {
        // v0.15.0: moved from filesDir/settings/ to SoMi/settings/.
        io.somi.data.StorageRoots.settings(context)
    }

    private val file: File get() = File(rootDir, "sampler.json")

    private val _params = MutableStateFlow(SamplerParams.DEFAULTS)
    val params: StateFlow<SamplerParams> = _params.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch { loadFromDiskInto(_params) }
    }

    suspend fun save(params: SamplerParams) = withContext(Dispatchers.IO) {
        val clamped = SamplerParams.clamp(params)
        _params.value = clamped
        try {
            val json = JSONObject().apply {
                put("temperature", clamped.temperature.toDouble())
                put("topP", clamped.topP.toDouble())
                put("repeatPenalty", clamped.repeatPenalty.toDouble())
                put("topK", clamped.topK)
                put("maxTokens", clamped.maxTokens)
            }
            file.writeText(json.toString())
        } catch (t: Throwable) {
            Log.e(TAG, "save failed", t)
        }
    }

    /** Force-reload from disk; useful after an external edit. */
    suspend fun reload() = withContext(Dispatchers.IO) {
        loadFromDiskInto(_params)
    }

    private fun loadFromDiskInto(target: MutableStateFlow<SamplerParams>) {
        if (!file.exists()) {
            target.value = SamplerParams.DEFAULTS
            return
        }
        try {
            val json = JSONObject(file.readText())
            val parsed = SamplerParams(
                temperature = json.optDouble("temperature", SamplerParams.DEFAULTS.temperature.toDouble()).toFloat(),
                topP = json.optDouble("topP", SamplerParams.DEFAULTS.topP.toDouble()).toFloat(),
                repeatPenalty = json.optDouble("repeatPenalty", SamplerParams.DEFAULTS.repeatPenalty.toDouble()).toFloat(),
                topK = json.optInt("topK", SamplerParams.DEFAULTS.topK),
                maxTokens = json.optInt("maxTokens", SamplerParams.DEFAULTS.maxTokens),
            )
            target.value = SamplerParams.clamp(parsed)
        } catch (t: Throwable) {
            Log.w(TAG, "load failed; using defaults", t)
            target.value = SamplerParams.DEFAULTS
        }
    }

    private companion object {
        const val TAG = "SamplerSettingsRepo"
    }
}
