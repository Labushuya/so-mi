package io.somi.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Row-shape mirror of [io.somi.common.chat.Message].
 *
 * Phase-3a persistence model: single conversation, append-only. The id
 * column is owned by Room (autoGenerate = true) so insertions return the
 * authoritative monotonic id which the ViewModel then uses as the key
 * for the streaming bubble.
 *
 * `text` is a TEXT column; Room's default UTF-8 SQLite encoding handles
 * German umlauts (äöüß) without any TypeConverter.
 *
 * The `author` column stores the [io.somi.common.chat.Author] enum as
 * its name ("USER" / "ASSISTANT"); see [Mappers] for the conversion.
 * Storing the name (not the ordinal) means we can reorder the enum in
 * core-common without a destructive schema migration.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "conversation_id", defaultValue = "1", index = true)
    val conversationId: Long = 1L,
    @ColumnInfo(name = "author")
    val author: String,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "timestamp", index = true)
    val timestamp: Long,
)
