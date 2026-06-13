package io.somi.common.embed

interface TextEmbedder {
    /** Returns null when the embedding model is not yet on disk. */
    suspend fun embed(text: String): FloatArray?
    suspend fun isAvailable(): Boolean
}
