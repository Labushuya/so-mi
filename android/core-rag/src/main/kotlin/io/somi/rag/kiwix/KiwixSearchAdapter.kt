package io.somi.rag.kiwix

import io.somi.common.kiwix.KiwixSearchPort
import io.somi.common.kiwix.KiwixSearchResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KiwixSearchAdapter @Inject constructor(
    private val repo: KiwixRepository,
) : KiwixSearchPort {

    override fun isOpen(): Boolean = repo.isOpen()

    override suspend fun search(query: String, maxResults: Int): List<KiwixSearchResult> =
        repo.search(query, maxResults).map { KiwixSearchResult(it.title, it.path, it.plainText) }
}
