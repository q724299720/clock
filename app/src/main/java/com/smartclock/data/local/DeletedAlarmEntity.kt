package com.smartclock.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deleted_alarms",
    indices = [
        Index(value = ["userId", "deletedAt"]),
        Index(value = ["clientUuid"])
    ]
)
data class DeletedAlarmEntity(
    @PrimaryKey(autoGenerate = true) val trashId: Long = 0L,
    val userId: Long,
    val alarmId: Long,
    val clientUuid: String,
    val title: String,
    val triggerTime: Long?,
    val deletedAt: Long,
    val expiresAt: Long,
    val payloadJson: String
)
