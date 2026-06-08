package com.smartclock.data.remote.dto

import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmLog
import com.smartclock.domain.model.AlarmLogAction
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.CalendarType
import com.smartclock.domain.model.NextOverrideMode
import com.smartclock.domain.model.User
import com.smartclock.domain.model.ScheduleMode
import com.smartclock.domain.model.AlertPolicy
import com.smartclock.domain.model.TimeAnchorMode
import java.time.Instant

data class ApiUserDto(
    val id: Long,
    val phone: String?,
    val email: String?,
    val nickname: String?,
    val role: String,
    val status: Int
)

data class ApiAuthRequest(
    val account: String,
    val isEmail: Boolean,
    val password: String,
    val nickname: String? = null,
    val clientType: String? = null
)

data class ApiRefreshTokenRequest(
    val refreshToken: String,
    val clientType: String? = null
)

data class ApiAuthResponse(
    val user: ApiUserDto,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String,
    val refreshTokenExpiresAt: String
)

data class ApiStatusResponse(
    val message: String
)

data class ApiErrorResponse(
    val code: String?,
    val message: String?
)

data class ApiAlarmSyncDto(
    val id: Long? = null,
    val clientUuid: String,
    val type: Int,
    val title: String,
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
    val nextOverrideMode: Int = 0,
    val nextOverrideAnchorDate: String? = null,
    val nextOverrideAnchorTriggerAt: String? = null,
    val nextOverrideTriggerAt: String? = null,
    val templateId: String? = null,
    val status: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ApiAlarmPushRequest(
    val alarms: List<ApiAlarmSyncDto>
)

data class ApiAlarmPushResponse(
    val alarms: List<ApiAlarmSyncDto>,
    val serverTime: String
)

data class ApiAlarmPullResponse(
    val alarms: List<ApiAlarmSyncDto>,
    val serverTime: String
)

data class ApiBootstrapResponse(
    val user: ApiUserDto,
    val alarms: List<ApiAlarmSyncDto>,
    val serverTime: String
)

data class ApiAlarmLogSyncDto(
    val alarmId: Long,
    val firedAt: String,
    val action: Int,
    val deviceId: String,
    val logHash: String
)

data class ApiAlarmLogBatchRequest(
    val alarmLogs: List<ApiAlarmLogSyncDto>
)

data class ApiAlarmLogBatchResponse(
    val inserted: Int,
    val ignored: Int,
    val serverTime: String
)

fun Alarm.toApiDto(): ApiAlarmSyncDto = ApiAlarmSyncDto(
    id = if (id > 0) id else null,
    clientUuid = clientUuid,
    type = type.code,
    title = title.trim().ifBlank { "闹钟" }.take(128),
    note = note,
    triggerTime = triggerTime?.let { Instant.ofEpochMilli(it).toString() },
    durationSec = durationSec,
    repeatWeekdays = repeatWeekdays,
    repeatMonthDays = repeatMonthDays,
    anniversaryCalendar = anniversaryCalendar.code,
    advanceNotifyDays = advanceNotifyDays?.trim()?.take(128)?.ifBlank { null },
    ringtone = ringtone?.trim()?.take(255)?.ifBlank { null },
    vibrate = vibrate,
    volumeFade = volumeFade,
    snoozeMinutes = snoozeMinutes,
    label = label?.trim()?.take(128)?.ifBlank { null },
    color = color?.trim()?.take(32)?.ifBlank { null },
    enabled = enabled,
    startDate = startDate?.let { Instant.ofEpochMilli(it).toString() },
    endDate = endDate?.let { Instant.ofEpochMilli(it).toString() },
    scheduleMode = scheduleMode.code,
    alertPolicy = alertPolicy.code,
    timeAnchorMode = timeAnchorMode.code,
    intervalMonths = intervalMonths,
    intervalYears = intervalYears,
    nextOverrideMode = nextOverrideMode.code,
    nextOverrideAnchorDate = nextOverrideAnchorDate,
    nextOverrideAnchorTriggerAt = nextOverrideAnchorTriggerAt?.let { Instant.ofEpochMilli(it).toString() },
    nextOverrideTriggerAt = nextOverrideTriggerAt?.let { Instant.ofEpochMilli(it).toString() },
    templateId = templateId?.trim()?.take(64)?.ifBlank { null },
    status = status,
    createdAt = Instant.ofEpochMilli(createdAt).toString(),
    updatedAt = Instant.ofEpochMilli(updatedAt).toString()
)

fun ApiAlarmSyncDto.toDomain(userId: Long): Alarm = Alarm(
    id = id ?: 0L,
    clientUuid = clientUuid,
    userId = userId,
    type = AlarmType.fromCode(type),
    title = title,
    note = note,
    triggerTime = triggerTime?.let { Instant.parse(it).toEpochMilli() },
    durationSec = durationSec,
    repeatWeekdays = repeatWeekdays,
    repeatMonthDays = repeatMonthDays,
    anniversaryCalendar = CalendarType.fromCode(anniversaryCalendar),
    advanceNotifyDays = advanceNotifyDays,
    ringtone = ringtone,
    vibrate = vibrate,
    volumeFade = volumeFade,
    snoozeMinutes = snoozeMinutes,
    label = label,
    color = color,
    enabled = enabled,
    startDate = startDate?.let { Instant.parse(it).toEpochMilli() },
    endDate = endDate?.let { Instant.parse(it).toEpochMilli() },
    scheduleMode = ScheduleMode.fromCode(scheduleMode),
    alertPolicy = AlertPolicy.fromCode(alertPolicy),
    timeAnchorMode = TimeAnchorMode.fromCode(timeAnchorMode),
    intervalMonths = intervalMonths,
    intervalYears = intervalYears,
    nextOverrideMode = NextOverrideMode.fromCode(nextOverrideMode),
    nextOverrideAnchorDate = nextOverrideAnchorDate,
    nextOverrideAnchorTriggerAt = nextOverrideAnchorTriggerAt?.let { Instant.parse(it).toEpochMilli() },
    nextOverrideTriggerAt = nextOverrideTriggerAt?.let { Instant.parse(it).toEpochMilli() },
    templateId = templateId,
    status = status,
    createdAt = createdAt?.let { Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.let { Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis()
)

fun ApiUserDto.toDomain(): User = User(
    id = id,
    phone = phone,
    email = email,
    nickname = nickname
)

fun AlarmLog.toApiDto(): ApiAlarmLogSyncDto = ApiAlarmLogSyncDto(
    alarmId = alarmId,
    firedAt = Instant.ofEpochMilli(firedAt).toString(),
    action = action.code,
    deviceId = deviceId.orEmpty(),
    logHash = logHash
)
