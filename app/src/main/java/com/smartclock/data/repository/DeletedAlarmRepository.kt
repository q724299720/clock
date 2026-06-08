package com.smartclock.data.repository

import com.google.gson.Gson
import com.smartclock.data.local.DeletedAlarmDao
import com.smartclock.data.local.DeletedAlarmEntity
import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.DeletedAlarmSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DeletedAlarmRepository @Inject constructor(
    private val dao: DeletedAlarmDao
) {
    private val gson = Gson()

    fun observeDeletedAlarms(userId: Long): Flow<List<DeletedAlarmSnapshot>> =
        dao.observeByUser(userId).map { list -> list.map(::toDomain) }

    suspend fun countByUser(userId: Long): Int = dao.countByUser(userId)

    suspend fun snapshot(
        alarm: Alarm,
        now: Long = System.currentTimeMillis(),
        retentionMillis: Long = RETENTION_MILLIS
    ): Long = dao.insert(alarm.toDeletedEntity(now, now + retentionMillis, gson))

    suspend fun snapshotAll(
        alarms: List<Alarm>,
        now: Long = System.currentTimeMillis(),
        retentionMillis: Long = RETENTION_MILLIS
    ): List<Long> {
        if (alarms.isEmpty()) return emptyList()
        return dao.insertAll(alarms.map { it.toDeletedEntity(now, now + retentionMillis, gson) })
    }

    suspend fun restore(trashId: Long): Alarm? {
        val snapshot = dao.getById(trashId) ?: return null
        val now = System.currentTimeMillis()
        val alarm = gson.fromJson(snapshot.payloadJson, Alarm::class.java)
            ?.copy(
                status = 0,
                updatedAt = now
            )
        dao.deleteById(trashId)
        return alarm
    }

    suspend fun restoreMany(trashIds: List<Long>): List<Alarm> {
        if (trashIds.isEmpty()) return emptyList()
        val snapshots = dao.getByIds(trashIds)
        val now = System.currentTimeMillis()
        dao.deleteByIds(trashIds)
        return snapshots.mapNotNull {
            runCatching {
                gson.fromJson(it.payloadJson, Alarm::class.java)?.copy(
                    status = 0,
                    updatedAt = now
                )
            }.getOrNull()
        }
    }

    suspend fun deleteExpired(now: Long = System.currentTimeMillis()): Int = dao.deleteExpired(now)

    private fun toDomain(entity: DeletedAlarmEntity): DeletedAlarmSnapshot {
        val alarm = gson.fromJson(entity.payloadJson, Alarm::class.java)
        return DeletedAlarmSnapshot(
            trashId = entity.trashId,
            userId = entity.userId,
            deletedAt = entity.deletedAt,
            expiresAt = entity.expiresAt,
            alarm = alarm
        )
    }

    private fun Alarm.toDeletedEntity(
        deletedAt: Long,
        expiresAt: Long,
        gson: Gson
    ): DeletedAlarmEntity = DeletedAlarmEntity(
        userId = userId,
        alarmId = id,
        clientUuid = clientUuid,
        title = title,
        triggerTime = triggerTime,
        deletedAt = deletedAt,
        expiresAt = expiresAt,
        payloadJson = gson.toJson(this)
    )

    companion object {
        const val RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }
}
