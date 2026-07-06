package io.somi.common.kiwix

data class KiwixSearchResult(
    val title: String,
    val path: String,
    val plainText: String,
)

interface KiwixSearchPort {
    suspend fun search(query: String, maxResults: Int = 5): List<KiwixSearchResult>
    fun isOpen(): Boolean
}
