package com.smartclock.widget

import android.content.Context

object WidgetUpdater {
    fun updateAll(context: Context) {
        NextReminderWidgetProvider.updateAll(context)
        TodayReminderWidgetProvider.updateAll(context)
    }
}
