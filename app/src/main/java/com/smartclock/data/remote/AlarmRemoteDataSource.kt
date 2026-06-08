package com.smartclock.data.remote

import android.util.Log
import com.smartclock.data.remote.api.ApiClient
import com.smartclock.data.remote.dto.ApiAlarmPullResponse
import com.smartclock.data.remote.dto.ApiAlarmPushRequest
import com.smartclock.data.remote.dto.ApiAlarmPushResponse
import com.smartclock.data.remote.dto.ApiBootstrapResponse
import com.smartclock.data.remote.dto.toApiDto
import com.smartclock.data.remote.dto.toDomain
import com.smartclock.domain.model.Alarm
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AlarmRemoteDataSource @Inject constructor(
    private val apiClient: ApiClient
) {
    private companion object {
        const val TAG = "AlarmRemoteDataSource"
    }

    suspend fun fetchSince(userId: Long, since: Long): List<Alarm> = withContext(Dispatchers.IO) {
        if (since <= 0L) {
            val response = apiClient.get<ApiBootstrapResponse>("/api/v1/sync/bootstrap")
            response.alarms.map { it.toDomain(userId) }
        } else {
            val encoded = URLEncoder.encode(java.time.Instant.ofEpochMilli(since).toString(), Charsets.UTF_8.name())
            val response = apiClient.get<ApiAlarmPullResponse>("/api/v1/sync/alarms/pull?since=$encoded")
            response.alarms.map { it.toDomain(userId) }
        }
    }

    suspend fun pushAlarms(alarms: List<Alarm>): List<Alarm> = withContext(Dispatchers.IO) {
        if (alarms.isEmpty()) return@withContext emptyList()
        val userId = alarms.first().userId
        runCatching { pushBatch(alarms, userId) }.getOrElse { batchError ->
            Log.w(TAG, "batch push failed, fallback to single alarm sync", batchError)
            val synced = mutableListOf<Alarm>()
            alarms.forEach { alarm ->
                runCatching { pushBatch(listOf(alarm), userId) }
                    .onSuccess { synced += it }
                    .onFailure { Log.w(TAG, "single alarm push failed for ${alarm.clientUuid}", it) }
            }
            if (synced.isEmpty()) {
                throw batchError
            }
            synced
        }
    }

    private suspend fun pushBatch(alarms: List<Alarm>, userId: Long): List<Alarm> {
        val response = apiClient.post<ApiAlarmPushResponse>(
            path = "/api/v1/sync/alarms/push",
            body = ApiAlarmPushRequest(alarms.map { it.toApiDto() }),
            authenticated = true
        )
        return response.alarms.map { it.toDomain(userId) }
    }
}
