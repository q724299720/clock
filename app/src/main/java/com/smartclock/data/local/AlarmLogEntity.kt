package com.smartclock.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartclock.domain.model.AlarmLog
import com.smartclock.domain.model.AlarmLogAction

@Entity(tableName = "alarm_logs")
data class AlarmLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val alarmId: Long,
    val userId: Long,
    val firedAt: Long,
    val action: Int,
    val deviceId: String? = null,
    val logHash: String,
    val syncStatus: Int = 1
)

fun AlarmLogEntity.toDomain(): AlarmLog = AlarmLog(
    id = id,
    alarmId = alarmId,
    userId = userId,
    firedAt = firedAt,
    action = AlarmLogAction.fromCode(action),
    deviceId = deviceId,
    logHash = logHash
)

fun AlarmLog.toEntity(syncStatus: Int = 1): AlarmLogEntity = AlarmLogEntity(
    id = id,
    alarmId = alarmId,
    userId = userId,
    firedAt = firedAt,
    action = action.code,
    deviceId = deviceId,
    logHash = logHash,
    syncStatus = syncStatus
)
