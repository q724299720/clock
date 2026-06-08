package com.smartclock.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smartclock.data.local.SessionStore
import com.smartclock.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var activeAlarmCoordinator: ActiveAlarmCoordinator
    @Inject lateinit var session: SessionStore

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (!isSupportedAction(action)) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = session.userIdFlow.first()
                activeAlarmCoordinator.restoreForUser(userId)
                WidgetUpdater.updateAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    private fun isSupportedAction(action: String): Boolean = when (action) {
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_TIME_CHANGED -> true
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        else -> false
    }
}
