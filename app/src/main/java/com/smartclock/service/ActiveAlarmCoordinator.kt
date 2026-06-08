package com.smartclock.service

import com.smartclock.data.local.CountdownRuntimeStore
import com.smartclock.data.repository.AlarmOccurrenceRepository
import com.smartclock.data.repository.AlarmRepository
import com.smartclock.data.sync.SyncStateStore
import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.CountdownStatus
import com.smartclock.domain.model.OccurrenceSource
import com.smartclock.util.ReminderScheduleResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveAlarmCoordinator @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val countdownStore: CountdownRuntimeStore,
    private val occurrenceRepository: AlarmOccurrenceRepository,
    private val syncStateStore: SyncStateStore
) {

    suspend fun restoreAllForUser(userId: Long) {
        val now = System.currentTimeMillis()
        expireStaleSnoozes(now)
        clearScheduledForUser(userId)
        alarmRepository.getAllAlarms(userId).forEach { alarm ->
            restoreAlarm(alarm, now)
        }
        syncStateStore.markFullRestore(now)
    }

    suspend fun restoreAffectedAlarms(userId: Long, alarmIds: Set<Long>) {
        if (alarmIds.isEmpty()) return
        val now = System.currentTimeMillis()
        expireStaleSnoozes(now)
        alarmIds.forEach { alarmId ->
            alarmScheduler.cancelScheduled(alarmId)
            val alarm = alarmRepository.getById(alarmId)
            if (alarm == null || alarm.userId != userId) {
                occurrenceRepository.cancelPendingForAlarm(alarmId)
                clearCountdownRuntime(alarmId)
                return@forEach
            }
            restoreAlarm(alarm, now)
        }
    }

    suspend fun clearForUser(userId: Long) {
        if (userId >= 0) {
            alarmRepository.getAllAlarms(userId).forEach { alarm ->
                alarmScheduler.cancel(alarm.id)
            }
        }
        alarmScheduler.stopCountdown()
        countdownStore.clear()
    }

    private suspend fun clearScheduledForUser(userId: Long) {
        if (userId >= 0) {
            alarmRepository.getAllAlarms(userId).forEach { alarm ->
                alarmScheduler.cancelScheduled(alarm.id)
            }
        }
        alarmScheduler.stopCountdown()
    }

    private suspend fun restoreAlarm(alarm: Alarm, now: Long) {
        if (!alarm.enabled) {
            occurrenceRepository.cancelPendingForAlarm(alarm.id)
            if (alarm.type == AlarmType.COUNTDOWN) {
                val runtime = countdownStore.current()
                val shouldKeepPausedState =
                    runtime?.alarmId == alarm.id &&
                        runtime.status == CountdownStatus.PAUSED &&
                        (alarm.triggerTime ?: 0L) > now
                if (!shouldKeepPausedState) {
                    clearCountdownRuntime(alarm.id)
                }
            }
            return
        }

        val normalized = when {
            alarm.type == AlarmType.COUNTDOWN && (alarm.triggerTime ?: 0L) <= now -> {
                occurrenceRepository.cancelPendingForAlarm(alarm.id)
                alarmRepository.setEnabled(alarm.id, false)
                clearCountdownRuntime(alarm.id)
                return
            }

            ReminderScheduleResolver.isOverrideStale(alarm, now) -> {
                alarmRepository.clearNextOverride(alarm.id)
                alarmRepository.getById(alarm.id) ?: alarm
            }

            else -> alarm
        }

        alarmScheduler.schedule(normalized)
        if (normalized.type == AlarmType.COUNTDOWN) {
            countdownStore.setRunning(
                alarmId = normalized.id,
                originalDurationSec = normalized.durationSec ?: 0,
                endAt = normalized.triggerTime ?: now
            )
        }
        occurrenceRepository.getPendingByAlarm(normalized.id)
            .filter { it.source == OccurrenceSource.SNOOZE }
            .forEach { snooze ->
                if ((snooze.expiresAt ?: Long.MAX_VALUE) < now) {
                    occurrenceRepository.markExpired(snooze.id)
                    alarmScheduler.cancelOccurrence(normalized.id, snooze)
                } else {
                    alarmScheduler.scheduleExistingSnooze(normalized.id, snooze)
                }
            }
    }

    private suspend fun expireStaleSnoozes(now: Long) {
        occurrenceRepository.expireStaleSnoozes(now).forEach { stale ->
            alarmScheduler.cancelOccurrence(stale.alarmId, stale)
        }
    }

    private suspend fun clearCountdownRuntime(alarmId: Long) {
        val runtime = countdownStore.current()
        if (runtime?.alarmId == alarmId && runtime.status == CountdownStatus.RUNNING) {
            alarmScheduler.stopCountdown()
        }
        countdownStore.clearIfMatches(alarmId)
    }
}
