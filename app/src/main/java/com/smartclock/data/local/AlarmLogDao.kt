package com.smartclock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AlarmLogEntity)

    @Update
    suspend fun update(log: AlarmLogEntity)

    @Query("SELECT * FROM alarm_logs WHERE userId = :userId ORDER BY firedAt DESC LIMIT :limit")
    fun observeRecentLogs(userId: Long, limit: Int): Flow<List<AlarmLogEntity>>

    @Query("SELECT * FROM alarm_logs WHERE userId = :userId AND syncStatus != 0 ORDER BY firedAt ASC LIMIT :limit")
    suspend fun getPendingSync(userId: Long, limit: Int = 200): List<AlarmLogEntity>

    @Query("UPDATE alarm_logs SET userId = :userId WHERE userId = 0 AND syncStatus != 0")
    suspend fun claimLocalLogs(userId: Long): Int

    @Query("UPDATE alarm_logs SET syncStatus = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("UPDATE alarm_logs SET alarmId = :newAlarmId WHERE alarmId = :oldAlarmId")
    suspend fun remapAlarmId(oldAlarmId: Long, newAlarmId: Long): Int

    @Query("SELECT COUNT(*) FROM alarm_logs WHERE userId = :userId AND syncStatus != 0")
    suspend fun countPendingSync(userId: Long): Int
}
