package com.smartclock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smartclock.data.repository.AlarmOccurrenceRepository
import com.smartclock.data.sync.SyncStateStore
import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmOccurrence
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.AlertPolicy
import com.smartclock.domain.model.OccurrenceSource
import com.smartclock.util.ReminderScheduleResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exactAlarmCapability: ExactAlarmCapability,
    private val occurrenceRepository: AlarmOccurrenceRepository,
    private val syncStateStore: SyncStateStore
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun schedule(alarm: Alarm) {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return
        }

        if (alarm.type == AlarmType.COUNTDOWN) {
            scheduleCountdown(alarm)
            return
        }

        val triggerAt = ReminderScheduleResolver.nextTrigger(alarm) ?: return
        val occurrence = occurrenceRepository.upsertPrimary(alarm.id, triggerAt)
        val broadcast = buildBroadcastPendingIntent(alarm.id, occurrence)
        val showIntent = if (alarm.alertPolicy == AlertPolicy.WAKE_ALARM) {
            buildScreenPendingIntent(alarm.id, alarm.title)
        } else {
            null
        }
        exactAlarmCapability.scheduleRtcWakeup(triggerAt, broadcast, showIntent)
        syncStateStore.setExactAlarmDegraded(!exactAlarmCapability.canScheduleExactAlarms())
    }

    suspend fun scheduleSnooze(
        alarmId: Long,
        originTriggerAt: Long,
        minutes: Int
    ): AlarmOccurrence? {
        val occurrence = occurrenceRepository.createSnooze(alarmId, originTriggerAt, minutes)
            ?: return null
        val broadcast = buildBroadcastPendingIntent(alarmId, occurrence)
        exactAlarmCapability.scheduleRtcWakeup(occurrence.triggerAt, broadcast)
        syncStateStore.setExactAlarmDegraded(!exactAlarmCapability.canScheduleExactAlarms())
        return occurrence
    }

    suspend fun scheduleExistingSnooze(alarmId: Long, occurrence: AlarmOccurrence) {
        val broadcast = buildBroadcastPendingIntent(alarmId, occurrence)
        exactAlarmCapability.scheduleRtcWakeup(occurrence.triggerAt, broadcast)
        syncStateStore.setExactAlarmDegraded(!exactAlarmCapability.canScheduleExactAlarms())
    }

    suspend fun cancel(alarmId: Long) {
        alarmManager.cancel(buildPrimaryPendingIntent(alarmId))
        occurrenceRepository.getPendingByAlarm(alarmId).forEach { occurrence ->
            alarmManager.cancel(buildBroadcastPendingIntent(alarmId, occurrence))
        }
        occurrenceRepository.cancelPendingForAlarm(alarmId)
    }

    fun cancelOccurrence(alarmId: Long, occurrence: AlarmOccurrence) {
        alarmManager.cancel(buildBroadcastPendingIntent(alarmId, occurrence))
    }

    fun stopCountdown() {
        context.stopService(Intent(context, CountdownService::class.java))
    }

    private suspend fun scheduleCountdown(alarm: Alarm) {
        val now = System.currentTimeMillis()
        val durationSec = alarm.durationSec ?: return
        val endAt = alarm.triggerTime ?: (now + durationSec * 1000L)
        if (endAt <= now) return

        val occurrence = occurrenceRepository.upsertPrimary(alarm.id, endAt)
        val broadcast = buildBroadcastPendingIntent(alarm.id, occurrence)
        val showIntent = buildScreenPendingIntent(alarm.id, alarm.title)
        exactAlarmCapability.scheduleRtcWakeup(endAt, broadcast, showIntent)
        syncStateStore.setExactAlarmDegraded(!exactAlarmCapability.canScheduleExactAlarms())

        val intent = Intent(context, CountdownService::class.java).apply {
            putExtra(CountdownService.EXTRA_ALARM_ID, alarm.id)
            putExtra(CountdownService.EXTRA_END_AT, endAt)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            context.startService(intent)
        }
    }

    private fun buildPrimaryPendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildBroadcastPendingIntent(
        alarmId: Long,
        occurrence: AlarmOccurrence
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, occurrence.triggerAt)
            putExtra(AlarmReceiver.EXTRA_OCCURRENCE_ID, occurrence.id)
            putExtra(AlarmReceiver.EXTRA_OCCURRENCE_SOURCE, occurrence.source.code)
            putExtra(AlarmReceiver.EXTRA_ORIGIN_TRIGGER_AT, occurrence.originTriggerAt)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(occurrence),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildScreenPendingIntent(alarmId: Long, title: String): PendingIntent {
        val intent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmAlertService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmAlertService.EXTRA_TITLE, title)
        }
        return PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun requestCodeFor(occurrence: AlarmOccurrence): Int =
        if (occurrence.source == OccurrenceSource.PRIMARY) {
            occurrence.alarmId.toInt()
        } else {
            occurrence.id.hashCode() xor SNOOZE_REQUEST_CODE_MASK
        }

    companion object {
        private const val SNOOZE_REQUEST_CODE_MASK = 0x40000000
    }
}
