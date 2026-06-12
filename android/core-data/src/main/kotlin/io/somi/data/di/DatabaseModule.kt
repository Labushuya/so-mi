package io.somi.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.somi.data.StorageRoots
import io.somi.data.db.ConversationDao
import io.somi.data.db.MessageDao
import io.somi.data.db.SoMiDatabase
import java.io.File
import javax.inject.Singleton

/**
 * Hilt graph for the Phase-3a persistence layer.
 *
 * v0.15.0: DB file moved from `filesDir/somi.db` to
 * `externalFilesDir/SoMi/db/somi.db` so all user-relevant state
 * lives under one visible root. StorageMigrator handles the
 * cross-volume copy on first launch. Chat history still NOT in
 * adb-backup by design — privacy.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    private const val DB_FILE_NAME = "somi.db"

    @Provides
    @Singleton
    fun provideSoMiDatabase(
        @ApplicationContext context: Context,
    ): SoMiDatabase {
        val dbFile = File(StorageRoots.db(context), DB_FILE_NAME)
        return Room.databaseBuilder(
            context.applicationContext,
            SoMiDatabase::class.java,
            dbFile.absolutePath,
        )
            .addMigrations(SoMiDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(db: SoMiDatabase): MessageDao = db.messageDao()

    @Provides
    @Singleton
    fun provideConversationDao(db: SoMiDatabase): ConversationDao = db.conversationDao()
}
