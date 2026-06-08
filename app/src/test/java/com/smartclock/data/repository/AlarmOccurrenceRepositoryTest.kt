package com.smartclock.data.repository

import com.smartclock.data.local.AlarmOccurrenceDao
import com.smartclock.data.local.AlarmOccurrenceEntity
import com.smartclock.domain.model.OccurrenceSource
import com.smartclock.domain.model.OccurrenceStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmOccurrenceRepositoryTest {

    @Test
    fun `snooze caps at five attempts`() = runTest {
        val repo = AlarmOccurrenceRepository(FakeAlarmOccurrenceDao())

        repeat(5) { index ->
            val occurrence = repo.createSnooze(
                alarmId = 1L,
                originTriggerAt = 100L,
                minutes = 5,
                now = (index + 1) * 1_000L
            )
            assertNotNull(occurrence)
        }

        val sixth = repo.createSnooze(
            alarmId = 1L,
            originTriggerAt = 100L,
            minutes = 5,
            now = 6_000L
        )

        assertNull(sixth)
    }

    @Test
    fun `snooze caps at sixty total minutes`() = runTest {
        val repo = AlarmOccurrenceRepository(FakeAlarmOccurrenceDao())

        assertNotNull(repo.createSnooze(1L, 100L, 30, now = 1_000L))
        assertNotNull(repo.createSnooze(1L, 100L, 30, now = 2_000L))
        assertNull(repo.createSnooze(1L, 100L, 5, now = 3_000L))
    }

    @Test
    fun `expire stale snoozes marks only overdue pending occurrences`() = runTest {
        val dao = FakeAlarmOccurrenceDao().apply {
            insert(
                AlarmOccurrenceEntity(
                    alarmId = 1L,
                    triggerAt = 1_000L,
                    originTriggerAt = 500L,
                    source = OccurrenceSource.SNOOZE.code,
                    status = OccurrenceStatus.PENDING.code,
                    snoozeCount = 1,
                    snoozeMinutes = 5,
                    expiresAt = 4_000L,
                    createdAt = 0L,
                    updatedAt = 0L
                )
            )
            insert(
                AlarmOccurrenceEntity(
                    alarmId = 1L,
                    triggerAt = 5_000L,
                    originTriggerAt = 500L,
                    source = OccurrenceSource.SNOOZE.code,
                    status = OccurrenceStatus.PENDING.code,
                    snoozeCount = 2,
                    snoozeMinutes = 5,
                    expiresAt = 10_000L,
                    createdAt = 0L,
                    updatedAt = 0L
                )
            )
        }
        val repo = AlarmOccurrenceRepository(dao)

        val expired = repo.expireStaleSnoozes(now = 8_000L)

        assertEquals(1, expired.size)
        assertEquals(OccurrenceStatus.EXPIRED.code, dao.getById(1L)?.status)
        assertEquals(OccurrenceStatus.PENDING.code, dao.getById(2L)?.status)
    }

    @Test
    fun `cancel pending for alarm marks primary and snooze occurrences canceled`() = runTest {
        val dao = FakeAlarmOccurrenceDao().apply {
            insert(
                AlarmOccurrenceEntity(
                    alarmId = 7L,
                    triggerAt = 1_000L,
                    originTriggerAt = 1_000L,
                    source = OccurrenceSource.PRIMARY.code,
                    status = OccurrenceStatus.PENDING.code,
                    snoozeCount = 0,
                    snoozeMinutes = 0,
                    expiresAt = null,
                    createdAt = 0L,
                    updatedAt = 0L
                )
            )
            insert(
                AlarmOccurrenceEntity(
                    alarmId = 7L,
                    triggerAt = 2_000L,
                    originTriggerAt = 1_000L,
                    source = OccurrenceSource.SNOOZE.code,
                    status = OccurrenceStatus.PENDING.code,
                    snoozeCount = 1,
                    snoozeMinutes = 5,
                    expiresAt = 7_000L,
                    createdAt = 0L,
                    updatedAt = 0L
                )
            )
        }
        val repo = AlarmOccurrenceRepository(dao)

        repo.cancelPendingForAlarm(7L)

        assertTrue(dao.getByAlarmStatusAnySource(7L, OccurrenceStatus.CANCELED.code).size >= 2)
    }

    private class FakeAlarmOccurrenceDao : AlarmOccurrenceDao {
        private val entities = linkedMapOf<Long, AlarmOccurrenceEntity>()
        private var nextId = 1L

        override suspend fun insert(entity: AlarmOccurrenceEntity): Long {
            val id = if (entity.id == 0L) nextId++ else entity.id
            entities[id] = entity.copy(id = id)
            return id
        }

        override suspend fun getById(id: Long): AlarmOccurrenceEntity? = entities[id]

        override suspend fun getByAlarmAndStatus(
            alarmId: Long,
            source: Int,
            status: Int
        ): List<AlarmOccurrenceEntity> =
            entities.values
                .filter { it.alarmId == alarmId && it.source == source && it.status == status }
                .sortedBy { it.triggerAt }

        override suspend fun getByAlarmStatusAnySource(
            alarmId: Long,
            status: Int
        ): List<AlarmOccurrenceEntity> =
            entities.values
                .filter { it.alarmId == alarmId && it.status == status }
                .sortedBy { it.triggerAt }

        override suspend fun getBySourceAndStatus(
            source: Int,
            status: Int
        ): List<AlarmOccurrenceEntity> =
            entities.values
                .filter { it.source == source && it.status == status }
                .sortedBy { it.triggerAt }

        override suspend fun maxSnoozeCount(
            alarmId: Long,
            originTriggerAt: Long,
            source: Int
        ): Int? = entities.values
            .filter {
                it.alarmId == alarmId &&
                    it.originTriggerAt == originTriggerAt &&
                    it.source == source
            }
            .maxOfOrNull { it.snoozeCount }

        override suspend fun totalSnoozeMinutes(
            alarmId: Long,
            originTriggerAt: Long,
            source: Int
        ): Int = entities.values
            .filter {
                it.alarmId == alarmId &&
                    it.originTriggerAt == originTriggerAt &&
                    it.source == source
            }
            .sumOf { it.snoozeMinutes }

        override suspend fun updateStatus(id: Long, status: Int, updatedAt: Long) {
            val current = entities[id] ?: return
            entities[id] = current.copy(status = status, updatedAt = updatedAt)
        }

        override suspend fun updatePendingForAlarm(
            alarmId: Long,
            source: Int,
            status: Int,
            pendingStatus: Int,
            updatedAt: Long
        ) {
            entities.replaceAll { _, entity ->
                if (entity.alarmId == alarmId && entity.source == source && entity.status == pendingStatus) {
                    entity.copy(status = status, updatedAt = updatedAt)
                } else {
                    entity
                }
            }
        }

        override suspend fun updatePendingForAlarmAnySource(
            alarmId: Long,
            status: Int,
            pendingStatus: Int,
            updatedAt: Long
        ) {
            entities.replaceAll { _, entity ->
                if (entity.alarmId == alarmId && entity.status == pendingStatus) {
                    entity.copy(status = status, updatedAt = updatedAt)
                } else {
                    entity
                }
            }
        }

        override suspend fun expirePendingSnoozes(
            source: Int,
            pendingStatus: Int,
            expiredStatus: Int,
            cutoff: Long,
            updatedAt: Long
        ) {
            entities.replaceAll { _, entity ->
                val expiresAt = entity.expiresAt
                if (
                    entity.source == source &&
                    entity.status == pendingStatus &&
                    expiresAt != null &&
                    expiresAt < cutoff
                ) {
                    entity.copy(status = expiredStatus, updatedAt = updatedAt)
                } else {
                    entity
                }
            }
        }
    }
}
