package com.smartclock.domain.model

enum class AlarmType(val code: Int) {
    ONCE(1),
    COUNTDOWN(2),
    WEEKLY(3),
    MONTHLY(4),
    ANNIVERSARY(5);

    companion object {
        fun fromCode(code: Int): AlarmType = entries.first { it.code == code }
    }
}

enum class CalendarType(val code: Int) {
    SOLAR(0),
    LUNAR(1);

    companion object {
        fun fromCode(code: Int?): CalendarType = if (code == 1) LUNAR else SOLAR
    }
}

enum class AlertPolicy(val code: Int) {
    WAKE_ALARM(0),
    QUIET_REMINDER(1);

    companion object {
        fun fromCode(code: Int?): AlertPolicy =
            if (code == QUIET_REMINDER.code) QUIET_REMINDER else WAKE_ALARM
    }
}

enum class TimeAnchorMode(val code: Int) {
    FLOATING_LOCAL(0),
    ABSOLUTE_UTC(1);

    companion object {
        fun fromCode(code: Int?): TimeAnchorMode =
            if (code == ABSOLUTE_UTC.code) ABSOLUTE_UTC else FLOATING_LOCAL
    }
}

enum class NextOverrideMode(val code: Int) {
    NONE(0),
    SKIP(1),
    RESCHEDULE(2);

    companion object {
        fun fromCode(code: Int?): NextOverrideMode = when (code) {
            SKIP.code -> SKIP
            RESCHEDULE.code -> RESCHEDULE
            else -> NONE
        }
    }
}

enum class ScheduleMode(val code: Int) {
    NORMAL(0),
    WORKDAY_CN(1);

    companion object {
        fun fromCode(code: Int?): ScheduleMode =
            if (code == WORKDAY_CN.code) WORKDAY_CN else NORMAL
    }
}

object AlarmTemplateIds {
    const val BIRTHDAY = "birthday_manager"
    const val WORKDAY = "workday_reminder"
    const val CREDIT_CARD = "credit_card"
    const val RENT = "rent"
    const val MEDICINE = "medicine"
    const val WATER = "water"
    const val LICENSE_REVIEW = "license_review"
    const val SHIFT = "shift"
}

data class Alarm(
    val id: Long = 0L,
    val clientUuid: String = "",
    val userId: Long,
    val type: AlarmType,
    val title: String,
    val note: String? = null,
    val triggerTime: Long? = null,
    val durationSec: Int? = null,
    val repeatWeekdays: Int = 0,
    val repeatMonthDays: Int = 0,
    val anniversaryCalendar: CalendarType = CalendarType.SOLAR,
    val advanceNotifyDays: String? = null,
    val ringtone: String? = null,
    val vibrate: Boolean = true,
    val volumeFade: Boolean = false,
    val snoozeMinutes: Int = 5,
    val label: String? = null,
    val color: String? = null,
    val enabled: Boolean = true,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val scheduleMode: ScheduleMode = ScheduleMode.NORMAL,
    val alertPolicy: AlertPolicy = defaultAlertPolicy(type, null),
    val timeAnchorMode: TimeAnchorMode = defaultTimeAnchorMode(type),
    val intervalMonths: Int = 1,
    val intervalYears: Int = 1,
    val nextOverrideMode: NextOverrideMode = NextOverrideMode.NONE,
    val nextOverrideAnchorDate: String? = null,
    val nextOverrideAnchorTriggerAt: Long? = null,
    val nextOverrideTriggerAt: Long? = null,
    val templateId: String? = null,
    val status: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun defaultAlertPolicy(type: AlarmType, templateId: String?): AlertPolicy = when {
    type == AlarmType.COUNTDOWN -> AlertPolicy.WAKE_ALARM
    templateId in setOf(
        AlarmTemplateIds.BIRTHDAY,
        AlarmTemplateIds.CREDIT_CARD,
        AlarmTemplateIds.RENT,
        AlarmTemplateIds.MEDICINE,
        AlarmTemplateIds.WATER,
        AlarmTemplateIds.LICENSE_REVIEW
    ) -> AlertPolicy.QUIET_REMINDER
    type == AlarmType.ANNIVERSARY -> AlertPolicy.QUIET_REMINDER
    else -> AlertPolicy.WAKE_ALARM
}

fun defaultTimeAnchorMode(type: AlarmType): TimeAnchorMode =
    if (type == AlarmType.COUNTDOWN) TimeAnchorMode.ABSOLUTE_UTC else TimeAnchorMode.FLOATING_LOCAL
