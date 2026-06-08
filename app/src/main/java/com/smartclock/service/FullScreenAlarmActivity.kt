package com.smartclock.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.smartclock.util.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

private data class AlarmScreenState(
    val alarmId: Long = 0L,
    val title: String = "闹钟"
)

@AndroidEntryPoint
class FullScreenAlarmActivity : ComponentActivity() {

    private var uiState by mutableStateOf(AlarmScreenState())

    private val alertStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val stoppedAlarmId = intent?.getLongExtra(AlarmAlertService.EXTRA_ALARM_ID, 0L) ?: 0L
            if (stoppedAlarmId == uiState.alarmId) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        applyIntent(intent)
        registerAlertStoppedReceiver()

        setContent {
            BackHandler(onBack = ::dismissAlarm)
            AlarmScreen(
                title = uiState.title,
                onClose = ::dismissAlarm,
                onSnooze = ::snoozeAlarm
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent?) {
        val alarmId = intent?.getLongExtra(AlarmAlertService.EXTRA_ALARM_ID, 0L) ?: 0L
        val title = intent?.getStringExtra(AlarmAlertService.EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "闹钟"
        uiState = AlarmScreenState(alarmId = alarmId, title = title)
    }

    private fun dismissAlarm() {
        AlarmAlertService.dismiss(this, uiState.alarmId)
        finish()
    }

    private fun snoozeAlarm(minutes: Int) {
        AlarmAlertService.snooze(this, uiState.alarmId, minutes)
        finish()
    }

    private fun registerAlertStoppedReceiver() {
        val filter = IntentFilter(AlarmAlertService.ACTION_ALERT_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alertStoppedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(alertStoppedReceiver, filter)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(alertStoppedReceiver) }
        super.onDestroy()
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    onClose: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF08101F),
                        Color(0xFF101B30),
                        Color(0xFF0B1220)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(
                text = "提醒响铃",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = TimeFormat.hhmm(now),
                color = Color.White,
                fontSize = 64.sp,
                lineHeight = 68.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "当前闹钟正在响铃",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.06f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "稍后提醒",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(5, 10, 30).forEach { minutes ->
                            Button(
                                onClick = { onSnooze(minutes) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.10f),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("${minutes} 分钟")
                            }
                        }
                    }
                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF08101F)
                        )
                    ) {
                        Text("关闭", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
