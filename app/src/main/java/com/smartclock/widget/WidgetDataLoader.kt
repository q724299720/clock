package com.smartclock.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.smartclock.MainActivity
import com.smartclock.R
import com.smartclock.data.local.SessionStore
import com.smartclock.data.repository.AlarmRepository
import com.smartclock.util.ReminderScheduleResolver
import com.smartclock.util.TimeFormat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

data class NextReminderWidgetState(
    val alarmId: Long? = null,
    val timeText: String,
    val titleText: String,
    val enabled: Boolean = false
)

data class TodayWidgetLine(
    val alarmId: Long,
    val timeText: String,
    val titleText: String
)

object WidgetDataLoader {
    private val optimisticEnabled = ConcurrentHashMap<Long, Boolean>()

    fun setOptimisticEnabled(alarmId: Long, enabled: Boolean) {
        optimisticEnabled[alarmId] = enabled
    }

    fun clearOptimisticEnabled(alarmId: Long) {
        optimisticEnabled.remove(alarmId)
    }

    fun buildLaunchPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun buildOpenEditPendingIntent(context: Context, alarmId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun buildTogglePendingIntent(
        context: Context,
        alarmId: Long,
        enable: Boolean
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (alarmId.toInt() * 31) xor if (enable) 1 else 0,
            Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_TOGGLE_ENABLED
                putExtra(WidgetActionReceiver.EXTRA_ALARM_ID, alarmId)
                putExtra(WidgetActionReceiver.EXTRA_ENABLED, enable)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun loadNextReminderState(context: Context): NextReminderWidgetState = runBlocking {
        val next = loadAllScheduled(context).minByOrNull { it.triggerAt }
        if (next == null) {
            NextReminderWidgetState(
                timeText = context.getString(R.string.widget_no_reminder_time),
                titleText = context.getString(R.string.widget_no_reminder_title),
                enabled = false
            )
        } else {
            NextReminderWidgetState(
                alarmId = next.alarmId,
                timeText = TimeFormat.hhmm(next.triggerAt),
                titleText = next.title,
                enabled = next.enabled
            )
        }
    }

    fun loadTodayReminders(context: Context, limit: Int = 4): List<TodayWidgetLine> = runBlocking {
        val today = LocalDate.now()
        loadAllScheduled(context)
            .filter { Instant.ofEpochMilli(it.triggerAt).atZone(ZoneId.systemDefault()).toLocalDate() == today }
            .sortedBy { it.triggerAt }
            .take(limit)
            .map { TodayWidgetLine(it.alarmId, TimeFormat.hhmm(it.triggerAt), it.title) }
    }

    private suspend fun loadAllScheduled(context: Context): List<ScheduledReminder> {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val sessionStore = entryPoint.sessionStore()
        val alarmRepository = entryPoint.alarmRepository()
        val userId = sessionStore.userIdFlow.first()
        return alarmRepository.getAllAlarms(userId)
            .asSequence()
            .map { alarm ->
                val optimistic = optimisticEnabled[alarm.id]
                if (optimistic != null) {
                    alarm.copy(enabled = optimistic)
                } else {
                    alarm
                }
            }
            .filter { it.enabled }
            .mapNotNull { alarm ->
                ReminderScheduleResolver.nextTrigger(alarm)?.let { triggerAt ->
                    ScheduledReminder(
                        alarmId = alarm.id,
                        title = alarm.title.ifBlank { context.getString(R.string.widget_no_reminder_title) },
                        triggerAt = triggerAt,
                        enabled = alarm.enabled
                    )
                }
            }
            .toList()
    }
}

private data class ScheduledReminder(
    val alarmId: Long,
    val title: String,
    val triggerAt: Long,
    val enabled: Boolean
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun sessionStore(): SessionStore
    fun alarmRepository(): AlarmRepository
}
