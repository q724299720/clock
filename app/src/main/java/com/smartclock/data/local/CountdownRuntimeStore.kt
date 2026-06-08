package com.smartclock.data.local

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartclock.domain.model.CountdownRuntime
import com.smartclock.domain.model.CountdownStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.countdownRuntimeDataStore by preferencesDataStore(name = "countdown_runtime")

@Singleton
class CountdownRuntimeStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyAlarmId = longPreferencesKey("alarm_id")
    private val keyStatus = stringPreferencesKey("status")
    private val keyEndAt = longPreferencesKey("end_at")
    private val keyRemainingSec = intPreferencesKey("remaining_sec")
    private val keyOriginalDurationSec = intPreferencesKey("original_duration_sec")

    val runtimeFlow: Flow<CountdownRuntime?> =
        context.countdownRuntimeDataStore.data.map { prefs ->
            val alarmId = prefs[keyAlarmId] ?: return@map null
            val status = prefs[keyStatus]
                ?.let { runCatching { CountdownStatus.valueOf(it) }.getOrNull() }
                ?: return@map null
            CountdownRuntime(
                alarmId = alarmId,
                status = status,
                endAt = prefs[keyEndAt],
                remainingSec = prefs[keyRemainingSec] ?: 0,
                originalDurationSec = prefs[keyOriginalDurationSec] ?: 0
            )
        }

    suspend fun current(): CountdownRuntime? = runtimeFlow.first()

    suspend fun setRunning(alarmId: Long, originalDurationSec: Int, endAt: Long) {
        context.countdownRuntimeDataStore.edit { prefs ->
            prefs[keyAlarmId] = alarmId
            prefs[keyStatus] = CountdownStatus.RUNNING.name
            prefs[keyEndAt] = endAt
            prefs[keyRemainingSec] = 0
            prefs[keyOriginalDurationSec] = originalDurationSec
        }
    }

    suspend fun setPaused(alarmId: Long, originalDurationSec: Int, remainingSec: Int) {
        context.countdownRuntimeDataStore.edit { prefs ->
            prefs[keyAlarmId] = alarmId
            prefs[keyStatus] = CountdownStatus.PAUSED.name
            prefs.remove(keyEndAt)
            prefs[keyRemainingSec] = remainingSec.coerceAtLeast(0)
            prefs[keyOriginalDurationSec] = originalDurationSec
        }
    }

    suspend fun clearIfMatches(alarmId: Long) {
        context.countdownRuntimeDataStore.edit { prefs ->
            if (prefs[keyAlarmId] == alarmId) {
                clearAll(prefs)
            }
        }
    }

    suspend fun clear() {
        context.countdownRuntimeDataStore.edit { prefs -> clearAll(prefs) }
    }

    private fun clearAll(
        prefs: androidx.datastore.preferences.core.MutablePreferences
    ) {
        prefs.remove(keyAlarmId)
        prefs.remove(keyStatus)
        prefs.remove(keyEndAt)
        prefs.remove(keyRemainingSec)
        prefs.remove(keyOriginalDurationSec)
    }
}
