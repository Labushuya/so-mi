package io.somi.rag.kiwix

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.somi.data.StorageRoots
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans StorageRoots.zim() on startup and opens the first installed ZIM
 * file in KiwixRepository.
 *
 * Called from RagBootstrap.ensureOpen() as a non-blocking launch{} so
 * the ZIM open doesn't delay the LLM startup path.
 */
@Singleton
class KiwixAutoOpen @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kiwixRepository: KiwixRepository,
) {

    suspend fun tryOpenFirstInstalled() {
        val zimDir = StorageRoots.zim(context)
        val zim = zimDir.listFiles()?.filter { it.extension == "zim" }?.firstOrNull()
            ?: return
        Log.i(TAG, "auto-opening ZIM: ${zim.name}")
        val ok = kiwixRepository.openZim(zim)
        if (!ok) Log.w(TAG, "auto-open failed: ${zim.name}")
    }

    private companion object {
        const val TAG = "KiwixAutoOpen"
    }
}
