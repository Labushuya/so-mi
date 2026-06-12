package io.somi.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, ConversationEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SoMiDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        /**
         * v0.37.0 Migration 1→2:
         * 1. Add conversations table
         * 2. Insert a default conversation so existing messages have a parent
         * 3. Add conversation_id column to messages (DEFAULT 1)
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create conversations table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_updated_at` ON `conversations` (`updated_at`)")

                // Insert default conversation for existing messages
                val now = System.currentTimeMillis()
                db.execSQL("INSERT INTO `conversations` (`id`, `title`, `created_at`, `updated_at`) VALUES (1, 'Erste Session', $now, $now)")

                // Add conversation_id to messages
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `conversation_id` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversation_id` ON `messages` (`conversation_id`)")
            }
        }
    }
}
