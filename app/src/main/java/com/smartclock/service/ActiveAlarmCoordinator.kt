package com.smartclock.service

import com.smartclock.data.local.CountdownRuntimeStore
import com.smartclock.data.repository.AlarmOccurrenceRepository
import com.smartclock.data.repository.AlarmRepository
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.OccurrenceSource
import com.smartclock.util.ReminderScheduleResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveAlarmCoordinator @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val countdownStore: CountdownRuntimeStore,
    private val occurrenceRepository: AlarmOccurrenceRepository
) {

    suspend fun restoreForUser(userId: Long) {
        val now = System.currentTimeMillis()
        occurrenceRepository.expireStaleSnoozes(now).forEach { stale ->
            alarmScheduler.cancelOccurrence(stale.alarmId, stale)
        }
        val allAlarms = alarmRepository.getAllAlarms(userId)
        allAlarms.filter { it.enabled }.forEach { alarm ->
            if (alarm.type == AlarmType.COUNTDOWN && (alarm.triggerTime ?: 0L) <= now) {
                alarmRepository.setEnabled(alarm.id, false)
                countdownStore.clearIfMatches(alarm.id)
            } else if (ReminderScheduleResolver.isOverrideStale(alarm, now)) {
                alarmRepository.clearNextOverride(alarm.id)
                alarmRepository.getById(alarm.id)?.let { alarmScheduler.schedule(it) }
            } else {
                alarmScheduler.schedule(alarm)
            }
        }
        allAlarms.forEach { alarm ->
            occurrenceRepository.getPendingByAlarm(alarm.id)
                .filter { it.source == OccurrenceSource.SNOOZE }
                .forEach { snooze ->
                    if ((snooze.expiresAt ?: Long.MAX_VALUE) < now) {
                        occurrenceRepository.markExpired(snooze.id)
                        alarmScheduler.cancelOccurrence(alarm.id, snooze)
                    } else {
                        alarmScheduler.scheduleExistingSnooze(alarm.id, snooze)
                    }
                }
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
}
