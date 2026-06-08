package com.smartclock.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.smartclock.MainActivity
import com.smartclock.R
import com.smartclock.SmartClockApp
import com.smartclock.data.local.CountdownRuntimeStore
import com.smartclock.data.repository.AlarmOccurrenceRepository
import com.smartclock.data.repository.AlarmRepository
import com.smartclock.domain.model.AlertPolicy
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.OccurrenceSource
import com.smartclock.domain.model.OccurrenceStatus
import com.smartclock.util.ReminderScheduleResolver
import com.smartclock.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var occurrenceRepository: AlarmOccurrenceRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var countdownStore: CountdownRuntimeStore
    @Inject lateinit var actionLogger: AlarmActionLogger

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_FIRE -> handleFire(context, intent)
                    ACTION_DISMISS_NOTIFICATION -> handleDismissNotification(context, intent)
                    ACTION_SNOOZE_NOTIFICATION -> handleSnoozeNotification(context, intent)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleFire(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        if (alarmId == 0L) return

        val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE_ID, 0L)
        val firedTriggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, 0L)
        val source = OccurrenceSource.fromCode(intent.getIntExtra(EXTRA_OCCURRENCE_SOURCE, 0))
        val originTriggerAt = intent.getLongExtra(EXTRA_ORIGIN_TRIGGER_AT, 0L)
        val occurrence = occurrenceRepository.get(occurrenceId)
        if (occurrenceId > 0L) {
            if (occurrence == null || occurrence.status != OccurrenceStatus.PENDING) return
            if (
                source == OccurrenceSource.SNOOZE &&
                occurrence.expiresAt != null &&
                occurrence.expiresAt < System.currentTimeMillis()
            ) {
                occurrenceRepository.markExpired(occurrence.id)
                scheduler.cancelOccurrence(alarmId, occurrence)
                return
            }
            occurrenceRepository.markConsumed(occurrence.id)
        }

        var alarm = repository.getById(alarmId) ?: return
        val title = alarm.title.takeIf { it.isNotBlank() } ?: DEFAULT_TITLE
        val effectiveFiredAt = occurrence?.originTriggerAt
            ?: originTriggerAt.takeIf { it > 0L }
            ?: firedTriggerAt.takeIf { it > 0L }
            ?: occurrence?.triggerAt
            ?: System.currentTimeMillis()

        if (ReminderScheduleResolver.shouldClearAfterTrigger(alarm, effectiveFiredAt)) {
            repository.clearNextOverride(alarm.id)
            alarm = repository.getById(alarm.id) ?: alarm
        }

        when (alarm.alertPolicy) {
            AlertPolicy.WAKE_ALARM -> {
                showWakeNotification(context, alarmId, title)
                AlarmAlertService.start(
                    context = context,
                    alarmId = alarmId,
                    title = title,
                    originTriggerAt = effectiveFiredAt
                )
            }
            AlertPolicy.QUIET_REMINDER -> {
                showHeadsUpNotification(
                    context = context,
                    alarmId = alarmId,
                    title = title,
                    originTriggerAt = effectiveFiredAt
                )
            }
        }

        if (alarm.enabled) {
            when (alarm.type) {
                AlarmType.ONCE -> repository.setEnabled(alarm.id, false)
                AlarmType.COUNTDOWN -> {
                    repository.setEnabled(alarm.id, false)
                    countdownStore.clearIfMatches(alarm.id)
                }
                else -> scheduler.schedule(alarm)
            }
        }
        WidgetUpdater.updateAll(context.applicationContext)
    }

    private suspend fun handleDismissNotification(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        if (alarmId == 0L) return
        actionLogger.recordDismiss(alarmId)
        dismissNotification(context, alarmId)
        WidgetUpdater.updateAll(context.applicationContext)
    }

    private suspend fun handleSnoozeNotification(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        val originTriggerAt = intent.getLongExtra(EXTRA_ORIGIN_TRIGGER_AT, 0L)
        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)
        if (alarmId == 0L || originTriggerAt <= 0L) return
        scheduler.scheduleSnooze(alarmId, originTriggerAt, minutes)
        actionLogger.recordSnooze(alarmId)
        dismissNotification(context, alarmId)
        WidgetUpdater.updateAll(context.applicationContext)
    }

    private fun showWakeNotification(context: Context, alarmId: Long, title: String) {
        val screenIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmAlertService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmAlertService.EXTRA_TITLE, title)
        }
        val screenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            screenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, SmartClockApp.CHANNEL_ALARM)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("闹钟时间到")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(screenPendingIntent)
            .setFullScreenIntent(screenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        notificationManager(context).notify(alarmId.toInt(), notification)
    }

    private fun showHeadsUpNotification(
        context: Context,
        alarmId: Long,
        title: String,
        originTriggerAt: Long
    ) {
        val contentIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val dismissIntent = buildActionPendingIntent(
            context = context,
            action = ACTION_DISMISS_NOTIFICATION,
            requestCode = alarmId.toInt() + 10,
            alarmId = alarmId,
            originTriggerAt = originTriggerAt
        )
        val snoozeIntent = buildActionPendingIntent(
            context = context,
            action = ACTION_SNOOZE_NOTIFICATION,
            requestCode = alarmId.toInt() + 20,
            alarmId = alarmId,
            originTriggerAt = originTriggerAt,
            minutes = DEFAULT_SNOOZE_MINUTES
        )

        val notification = NotificationCompat.Builder(context, SmartClockApp.CHANNEL_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("生活提醒")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(contentIntent)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", dismissIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "稍后 5 分钟", snoozeIntent)
            .build()

        notificationManager(context).notify(alarmId.toInt(), notification)
    }

    private fun buildActionPendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
        alarmId: Long,
        originTriggerAt: Long,
        minutes: Int? = null
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ORIGIN_TRIGGER_AT, originTriggerAt)
            minutes?.let { putExtra(EXTRA_SNOOZE_MINUTES, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun dismissNotification(context: Context, alarmId: Long) {
        notificationManager(context).cancel(alarmId.toInt())
    }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_FIRE = "com.smartclock.action.ALARM_FIRE"
        const val ACTION_DISMISS_NOTIFICATION = "com.smartclock.action.REMINDER_DISMISS"
        const val ACTION_SNOOZE_NOTIFICATION = "com.smartclock.action.REMINDER_SNOOZE"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_OCCURRENCE_SOURCE = "occurrence_source"
        const val EXTRA_ORIGIN_TRIGGER_AT = "origin_trigger_at"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

        private const val DEFAULT_TITLE = "闹钟"
        private const val DEFAULT_SNOOZE_MINUTES = 5
    }
}
