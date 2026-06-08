package com.smartclock.util

import com.nlf.calendar.util.HolidayUtil
import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.CalendarType
import com.smartclock.domain.model.ScheduleMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

object ScheduleUtil {

    fun nextTrigger(alarm: Alarm, from: Long = System.currentTimeMillis()): Long? {
        alarm.endDate?.let { if (from > it) return null }
        val base = maxOf(from, alarm.startDate ?: from)
        return when (alarm.type) {
            AlarmType.ONCE -> alarm.triggerTime?.takeIf { it > from }
            AlarmType.COUNTDOWN -> null
            AlarmType.WEEKLY -> {
                if (alarm.scheduleMode == ScheduleMode.WORKDAY_CN) {
                    nextChineseWorkday(alarm, base)
                } else {
                    nextWeekly(alarm, base)
                }
            }
            AlarmType.MONTHLY -> nextMonthly(alarm, base)
            AlarmType.ANNIVERSARY -> nextAnniversary(alarm, base)
        }
    }

    fun occurrencesBetween(
        alarm: Alarm,
        startInclusive: Long,
        endExclusive: Long,
        limit: Int = 64
    ): List<Long> {
        if (!alarm.enabled || endExclusive <= startInclusive) return emptyList()

        val occurrences = mutableListOf<Long>()
        var cursor = startInclusive - 1
        while (occurrences.size < limit) {
            val next = nextTrigger(alarm, cursor) ?: break
            if (next >= endExclusive) break
            if (next >= startInclusive) {
                occurrences += next
            }
            if (alarm.type == AlarmType.ONCE || alarm.type == AlarmType.COUNTDOWN) break
            cursor = next
        }
        return occurrences
    }

    private fun hourMinuteOf(alarm: Alarm): Pair<Int, Int> {
        val calendar = Calendar.getInstance().apply { timeInMillis = alarm.triggerTime ?: 0L }
        return calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
    }

    private fun nextChineseWorkday(alarm: Alarm, from: Long): Long? {
        val (hour, minute) = hourMinuteOf(alarm)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(370) {
            val candidate = calendar.timeInMillis
            if (candidate > from && isChineseWorkday(candidate.toLocalDate())) {
                return candidate
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
        }
        return null
    }

    private fun nextWeekly(alarm: Alarm, from: Long): Long? {
        if (alarm.repeatWeekdays == 0) return null
        val (hour, minute) = hourMinuteOf(alarm)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            val weekdayBit = 1 shl (calendar.get(Calendar.DAY_OF_WEEK) - 1)
            if ((alarm.repeatWeekdays and weekdayBit) != 0 && calendar.timeInMillis > from) {
                return calendar.timeInMillis
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
        }
        return null
    }

    private fun nextMonthly(alarm: Alarm, from: Long): Long? {
        if (alarm.repeatMonthDays == 0) return null
        val intervalMonths = alarm.intervalMonths.coerceAtLeast(1)
        val (hour, minute) = hourMinuteOf(alarm)
        val anchor = Calendar.getInstance().apply { timeInMillis = alarm.triggerTime ?: from }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(13 * 31 * intervalMonths) {
            val monthsFromAnchor = monthsBetween(anchor, calendar)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val selected = (alarm.repeatMonthDays and (1 shl day)) != 0
            if (
                selected &&
                calendar.timeInMillis > from &&
                monthsFromAnchor >= 0 &&
                monthsFromAnchor % intervalMonths == 0
            ) {
                return calendar.timeInMillis
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
        }
        return null
    }

    private fun nextAnniversary(alarm: Alarm, from: Long): Long? {
        val trigger = alarm.triggerTime ?: return null
        val (hour, minute) = hourMinuteOf(alarm)
        val intervalYears = alarm.intervalYears.coerceAtLeast(1)
        return if (alarm.anniversaryCalendar == CalendarType.LUNAR) {
            nextLunarAnniversary(trigger, from, hour, minute, intervalYears)
        } else {
            nextSolarAnniversary(trigger, from, hour, minute, intervalYears)
        }
    }

    private fun nextSolarAnniversary(
        trigger: Long,
        from: Long,
        hour: Int,
        minute: Int,
        intervalYears: Int
    ): Long {
        val source = Calendar.getInstance().apply { timeInMillis = trigger }
        val targetMonth = source.get(Calendar.MONTH)
        val targetDay = source.get(Calendar.DAY_OF_MONTH)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.MONTH, targetMonth)
            set(Calendar.DAY_OF_MONTH, targetDay)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (calendar.timeInMillis <= from || !matchesYearInterval(source, calendar, intervalYears)) {
            calendar.add(Calendar.YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun nextLunarAnniversary(
        trigger: Long,
        from: Long,
        hour: Int,
        minute: Int,
        intervalYears: Int
    ): Long? {
        if (intervalYears <= 1) return LunarUtil.nextLunarAnniversary(trigger, from, hour, minute)
        val sourceYear = Calendar.getInstance().apply { timeInMillis = trigger }.get(Calendar.YEAR)
        var searchFrom = from
        repeat(60) {
            val candidate = LunarUtil.nextLunarAnniversary(trigger, searchFrom, hour, minute) ?: return null
            val candidateYear = Calendar.getInstance().apply { timeInMillis = candidate }.get(Calendar.YEAR)
            if ((candidateYear - sourceYear) % intervalYears == 0) return candidate
            searchFrom = candidate + ONE_DAY_MILLIS
        }
        return null
    }

    private fun matchesYearInterval(
        source: Calendar,
        candidate: Calendar,
        intervalYears: Int
    ): Boolean {
        val years = candidate.get(Calendar.YEAR) - source.get(Calendar.YEAR)
        return years >= 0 && years % intervalYears == 0
    }

    private fun monthsBetween(anchor: Calendar, candidate: Calendar): Int =
        (candidate.get(Calendar.YEAR) - anchor.get(Calendar.YEAR)) * 12 +
            (candidate.get(Calendar.MONTH) - anchor.get(Calendar.MONTH))

    private fun isChineseWorkday(date: LocalDate): Boolean {
        val holiday = HolidayUtil.getHoliday(date.year, date.monthValue, date.dayOfMonth)
        return if (holiday != null) {
            holiday.isWork
        } else {
            date.dayOfWeek.value in 1..5
        }
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    fun yearsPassed(triggerTime: Long, now: Long = System.currentTimeMillis()): Int {
        val a = Calendar.getInstance().apply { timeInMillis = triggerTime }
        val b = Calendar.getInstance().apply { timeInMillis = now }
        var years = b.get(Calendar.YEAR) - a.get(Calendar.YEAR)
        if (b.get(Calendar.DAY_OF_YEAR) < a.get(Calendar.DAY_OF_YEAR)) years--
        return years.coerceAtLeast(0)
    }

    fun daysUntil(target: Long, now: Long = System.currentTimeMillis()): Long {
        val diff = target - now
        return if (diff <= 0) 0 else (diff + ONE_DAY_MILLIS - 1) / ONE_DAY_MILLIS
    }

    private const val ONE_DAY_MILLIS = 86_400_000L
}
