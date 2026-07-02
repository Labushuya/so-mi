package io.somi.tools.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.toolPrefsStore by preferencesDataStore(name = "tool_prefs")

@Singleton
class ToolPrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val WEB_CONSENT = booleanPreferencesKey("web_consent_shown")

    suspend fun webConsentShown(): Boolean =
        context.toolPrefsStore.data.first()[WEB_CONSENT] ?: false

    suspend fun setWebConsentShown(shown: Boolean) {
        context.toolPrefsStore.edit { it[WEB_CONSENT] = shown }
    }

    private fun toolKey(toolId: String) = booleanPreferencesKey("tool_enabled_$toolId")

    suspend fun isToolEnabled(toolId: String): Boolean =
        context.toolPrefsStore.data.first()[toolKey(toolId)] ?: true

    /** Read all tool-enabled states in a single DataStore access instead of N sequential reads. */
    suspend fun enabledToolIds(allIds: List<String>): Set<String> =
        context.toolPrefsStore.data.first().let { prefs ->
            allIds.filter { id -> prefs[toolKey(id)] ?: true }.toSet()
        }

    suspend fun setToolEnabled(toolId: String, enabled: Boolean) {
        context.toolPrefsStore.edit { prefs -> prefs[toolKey(toolId)] = enabled }
    }

    fun toolEnabledFlow(toolId: String): Flow<Boolean> =
        context.toolPrefsStore.data.map { prefs -> prefs[toolKey(toolId)] ?: true }
}
