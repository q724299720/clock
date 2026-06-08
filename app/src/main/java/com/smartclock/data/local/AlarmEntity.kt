package com.smartclock.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.AlertPolicy
import com.smartclock.domain.model.CalendarType
import com.smartclock.domain.model.NextOverrideMode
import com.smartclock.domain.model.ScheduleMode
import com.smartclock.domain.model.TimeAnchorMode
import com.smartclock.domain.model.defaultAlertPolicy
import com.smartclock.domain.model.defaultTimeAnchorMode

@Entity(
    tableName = "alarms",
    indices = [Index(value = ["clientUuid"], unique = true)]
)
data class AlarmEntity(
    @PrimaryKey val id: Long,
    val clientUuid: String,
    val userId: Long,
    val type: Int,
    val title: String,
    val note: String?,
    val triggerTime: Long?,
    val durationSec: Int?,
    val repeatWeekdays: Int,
    val repeatMonthDays: Int,
    val anniversaryCalendar: Int,
    val advanceNotifyDays: String?,
    val ringtone: String?,
    val vibrate: Int,
    val volumeFade: Int,
    val snoozeMinutes: Int,
    val label: String?,
    val color: String?,
    val enabled: Int,
    val startDate: Long?,
    val endDate: Long?,
    val scheduleMode: Int = ScheduleMode.NORMAL.code,
    val alertPolicy: Int? = null,
    val timeAnchorMode: Int? = null,
    val intervalMonths: Int = 1,
    val intervalYears: Int = 1,
    val nextOverrideMode: Int = NextOverrideMode.NONE.code,
    val nextOverrideAnchorDate: String? = null,
    val nextOverrideAnchorTriggerAt: Long? = null,
    val nextOverrideTriggerAt: Long? = null,
    val templateId: String? = null,
    val status: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: Int = 1
)

fun AlarmEntity.toDomain(): Alarm {
    val alarmType = AlarmType.fromCode(type)
    return Alarm(
        id = id,
        clientUuid = clientUuid,
        userId = userId,
        type = alarmType,
        title = title,
        note = note,
        triggerTime = triggerTime,
        durationSec = durationSec,
        repeatWeekdays = repeatWeekdays,
        repeatMonthDays = repeatMonthDays,
        anniversaryCalendar = CalendarType.fromCode(anniversaryCalendar),
        advanceNotifyDays = advanceNotifyDays,
        ringtone = ringtone,
        vibrate = vibrate == 1,
        volumeFade = volumeFade == 1,
        snoozeMinutes = snoozeMinutes,
        label = label,
        color = color,
        enabled = enabled == 1,
        startDate = startDate,
        endDate = endDate,
        scheduleMode = ScheduleMode.fromCode(scheduleMode),
        alertPolicy = AlertPolicy.fromCode(alertPolicy)
            .let { if (alertPolicy == null) defaultAlertPolicy(alarmType, templateId) else it },
        timeAnchorMode = TimeAnchorMode.fromCode(timeAnchorMode)
            .let { if (timeAnchorMode == null) defaultTimeAnchorMode(alarmType) else it },
        intervalMonths = intervalMonths.coerceAtLeast(1),
        intervalYears = intervalYears.coerceAtLeast(1),
        nextOverrideMode = NextOverrideMode.fromCode(nextOverrideMode),
        nextOverrideAnchorDate = nextOverrideAnchorDate,
        nextOverrideAnchorTriggerAt = nextOverrideAnchorTriggerAt,
        nextOverrideTriggerAt = nextOverrideTriggerAt,
        templateId = templateId,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Alarm.toEntity(syncStatus: Int = 1): AlarmEntity = AlarmEntity(
    id = id,
    clientUuid = clientUuid,
    userId = userId,
    type = type.code,
    title = title,
    note = note,
    triggerTime = triggerTime,
    durationSec = durationSec,
    repeatWeekdays = repeatWeekdays,
    repeatMonthDays = repeatMonthDays,
    anniversaryCalendar = anniversaryCalendar.code,
    advanceNotifyDays = advanceNotifyDays,
    ringtone = ringtone,
    vibrate = if (vibrate) 1 else 0,
    volumeFade = if (volumeFade) 1 else 0,
    snoozeMinutes = snoozeMinutes,
    label = label,
    color = color,
    enabled = if (enabled) 1 else 0,
    startDate = startDate,
    endDate = endDate,
    scheduleMode = scheduleMode.code,
    alertPolicy = alertPolicy.code,
    timeAnchorMode = timeAnchorMode.code,
    intervalMonths = intervalMonths.coerceAtLeast(1),
    intervalYears = intervalYears.coerceAtLeast(1),
    nextOverrideMode = nextOverrideMode.code,
    nextOverrideAnchorDate = nextOverrideAnchorDate,
    nextOverrideAnchorTriggerAt = nextOverrideAnchorTriggerAt,
    nextOverrideTriggerAt = nextOverrideTriggerAt,
    templateId = templateId,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus
)
