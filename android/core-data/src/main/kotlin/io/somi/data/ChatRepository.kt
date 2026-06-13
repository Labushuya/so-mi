package io.somi.data

import io.somi.common.chat.Author
import io.somi.common.chat.Message
import io.somi.data.db.MessageDao
import io.somi.data.db.MessageEntity
import io.somi.data.db.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.37.0 — Multi-conversation chat persistence facade.
 * All read/write ops are scoped to a [conversationId].
 */
@Singleton
class ChatRepository @Inject constructor(
    private val dao: MessageDao,
) {
    private var _currentConversationId: Long = 1L
    val currentConversationId: Long get() = _currentConversationId

    fun setConversation(id: Long) {
        _currentConversationId = id
    }

    fun observeMessages(): Flow<List<Message>> =
        dao.observeByConversation(_currentConversationId)
            .map { rows -> rows.map(MessageEntity::toDomain) }

    /** For flatMapLatest in ChatViewModel — observes a specific conversation. */
    fun observeMessagesForConversation(id: Long): Flow<List<Message>> =
        dao.observeByConversation(id).map { rows -> rows.map(MessageEntity::toDomain) }

    suspend fun appendUser(text: String): Long {
        val entity = MessageEntity(
            conversationId = _currentConversationId,
            author = Author.USER.name,
            text = text,
            timestamp = System.currentTimeMillis(),
        )
        return dao.insert(entity)
    }

    suspend fun appendAssistant(text: String, replacePartialId: Long? = null): Long {
        require(replacePartialId == null) { "replacePartialId reserved for future use" }
        val entity = MessageEntity(
            conversationId = _currentConversationId,
            author = Author.ASSISTANT.name,
            text = text,
            timestamp = System.currentTimeMillis(),
        )
        return dao.insert(entity)
    }

    suspend fun clearCurrentConversation() {
        dao.deleteByConversation(_currentConversationId)
    }

    suspend fun searchInCurrentConversation(query: String): List<Message> =
        dao.searchInConversation(_currentConversationId, query)
            .map(MessageEntity::toDomain)

    suspend fun searchInConversation(id: Long, query: String): List<Message> =
        dao.searchInConversation(id, query).map(MessageEntity::toDomain)

    suspend fun clearAll() {
        dao.deleteAll()
    }

    /** Returns the [limit] most-recent messages for the current conversation, oldest first. */
    suspend fun getRecentMessages(limit: Int): List<Message> =
        dao.getRecentForConversation(_currentConversationId, limit)
            .reversed()
            .map(MessageEntity::toDomain)
}
