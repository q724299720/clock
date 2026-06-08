package com.smartclock.server.dto

data class UpdateUserStatusRequest(
    val status: Int
)

data class UpdateAlarmRequest(
    val title: String? = null,
    val note: String? = null,
    val enabled: Boolean? = null,
    val status: Int? = null,
    val triggerTime: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val scheduleMode: Int? = null,
    val alertPolicy: Int? = null,
    val timeAnchorMode: Int? = null,
    val intervalMonths: Int? = null,
    val intervalYears: Int? = null,
    val templateId: String? = null
)

data class AlarmLogAdminDto(
    val id: Long,
    val alarmId: Long,
    val userId: Long,
    val firedAt: String,
    val action: Int,
    val deviceId: String,
    val logHash: String
)

data class AdminAuditLogDto(
    val id: Long,
    val adminUserId: Long,
    val action: String,
    val targetType: String,
    val targetId: String?,
    val detailJson: String?,
    val ipAddress: String?,
    val createdAt: String
)
