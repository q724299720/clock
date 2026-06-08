package com.smartclock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.smartclock.data.local.SessionStore
import com.smartclock.ui.SmartClockNav
import com.smartclock.ui.theme.SmartClockTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

data class MainLaunchRequest(
    val editAlarmId: Long? = null
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var session: SessionStore
    private var launchRequest by mutableStateOf<MainLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchRequest = extractLaunchRequest(intent)
        setContent {
            SmartClockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val permissionGuideDone: Boolean? by session.permissionGuideDoneFlow
                        .collectAsState(initial = null)

                    if (permissionGuideDone == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        SmartClockNav(
                            needsPermissionGuide = permissionGuideDone != true,
                            launchRequest = launchRequest,
                            onLaunchHandled = { launchRequest = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = extractLaunchRequest(intent)
    }

    private fun extractLaunchRequest(intent: Intent?): MainLaunchRequest? {
        val alarmId = intent?.getLongExtra(EXTRA_OPEN_ALARM_ID, 0L) ?: 0L
        return if (alarmId > 0L) {
            MainLaunchRequest(editAlarmId = alarmId)
        } else {
            null
        }
    }

    companion object {
        const val EXTRA_OPEN_ALARM_ID = "open_alarm_id"
    }
}
