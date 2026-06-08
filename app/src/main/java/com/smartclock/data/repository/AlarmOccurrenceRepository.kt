package com.smartclock.data.repository

import com.smartclock.data.local.AlarmOccurrenceDao
import com.smartclock.data.local.toDomain
import com.smartclock.data.local.toEntity
import com.smartclock.domain.model.AlarmOccurrence
import com.smartclock.domain.model.OccurrenceSource
import com.smartclock.domain.model.OccurrenceStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmOccurrenceRepository @Inject constructor(
    private val dao: AlarmOccurrenceDao
) {

    suspend fun upsertPrimary(alarmId: Long, triggerAt: Long): AlarmOccurrence {
        val now = System.currentTimeMillis()
        dao.updatePendingForAlarm(
            alarmId = alarmId,
            source = OccurrenceSource.PRIMARY.code,
            status = OccurrenceStatus.CANCELED.code,
            pendingStatus = OccurrenceStatus.PENDING.code,
            updatedAt = now
        )
        val occurrence = AlarmOccurrence(
            alarmId = alarmId,
            triggerAt = triggerAt,
            originTriggerAt = triggerAt,
            source = OccurrenceSource.PRIMARY,
            status = OccurrenceStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        return occurrence.copy(id = dao.insert(occurrence.toEntity()))
    }

    suspend fun createSnooze(
        alarmId: Long,
        originTriggerAt: Long,
        minutes: Int,
        now: Long = System.currentTimeMillis()
    ): AlarmOccurrence? {
        val normalizedMinutes = minutes.coerceAtLeast(1)
        val snoozeCount = (dao.maxSnoozeCount(
            alarmId = alarmId,
            originTriggerAt = originTriggerAt,
            source = OccurrenceSource.SNOOZE.code
        ) ?: 0) + 1
        val totalMinutes = dao.totalSnoozeMinutes(
            alarmId = alarmId,
            originTriggerAt = originTriggerAt,
            source = OccurrenceSource.SNOOZE.code
        ) + normalizedMinutes
        if (snoozeCount > MAX_SNOOZE_COUNT || totalMinutes > MAX_TOTAL_SNOOZE_MINUTES) {
            return null
        }

        dao.updatePendingForAlarm(
            alarmId = alarmId,
            source = OccurrenceSource.SNOOZE.code,
            status = OccurrenceStatus.CANCELED.code,
            pendingStatus = OccurrenceStatus.PENDING.code,
            updatedAt = now
        )

        val triggerAt = now + normalizedMinutes * 60_000L
        val occurrence = AlarmOccurrence(
            alarmId = alarmId,
            triggerAt = triggerAt,
            originTriggerAt = originTriggerAt,
            source = OccurrenceSource.SNOOZE,
            status = OccurrenceStatus.PENDING,
            snoozeCount = snoozeCount,
            snoozeMinutes = normalizedMinutes,
            expiresAt = triggerAt + SNOOZE_STALE_WINDOW_MILLIS,
            createdAt = now,
            updatedAt = now
        )
        return occurrence.copy(id = dao.insert(occurrence.toEntity()))
    }

    suspend fun get(id: Long): AlarmOccurrence? = dao.getById(id)?.toDomain()

    suspend fun getPendingByAlarm(alarmId: Long): List<AlarmOccurrence> =
        dao.getByAlarmStatusAnySource(alarmId, OccurrenceStatus.PENDING.code).map { it.toDomain() }

    suspend fun getPendingSnoozes(): List<AlarmOccurrence> =
        dao.getBySourceAndStatus(
            OccurrenceSource.SNOOZE.code,
            OccurrenceStatus.PENDING.code
        ).map { it.toDomain() }

    suspend fun markConsumed(id: Long) = markStatus(id, OccurrenceStatus.CONSUMED)

    suspend fun markExpired(id: Long) = markStatus(id, OccurrenceStatus.EXPIRED)

    suspend fun markCanceled(id: Long) = markStatus(id, OccurrenceStatus.CANCELED)

    suspend fun cancelPendingForAlarm(alarmId: Long) {
        dao.updatePendingForAlarmAnySource(
            alarmId = alarmId,
            status = OccurrenceStatus.CANCELED.code,
            pendingStatus = OccurrenceStatus.PENDING.code,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun expireStaleSnoozes(now: Long = System.currentTimeMillis()): List<AlarmOccurrence> {
        val stale = getPendingSnoozes().filter { it.expiresAt != null && it.expiresAt < now }
        dao.expirePendingSnoozes(
            source = OccurrenceSource.SNOOZE.code,
            pendingStatus = OccurrenceStatus.PENDING.code,
            expiredStatus = OccurrenceStatus.EXPIRED.code,
            cutoff = now,
            updatedAt = now
        )
        return stale
    }

    private suspend fun markStatus(id: Long, status: OccurrenceStatus) {
        dao.updateStatus(id, status.code, System.currentTimeMillis())
    }

    companion object {
        const val MAX_SNOOZE_COUNT = 5
        const val MAX_TOTAL_SNOOZE_MINUTES = 60
        const val SNOOZE_STALE_WINDOW_MILLIS = 2 * 60 * 60 * 1000L
    }
}
