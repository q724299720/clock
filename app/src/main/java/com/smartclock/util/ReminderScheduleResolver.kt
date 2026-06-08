package com.smartclock.util

import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.NextOverrideMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

object ReminderScheduleResolver {
    private const val MATCH_TOLERANCE_MILLIS = 5 * 60 * 1000L

    fun nextTrigger(
        alarm: Alarm,
        from: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? = when (alarm.type) {
        AlarmType.COUNTDOWN -> alarm.triggerTime
        AlarmType.ONCE -> alarm.triggerTime?.takeIf { it > from }
        else -> {
            val baseTrigger = ScheduleUtil.nextTrigger(alarm, from)
            applyOverride(alarm, baseTrigger, from, zoneId)
        }
    }

    fun occurrencesBetween(
        alarm: Alarm,
        startInclusive: Long,
        endExclusive: Long,
        limit: Int = 64,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<Long> {
        if (!alarm.enabled || endExclusive <= startInclusive) return emptyList()
        val occurrences = mutableListOf<Long>()
        var cursor = startInclusive - 1
        while (occurrences.size < limit) {
            val next = nextTrigger(alarm, cursor, zoneId) ?: break
            if (next >= endExclusive) break
            if (next >= startInclusive) occurrences += next
            if (alarm.type == AlarmType.ONCE || alarm.type == AlarmType.COUNTDOWN) break
            cursor = next
        }
        return occurrences
    }

    fun scheduleFingerprint(alarm: Alarm): String = listOf(
        alarm.type.code.toString(),
        alarm.triggerTime?.toString().orEmpty(),
        alarm.repeatWeekdays.toString(),
        alarm.repeatMonthDays.toString(),
        alarm.anniversaryCalendar.code.toString(),
        alarm.scheduleMode.code.toString(),
        alarm.startDate?.toString().orEmpty(),
        alarm.endDate?.toString().orEmpty(),
        alarm.intervalMonths.toString(),
        alarm.intervalYears.toString(),
        alarm.timeAnchorMode.code.toString()
    ).joinToString("|")

    fun hasScheduleChanged(previous: Alarm, updated: Alarm): Boolean =
        scheduleFingerprint(previous) != scheduleFingerprint(updated)

    fun skipNextOccurrence(
        alarm: Alarm,
        anchorTriggerAt: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Alarm = alarm.copy(
        nextOverrideMode = NextOverrideMode.SKIP,
        nextOverrideAnchorDate = anchorDate(anchorTriggerAt, zoneId),
        nextOverrideAnchorTriggerAt = anchorTriggerAt,
        nextOverrideTriggerAt = null
    )

    fun rescheduleNextOccurrence(
        alarm: Alarm,
        anchorTriggerAt: Long,
        nextTriggerAt: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Alarm = alarm.copy(
        nextOverrideMode = NextOverrideMode.RESCHEDULE,
        nextOverrideAnchorDate = anchorDate(anchorTriggerAt, zoneId),
        nextOverrideAnchorTriggerAt = anchorTriggerAt,
        nextOverrideTriggerAt = nextTriggerAt
    )

    fun clearOverride(alarm: Alarm): Alarm = alarm.copy(
        nextOverrideMode = NextOverrideMode.NONE,
        nextOverrideAnchorDate = null,
        nextOverrideAnchorTriggerAt = null,
        nextOverrideTriggerAt = null
    )

    fun isOverrideStale(
        alarm: Alarm,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (alarm.nextOverrideMode == NextOverrideMode.NONE) return false
        val today = localDate(now, zoneId)
        return when (alarm.nextOverrideMode) {
            NextOverrideMode.NONE -> false
            NextOverrideMode.SKIP -> {
                val anchorDate = parseAnchorDate(alarm.nextOverrideAnchorDate)
                anchorDate?.isBefore(today) == true ||
                    alarm.nextOverrideAnchorTriggerAt?.let { now > it + MATCH_TOLERANCE_MILLIS } == true
            }

            NextOverrideMode.RESCHEDULE -> {
                alarm.nextOverrideTriggerAt?.let { now > it + MATCH_TOLERANCE_MILLIS } == true
            }
        }
    }

    fun shouldClearAfterTrigger(
        alarm: Alarm,
        firedAt: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (alarm.nextOverrideMode == NextOverrideMode.NONE) return false
        val firedDate = localDate(firedAt, zoneId)
        return when (alarm.nextOverrideMode) {
            NextOverrideMode.NONE -> false
            NextOverrideMode.SKIP -> {
                val anchorDate = parseAnchorDate(alarm.nextOverrideAnchorDate)
                (anchorDate != null && firedDate.isAfter(anchorDate)) ||
                    alarm.nextOverrideAnchorTriggerAt?.let { firedAt > it + MATCH_TOLERANCE_MILLIS } == true
            }

            NextOverrideMode.RESCHEDULE -> {
                alarm.nextOverrideTriggerAt?.let { abs(firedAt - it) <= MATCH_TOLERANCE_MILLIS || firedAt > it } == true
            }
        }
    }

    private fun applyOverride(
        alarm: Alarm,
        baseTrigger: Long?,
        from: Long,
        zoneId: ZoneId
    ): Long? {
        if (baseTrigger == null || alarm.nextOverrideMode == NextOverrideMode.NONE) return baseTrigger
        if (!matchesOverrideAnchor(alarm, baseTrigger, zoneId)) return baseTrigger
        return when (alarm.nextOverrideMode) {
            NextOverrideMode.NONE -> baseTrigger
            NextOverrideMode.SKIP -> ScheduleUtil.nextTrigger(alarm, baseTrigger)
            NextOverrideMode.RESCHEDULE -> {
                val overrideTriggerAt = alarm.nextOverrideTriggerAt
                when {
                    overrideTriggerAt == null -> baseTrigger
                    overrideTriggerAt <= from -> ScheduleUtil.nextTrigger(alarm, baseTrigger)
                    else -> overrideTriggerAt
                }
            }
        }
    }

    private fun matchesOverrideAnchor(
        alarm: Alarm,
        candidateTriggerAt: Long,
        zoneId: ZoneId
    ): Boolean {
        val anchorDate = parseAnchorDate(alarm.nextOverrideAnchorDate)
        if (anchorDate != null && localDate(candidateTriggerAt, zoneId) == anchorDate) {
            return true
        }
        val anchorTriggerAt = alarm.nextOverrideAnchorTriggerAt ?: return false
        return abs(candidateTriggerAt - anchorTriggerAt) <= MATCH_TOLERANCE_MILLIS
    }

    private fun parseAnchorDate(value: String?): LocalDate? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun anchorDate(triggerAt: Long, zoneId: ZoneId): String =
        localDate(triggerAt, zoneId).toString()

    private fun localDate(triggerAt: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(triggerAt).atZone(zoneId).toLocalDate()
}
