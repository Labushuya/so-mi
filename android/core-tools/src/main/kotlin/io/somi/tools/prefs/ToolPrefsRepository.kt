package io.somi.tools.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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
}
