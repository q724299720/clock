package com.smartclock.data.repository

import com.smartclock.data.local.SessionStore
import com.smartclock.data.remote.UserRemoteDataSource
import com.smartclock.data.remote.UserRemoteDataSource.AuthResult
import com.smartclock.data.repository.AlarmLogRepository
import com.smartclock.data.sync.SyncCoordinator
import com.smartclock.data.sync.SyncScheduler
import com.smartclock.data.sync.SyncTriggerSource
import com.smartclock.service.ActiveAlarmCoordinator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class UserRepository @Inject constructor(
    private val remote: UserRemoteDataSource,
    private val session: SessionStore,
    private val alarmRepository: AlarmRepository,
    private val alarmLogRepository: AlarmLogRepository,
    private val syncCoordinator: SyncCoordinator,
    private val syncScheduler: SyncScheduler,
    private val activeAlarmCoordinator: ActiveAlarmCoordinator
) {
    val currentUserId: Flow<Long> = session.userIdFlow

    suspend fun register(account: String, isEmail: Boolean, password: String, nickname: String?): AuthResult {
        val previousUserId = session.userIdFlow.first()
        val result = remote.register(account, isEmail, password, nickname)
        if (result is AuthResult.Success) {
            activeAlarmCoordinator.clearForUser(previousUserId)
            session.save(result.user.id, result.accessToken, result.refreshToken, result.user.nickname)
            alarmRepository.claimLocalAlarms(result.user.id)
            alarmLogRepository.claimLocalLogs(result.user.id)
            val synced = runCatching { syncCoordinator.syncNow(SyncTriggerSource.REGISTER) }.getOrNull() != null
            activeAlarmCoordinator.restoreForUser(result.user.id)
            if (!synced) syncScheduler.scheduleImmediateSync(SyncTriggerSource.REGISTER)
        }
        return result
    }

    suspend fun login(account: String, isEmail: Boolean, password: String): AuthResult {
        val previousUserId = session.userIdFlow.first()
        val result = remote.login(account, isEmail, password)
        if (result is AuthResult.Success) {
            activeAlarmCoordinator.clearForUser(previousUserId)
            session.save(result.user.id, result.accessToken, result.refreshToken, result.user.nickname)
            alarmRepository.claimLocalAlarms(result.user.id)
            alarmLogRepository.claimLocalLogs(result.user.id)
            val synced = runCatching { syncCoordinator.syncNow(SyncTriggerSource.LOGIN) }.getOrNull() != null
            activeAlarmCoordinator.restoreForUser(result.user.id)
            if (!synced) syncScheduler.scheduleImmediateSync(SyncTriggerSource.LOGIN)
        }
        return result
    }

    suspend fun logout() {
        remote.logoutBestEffort()
        session.clear()
    }
}
