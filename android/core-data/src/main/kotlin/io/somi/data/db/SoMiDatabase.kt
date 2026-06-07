package io.somi.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Phase-3a Room database. Version 1; no migrations yet.
 *
 * The DB file lives at `context.filesDir/somi.db` (see DatabaseModule).
 * filesDir is wiped only on app-data clear, which is the documented
 * acceptable failure mode for Phase 3a — the user explicitly chose to
 * forget.
 *
 * Future entities (Phase 3b: ConversationEntity; Phase 4: RagDocEntity)
 * will be added by bumping the version and writing a Migration. Don't
 * add `fallbackToDestructiveMigration()` here — silent data loss is
 * worse than a crash log that tells us we forgot to write the migration.
 */
@Database(
    entities = [MessageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SoMiDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
