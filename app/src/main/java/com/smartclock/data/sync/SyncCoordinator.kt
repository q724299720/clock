package com.smartclock.data.sync

import android.util.Log
import com.smartclock.data.local.SessionStore
import com.smartclock.data.repository.AlarmLogRepository
import com.smartclock.data.repository.AlarmRepository
import com.smartclock.service.ExactAlarmCapability
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class SyncCoordinator @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmLogRepository: AlarmLogRepository,
    private val session: SessionStore,
    private val syncStateStore: SyncStateStore,
    private val exactAlarmCapability: ExactAlarmCapability
) {
    suspend fun syncNow(triggerSource: SyncTriggerSource = SyncTriggerSource.WORKER): SyncReport? {
        val userId = session.userIdFlow.first()
        if (userId <= 0L) return null
        val attemptedAt = System.currentTimeMillis()

        var pushedAlarmCount = 0
        runCatching { alarmRepository.pushPending(userId) }
            .onSuccess { pushedAlarmCount = it }
            .onFailure { Log.w("SyncCoordinator", "push pending alarms failed", it) }

        val pulledAlarmCount = alarmRepository.pullRemote(userId)

        var pushedLogCount = 0
        var errorMessage: String? = null
        runCatching { alarmLogRepository.pushPending(userId) }
            .onSuccess { pushedLogCount = it }
            .onFailure {
                errorMessage = it.message ?: "push pending alarm logs failed"
                Log.w("SyncCoordinator", "push pending alarm logs failed", it)
            }

        val report = SyncReport(
            userId = userId,
            triggerSource = triggerSource,
            lastAttemptAt = attemptedAt,
            lastSuccessAt = System.currentTimeMillis(),
            lastErrorMessage = errorMessage,
            pushedAlarmCount = pushedAlarmCount,
            pulledAlarmCount = pulledAlarmCount,
            pushedLogCount = pushedLogCount,
            pendingAlarmCount = alarmRepository.countPendingUpserts(userId),
            pendingDeleteCount = alarmRepository.countPendingDeletes(userId),
            pendingLogCount = alarmLogRepository.countPending(userId),
            exactAlarmDegraded = !exactAlarmCapability.canScheduleExactAlarms()
        )
        syncStateStore.record(report)
        return report
    }

    suspend fun recordFailure(triggerSource: SyncTriggerSource, throwable: Throwable) {
        val userId = session.userIdFlow.first().takeIf { it > 0L } ?: 0L
        syncStateStore.record(
            SyncReport(
                userId = userId,
                triggerSource = triggerSource,
                lastAttemptAt = System.currentTimeMillis(),
                lastSuccessAt = null,
                lastErrorMessage = throwable.message ?: "同步失败",
                pushedAlarmCount = 0,
                pulledAlarmCount = 0,
                pushedLogCount = 0,
                pendingAlarmCount = if (userId > 0L) alarmRepository.countPendingUpserts(userId) else 0,
                pendingDeleteCount = if (userId > 0L) alarmRepository.countPendingDeletes(userId) else 0,
                pendingLogCount = if (userId > 0L) alarmLogRepository.countPending(userId) else 0,
                exactAlarmDegraded = !exactAlarmCapability.canScheduleExactAlarms()
            )
        )
    }
}
