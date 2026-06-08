package com.smartclock.data.sync

enum class SyncTriggerSource {
    APP_START,
    USER_ACTION,
    LOGIN,
    REGISTER,
    MANUAL,
    PERIODIC,
    WORKER
}

data class SyncReport(
    val userId: Long,
    val triggerSource: SyncTriggerSource,
    val lastAttemptAt: Long,
    val lastSuccessAt: Long?,
    val lastErrorMessage: String?,
    val pushedAlarmCount: Int,
    val pulledAlarmCount: Int,
    val pushedLogCount: Int,
    val pendingAlarmCount: Int,
    val pendingDeleteCount: Int,
    val pendingLogCount: Int,
    val exactAlarmDegraded: Boolean
)

data class SyncState(
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastErrorMessage: String? = null,
    val lastTriggerSource: SyncTriggerSource? = null,
    val pushedAlarmCount: Int = 0,
    val pulledAlarmCount: Int = 0,
    val pushedLogCount: Int = 0,
    val pendingAlarmCount: Int = 0,
    val pendingDeleteCount: Int = 0,
    val pendingLogCount: Int = 0,
    val exactAlarmDegraded: Boolean = false
)
