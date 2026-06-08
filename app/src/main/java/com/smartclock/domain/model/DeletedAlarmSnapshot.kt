package com.smartclock.domain.model

data class DeletedAlarmSnapshot(
    val trashId: Long,
    val userId: Long,
    val deletedAt: Long,
    val expiresAt: Long,
    val alarm: Alarm
)
