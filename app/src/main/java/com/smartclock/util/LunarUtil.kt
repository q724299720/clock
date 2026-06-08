package com.smartclock.util

import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.util.Calendar

/**
 * 农历换算，基于 cn.6tail:lunar 库（离线）。
 */
object LunarUtil {

    /** 公历 epoch → 农历显示文案，如 "农历五月初五" */
    fun lunarText(epoch: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = epoch }
        val solar = Solar.fromYmd(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
        val lunar = solar.lunar
        return "农历" + lunar.monthInChinese + "月" + lunar.dayInChinese
    }

    /**
     * 计算下一个农历纪念日对应的公历 epoch。
     * triggerTime 为首发公历日期，取其农历月日，找 from 之后最近一次该农历月日的公历时刻。
     */
    fun nextLunarAnniversary(triggerTime: Long, from: Long, hour: Int, minute: Int): Long? {
        val src = Calendar.getInstance().apply { timeInMillis = triggerTime }
        val srcLunar = Solar.fromYmd(
            src.get(Calendar.YEAR),
            src.get(Calendar.MONTH) + 1,
            src.get(Calendar.DAY_OF_MONTH)
        ).lunar
        val lunarMonth = srcLunar.month
        val lunarDay = srcLunar.day

        val fromCal = Calendar.getInstance().apply { timeInMillis = from }
        val startLunarYear = Solar.fromYmd(
            fromCal.get(Calendar.YEAR),
            fromCal.get(Calendar.MONTH) + 1,
            fromCal.get(Calendar.DAY_OF_MONTH)
        ).lunar.year

        // 向后查 3 个农历年，覆盖闰月等边界
        for (y in startLunarYear..(startLunarYear + 2)) {
            val candidate = runCatching {
                Lunar.fromYmd(y, lunarMonth, lunarDay).solar
            }.getOrNull() ?: continue
            val cal = Calendar.getInstance().apply {
                set(candidate.year, candidate.month - 1, candidate.day, hour, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis > from) return cal.timeInMillis
        }
        return null
    }
}
