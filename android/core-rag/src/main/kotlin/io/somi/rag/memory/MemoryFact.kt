package io.somi.rag.memory

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType

/**
 * v0.14.0 M3 — ObjectBox entity for a single conversational memory.
 *
 * Schema notes:
 *  - `embedding` is FloatArray(384) — paraphrase-multilingual-MiniLM-
 *    L12-v2 output dimension.
 *  - HNSW vector index lets us run cosine top-K queries over millions
 *    of facts without scanning. Distance type COSINE matches the L2-
 *    normalized vectors the [io.somi.rag.embed.Embedder] produces.
 *  - `supersedesId` enables mem0's contradiction handling: a new fact
 *    that updates an old one (e.g. "Ich heiße Christopher" supersedes
 *    "Ich heiße Chris") points back to the old row's id. Retrieval
 *    filters out superseded chains; an audit trail stays intact.
 *  - `lastSeenAt` updated on each successful retrieval — used by the
 *    decay scoring helper to keep frequently-recalled facts fresh.
 *  - `topic` is a [MemoryTopic.id] string. Stored as String (not
 *    enum) so future dynamic topics from M9 fit without schema migration.
 *
 * **HNSW config:** dimensions=384 fixed by the embedder. M = 16 is
 * the ObjectBox default and the same Faiss / hnswlib default —
 * good recall at typical corpus sizes (<100k facts on a phone).
 */
@Entity
data class MemoryFact(
    @Id var id: Long = 0,

    /**
     * The fact text in user's words, normalized minimally
     * (whitespace trim, no NFC/NFKD per soul.md preserving rules).
     */
    var fact: String = "",

    /** [MemoryTopic.id], or a free-form tag once M9's classifier promotes one. */
    @Index var topic: String = MemoryTopic.NOTES.id,

    /** Embedding from [io.somi.rag.embed.Embedder]; L2-normalized FP32. */
    @HnswIndex(dimensions = 384, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray = FloatArray(0),

    /** epoch millis. */
    var createdAt: Long = 0,

    /** epoch millis; bumped on each retrieval that surfaces this fact. */
    var lastSeenAt: Long = 0,

    /**
     * Confidence emitted by the source pass.
     *  - 1.0 for keyword-trigger saves (M6) — the user explicitly asked.
     *  - Variable for M9's auto-classifier saves (gated at >= 0.7).
     */
    var confidence: Float = 1.0f,

    /**
     * mem0 supersedes pointer. Non-zero when this row replaces an
     * older single-value attribute (e.g. preferred name). Retrieval
     * filters chains so only the head is surfaced. Zero = head row.
     */
    var supersedesId: Long = 0,

    /**
     * Soft-delete tombstone. Kept around so the audit chain via
     * [supersedesId] doesn't break. Retrieval hides tombstoned rows.
     */
    var tombstoned: Boolean = false,
) {
    // ObjectBox + data class with a FloatArray field needs explicit
    // equals/hashCode because contentEquals isn't auto-generated.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryFact) return false
        if (id != other.id) return false
        if (fact != other.fact) return false
        if (topic != other.topic) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (createdAt != other.createdAt) return false
        if (lastSeenAt != other.lastSeenAt) return false
        if (confidence != other.confidence) return false
        if (supersedesId != other.supersedesId) return false
        if (tombstoned != other.tombstoned) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + fact.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + lastSeenAt.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + supersedesId.hashCode()
        result = 31 * result + tombstoned.hashCode()
        return result
    }
}
