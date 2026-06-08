package com.smartclock.util

import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.CalendarType
import com.smartclock.domain.model.ScheduleMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ScheduleUtilTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `once alarm returns trigger time when still in future`() {
        val from = millis(2026, 6, 5, 8, 0)
        val trigger = millis(2026, 6, 5, 9, 30)
        val alarm = alarm(type = AlarmType.ONCE, triggerTime = trigger)

        assertEquals(trigger, ScheduleUtil.nextTrigger(alarm, from))
    }

    @Test
    fun `once alarm returns null when already expired`() {
        val from = millis(2026, 6, 5, 10, 0)
        val trigger = millis(2026, 6, 5, 9, 30)
        val alarm = alarm(type = AlarmType.ONCE, triggerTime = trigger)

        assertNull(ScheduleUtil.nextTrigger(alarm, from))
    }

    @Test
    fun `weekly alarm returns same day when selected time is still ahead`() {
        val from = millis(2026, 6, 1, 8, 0)
        val trigger = millis(2026, 6, 1, 9, 30)
        val mondayBit = 1 shl 1
        val wednesdayBit = 1 shl 3
        val alarm = alarm(
            type = AlarmType.WEEKLY,
            triggerTime = trigger,
            repeatWeekdays = mondayBit or wednesdayBit
        )

        assertEquals(trigger, ScheduleUtil.nextTrigger(alarm, from))
    }

    @Test
    fun `weekly alarm skips to next selected weekday when today's time passed`() {
        val from = millis(2026, 6, 1, 10, 0)
        val trigger = millis(2026, 6, 1, 9, 30)
        val mondayBit = 1 shl 1
        val wednesdayBit = 1 shl 3
        val expected = millis(2026, 6, 3, 9, 30)
        val alarm = alarm(
            type = AlarmType.WEEKLY,
            triggerTime = trigger,
            repeatWeekdays = mondayBit or wednesdayBit
        )

        assertEquals(expected, ScheduleUtil.nextTrigger(alarm, from))
    }

    @Test
    fun `occurrences between expands weekly range`() {
        val start = millis(2026, 6, 1, 0, 0)
        val end = millis(2026, 6, 8, 0, 0)
        val mondayBit = 1 shl 1
        val wednesdayBit = 1 shl 3
        val fridayBit = 1 shl 5
        val alarm = alarm(
            type = AlarmType.WEEKLY,
            triggerTime = millis(2026, 6, 1, 9, 30),
            repeatWeekdays = mondayBit or wednesdayBit or fridayBit
        )

        assertEquals(
            listOf(
                millis(2026, 6, 1, 9, 30),
                millis(2026, 6, 3, 9, 30),
                millis(2026, 6, 5, 9, 30)
            ),
            ScheduleUtil.occurrencesBetween(alarm, start, end)
        )
    }

    @Test
    fun `workday mode fires on makeup Sunday`() {
        val from = millis(2024, 2, 18, 8, 0)
        val trigger = millis(2024, 2, 1, 9, 0)
        val alarm = alarm(
            type = AlarmType.WEEKLY,
            triggerTime = trigger,
            scheduleMode = ScheduleMode.WORKDAY_CN
        )

        assertEquals(millis(2024, 2, 18, 9, 0), ScheduleUtil.nextTrigger(alarm, from))
    }

    @Test
    fun `workday mode skips legal holiday weekday`() {
        val from = millis(2024, 10, 2, 8, 0)
        val trigger = millis(2024, 1, 1, 9, 0)
        val alarm = alarm(
            type = AlarmType.WEEKLY,
            triggerTime = trigger,
            scheduleMode = ScheduleMode.WORKDAY_CN
        )

        assertEquals(millis(2024, 10, 8, 9, 0), ScheduleUtil.nextTrigger(alarm, from))
    }

    @Test
    fun `occurrences between includes makeup sunday and following monday`() {
        val start = millis(2024, 2, 17, 0, 0)
        val end = millis(2024, 2, 20, 0, 0)
        val alarm = alarm(
            type = AlarmType.WEEKLY,
            triggerTime = millis(2024, 2, 1, 9, 0),
            scheduleMode = ScheduleMode.WORKDAY_CN
        )

        assertEquals(
            listOf(
                millis(2024, 2, 18, 9, 0),
                millis(2024, 2, 19, 9, 0)
            ),
            ScheduleUtil.occurrencesBetween(alarm, start, end)
        )
    }

    @Test
    fun `monthly alarm returns next selected day in current month`() {
        val from = millis(2026, 6, 5, 8, 0)
        val trigger = millis(2026, 6, 1, 7, 45)
        val alarm = alarm(
            type = AlarmType.MONTHLY,
            triggerTime = trigger,
            repeatMonthDays = (1 shl 10) or (1 shl 18)
        )

        assertEquals(millis(2026, 6, 10, 7, 45), ScheduleUtil.nextTrigger(alarm, from))
    }

    @Test
    fun `monthly interval skips off months`() {
        val from = millis(2026, 2, 1, 8, 0)
        val trigger = millis(2026, 1, 5, 7, 45)
        val alarm = alarm(
            type = AlarmType.MONTHLY,
            triggerTime = trigger,
            repeatMonthDays = 1 shl 5,
            intervalMonths = 2
        )

        assertEquals(millis(2026, 3, 5, 7, 45), ScheduleUtil.nextTrigger(alarm, from))
    }

    @Test
    fun `occurrences between keeps monthly interval cadence`() {
        val start = millis(2026, 1, 1, 0, 0)
        val end = millis(2026, 5, 1, 0, 0)
        val alarm = alarm(
            type = AlarmType.MONTHLY,
            triggerTime = millis(2026, 1, 5, 7, 45),
            repeatMonthDays = 1 shl 5,
            intervalMonths = 2
        )

        assertEquals(
            listOf(
                millis(2026, 1, 5, 7, 45),
                millis(2026, 3, 5, 7, 45)
            ),
            ScheduleUtil.occurrencesBetween(alarm, start, end)
        )
    }

    @Test
    fun `solar anniversary rolls to next interval year after this year's date has passed`() {
        val trigger = millis(2020, 5, 20, 8, 30)
        val from = millis(2026, 6, 5, 9, 0)
        val alarm = alarm(
            type = AlarmType.ANNIVERSARY,
            triggerTime = trigger,
            anniversaryCalendar = CalendarType.SOLAR,
            intervalYears = 2
        )

        assertEquals(millis(2028, 5, 20, 8, 30), ScheduleUtil.nextTrigger(alarm, from))
    }

    @Test
    fun `end date before from disables future trigger`() {
        val alarm = alarm(
            type = AlarmType.WEEKLY,
            triggerTime = millis(2026, 6, 1, 9, 0),
            repeatWeekdays = 1 shl 1,
            endDate = millis(2026, 6, 1, 0, 0)
        )

        assertNull(ScheduleUtil.nextTrigger(alarm, millis(2026, 6, 2, 8, 0)))
    }

    @Test
    fun `duration formatting pads to hh mm ss`() {
        assertEquals("01:01:01", TimeFormat.duration(3661))
    }

    @Test
    fun `weekdays text renders every day when all bits are selected`() {
        val allDays = (0..6).fold(0) { acc, bit -> acc or (1 shl bit) }
        assertEquals("每天", TimeFormat.weekdaysText(allDays))
    }

    @Test
    fun `month days text renders selected days`() {
        val bits = (1 shl 1) or (1 shl 15) or (1 shl 28)
        assertEquals("每月 1 15 28 号", TimeFormat.monthDaysText(bits))
    }

    @Test
    fun `workday subtitle uses legal workday wording`() {
        val alarm = alarm(
            type = AlarmType.WEEKLY,
            triggerTime = millis(2026, 6, 1, 9, 0),
            scheduleMode = ScheduleMode.WORKDAY_CN
        )

        assertEquals("法定工作日", TimeFormat.subtitle(alarm))
    }

    @Test
    fun `countdown subtitle includes formatted duration`() {
        val alarm = alarm(type = AlarmType.COUNTDOWN, durationSec = 90)
        assertEquals("倒计时 00:01:30", TimeFormat.subtitle(alarm))
    }

    private fun alarm(
        type: AlarmType,
        triggerTime: Long? = null,
        durationSec: Int? = null,
        repeatWeekdays: Int = 0,
        repeatMonthDays: Int = 0,
        anniversaryCalendar: CalendarType = CalendarType.SOLAR,
        endDate: Long? = null,
        scheduleMode: ScheduleMode = ScheduleMode.NORMAL,
        intervalMonths: Int = 1,
        intervalYears: Int = 1
    ): Alarm = Alarm(
        userId = 1L,
        type = type,
        title = "test",
        triggerTime = triggerTime,
        durationSec = durationSec,
        repeatWeekdays = repeatWeekdays,
        repeatMonthDays = repeatMonthDays,
        anniversaryCalendar = anniversaryCalendar,
        endDate = endDate,
        scheduleMode = scheduleMode,
        intervalMonths = intervalMonths,
        intervalYears = intervalYears
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
