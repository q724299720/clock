package com.smartclock

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.smartclock.data.local.SessionStore
import com.smartclock.data.repository.DeletedAlarmRepository
import com.smartclock.data.sync.SyncScheduler
import com.smartclock.data.sync.SyncTriggerSource
import com.smartclock.service.ActiveAlarmCoordinator
import com.smartclock.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SmartClockApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var activeAlarmCoordinator: ActiveAlarmCoordinator
    @Inject lateinit var deletedAlarmRepository: DeletedAlarmRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        syncScheduler.ensurePeriodicSync()
        syncScheduler.scheduleImmediateSync(SyncTriggerSource.APP_START)
        cleanupDeletedAlarms()
        restoreEnabledAlarms()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "\u95f9\u949f\u89e6\u53d1\u540e\u7684\u5f3a\u63d0\u9192\u901a\u77e5"
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "生活提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "生活提醒与法定工作日等非强提醒通知"
            enableVibration(true)
            setBypassDnd(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val countdownChannel = NotificationChannel(
            CHANNEL_COUNTDOWN,
            getString(R.string.channel_countdown_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "\u5012\u8ba1\u65f6\u8fd0\u884c\u4e2d\u7684\u5e38\u9a7b\u901a\u77e5"
            setSound(null, null)
        }

        nm.createNotificationChannels(listOf(alarmChannel, reminderChannel, countdownChannel))
    }

    private fun restoreEnabledAlarms() {
        appScope.launch {
            val userId = sessionStore.userIdFlow.first()
            activeAlarmCoordinator.restoreForUser(userId)
            WidgetUpdater.updateAll(applicationContext)
        }
    }

    private fun cleanupDeletedAlarms() {
        appScope.launch {
            deletedAlarmRepository.deleteExpired()
        }
    }

    companion object {
        const val CHANNEL_ALARM = "channel_alarm_v2"
        const val CHANNEL_REMINDER = "channel_reminder_v1"
        const val CHANNEL_COUNTDOWN = "channel_countdown"
    }
}
