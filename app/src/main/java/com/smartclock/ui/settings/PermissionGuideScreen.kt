package com.smartclock.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartclock.ui.component.PermissionGuideCard

@Composable
fun PermissionGuideScreen(
    onDone: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = vm.uiState.collectAsStateWithLifecycle().value

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshPermissionChecks()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("首次使用引导", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "为了保证闹钟准时提醒，建议先完成这几个系统开关。",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                )

                PermissionGuideCard(
                    permission = state.exactAlarm.title,
                    granted = state.exactAlarm.granted,
                    onFix = { openExactAlarmSettingsGuide(context) },
                    modifier = Modifier.fillMaxWidth()
                )
                PermissionGuideCard(
                    permission = state.notifications.title,
                    granted = state.notifications.granted,
                    onFix = { openNotificationSettingsGuide(context) },
                    modifier = Modifier.fillMaxWidth()
                )
                PermissionGuideCard(
                    permission = state.overlay.title,
                    granted = state.overlay.granted,
                    onFix = { openOverlaySettingsGuide(context) },
                    modifier = Modifier.fillMaxWidth()
                )
                PermissionGuideCard(
                    permission = state.batteryOptimization.title,
                    granted = state.batteryOptimization.granted,
                    onFix = { openBatteryOptimizationSettingsGuide(context) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.missingChecks.isEmpty()) {
                    Text(
                        "关键权限已就绪，可以开始使用。",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { vm.completePermissionGuide(onDone) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.missingChecks.isEmpty()) "开始使用" else "继续进入应用")
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text("打开应用详情")
                }
            }
        }
    }
}

private fun openExactAlarmSettingsGuide(context: android.content.Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        } else {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }
}

private fun openNotificationSettingsGuide(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        )
    }
}

private fun openOverlaySettingsGuide(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        )
    }
}

private fun openBatteryOptimizationSettingsGuide(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
