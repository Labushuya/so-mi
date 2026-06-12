package io.somi.data

import io.somi.data.db.ConversationDao
import io.somi.data.db.ConversationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val dao: ConversationDao,
) {
    fun observeAll(): Flow<List<ConversationEntity>> = dao.observeAll()

    suspend fun create(title: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(ConversationEntity(title = title, createdAt = now, updatedAt = now))
    }

    suspend fun rename(id: Long, newTitle: String) {
        dao.rename(id, newTitle, System.currentTimeMillis())
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun touch(id: Long) = dao.touch(id, System.currentTimeMillis())

    suspend fun getOrCreateDefault(): Long {
        return if (dao.count() == 0) create("Erste Session") else 1L
    }
}
