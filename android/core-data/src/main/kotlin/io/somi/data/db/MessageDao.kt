package io.somi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [MessageEntity]. Append-only in Phase 3a.
 *
 * [observeAll] is a Room-backed [Flow]: it emits the current snapshot
 * immediately on collect and re-emits whenever the messages table
 * changes. That replaces the Phase-2.6 MutableStateFlow at the
 * ViewModel layer without changing the type the UI sees.
 */
@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages ORDER BY id ASC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int
}
