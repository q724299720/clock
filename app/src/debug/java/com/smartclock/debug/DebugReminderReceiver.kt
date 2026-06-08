package com.smartclock.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smartclock.data.local.SessionStore
import com.smartclock.data.repository.AlarmRepository
import com.smartclock.domain.model.AlertPolicy
import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmType
import com.smartclock.service.AlarmReceiver
import com.smartclock.service.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DebugReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "received action=${intent.action}")
                val userId = sessionStore.userIdFlow.first()
                val requestedAlarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
                val quiet = intent.getBooleanExtra(EXTRA_QUIET, false)
                val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
                Log.i(
                    TAG,
                    "resolved session userId=$userId requestedAlarmId=$requestedAlarmId quiet=$quiet title=$title"
                )

                when (intent.action) {
                    ACTION_SCHEDULE -> {
                        val delayMinutes = intent.getIntExtra(EXTRA_DELAY_MINUTES, DEFAULT_DELAY_MINUTES)
                        Log.i(TAG, "scheduling debug alarm delayMinutes=$delayMinutes")
                        scheduleDebugAlarm(
                            userId = userId,
                            quiet = quiet,
                            title = title ?: "3-minute notification test",
                            delayMinutes = delayMinutes.coerceAtLeast(1)
                        )
                    }

                    else -> {
                        val alarm = resolveAlarm(
                            userId = userId,
                            requestedAlarmId = requestedAlarmId,
                            quiet = quiet,
                            title = title
                        )
                        Log.i(TAG, "triggering debug reminder alarmId=${alarm.id} title=${alarm.title}")

                        context.sendBroadcast(
                            Intent(context, AlarmReceiver::class.java).apply {
                                action = AlarmReceiver.ACTION_FIRE
                                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
                                putExtra(AlarmReceiver.EXTRA_ORIGIN_TRIGGER_AT, System.currentTimeMillis())
                            }
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "debug reminder action failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun resolveAlarm(
        userId: Long,
        requestedAlarmId: Long,
        quiet: Boolean,
        title: String?
    ): Alarm {
        if (requestedAlarmId > 0L) {
            repository.getById(requestedAlarmId)?.let { return it }
        }

        if (title != null) {
            return createDebugAlarm(userId, quiet, title)
        }

        val preferredPolicy = if (quiet) AlertPolicy.QUIET_REMINDER else AlertPolicy.WAKE_ALARM
        val enabledAlarms = repository.getEnabledAlarms(userId)
        enabledAlarms.firstOrNull { it.alertPolicy == preferredPolicy }?.let { return it }
        enabledAlarms.firstOrNull()?.let { return it }

        val allAlarms = repository.getAllAlarms(userId)
        allAlarms.firstOrNull { it.alertPolicy == preferredPolicy }?.let { return it }
        allAlarms.firstOrNull()?.let { return it }

        return createDebugAlarm(
            userId = userId,
            quiet = quiet,
            title = if (quiet) "debug quiet reminder" else "debug alarm"
        )
    }

    private suspend fun createDebugAlarm(
        userId: Long,
        quiet: Boolean,
        title: String
    ): Alarm {
        val debugAlarm = Alarm(
            userId = userId,
            type = AlarmType.ONCE,
            title = title,
            triggerTime = System.currentTimeMillis(),
            enabled = false,
            alertPolicy = if (quiet) AlertPolicy.QUIET_REMINDER else AlertPolicy.WAKE_ALARM
        )
        val savedId = repository.save(debugAlarm)
        Log.i(TAG, "created debug alarm savedId=$savedId title=$title quiet=$quiet")
        return repository.getById(savedId) ?: debugAlarm.copy(id = savedId)
    }

    private suspend fun scheduleDebugAlarm(
        userId: Long,
        quiet: Boolean,
        title: String,
        delayMinutes: Int
    ): Alarm {
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        val scheduledAlarm = Alarm(
            userId = userId,
            type = AlarmType.ONCE,
            title = title,
            triggerTime = triggerAt,
            enabled = true,
            alertPolicy = if (quiet) AlertPolicy.QUIET_REMINDER else AlertPolicy.WAKE_ALARM
        )
        val savedId = repository.save(scheduledAlarm)
        val saved = repository.getById(savedId) ?: scheduledAlarm.copy(id = savedId)
        Log.i(
            TAG,
            "scheduled debug alarm savedId=$savedId triggerAt=${saved.triggerTime} enabled=${saved.enabled} userId=${saved.userId}"
        )
        scheduler.schedule(saved)
        Log.i(TAG, "scheduler.schedule finished alarmId=${saved.id}")
        return saved
    }

    companion object {
        private const val TAG = "DebugReminderReceiver"
        const val ACTION_TRIGGER = "com.smartclock.debug.TRIGGER_REMINDER"
        const val ACTION_SCHEDULE = "com.smartclock.debug.SCHEDULE_REMINDER"
        const val EXTRA_ALARM_ID = "debug_alarm_id"
        const val EXTRA_QUIET = "debug_quiet"
        const val EXTRA_TITLE = "debug_title"
        const val EXTRA_DELAY_MINUTES = "debug_delay_minutes"
        private const val DEFAULT_DELAY_MINUTES = 3
    }
}
