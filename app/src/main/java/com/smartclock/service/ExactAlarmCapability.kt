package com.smartclock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class ScheduleOutcome {
    EXACT,
    INEXACT_FALLBACK
}

@Singleton
class ExactAlarmCapability @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager.canScheduleExactAlarms()
    }

    fun scheduleRtcWakeup(
        triggerAt: Long,
        operation: PendingIntent,
        showIntent: PendingIntent? = null
    ): ScheduleOutcome {
        if (canScheduleExactAlarms()) {
            if (showIntent != null) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
            return ScheduleOutcome.EXACT
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            return ScheduleOutcome.INEXACT_FALLBACK
        }
    }
}
