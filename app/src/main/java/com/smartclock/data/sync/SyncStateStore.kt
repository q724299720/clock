package com.smartclock.data.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartclock.util.LegacyTextSanitizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext

private val Context.syncStateDataStore by preferencesDataStore(name = "sync_state")

@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyLastAttemptAt = longPreferencesKey("last_attempt_at")
    private val keyLastSuccessAt = longPreferencesKey("last_success_at")
    private val keyLastErrorMessage = stringPreferencesKey("last_error_message")
    private val keyLastTriggerSource = stringPreferencesKey("last_trigger_source")
    private val keyPushedAlarmCount = intPreferencesKey("pushed_alarm_count")
    private val keyPulledAlarmCount = intPreferencesKey("pulled_alarm_count")
    private val keyPushedLogCount = intPreferencesKey("pushed_log_count")
    private val keyPendingAlarmCount = intPreferencesKey("pending_alarm_count")
    private val keyPendingDeleteCount = intPreferencesKey("pending_delete_count")
    private val keyPendingLogCount = intPreferencesKey("pending_log_count")
    private val keyExactAlarmDegraded = booleanPreferencesKey("exact_alarm_degraded")

    val stateFlow: Flow<SyncState> = context.syncStateDataStore.data.map { prefs ->
        SyncState(
            lastAttemptAt = prefs[keyLastAttemptAt],
            lastSuccessAt = prefs[keyLastSuccessAt],
            lastErrorMessage = LegacyTextSanitizer.sanitize(prefs[keyLastErrorMessage]),
            lastTriggerSource = prefs[keyLastTriggerSource]
                ?.let { runCatching { SyncTriggerSource.valueOf(it) }.getOrNull() },
            pushedAlarmCount = prefs[keyPushedAlarmCount] ?: 0,
            pulledAlarmCount = prefs[keyPulledAlarmCount] ?: 0,
            pushedLogCount = prefs[keyPushedLogCount] ?: 0,
            pendingAlarmCount = prefs[keyPendingAlarmCount] ?: 0,
            pendingDeleteCount = prefs[keyPendingDeleteCount] ?: 0,
            pendingLogCount = prefs[keyPendingLogCount] ?: 0,
            exactAlarmDegraded = prefs[keyExactAlarmDegraded] ?: false
        )
    }

    suspend fun record(report: SyncReport) {
        context.syncStateDataStore.edit { prefs ->
            prefs[keyLastAttemptAt] = report.lastAttemptAt
            report.lastSuccessAt?.let { prefs[keyLastSuccessAt] = it } ?: prefs.remove(keyLastSuccessAt)
            LegacyTextSanitizer.sanitize(report.lastErrorMessage)
                ?.let { prefs[keyLastErrorMessage] = it }
                ?: prefs.remove(keyLastErrorMessage)
            prefs[keyLastTriggerSource] = report.triggerSource.name
            prefs[keyPushedAlarmCount] = report.pushedAlarmCount
            prefs[keyPulledAlarmCount] = report.pulledAlarmCount
            prefs[keyPushedLogCount] = report.pushedLogCount
            prefs[keyPendingAlarmCount] = report.pendingAlarmCount
            prefs[keyPendingDeleteCount] = report.pendingDeleteCount
            prefs[keyPendingLogCount] = report.pendingLogCount
            prefs[keyExactAlarmDegraded] = report.exactAlarmDegraded
        }
    }

    suspend fun setExactAlarmDegraded(degraded: Boolean) {
        context.syncStateDataStore.edit { prefs ->
            prefs[keyExactAlarmDegraded] = degraded
        }
    }
}
