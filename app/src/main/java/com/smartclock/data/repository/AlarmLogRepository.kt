package com.smartclock.data.repository

import com.smartclock.data.identity.DeviceIdentityStore
import com.smartclock.data.local.AlarmLogDao
import com.smartclock.data.local.AlarmLogEntity
import com.smartclock.data.local.toDomain
import com.smartclock.data.local.toEntity
import com.smartclock.data.remote.AlarmLogRemoteDataSource
import com.smartclock.domain.model.AlarmLog
import com.smartclock.domain.model.AlarmLogAction
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AlarmLogRepository @Inject constructor(
    private val dao: AlarmLogDao,
    private val remote: AlarmLogRemoteDataSource,
    private val deviceIdentityStore: DeviceIdentityStore
) {

    fun observeRecentLogs(userId: Long, limit: Int = 5): Flow<List<AlarmLog>> =
        dao.observeRecentLogs(userId, limit).map { logs -> logs.map { it.toDomain() } }

    suspend fun claimLocalLogs(userId: Long): Int = dao.claimLocalLogs(userId)

    suspend fun record(
        alarmId: Long,
        userId: Long,
        action: AlarmLogAction,
        firedAt: Long = System.currentTimeMillis(),
        deviceId: String? = null
    ) {
        val resolvedDeviceId = deviceId ?: deviceIdentityStore.getOrCreateDeviceId()
        val logHash = sha256Hex("$userId|$alarmId|$firedAt|${action.code}|$resolvedDeviceId")
        dao.insert(
            AlarmLog(
                alarmId = alarmId,
                userId = userId,
                firedAt = firedAt,
                action = action,
                deviceId = resolvedDeviceId,
                logHash = logHash
            ).toEntity(syncStatus = 1)
        )
    }

    suspend fun countPending(userId: Long): Int = dao.countPendingSync(userId)

    suspend fun remapAlarmReferences(oldAlarmId: Long, newAlarmId: Long): Int {
        if (oldAlarmId <= 0L || newAlarmId <= 0L || oldAlarmId == newAlarmId) return 0
        return dao.remapAlarmId(oldAlarmId, newAlarmId)
    }

    suspend fun pushPending(userId: Long): Int {
        val pending = dao.getPendingSync(userId)
        if (pending.isEmpty()) return 0

        val fallbackDeviceId = deviceIdentityStore.getOrCreateDeviceId()
        val sanitized = mutableListOf<AlarmLogEntity>()
        val discardIds = mutableListOf<Long>()

        pending.forEach { entity ->
            sanitizePending(entity, fallbackDeviceId)?.let { repaired ->
                if (repaired != entity) {
                    dao.update(repaired)
                }
                sanitized += repaired
            } ?: discardIds.add(entity.id)
        }

        if (discardIds.isNotEmpty()) {
            dao.markSynced(discardIds)
        }
        if (sanitized.isEmpty()) {
            return 0
        }

        remote.pushLogs(sanitized.map { it.toDomain() })
        dao.markSynced(sanitized.map { it.id })
        return sanitized.size
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private suspend fun sanitizePending(
        entity: AlarmLogEntity,
        fallbackDeviceId: String
    ): AlarmLogEntity? {
        if (entity.alarmId <= 0L) return null

        val resolvedDeviceId = entity.deviceId?.trim().takeUnless { it.isNullOrBlank() } ?: fallbackDeviceId
        val resolvedHash = entity.logHash.takeUnless { it.isBlank() }
            ?: sha256Hex(
                "${entity.userId}|${entity.alarmId}|${entity.firedAt}|${entity.action}|$resolvedDeviceId"
            )

        return if (resolvedDeviceId != entity.deviceId || resolvedHash != entity.logHash) {
            entity.copy(deviceId = resolvedDeviceId, logHash = resolvedHash)
        } else {
            entity
        }
    }
}
