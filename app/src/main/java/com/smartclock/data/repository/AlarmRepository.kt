package com.smartclock.data.repository

import com.smartclock.data.local.AlarmDao
import com.smartclock.data.local.AlarmEntity
import com.smartclock.data.local.toDomain
import com.smartclock.data.local.toEntity
import com.smartclock.data.remote.AlarmRemoteDataSource
import com.smartclock.domain.model.Alarm
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AlarmRepository @Inject constructor(
    private val dao: AlarmDao,
    private val remote: AlarmRemoteDataSource,
    private val alarmLogRepository: AlarmLogRepository
) {

    fun observeAlarms(userId: Long): Flow<List<Alarm>> =
        dao.observeAlarms(userId).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Alarm? = dao.getById(id)?.toDomain()

    suspend fun getEnabledAlarms(userId: Long): List<Alarm> =
        dao.getEnabledAlarms(userId).map { it.toDomain() }

    suspend fun getAllAlarms(userId: Long): List<Alarm> =
        dao.getAllAlarms(userId).map { it.toDomain() }

    suspend fun countPendingUpserts(userId: Long): Int = dao.countPendingUpserts(userId)

    suspend fun countPendingDeletes(userId: Long): Int = dao.countPendingDeletes(userId)

    suspend fun save(alarm: Alarm): Long {
        val now = System.currentTimeMillis()
        val localId = if (alarm.id == 0L) -now else alarm.id
        val clientUuid = alarm.clientUuid.ifBlank { UUID.randomUUID().toString() }
        val entity = alarm.copy(
            id = localId,
            clientUuid = clientUuid,
            updatedAt = now
        ).toEntity(syncStatus = 1)
        dao.upsert(entity)
        return localId
    }

    suspend fun claimLocalAlarms(userId: Long): Int = dao.claimLocalAlarms(userId)

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, if (enabled) 1 else 0, System.currentTimeMillis())
    }

    suspend fun delete(id: Long) {
        dao.markDeleted(id, System.currentTimeMillis())
    }

    suspend fun clearNextOverride(id: Long) {
        dao.clearNextOverride(id, System.currentTimeMillis())
    }

    suspend fun pushPending(userId: Long): Int {
        val pending = dao.getPendingSync(userId)
        if (pending.isEmpty()) return 0

        val synced = remote.pushAlarms(pending.map(AlarmEntity::toDomain))
        val syncedByUuid = synced.associateBy { it.clientUuid }

        pending.forEach { entity ->
            val remoteAlarm = syncedByUuid[entity.clientUuid] ?: return@forEach
            if (entity.syncStatus == 2) {
                dao.hardDelete(entity.id)
                return@forEach
            }

            val existing = dao.getByClientUuid(entity.clientUuid)
            if (existing != null && existing.id != remoteAlarm.id) {
                alarmLogRepository.remapAlarmReferences(existing.id, remoteAlarm.id)
                dao.hardDelete(existing.id)
            }
            dao.upsert(remoteAlarm.toEntity(syncStatus = 0))
        }
        return synced.size
    }

    suspend fun pullRemote(userId: Long): Int {
        val since = dao.lastSyncedAt(userId) ?: 0L
        val remoteList = remote.fetchSince(userId, since)
        remoteList.forEach { remoteAlarm ->
            if (remoteAlarm.status == 1) {
                dao.hardDeleteByClientUuid(remoteAlarm.clientUuid)
            } else {
                val existing = dao.getByClientUuid(remoteAlarm.clientUuid)
                if (existing != null && existing.id != remoteAlarm.id) {
                    alarmLogRepository.remapAlarmReferences(existing.id, remoteAlarm.id)
                    dao.hardDelete(existing.id)
                }
                dao.upsert(remoteAlarm.toEntity(syncStatus = 0))
            }
        }
        return remoteList.size
    }
}
