package io.somi.rag.embed

import io.somi.common.embed.TextEmbedder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextEmbedderAdapter @Inject constructor(
    private val embedder: Embedder,
) : TextEmbedder {
    override suspend fun embed(text: String): FloatArray? =
        runCatching { embedder.embed(text) }.getOrNull()

    override suspend fun isAvailable(): Boolean =
        runCatching { embedder.isAvailable() }.getOrDefault(false)
}
