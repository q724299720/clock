package com.smartclock.ui.settings

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartclock.data.local.SessionStore
import com.smartclock.data.repository.AlarmLogRepository
import com.smartclock.data.repository.UserRepository
import com.smartclock.data.sync.SyncCoordinator
import com.smartclock.data.sync.SyncState
import com.smartclock.data.sync.SyncStateStore
import com.smartclock.data.sync.SyncTriggerSource
import com.smartclock.domain.model.AlarmLog
import com.smartclock.service.ActiveAlarmCoordinator
import com.smartclock.util.LegacyTextSanitizer
import com.smartclock.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionCheck(
    val title: String,
    val granted: Boolean
)

data class SettingsUiState(
    val loggedIn: Boolean = false,
    val syncing: Boolean = false,
    val syncMessage: String? = null,
    val syncMessageIsError: Boolean = false,
    val syncState: SyncState = SyncState(),
    val exactAlarm: PermissionCheck = PermissionCheck("精准闹钟权限", true),
    val fullScreenIntent: PermissionCheck = PermissionCheck("全屏提醒权限", true),
    val notifications: PermissionCheck = PermissionCheck("通知权限", true),
    val overlay: PermissionCheck = PermissionCheck("悬浮窗权限", true),
    val batteryOptimization: PermissionCheck = PermissionCheck("电池优化白名单", true),
    val recentLogs: List<AlarmLog> = emptyList()
) {
    val missingChecks: List<PermissionCheck>
        get() = listOf(
            exactAlarm,
            fullScreenIntent,
            notifications,
            overlay,
            batteryOptimization
        ).filterNot { it.granted }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val sessionStore: SessionStore,
    private val alarmLogRepository: AlarmLogRepository,
    private val syncCoordinator: SyncCoordinator,
    private val syncStateStore: SyncStateStore,
    private val activeAlarmCoordinator: ActiveAlarmCoordinator,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionChecks()
        observeSessionState()
        observeRecentLogs()
        observeSyncState()
    }

    fun refreshPermissionChecks() {
        _uiState.update {
            it.copy(
                exactAlarm = PermissionCheck("精准闹钟权限", canScheduleExactAlarms()),
                fullScreenIntent = PermissionCheck("全屏提醒权限", canUseFullScreenIntent()),
                notifications = PermissionCheck(
                    "通知权限",
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                ),
                overlay = PermissionCheck("悬浮窗权限", Settings.canDrawOverlays(context)),
                batteryOptimization = PermissionCheck(
                    "电池优化白名单",
                    isIgnoringBatteryOptimizations()
                )
            )
        }
    }

    fun completePermissionGuide(onDone: () -> Unit) {
        viewModelScope.launch {
            sessionStore.markPermissionGuideCompleted()
            onDone()
        }
    }

    fun switchToLocalMode(onDone: () -> Unit) {
        viewModelScope.launch {
            val userId = sessionStore.userIdFlow.first()
            activeAlarmCoordinator.clearForUser(userId)
            userRepo.logout()
            activeAlarmCoordinator.restoreForUser(0L)
            onDone()
        }
    }

    fun syncNow() {
        if (_uiState.value.syncing) return
        viewModelScope.launch {
            val currentUserId = sessionStore.userIdFlow.first()
            if (currentUserId <= 0L) {
                _uiState.update {
                    it.copy(
                        syncing = false,
                        syncMessage = "请先登录后再同步",
                        syncMessageIsError = true
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    syncing = true,
                    syncMessage = null,
                    syncMessageIsError = false
                )
            }

            runCatching {
                syncCoordinator.syncNow(SyncTriggerSource.MANUAL)?.let { report ->
                    activeAlarmCoordinator.restoreForUser(report.userId)
                    WidgetUpdater.updateAll(context)
                } ?: error("同步失败")
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        syncing = false,
                        syncMessage = "已同步云端数据",
                        syncMessageIsError = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        syncing = false,
                        syncMessage = LegacyTextSanitizer.sanitize(error.message) ?: "同步失败",
                        syncMessageIsError = true
                    )
                }
            }
        }
    }

    private fun observeSessionState() {
        viewModelScope.launch {
            sessionStore.userIdFlow.collect { userId ->
                _uiState.update {
                    it.copy(
                        loggedIn = userId > 0,
                        syncing = false,
                        syncMessage = null,
                        syncMessageIsError = false
                    )
                }
            }
        }
    }

    private fun observeRecentLogs() {
        viewModelScope.launch {
            sessionStore.userIdFlow
                .flatMapLatest { userId -> alarmLogRepository.observeRecentLogs(userId) }
                .collect { logs ->
                    _uiState.update { it.copy(recentLogs = logs) }
                }
        }
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncStateStore.stateFlow.collect { syncState ->
                _uiState.update { it.copy(syncState = syncState) }
            }
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    private fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.canUseFullScreenIntent()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
