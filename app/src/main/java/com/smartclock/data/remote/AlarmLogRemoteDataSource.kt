package com.smartclock.data.remote

import com.smartclock.data.remote.api.ApiClient
import com.smartclock.data.remote.dto.ApiAlarmLogBatchRequest
import com.smartclock.data.remote.dto.ApiAlarmLogBatchResponse
import com.smartclock.data.remote.dto.toApiDto
import com.smartclock.domain.model.AlarmLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AlarmLogRemoteDataSource @Inject constructor(
    private val apiClient: ApiClient
) {
    suspend fun pushLogs(logs: List<AlarmLog>): ApiAlarmLogBatchResponse = withContext(Dispatchers.IO) {
        apiClient.post(
            path = "/api/v1/sync/alarm-logs/batch",
            body = ApiAlarmLogBatchRequest(logs.map { it.toApiDto() }),
            authenticated = true
        )
    }
}
