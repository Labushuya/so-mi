package io.somi.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.somi.data.db.MessageDao
import io.somi.data.db.SoMiDatabase
import java.io.File
import javax.inject.Singleton

/**
 * Hilt graph for the Phase-3a persistence layer.
 *
 * The DB file is placed at `context.filesDir/somi.db`. filesDir is the
 * same root that ModelStorage uses, so all of so-mi's persistent state
 * lives in one inspectable place during dev. Chat history NOT in
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
        val dbFile = File(context.filesDir, DB_FILE_NAME)
        return Room.databaseBuilder(
            context.applicationContext,
            SoMiDatabase::class.java,
            dbFile.absolutePath,
        ).build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(db: SoMiDatabase): MessageDao = db.messageDao()
}
