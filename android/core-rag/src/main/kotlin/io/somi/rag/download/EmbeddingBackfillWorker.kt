package io.somi.rag.download

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.somi.rag.embed.Embedder
import io.somi.rag.memory.MemoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v0.41.1 — One-shot backfill: re-embeds all MemoryFact rows that were
 * saved before the embedder was installed (zero-vector placeholder).
 * Enqueued once when embedder transitions to Installed. KEEP policy
 * so reruns are no-ops.
 */
@HiltWorker
class EmbeddingBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val memoryStore: MemoryStore,
    private val embedder: Embedder,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!embedder.isAvailable()) {
            Log.w(TAG, "embedder not available, skipping backfill")
            return@withContext Result.retry()
        }
        val needsBackfill = memoryStore.allHeads().filter { fact ->
            fact.embedding.isEmpty() || fact.embedding.all { v -> v == 0f }
        }
        if (needsBackfill.isEmpty()) {
            Log.i(TAG, "backfill: nothing to do")
            return@withContext Result.success()
        }
        Log.i(TAG, "backfill: ${needsBackfill.size} facts to re-embed")
        var done = 0
        for (fact in needsBackfill) {
            runCatching {
                val embedding = embedder.embed(fact.fact)
                memoryStore.updateEmbedding(fact.id, embedding)
                done++
            }.onFailure { Log.w(TAG, "backfill failed for fact ${fact.id}", it) }
        }
        Log.i(TAG, "backfill: done $done/${needsBackfill.size}")
        Result.success()
    }

    companion object {
        const val WORK_NAME = "embedding-backfill"
        const val TAG = "EmbeddingBackfillWorker"
    }
}
