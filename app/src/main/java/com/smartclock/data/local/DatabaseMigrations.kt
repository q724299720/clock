package com.smartclock.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS alarm_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    alarmId INTEGER NOT NULL,
                    userId INTEGER NOT NULL,
                    firedAt INTEGER NOT NULL,
                    action INTEGER NOT NULL,
                    deviceId TEXT
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN scheduleMode INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE alarms ADD COLUMN alertPolicy INTEGER")
            db.execSQL("ALTER TABLE alarms ADD COLUMN timeAnchorMode INTEGER")
            db.execSQL("ALTER TABLE alarms ADD COLUMN intervalMonths INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE alarms ADD COLUMN intervalYears INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE alarms ADD COLUMN templateId TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS alarm_occurrences (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    alarmId INTEGER NOT NULL,
                    triggerAt INTEGER NOT NULL,
                    originTriggerAt INTEGER NOT NULL,
                    source INTEGER NOT NULL,
                    status INTEGER NOT NULL,
                    snoozeCount INTEGER NOT NULL,
                    snoozeMinutes INTEGER NOT NULL,
                    expiresAt INTEGER,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_alarm_occurrences_alarmId
                ON alarm_occurrences(alarmId)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_alarm_occurrences_status_triggerAt
                ON alarm_occurrences(status, triggerAt)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_alarm_occurrences_alarmId_source_status
                ON alarm_occurrences(alarmId, source, status)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN clientUuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE alarms SET clientUuid = 'legacy-' || ABS(id) || '-' || createdAt WHERE clientUuid = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_alarms_clientUuid ON alarms(clientUuid)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS alarm_logs_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    alarmId INTEGER NOT NULL,
                    userId INTEGER NOT NULL,
                    firedAt INTEGER NOT NULL,
                    action INTEGER NOT NULL,
                    deviceId TEXT,
                    logHash TEXT NOT NULL,
                    syncStatus INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO alarm_logs_new (id, alarmId, userId, firedAt, action, deviceId, logHash, syncStatus)
                SELECT id, alarmId, userId, firedAt, action, deviceId, 'legacy-' || id, 1
                FROM alarm_logs
                """.trimIndent()
            )
            db.execSQL("DROP TABLE alarm_logs")
            db.execSQL("ALTER TABLE alarm_logs_new RENAME TO alarm_logs")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN nextOverrideMode INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE alarms ADD COLUMN nextOverrideAnchorDate TEXT")
            db.execSQL("ALTER TABLE alarms ADD COLUMN nextOverrideAnchorTriggerAt INTEGER")
            db.execSQL("ALTER TABLE alarms ADD COLUMN nextOverrideTriggerAt INTEGER")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS deleted_alarms (
                    trashId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId INTEGER NOT NULL,
                    alarmId INTEGER NOT NULL,
                    clientUuid TEXT NOT NULL,
                    title TEXT NOT NULL,
                    triggerTime INTEGER,
                    deletedAt INTEGER NOT NULL,
                    expiresAt INTEGER NOT NULL,
                    payloadJson TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_deleted_alarms_userId_deletedAt
                ON deleted_alarms(userId, deletedAt)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_deleted_alarms_clientUuid
                ON deleted_alarms(clientUuid)
                """.trimIndent()
            )
        }
    }
}
