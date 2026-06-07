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
 * Phase-3a chat persistence facade. Backs ChatViewModel's messages
 * Flow and the append paths. Single conversation; multi-conversation
 * arrives in Phase 3b via a `conversationId` column + WHERE clause.
 *
 * Threading: every method that touches the DB is `suspend` and Room
 * routes it onto its internal IO executor. The ViewModel may call
 * append* from `viewModelScope.launch { ... }` without an explicit
 * `withContext(Dispatchers.IO)`.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val dao: MessageDao,
) {

    /**
     * Hot-on-collect Flow of every persisted message in insertion
     * order. Backed by Room's invalidation tracker — emits a fresh list
     * after every successful insert/clear.
     */
    fun observeMessages(): Flow<List<Message>> =
        dao.observeAll().map { rows -> rows.map(MessageEntity::toDomain) }

    /**
     * Append a USER turn. Returns the Room-assigned id so the caller
     * can key the streaming bubble (ChatState.Generating.promptId) to
     * the persisted row.
     */
    suspend fun appendUser(text: String): Long {
        val entity = MessageEntity(
            id = 0L,
            author = Author.USER.name,
            text = text,
            timestamp = System.currentTimeMillis(),
        )
        return dao.insert(entity)
    }

    /**
     * Append an ASSISTANT turn. [replacePartialId] is reserved for a
     * future Phase-3b feature; in Phase 3a it is always null.
     */
    suspend fun appendAssistant(text: String, replacePartialId: Long? = null): Long {
        require(replacePartialId == null) {
            "replacePartialId is reserved for Phase 3b; pass null in Phase 3a"
        }
        val entity = MessageEntity(
            id = 0L,
            author = Author.ASSISTANT.name,
            text = text,
            timestamp = System.currentTimeMillis(),
        )
        return dao.insert(entity)
    }

    /** Wipe the entire transcript. */
    suspend fun clearAll() {
        dao.deleteAll()
    }
}
