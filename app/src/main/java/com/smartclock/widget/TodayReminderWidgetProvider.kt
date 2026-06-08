package com.smartclock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.smartclock.R

class TodayReminderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TodayReminderWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return
            ids.forEach { appWidgetId ->
                appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today_reminders)
            val lines = WidgetDataLoader.loadTodayReminders(context)
            val pendingIntent = WidgetDataLoader.buildLaunchPendingIntent(context)
            val lineIds = listOf(
                R.id.widget_today_line1,
                R.id.widget_today_line2,
                R.id.widget_today_line3,
                R.id.widget_today_line4
            )

            views.setOnClickPendingIntent(R.id.widget_today_label, pendingIntent)
            lineIds.forEach { id -> views.setOnClickPendingIntent(id, pendingIntent) }

            if (lines.isEmpty()) {
                views.setTextViewText(R.id.widget_today_line1, context.getString(R.string.widget_today_reminders_empty))
                views.setTextViewText(R.id.widget_today_line2, "")
                views.setTextViewText(R.id.widget_today_line3, "")
                views.setTextViewText(R.id.widget_today_line4, "")
            } else {
                lineIds.forEachIndexed { index, id ->
                    val line = lines.getOrNull(index)
                    views.setTextViewText(
                        id,
                        line?.let { "${it.timeText}  ${it.titleText}" } ?: ""
                    )
                    if (line != null) {
                        views.setOnClickPendingIntent(
                            id,
                            WidgetDataLoader.buildOpenEditPendingIntent(context, line.alarmId)
                        )
                    } else {
                        views.setOnClickPendingIntent(id, pendingIntent)
                    }
                }
            }
            return views
        }
    }
}
