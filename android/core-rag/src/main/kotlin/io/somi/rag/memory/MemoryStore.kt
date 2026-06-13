package io.somi.rag.memory

import android.util.Log
import dagger.Lazy
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryBuilder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * v0.14.0 M3 — write/read API for the conversational memory store.
 *
 * Wraps [MemoryFact] ObjectBox box with the high-level operations
 * the rest of the app needs:
 *  - [save] — insert a new fact with embedding (no contradiction
 *    handling here; supersedes-link decisions live in M9 with the
 *    classifier).
 *  - [supersede] — link a new row's `supersedesId` to an old row's
 *    `id`. Old row stays in the DB for audit; retrieval filters it.
 *  - [tombstone] — soft-delete a fact (kept in DB so chain links don't break).
 *  - [topK] — cosine top-K against a query embedding, decay-weighted.
 *  - [byTopic] — list non-tombstoned heads of a single topic for the
 *    Memory-Browser UI in M7.
 *  - [allHeads] — all non-tombstoned, non-superseded rows (the
 *    "current" memory set).
 *
 * BoxStore is injected lazily so first-touch happens on-demand, not
 * on Hilt graph creation. Same pattern as [io.somi.rag.RagBootstrap].
 */
@Singleton
class MemoryStore @Inject constructor(
    private val boxStoreLazy: Lazy<BoxStore>,
) {
    private val box by lazy { boxStoreLazy.get().boxFor<MemoryFact>() }

    /**
     * Insert a fact and return its assigned id.
     *
     * @param now epoch millis; defaults to System.currentTimeMillis()
     *   in production but can be injected for tests.
     */
    fun save(
        fact: String,
        topic: MemoryTopic,
        embedding: FloatArray,
        confidence: Float = 1.0f,
        supersedesId: Long = 0,
        now: Long = System.currentTimeMillis(),
    ): Long {
        require(fact.isNotBlank()) { "fact must be non-blank" }
        require(embedding.isNotEmpty()) { "embedding must be non-empty" }
        val row = MemoryFact(
            id = 0,
            fact = fact.trim(),
            topic = topic.id,
            embedding = embedding,
            createdAt = now,
            lastSeenAt = now,
            confidence = confidence,
            supersedesId = supersedesId,
            tombstoned = false,
        )
        val id = box.put(row)
        Log.i(TAG, "save: id=$id topic=${topic.id} len=${fact.length}")
        return id
    }

    /** Mark a fact as superseded by [newId]. Idempotent. */
    fun markSuperseded(oldId: Long, newId: Long) {
        require(oldId != 0L && newId != 0L) { "ids must be non-zero" }
        val old = box.get(oldId) ?: return
        // Old row stays as-is; the new row carries the supersedesId
        // pointing back. We just verify the chain is consistent.
        val new = box.get(newId) ?: return
        if (new.supersedesId != oldId) {
            new.supersedesId = oldId
            box.put(new)
        }
        Log.i(TAG, "markSuperseded: $oldId → $newId")
    }

    /**
     * Soft-delete a fact. Hides it from retrieval but keeps the row
     * for chain integrity. M7's Memory-Browser uses this for the
     * "Bullet löschen" action.
     */
    fun tombstone(id: Long) {
        val row = box.get(id) ?: return
        if (row.tombstoned) return
        row.tombstoned = true
        box.put(row)
        Log.i(TAG, "tombstone: $id")
    }

    /** Move a fact to a different topic (M7's "Verschieben"-Action). */
    fun moveTopic(id: Long, newTopic: MemoryTopic) {
        val row = box.get(id) ?: return
        if (row.topic == newTopic.id) return
        row.topic = newTopic.id
        box.put(row)
        Log.i(TAG, "moveTopic: $id → ${newTopic.id}")
    }

    /** Edit the fact text. Embedding stays as-is — the M3 caller is
     *  expected to re-embed and call [save] for a new row in that case. */
    fun editFact(id: Long, newText: String) {
        require(newText.isNotBlank()) { "fact must be non-blank" }
        val row = box.get(id) ?: return
        row.fact = newText.trim()
        box.put(row)
    }

    /** v0.41.1 — Update the embedding of an existing fact (backfill). */
    fun updateEmbedding(id: Long, embedding: FloatArray) {
        val row = box.get(id) ?: return
        row.embedding = embedding
        box.put(row)
    }

    /**
     * Get the head of a supersedes-chain starting at [id], following
     * forward links until none remain. Returns the most recent row.
     */
    fun head(id: Long): MemoryFact? {
        val visited = HashSet<Long>()
        var current = box.get(id) ?: return null
        // Find any row whose supersedesId == current.id (forward link).
        // Since chains are typically short (1-2 hops) the linear scan
        // over a small heads-list is acceptable; bigger corpora can
        // add a reverse-index later.
        while (true) {
            if (!visited.add(current.id)) return current // cycle guard
            val next = box.query(MemoryFact_.supersedesId.equal(current.id))
                .build().findFirst() ?: return current
            current = next
        }
    }

    /** All non-tombstoned, non-superseded rows. */
    fun allHeads(): List<MemoryFact> {
        // A "head" is a row that isn't tombstoned AND isn't pointed to
        // by anyone else's supersedesId.
        val all = box.query(MemoryFact_.tombstoned.equal(false))
            .build().find()
        if (all.isEmpty()) return emptyList()
        val supersededIds = HashSet<Long>(all.size)
        for (row in all) {
            if (row.supersedesId != 0L) supersededIds += row.supersedesId
        }
        return all.filter { it.id !in supersededIds }
    }

    /** Heads of a specific topic (for Memory-Browser per-topic view). */
    fun byTopic(topic: MemoryTopic): List<MemoryFact> =
        allHeads().filter { it.topic == topic.id }

    /**
     * v0.15.1 M8 — HNSW-backed top-K using ObjectBox nearestNeighbors.
     * Requests [k]*4 (min 40) candidates to absorb tombstoned/superseded
     * rows that are filtered post-query. Falls back to [topKScan] if the
     * HNSW index is unavailable (empty store or query error).
     */
    fun topK(
        queryEmbedding: FloatArray,
        k: Int = 5,
        cosineThreshold: Float = 0.0f,
        now: Long = System.currentTimeMillis(),
        decayHalfLifeDays: Int = 90,
    ): List<RankedFact> {
        require(queryEmbedding.isNotEmpty())
        // Request more candidates than k to account for tombstoned/superseded rows
        // that will be filtered out after the HNSW query.
        val candidateCount = (k * 4).coerceAtLeast(40)
        val rawResults = runCatching {
            box.query(MemoryFact_.embedding.nearestNeighbors(queryEmbedding, candidateCount))
                .build()
                .findWithScores()
        }.getOrElse { t ->
            Log.w(TAG, "HNSW query failed, falling back to scan", t)
            return topKScan(queryEmbedding, k, cosineThreshold, now, decayHalfLifeDays)
        }
        if (rawResults.isEmpty()) return topKScan(queryEmbedding, k, cosineThreshold, now, decayHalfLifeDays)

        // Build superseded-id set for head filtering
        val supersededIds = rawResults.map { it.get() }
            .filter { it.supersedesId != 0L }
            .map { it.supersedesId }
            .toHashSet()

        val ranked = ArrayList<RankedFact>(k)
        for (r in rawResults) {
            val fact = r.get()
            if (fact.tombstoned) continue
            if (fact.id in supersededIds) continue
            if (fact.embedding.size != queryEmbedding.size) continue
            // COSINE distance from ObjectBox: 0 = identical, 1 = orthogonal, 2 = opposite
            // Convert to similarity: similarity = 1 - distance
            val cos = (1f - r.score.toFloat()).coerceIn(0f, 1f)
            if (cos < cosineThreshold) continue
            val ageDays = (now - fact.lastSeenAt).coerceAtLeast(0L) / MS_PER_DAY
            val decay = exp(-ageDays.toDouble() / decayHalfLifeDays).toFloat()
            ranked += RankedFact(fact = fact, cosine = cos, score = cos * decay)
            if (ranked.size >= k) break
        }
        ranked.sortByDescending { it.score }
        return ranked
    }

    /**
     * v0.14.0 M3 — naive cosine scan over allHeads(). Kept as fallback
     * for [topK] when the HNSW index is unavailable or returns no results.
     * Not suitable for production scale.
     */
    private fun topKScan(
        queryEmbedding: FloatArray,
        k: Int = 5,
        cosineThreshold: Float = 0.0f,
        now: Long = System.currentTimeMillis(),
        decayHalfLifeDays: Int = 90,
    ): List<RankedFact> {
        require(queryEmbedding.isNotEmpty())
        val candidates = allHeads()
        if (candidates.isEmpty()) return emptyList()
        val ranked = ArrayList<RankedFact>(candidates.size)
        for (row in candidates) {
            if (row.embedding.size != queryEmbedding.size) continue
            val cos = cosine(queryEmbedding, row.embedding)
            if (cos < cosineThreshold) continue
            val ageDays = (now - row.lastSeenAt).coerceAtLeast(0L) / MS_PER_DAY
            val decay = exp(-ageDays.toDouble() / decayHalfLifeDays).toFloat()
            ranked += RankedFact(fact = row, cosine = cos, score = cos * decay)
        }
        ranked.sortByDescending { it.score }
        return if (ranked.size > k) ranked.subList(0, k) else ranked
    }

    /** Refresh `lastSeenAt` on a recalled fact — bumps decay scoring. */
    fun touch(id: Long, now: Long = System.currentTimeMillis()) {
        val row = box.get(id) ?: return
        row.lastSeenAt = now
        box.put(row)
    }

    /** Clear the entire memory store. Used in tests + Settings reset. */
    fun clearAll() {
        box.removeAll()
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        // Both vectors are L2-normalized at write time, so dot == cosine.
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private companion object {
        const val TAG = "MemoryStore"
        const val MS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}

/** Top-K result; pairs the row with its cosine + decay-weighted score. */
data class RankedFact(
    val fact: MemoryFact,
    val cosine: Float,
    val score: Float,
)
