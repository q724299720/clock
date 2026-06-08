package com.smartclock.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AlarmEntity::class, AlarmLogEntity::class, AlarmOccurrenceEntity::class, DeletedAlarmEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun alarmLogDao(): AlarmLogDao
    abstract fun alarmOccurrenceDao(): AlarmOccurrenceDao
    abstract fun deletedAlarmDao(): DeletedAlarmDao

    companion object {
        const val NAME = "smartclock.db"
    }
}
