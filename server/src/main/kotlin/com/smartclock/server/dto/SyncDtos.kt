package com.smartclock.server.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class AlarmSyncDto(
    val id: Long? = null,
    @field:NotBlank val clientUuid: String,
    val type: Int,
    @field:NotBlank val title: String,
    val note: String? = null,
    val triggerTime: String? = null,
    val durationSec: Int? = null,
    val repeatWeekdays: Int = 0,
    val repeatMonthDays: Int = 0,
    val anniversaryCalendar: Int = 0,
    val advanceNotifyDays: String? = null,
    val ringtone: String? = null,
    val vibrate: Boolean = true,
    val volumeFade: Boolean = false,
    val snoozeMinutes: Int = 5,
    val label: String? = null,
    val color: String? = null,
    val enabled: Boolean = true,
    val startDate: String? = null,
    val endDate: String? = null,
    val scheduleMode: Int = 0,
    val alertPolicy: Int = 0,
    val timeAnchorMode: Int = 0,
    val intervalMonths: Int = 1,
    val intervalYears: Int = 1,
    val templateId: String? = null,
    val status: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class AlarmPushRequest(
    @field:Valid @field:NotEmpty val alarms: List<AlarmSyncDto>
)

data class AlarmPushResponse(
    val alarms: List<AlarmSyncDto>,
    val serverTime: String
)

data class AlarmPullResponse(
    val alarms: List<AlarmSyncDto>,
    val serverTime: String
)

data class BootstrapResponse(
    val user: ApiUserDto,
    val alarms: List<AlarmSyncDto>,
    val serverTime: String
)

data class AlarmLogSyncDto(
    val alarmId: Long,
    @field:NotBlank val firedAt: String,
    val action: Int,
    @field:NotBlank val deviceId: String,
    @field:NotBlank val logHash: String
)

data class AlarmLogBatchRequest(
    @field:Valid @field:NotEmpty val alarmLogs: List<AlarmLogSyncDto>
)

data class AlarmLogBatchResponse(
    val inserted: Int,
    val ignored: Int,
    val serverTime: String
)
