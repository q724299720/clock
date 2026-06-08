package com.smartclock.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smartclock.domain.model.AlarmOccurrence
import com.smartclock.domain.model.OccurrenceSource
import com.smartclock.domain.model.OccurrenceStatus

@Entity(
    tableName = "alarm_occurrences",
    indices = [
        Index("alarmId"),
        Index(value = ["status", "triggerAt"]),
        Index(value = ["alarmId", "source", "status"])
    ]
)
data class AlarmOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val alarmId: Long,
    val triggerAt: Long,
    val originTriggerAt: Long,
    val source: Int,
    val status: Int,
    val snoozeCount: Int,
    val snoozeMinutes: Int,
    val expiresAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

fun AlarmOccurrenceEntity.toDomain(): AlarmOccurrence = AlarmOccurrence(
    id = id,
    alarmId = alarmId,
    triggerAt = triggerAt,
    originTriggerAt = originTriggerAt,
    source = OccurrenceSource.fromCode(source),
    status = OccurrenceStatus.fromCode(status),
    snoozeCount = snoozeCount,
    snoozeMinutes = snoozeMinutes,
    expiresAt = expiresAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun AlarmOccurrence.toEntity(): AlarmOccurrenceEntity = AlarmOccurrenceEntity(
    id = id,
    alarmId = alarmId,
    triggerAt = triggerAt,
    originTriggerAt = originTriggerAt,
    source = source.code,
    status = status.code,
    snoozeCount = snoozeCount,
    snoozeMinutes = snoozeMinutes,
    expiresAt = expiresAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)
