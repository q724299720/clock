package com.smartclock.service

import com.smartclock.data.repository.AlarmLogRepository
import com.smartclock.data.repository.AlarmRepository
import com.smartclock.domain.model.AlarmLogAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmActionLogger @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmLogRepository: AlarmLogRepository
) {

    suspend fun recordDismiss(alarmId: Long) {
        record(alarmId, AlarmLogAction.DISMISS)
    }

    suspend fun recordSnooze(alarmId: Long) {
        record(alarmId, AlarmLogAction.SNOOZE)
    }

    private suspend fun record(alarmId: Long, action: AlarmLogAction) {
        val alarm = alarmRepository.getById(alarmId) ?: return
        alarmLogRepository.record(
            alarmId = alarm.id,
            userId = alarm.userId,
            action = action
        )
    }
}
