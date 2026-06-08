package com.smartclock.util

import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.CalendarType
import com.smartclock.domain.model.ScheduleMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormat {

    private val hm = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val ymdHm = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val md = SimpleDateFormat("MM-dd", Locale.getDefault())

    fun hhmm(epoch: Long?): String = if (epoch == null) "--:--" else hm.format(Date(epoch))

    fun full(epoch: Long?): String = if (epoch == null) "--" else ymdHm.format(Date(epoch))

    fun duration(totalSec: Int): String {
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private val weekNames = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    fun weekdaysText(bits: Int): String {
        if (bits == 0) return ""
        val all = (0..6).all { (bits and (1 shl it)) != 0 }
        if (all) return "每天"
        return (0..6)
            .filter { (bits and (1 shl it)) != 0 }
            .joinToString(" ") { weekNames[it] }
    }

    fun monthDaysText(bits: Int, intervalMonths: Int = 1): String {
        val days = (1..31).filter { (bits and (1 shl it)) != 0 }
        if (days.isEmpty()) return ""
        val prefix = if (intervalMonths > 1) "每 $intervalMonths 个月" else "每月"
        return "$prefix ${days.joinToString(" ")} 号"
    }

    fun subtitle(alarm: Alarm): String = when (alarm.type) {
        AlarmType.ONCE -> full(alarm.triggerTime)
        AlarmType.COUNTDOWN -> "倒计时 ${duration(alarm.durationSec ?: 0)}"
        AlarmType.WEEKLY -> {
            if (alarm.scheduleMode == ScheduleMode.WORKDAY_CN) {
                "法定工作日"
            } else {
                weekdaysText(alarm.repeatWeekdays)
            }
        }

        AlarmType.MONTHLY -> monthDaysText(alarm.repeatMonthDays, alarm.intervalMonths)
        AlarmType.ANNIVERSARY -> {
            val prefix = when (alarm.anniversaryCalendar) {
                CalendarType.LUNAR -> "农历"
                CalendarType.SOLAR -> "公历"
            }
            val interval = if (alarm.intervalYears > 1) "每 ${alarm.intervalYears} 年" else "每年"
            "$interval $prefix ${md.format(Date(alarm.triggerTime ?: 0L))}"
        }
    }
}
