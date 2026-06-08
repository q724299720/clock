package com.smartclock.ui.alarm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartclock.data.local.CountdownRuntimeStore
import com.smartclock.data.local.SessionStore
import com.smartclock.data.repository.AlarmRepository
import com.smartclock.data.repository.DeletedAlarmRepository
import com.smartclock.data.sync.SyncScheduler
import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.CountdownStatus
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.DeletedAlarmSnapshot
import com.smartclock.service.AlarmScheduler
import com.smartclock.util.ReminderScheduleResolver
import com.smartclock.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AlarmSaveMode {
    NORMAL,
    OVERRIDE_NEXT
}

sealed interface AlarmUiEvent {
    data class Deleted(val trashIds: List<Long>, val count: Int) : AlarmUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val repo: AlarmRepository,
    private val deletedAlarmRepository: DeletedAlarmRepository,
    private val scheduler: AlarmScheduler,
    private val session: SessionStore,
    private val syncScheduler: SyncScheduler,
    private val countdownStore: CountdownRuntimeStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> =
        session.userIdFlow
            .flatMapLatest { uid -> repo.observeAlarms(uid) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val countdownRuntime =
        countdownStore.runtimeFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deletedAlarms: StateFlow<List<DeletedAlarmSnapshot>> =
        session.userIdFlow
            .flatMapLatest { uid -> deletedAlarmRepository.observeDeletedAlarms(uid) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<AlarmUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AlarmUiEvent> = _events.asSharedFlow()

    fun byType(type: AlarmType): List<Alarm> = alarms.value.filter { it.type == type }

    fun save(alarm: Alarm, saveMode: AlarmSaveMode = AlarmSaveMode.NORMAL) {
        viewModelScope.launch {
            saveInternal(listOf(alarm), saveMode)
        }
    }

    fun saveBatch(alarmsToCreate: List<Alarm>) {
        if (alarmsToCreate.isEmpty()) return
        viewModelScope.launch {
            saveInternal(alarmsToCreate, AlarmSaveMode.NORMAL)
        }
    }

    private suspend fun saveInternal(
        alarmsToCreate: List<Alarm>,
        saveMode: AlarmSaveMode
    ) {
        val userId = currentUserId()
        val now = System.currentTimeMillis()
        alarmsToCreate.forEach { alarm ->
            val normalized = if (alarm.type == AlarmType.COUNTDOWN) {
                val durationSec = alarm.durationSec ?: 0
                alarm.copy(
                    userId = userId,
                    triggerTime = now + durationSec * 1000L
                )
            } else {
                alarm.copy(userId = userId)
            }
            val prepared = prepareForSave(normalized, saveMode) ?: return@forEach
            val localId = repo.save(prepared)
            val saved = repo.getById(localId) ?: return@forEach
            if (saved.type == AlarmType.COUNTDOWN) {
                stopOtherCountdowns(saved.id)
                countdownStore.setRunning(
                    alarmId = saved.id,
                    originalDurationSec = saved.durationSec ?: 0,
                    endAt = saved.triggerTime ?: now
                )
            }
            scheduler.schedule(saved)
        }
        syncScheduler.scheduleImmediateSync()
        refreshWidgets()
    }

    fun toggle(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            if (alarm.type == AlarmType.COUNTDOWN) {
                if (enabled) {
                    startCountdown(alarm)
                } else {
                    disableCountdown(alarm)
                }
                syncScheduler.scheduleImmediateSync()
                refreshWidgets()
                return@launch
            }
            repo.setEnabled(alarm.id, enabled)
            val updated = repo.getById(alarm.id) ?: return@launch
            if (enabled) {
                scheduler.schedule(updated)
            } else {
                scheduler.cancel(alarm.id)
            }
            syncScheduler.scheduleImmediateSync()
            refreshWidgets()
        }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch {
            val trashId = deletedAlarmRepository.snapshot(alarm)
            scheduler.cancel(alarm.id)
            if (alarm.type == AlarmType.COUNTDOWN) {
                scheduler.stopCountdown()
                countdownStore.clearIfMatches(alarm.id)
            }
            repo.delete(alarm.id)
            _events.emit(AlarmUiEvent.Deleted(listOf(trashId), 1))
            syncScheduler.scheduleImmediateSync()
            refreshWidgets()
        }
    }

    fun deleteMany(alarmsToDelete: List<Alarm>) {
        if (alarmsToDelete.isEmpty()) return
        viewModelScope.launch {
            val trashIds = deletedAlarmRepository.snapshotAll(alarmsToDelete)
            alarmsToDelete.forEach { alarm ->
                scheduler.cancel(alarm.id)
                if (alarm.type == AlarmType.COUNTDOWN) {
                    scheduler.stopCountdown()
                    countdownStore.clearIfMatches(alarm.id)
                }
                repo.delete(alarm.id)
            }
            if (trashIds.isNotEmpty()) {
                _events.emit(AlarmUiEvent.Deleted(trashIds, trashIds.size))
            }
            syncScheduler.scheduleImmediateSync()
            refreshWidgets()
        }
    }

    fun toggleMany(alarmsToToggle: List<Alarm>, enabled: Boolean) {
        if (alarmsToToggle.isEmpty()) return
        viewModelScope.launch {
            alarmsToToggle.forEach { alarm ->
                if (alarm.type == AlarmType.COUNTDOWN) {
                    if (enabled) {
                        startCountdown(alarm)
                    } else {
                        disableCountdown(alarm)
                    }
                } else {
                    repo.setEnabled(alarm.id, enabled)
                    val updated = repo.getById(alarm.id) ?: return@forEach
                    if (enabled) {
                        scheduler.schedule(updated)
                    } else {
                        scheduler.cancel(alarm.id)
                    }
                }
            }
            syncScheduler.scheduleImmediateSync()
            refreshWidgets()
        }
    }

    fun restoreDeleted(trashId: Long) {
        viewModelScope.launch {
            restoreDeletedInternal(listOf(trashId))
        }
    }

    fun restoreDeletedMany(trashIds: List<Long>) {
        if (trashIds.isEmpty()) return
        viewModelScope.launch {
            restoreDeletedInternal(trashIds)
        }
    }

    fun skipToday(alarm: Alarm) {
        if (alarm.type == AlarmType.ONCE || alarm.type == AlarmType.COUNTDOWN) return
        viewModelScope.launch {
            val anchor = ReminderScheduleResolver.nextTrigger(alarm) ?: return@launch
            val today = Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val anchorDate = Instant.ofEpochMilli(anchor)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            if (anchorDate != today) return@launch
            val updated = ReminderScheduleResolver.skipNextOccurrence(alarm, anchor)
            val localId = repo.save(updated)
            repo.getById(localId)?.let { scheduler.schedule(it) }
            syncScheduler.scheduleImmediateSync()
            refreshWidgets()
        }
    }

    fun pauseCountdown(alarm: Alarm) {
        viewModelScope.launch {
            val runtime = countdownStore.current() ?: return@launch
            if (alarm.type != AlarmType.COUNTDOWN ||
                runtime.alarmId != alarm.id ||
                runtime.status != CountdownStatus.RUNNING
            ) {
                return@launch
            }

            val remainingSec = runtime.remainingAt().coerceAtLeast(1)
            val userId = currentUserId()
            scheduler.cancel(alarm.id)
            scheduler.stopCountdown()
            repo.save(
                alarm.copy(
                    userId = userId,
                    enabled = false,
                    triggerTime = System.currentTimeMillis() + remainingSec * 1000L
                )
            )
            countdownStore.setPaused(
                alarmId = alarm.id,
                originalDurationSec = alarm.durationSec ?: runtime.originalDurationSec,
                remainingSec = remainingSec
            )
            syncScheduler.scheduleImmediateSync()
            refreshWidgets()
        }
    }

    fun resumeCountdown(alarm: Alarm) {
        viewModelScope.launch {
            val runtime = countdownStore.current() ?: return@launch
            if (alarm.type != AlarmType.COUNTDOWN ||
                runtime.alarmId != alarm.id ||
                runtime.status != CountdownStatus.PAUSED
            ) {
                return@launch
            }

            val userId = currentUserId()
            val endAt = System.currentTimeMillis() + runtime.remainingSec * 1000L
            stopOtherCountdowns(alarm.id)
            val localId = repo.save(
                alarm.copy(
                    userId = userId,
                    enabled = true,
                    triggerTime = endAt
                )
            )
            val saved = repo.getById(localId) ?: return@launch
            scheduler.schedule(saved)
            countdownStore.setRunning(
                alarmId = alarm.id,
                originalDurationSec = alarm.durationSec ?: runtime.originalDurationSec,
                endAt = endAt
            )
            syncScheduler.scheduleImmediateSync()
            refreshWidgets()
        }
    }

    fun extendCountdown(alarm: Alarm, extraSeconds: Int = 60) {
        viewModelScope.launch {
            val runtime = countdownStore.current() ?: return@launch
            if (alarm.type != AlarmType.COUNTDOWN || runtime.alarmId != alarm.id) return@launch

            val userId = currentUserId()
            val originalDurationSec = alarm.durationSec ?: runtime.originalDurationSec
            when (runtime.status) {
                CountdownStatus.RUNNING -> {
                    val baseEndAt = maxOf(runtime.endAt ?: System.currentTimeMillis(), System.currentTimeMillis())
                    val endAt = baseEndAt + extraSeconds * 1000L
                    val localId = repo.save(
                        alarm.copy(
                            userId = userId,
                            enabled = true,
                            triggerTime = endAt
                        )
                    )
                    val saved = repo.getById(localId) ?: return@launch
                    scheduler.schedule(saved)
                    countdownStore.setRunning(alarm.id, originalDurationSec, endAt)
                }

                CountdownStatus.PAUSED -> {
                    val remainingSec = runtime.remainingAt() + extraSeconds
                    repo.save(
                        alarm.copy(
                            userId = userId,
                            enabled = false,
                            triggerTime = System.currentTimeMillis() + remainingSec * 1000L
                        )
                    )
                    countdownStore.setPaused(alarm.id, originalDurationSec, remainingSec)
                }
            }
            syncScheduler.scheduleImmediateSync()
            refreshWidgets()
        }
    }

    fun resetCountdown(alarm: Alarm) {
        viewModelScope.launch {
            val originalDurationSec = alarm.durationSec ?: return@launch
            val userId = currentUserId()
            scheduler.cancel(alarm.id)
            scheduler.stopCountdown()
            repo.save(
                alarm.copy(
                    userId = userId,
                    enabled = false,
                    triggerTime = System.currentTimeMillis() + originalDurationSec * 1000L
                )
            )
            countdownStore.setPaused(
                alarmId = alarm.id,
                originalDurationSec = originalDurationSec,
                remainingSec = originalDurationSec
            )
            syncScheduler.scheduleImmediateSync()
            refreshWidgets()
        }
    }

    suspend fun getById(id: Long): Alarm? = repo.getById(id)

    private suspend fun currentUserId(): Long = session.userIdFlow.first()

    private suspend fun prepareForSave(
        alarm: Alarm,
        saveMode: AlarmSaveMode
    ): Alarm? {
        val existing = alarm.id.takeIf { it > 0L }?.let { repo.getById(it) }
        return when (saveMode) {
            AlarmSaveMode.NORMAL -> {
                when {
                    existing == null -> alarm
                    ReminderScheduleResolver.hasScheduleChanged(existing, alarm) ->
                        ReminderScheduleResolver.clearOverride(alarm)
                    else -> alarm.copy(
                        nextOverrideMode = existing.nextOverrideMode,
                        nextOverrideAnchorDate = existing.nextOverrideAnchorDate,
                        nextOverrideAnchorTriggerAt = existing.nextOverrideAnchorTriggerAt,
                        nextOverrideTriggerAt = existing.nextOverrideTriggerAt
                    )
                }
            }

            AlarmSaveMode.OVERRIDE_NEXT -> {
                val base = existing ?: return null
                base.copy(
                    nextOverrideMode = alarm.nextOverrideMode,
                    nextOverrideAnchorDate = alarm.nextOverrideAnchorDate,
                    nextOverrideAnchorTriggerAt = alarm.nextOverrideAnchorTriggerAt,
                    nextOverrideTriggerAt = alarm.nextOverrideTriggerAt
                )
            }
        }
    }

    private suspend fun restoreDeletedInternal(trashIds: List<Long>) {
        val restored = if (trashIds.size == 1) {
            deletedAlarmRepository.restore(trashIds.first())?.let(::listOf).orEmpty()
        } else {
            deletedAlarmRepository.restoreMany(trashIds)
        }
        restored.forEach { alarm ->
            val localId = repo.save(alarm.copy(status = 0))
            val saved = repo.getById(localId) ?: return@forEach
            if (saved.enabled) {
                scheduler.schedule(saved)
            } else {
                scheduler.cancel(saved.id)
            }
        }
        if (restored.isNotEmpty()) {
            syncScheduler.scheduleImmediateSync()
            refreshWidgets()
        }
    }

    private suspend fun startCountdown(alarm: Alarm) {
        val userId = currentUserId()
        val durationSec = alarm.durationSec ?: return
        val endAt = System.currentTimeMillis() + durationSec * 1000L
        stopOtherCountdowns(alarm.id)
        val localId = repo.save(
            alarm.copy(
                userId = userId,
                enabled = true,
                triggerTime = endAt
            )
        )
        val saved = repo.getById(localId) ?: return
        scheduler.schedule(saved)
        countdownStore.setRunning(saved.id, durationSec, endAt)
        refreshWidgets()
    }

    private suspend fun disableCountdown(alarm: Alarm) {
        scheduler.cancel(alarm.id)
        scheduler.stopCountdown()
        repo.setEnabled(alarm.id, false)
        countdownStore.clearIfMatches(alarm.id)
        refreshWidgets()
    }

    private suspend fun stopOtherCountdowns(activeId: Long) {
        alarms.value
            .filter { it.type == AlarmType.COUNTDOWN && it.id != activeId && it.enabled }
            .forEach { other ->
                scheduler.cancel(other.id)
                repo.setEnabled(other.id, false)
            }

        val runtime = countdownStore.current()
        if (runtime != null && runtime.alarmId != activeId) {
            scheduler.cancel(runtime.alarmId)
            scheduler.stopCountdown()
        }
    }

    private fun refreshWidgets() {
        WidgetUpdater.updateAll(context)
    }
}
