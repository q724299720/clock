package com.smartclock.di

import android.content.Context
import androidx.room.Room
import com.smartclock.data.local.AppDatabase
import com.smartclock.data.local.AlarmDao
import com.smartclock.data.local.AlarmOccurrenceDao
import com.smartclock.data.local.AlarmLogDao
import com.smartclock.data.local.DeletedAlarmDao
import com.smartclock.data.local.DatabaseBackupUtil
import com.smartclock.data.local.DatabaseMigrations
import com.smartclock.data.local.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        DatabaseBackupUtil.backupExistingDatabase(context)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(
                DatabaseMigrations.MIGRATION_1_2,
                DatabaseMigrations.MIGRATION_2_3,
                DatabaseMigrations.MIGRATION_3_4,
                DatabaseMigrations.MIGRATION_4_5
            )
            .build()
    }

    @Provides
    fun provideAlarmDao(db: AppDatabase): AlarmDao = db.alarmDao()

    @Provides
    fun provideAlarmLogDao(db: AppDatabase): AlarmLogDao = db.alarmLogDao()

    @Provides
    fun provideAlarmOccurrenceDao(db: AppDatabase): AlarmOccurrenceDao = db.alarmOccurrenceDao()

    @Provides
    fun provideDeletedAlarmDao(db: AppDatabase): DeletedAlarmDao = db.deletedAlarmDao()

    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore =
        SessionStore(context)
}
