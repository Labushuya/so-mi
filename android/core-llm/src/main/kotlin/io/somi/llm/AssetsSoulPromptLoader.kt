package io.somi.llm

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-2.10 / v0.11.4 implementation.
 *
 * Lookup order (first hit wins):
 *  1. `$filesDir/soul/soul.md`  — user edits via Settings → Persönlichkeit
 *  2. `assets/soul_condensed.md` — factory default, ships with the APK
 *
 * The factory default is the hand-crafted compaction that preserves the
 * persona core (~600–800 chars). The full `soul.md` (~4 KB) is still in
 * assets for Phase 3 RAG retrieval, but is NEVER fed to setSystemPrompt
 * because prefill on a 7B Q4_K_M CPU path takes minutes for that length.
 *
 * Live-reload: [invalidate] drops the @Volatile cache. ChatViewModel.reloadSoul()
 * calls invalidate() then re-runs setSystemPrompt with the new text. Backups
 * are managed by SoulRepository; this loader is read-only.
 *
 * Decoded as UTF-8 — soul.md contains German umlauts and em-dashes; any
 * other charset would corrupt them.
 */
@Singleton
internal class AssetsSoulPromptLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) : SoulPromptLoader {

    @Volatile
    private var cached: String? = null

    private val overrideFile: File
        // v0.15.0: was filesDir/soul/soul.md, now SoMi/soul/soul.md
        // under externalFilesDir for user-visibility (StorageRoots).
        // core-llm doesn't depend on core-data so we hardcode the
        // path here; if StorageRoots layout changes, both must move
        // in lockstep.
        get() {
            val external = context.getExternalFilesDir(null) ?: context.filesDir
            return File(File(File(external, "SoMi"), "soul"), "soul.md")
        }

    override suspend fun load(): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val text = readOverrideOrFallback()
            cached = text
            return text
        }
    }

    override fun invalidate() {
        cached = null
        Log.i(TAG, "soul cache invalidated")
    }

    private fun readOverrideOrFallback(): String {
        val override = overrideFile
        if (override.exists() && override.canRead() && override.length() > 0L) {
            try {
                val text = override.readText(Charsets.UTF_8)
                if (text.isNotBlank()) {
                    Log.i(TAG, "soul loaded from override file: ${override.absolutePath}")
                    return text
                }
            } catch (t: Throwable) {
                // Override read failure is non-fatal — fall through to asset.
                Log.w(TAG, "override read failed; falling back to asset", t)
            }
        }
        Log.i(TAG, "soul loaded from asset $ASSET_NAME")
        return context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
    }

    private companion object {
        const val ASSET_NAME = "soul_condensed.md"
        const val TAG = "AssetsSoulPromptLoader"
    }
}
