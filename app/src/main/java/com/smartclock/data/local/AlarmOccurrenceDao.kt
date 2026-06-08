package com.smartclock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AlarmOccurrenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AlarmOccurrenceEntity): Long

    @Query("SELECT * FROM alarm_occurrences WHERE id = :id")
    suspend fun getById(id: Long): AlarmOccurrenceEntity?

    @Query(
        """
        SELECT * FROM alarm_occurrences
        WHERE alarmId = :alarmId AND source = :source AND status = :status
        ORDER BY triggerAt ASC
        """
    )
    suspend fun getByAlarmAndStatus(
        alarmId: Long,
        source: Int,
        status: Int
    ): List<AlarmOccurrenceEntity>

    @Query(
        """
        SELECT * FROM alarm_occurrences
        WHERE alarmId = :alarmId AND status = :status
        ORDER BY triggerAt ASC
        """
    )
    suspend fun getByAlarmStatusAnySource(alarmId: Long, status: Int): List<AlarmOccurrenceEntity>

    @Query(
        """
        SELECT * FROM alarm_occurrences
        WHERE source = :source AND status = :status
        ORDER BY triggerAt ASC
        """
    )
    suspend fun getBySourceAndStatus(source: Int, status: Int): List<AlarmOccurrenceEntity>

    @Query(
        """
        SELECT MAX(snoozeCount)
        FROM alarm_occurrences
        WHERE alarmId = :alarmId AND originTriggerAt = :originTriggerAt AND source = :source
        """
    )
    suspend fun maxSnoozeCount(alarmId: Long, originTriggerAt: Long, source: Int): Int?

    @Query(
        """
        SELECT COALESCE(SUM(snoozeMinutes), 0)
        FROM alarm_occurrences
        WHERE alarmId = :alarmId AND originTriggerAt = :originTriggerAt AND source = :source
        """
    )
    suspend fun totalSnoozeMinutes(alarmId: Long, originTriggerAt: Long, source: Int): Int

    @Query(
        """
        UPDATE alarm_occurrences
        SET status = :status, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateStatus(id: Long, status: Int, updatedAt: Long)

    @Query(
        """
        UPDATE alarm_occurrences
        SET status = :status, updatedAt = :updatedAt
        WHERE alarmId = :alarmId AND source = :source AND status = :pendingStatus
        """
    )
    suspend fun updatePendingForAlarm(
        alarmId: Long,
        source: Int,
        status: Int,
        pendingStatus: Int,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE alarm_occurrences
        SET status = :status, updatedAt = :updatedAt
        WHERE alarmId = :alarmId AND status = :pendingStatus
        """
    )
    suspend fun updatePendingForAlarmAnySource(
        alarmId: Long,
        status: Int,
        pendingStatus: Int,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE alarm_occurrences
        SET status = :expiredStatus, updatedAt = :updatedAt
        WHERE status = :pendingStatus AND source = :source AND expiresAt IS NOT NULL AND expiresAt < :cutoff
        """
    )
    suspend fun expirePendingSnoozes(
        source: Int,
        pendingStatus: Int,
        expiredStatus: Int,
        cutoff: Long,
        updatedAt: Long
    )
}
