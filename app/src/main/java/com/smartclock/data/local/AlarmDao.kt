package com.smartclock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms WHERE userId = :userId AND status = 0 ORDER BY triggerTime")
    fun observeAlarms(userId: Long): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE userId = :userId AND enabled = 1 AND status = 0")
    suspend fun getEnabledAlarms(userId: Long): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE userId = :userId AND status = 0")
    suspend fun getAllAlarms(userId: Long): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE clientUuid = :clientUuid LIMIT 1")
    suspend fun getByClientUuid(clientUuid: String): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE userId = :userId AND syncStatus != 0")
    suspend fun getPendingSync(userId: Long): List<AlarmEntity>

    @Query("SELECT COUNT(*) FROM alarms WHERE userId = :userId AND syncStatus = 1")
    suspend fun countPendingUpserts(userId: Long): Int

    @Query("SELECT COUNT(*) FROM alarms WHERE userId = :userId AND syncStatus = 2")
    suspend fun countPendingDeletes(userId: Long): Int

    @Query("UPDATE alarms SET userId = :userId, syncStatus = 1 WHERE userId = 0 AND status = 0")
    suspend fun claimLocalAlarms(userId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: AlarmEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(alarms: List<AlarmEntity>)

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled, updatedAt = :now, syncStatus = 1 WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Int, now: Long)

    @Query("UPDATE alarms SET status = 1, syncStatus = 2, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: Long, now: Long)

    @Query(
        """
        UPDATE alarms
        SET nextOverrideMode = 0,
            nextOverrideAnchorDate = NULL,
            nextOverrideAnchorTriggerAt = NULL,
            nextOverrideTriggerAt = NULL,
            updatedAt = :now,
            syncStatus = 1
        WHERE id = :id
        """
    )
    suspend fun clearNextOverride(id: Long, now: Long)

    @Query("UPDATE alarms SET syncStatus = 0 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("DELETE FROM alarms WHERE clientUuid = :clientUuid")
    suspend fun hardDeleteByClientUuid(clientUuid: String)

    @Query("SELECT MAX(updatedAt) FROM alarms WHERE userId = :userId AND syncStatus = 0")
    suspend fun lastSyncedAt(userId: Long): Long?
}
