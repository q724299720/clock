package com.smartclock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.smartclock.R

class NextReminderWidgetProvider : AppWidgetProvider() {

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
            val componentName = ComponentName(context, NextReminderWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return
            ids.forEach { appWidgetId ->
                appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_next_reminder)
            val state = WidgetDataLoader.loadNextReminderState(context)
            views.setTextViewText(R.id.widget_time, state.timeText)
            views.setTextViewText(R.id.widget_title, state.titleText)
            views.setTextViewText(
                R.id.widget_toggle,
                if (state.alarmId == null) "Open" else if (state.enabled) "Disable" else "Enable"
            )
            val openPendingIntent = state.alarmId?.let {
                WidgetDataLoader.buildOpenEditPendingIntent(context, it)
            } ?: WidgetDataLoader.buildLaunchPendingIntent(context)
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_time, openPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_title, openPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_label, openPendingIntent)
            views.setOnClickPendingIntent(
                R.id.widget_toggle,
                state.alarmId?.let {
                    WidgetDataLoader.buildTogglePendingIntent(context, it, !state.enabled)
                } ?: WidgetDataLoader.buildLaunchPendingIntent(context)
            )
            return views
        }
    }
}
