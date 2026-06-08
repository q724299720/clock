package com.smartclock.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.smartclock.R
import com.smartclock.SmartClockApp
import com.smartclock.util.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 倒计时前台服务。基于绝对结束时间戳更新通知，息屏不影响计时（策划方案 4.2）。
 * 结束时间到由 AlarmScheduler.scheduleCountdown 注册的精确闹钟触发提醒。
 */
@AndroidEntryPoint
class CountdownService : Service() {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val endAt = intent?.getLongExtra(EXTRA_END_AT, 0L) ?: 0L
        if (endAt <= System.currentTimeMillis()) {
            stopSelf(); return START_NOT_STICKY
        }

        startForegroundCompat(remainingText(endAt))

        job?.cancel()
        job = scope.launch {
            while (System.currentTimeMillis() < endAt) {
                updateNotification(remainingText(endAt))
                delay(1000)
            }
            stopSelf()
        }
        return START_STICKY
    }

    private fun remainingText(endAt: Long): String {
        val remain = ((endAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
        return "剩余 " + TimeFormat.duration(remain)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, SmartClockApp.CHANNEL_COUNTDOWN)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("倒计时进行中")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun startForegroundCompat(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildNotification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification(text))
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }

    companion object {
        const val EXTRA_END_AT = "end_at"
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val NOTIF_ID = 9001
    }
}
