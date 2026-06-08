package com.smartclock.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartclock.data.local.CountdownRuntimeStore
import com.smartclock.data.repository.AlarmRepository
import com.smartclock.domain.model.AlarmType
import com.smartclock.service.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var countdownRuntimeStore: CountdownRuntimeStore

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_ENABLED -> handleToggle(context, intent)
        }
    }

    private fun handleToggle(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
        if (alarmId <= 0L) return

        val now = System.currentTimeMillis()
        val previous = lastToggleAt[alarmId]
        if (previous != null && now - previous < DEBOUNCE_WINDOW_MS) return
        lastToggleAt[alarmId] = now

        WidgetDataLoader.setOptimisticEnabled(alarmId, enabled)
        WidgetUpdater.updateAll(context.applicationContext)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.setEnabled(alarmId, enabled)
                val alarm = repository.getById(alarmId) ?: return@launch
                if (enabled) {
                    scheduler.schedule(alarm)
                } else {
                    scheduler.cancel(alarmId)
                    if (alarm.type == AlarmType.COUNTDOWN) {
                        scheduler.stopCountdown()
                        countdownRuntimeStore.clearIfMatches(alarmId)
                    }
                }
            } finally {
                WidgetDataLoader.clearOptimisticEnabled(alarmId)
                WidgetUpdater.updateAll(context.applicationContext)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_ENABLED = "com.smartclock.widget.TOGGLE_ENABLED"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ENABLED = "enabled"

        private const val DEBOUNCE_WINDOW_MS = 800L
        private val lastToggleAt = ConcurrentHashMap<Long, Long>()
    }
}
