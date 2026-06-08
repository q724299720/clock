package com.smartclock.service

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smartclock.R
import com.smartclock.SmartClockApp
import com.smartclock.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmAlertService : Service() {

    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var actionLogger: AlarmActionLogger
    @Inject lateinit var overlay: AlarmAlertOverlay

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var currentAlarmId: Long = 0L
    private var currentOriginTriggerAt: Long = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(BOOTSTRAP_NOTIFICATION_ID, buildBootstrapNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> handleStart(intent)
            ACTION_DISMISS -> dismissCurrentAlert()
            ACTION_SNOOZE -> {
                val minutes = intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)
                    ?: DEFAULT_SNOOZE_MINUTES
                snoozeCurrentAlert(minutes)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlay.hide()
        stopAlerting()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun handleStart(intent: Intent?) {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, 0L) ?: 0L
        if (alarmId == 0L) {
            stopSelf()
            return
        }

        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_TITLE

        if (currentAlarmId != 0L && currentAlarmId != alarmId) {
            clearAlertState(stopService = false)
        }

        currentAlarmId = alarmId
        currentOriginTriggerAt = intent?.getLongExtra(EXTRA_ORIGIN_TRIGGER_AT, 0L)
            ?.takeIf { it > 0L }
            ?: System.currentTimeMillis()

        acquireWakeLock()
        startForeground(alarmId.toInt(), buildNotification(alarmId, title))
        startAlerting()
        presentAlert(alarmId, title)
    }

    private fun presentAlert(alarmId: Long, title: String) {
        overlay.hide()
        launchAlarmScreen(alarmId, title)
    }

    private fun startAlerting() {
        stopAlerting()

        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                play()
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 500), 0))
    }

    private fun stopAlerting() {
        runCatching { ringtone?.stop() }
        runCatching { vibrator?.cancel() }
        ringtone = null
        vibrator = null
    }

    private fun dismissCurrentAlert() {
        val alarmId = currentAlarmId
        if (alarmId == 0L) {
            stopSelf()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            actionLogger.recordDismiss(alarmId)
            WidgetUpdater.updateAll(applicationContext)
        }
        clearAlertState()
    }

    private fun snoozeCurrentAlert(minutes: Int) {
        val alarmId = currentAlarmId
        if (alarmId == 0L) {
            stopSelf()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            scheduler.scheduleSnooze(
                alarmId = alarmId,
                originTriggerAt = currentOriginTriggerAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                minutes = minutes
            )
            actionLogger.recordSnooze(alarmId)
            WidgetUpdater.updateAll(applicationContext)
        }
        clearAlertState()
    }

    private fun clearAlertState(stopService: Boolean = true) {
        val alarmId = currentAlarmId
        overlay.hide()
        stopAlerting()
        if (alarmId != 0L) {
            dismissNotification(alarmId)
            sendAlertStoppedBroadcast(alarmId)
        }
        currentAlarmId = 0L
        currentOriginTriggerAt = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        if (stopService) stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "smartclock:alarm-alert"
        ).apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun dismissNotification(alarmId: Long) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(alarmId.toInt())
    }

    private fun buildNotification(alarmId: Long, title: String): Notification {
        val contentIntent = buildAlarmScreenPendingIntent(alarmId, title)
        return NotificationCompat.Builder(this, SmartClockApp.CHANNEL_ALARM)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("\u95f9\u949f\u65f6\u95f4\u5230")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "\u5173\u95ed",
                buildServiceActionPendingIntent(
                    action = ACTION_DISMISS,
                    requestCode = alarmId.toInt() + 10
                )
            )
            .addAction(
                android.R.drawable.ic_popup_reminder,
                "\u7a0d\u540e 5 \u5206\u949f",
                buildServiceActionPendingIntent(
                    action = ACTION_SNOOZE,
                    requestCode = alarmId.toInt() + 20,
                    minutes = DEFAULT_SNOOZE_MINUTES
                )
            )
            .build()
    }

    private fun buildBootstrapNotification(): Notification =
        NotificationCompat.Builder(this, SmartClockApp.CHANNEL_ALARM)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(DEFAULT_TITLE)
            .setContentText("\u6b63\u5728\u51c6\u5907\u63d0\u9192")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun buildAlarmScreenPendingIntent(alarmId: Long, title: String): PendingIntent {
        val intent = Intent(this, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildServiceActionPendingIntent(
        action: String,
        requestCode: Int,
        minutes: Int? = null
    ): PendingIntent {
        val intent = Intent(this, AlarmAlertService::class.java).apply {
            this.action = action
            putExtra(EXTRA_ALARM_ID, currentAlarmId)
            putExtra(EXTRA_ORIGIN_TRIGGER_AT, currentOriginTriggerAt)
            minutes?.let { putExtra(EXTRA_SNOOZE_MINUTES, it) }
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun launchAlarmScreen(alarmId: Long, title: String) {
        val pendingIntent = buildAlarmScreenPendingIntent(alarmId, title)
        val sent = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                pendingIntent.send(this, 0, null, null, null, null, options.toBundle())
            } else {
                pendingIntent.send()
            }
        }.isSuccess

        if (!sent) {
            runCatching {
                startActivity(
                    Intent(this, FullScreenAlarmActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(EXTRA_ALARM_ID, alarmId)
                        putExtra(EXTRA_TITLE, title)
                    }
                )
            }
        }
    }

    private fun sendAlertStoppedBroadcast(alarmId: Long) {
        sendBroadcast(
            Intent(ACTION_ALERT_STOPPED).apply {
                setPackage(packageName)
                putExtra(EXTRA_ALARM_ID, alarmId)
            }
        )
    }

    companion object {
        const val ACTION_START = "com.smartclock.action.ALERT_START"
        const val ACTION_DISMISS = "com.smartclock.action.ALERT_DISMISS"
        const val ACTION_SNOOZE = "com.smartclock.action.ALERT_SNOOZE"
        const val ACTION_ALERT_STOPPED = "com.smartclock.action.ALERT_STOPPED"

        const val EXTRA_ALARM_ID = "alert_alarm_id"
        const val EXTRA_TITLE = "alert_title"
        const val EXTRA_ORIGIN_TRIGGER_AT = "alert_origin_trigger_at"
        const val EXTRA_SNOOZE_MINUTES = "alert_snooze_minutes"

        private const val DEFAULT_TITLE = "\u95f9\u949f"
        private const val DEFAULT_SNOOZE_MINUTES = 5
        private const val BOOTSTRAP_NOTIFICATION_ID = 0x53434C4B

        fun start(context: Context, alarmId: Long, title: String, originTriggerAt: Long) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmAlertService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_ALARM_ID, alarmId)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_ORIGIN_TRIGGER_AT, originTriggerAt)
                }
            )
        }

        fun dismiss(context: Context, alarmId: Long) {
            context.startService(
                Intent(context, AlarmAlertService::class.java).apply {
                    action = ACTION_DISMISS
                    putExtra(EXTRA_ALARM_ID, alarmId)
                }
            )
        }

        fun snooze(context: Context, alarmId: Long, minutes: Int) {
            context.startService(
                Intent(context, AlarmAlertService::class.java).apply {
                    action = ACTION_SNOOZE
                    putExtra(EXTRA_ALARM_ID, alarmId)
                    putExtra(EXTRA_SNOOZE_MINUTES, minutes)
                }
            )
        }
    }
}
