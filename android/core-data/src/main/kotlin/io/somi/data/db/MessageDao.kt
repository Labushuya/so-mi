package io.somi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY id ASC")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY id ASC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int

    @Query("SELECT text FROM messages WHERE conversation_id = :conversationId ORDER BY id DESC LIMIT 1")
    suspend fun lastMessageText(conversationId: Long): String?
}
