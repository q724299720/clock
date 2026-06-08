package com.smartclock.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartclock.data.sync.SyncTriggerSource
import com.smartclock.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusCenterScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val state = vm.uiState.collectAsStateWithLifecycle().value
    val syncState = state.syncState

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("同步状态中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SyncCenterCard(
                    icon = Icons.Default.Sync,
                    title = if (state.loggedIn) "当前为云同步模式" else "当前为本地模式",
                    lines = listOf(
                        "最近一次同步来源：${syncTriggerSourceLabel(syncState.lastTriggerSource)}",
                        "最后尝试时间：${syncState.lastAttemptAt?.let(TimeFormat::full) ?: "暂无"}",
                        "最后成功时间：${syncState.lastSuccessAt?.let(TimeFormat::full) ?: "暂无"}"
                    )
                )
            }

            item {
                SyncCenterCard(
                    icon = Icons.Default.VerifiedUser,
                    title = "同步队列",
                    lines = listOf(
                        "待上传提醒：${syncState.pendingAlarmCount}",
                        "待删除提醒：${syncState.pendingDeleteCount}",
                        "待上传日志：${syncState.pendingLogCount}"
                    )
                )
            }

            item {
                SyncCenterCard(
                    icon = Icons.Default.Security,
                    title = "最近一次结果",
                    lines = listOf(
                        "推送提醒：${syncState.pushedAlarmCount}",
                        "拉取云端数据：${syncState.pulledAlarmCount}",
                        "上传日志：${syncState.pushedLogCount}",
                        "精准闹钟：${if (syncState.exactAlarmDegraded) "已降级，可能不准时" else "可用"}"
                    ),
                    emphasis = if (syncState.exactAlarmDegraded) {
                        "系统当前只能使用降级调度，提醒时间可能会有偏差。"
                    } else {
                        syncState.lastErrorMessage
                    },
                    emphasisIsError = syncState.exactAlarmDegraded || !syncState.lastErrorMessage.isNullOrBlank()
                )
            }

            item {
                Button(
                    onClick = { vm.syncNow() },
                    enabled = state.loggedIn && !state.syncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.syncing) "同步中..." else "立即同步")
                }
            }
        }
    }
}

@Composable
private fun SyncCenterCard(
    icon: ImageVector,
    title: String,
    lines: List<String>,
    emphasis: String? = null,
    emphasisIsError: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!emphasis.isNullOrBlank()) {
                Text(
                    text = emphasis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (emphasisIsError) MaterialTheme.colorScheme.error else Color(0xFF20B15A)
                )
            }
        }
    }
}

private fun syncTriggerSourceLabel(source: SyncTriggerSource?): String = when (source) {
    SyncTriggerSource.APP_START -> "应用启动"
    SyncTriggerSource.USER_ACTION -> "用户操作"
    SyncTriggerSource.LOGIN -> "登录"
    SyncTriggerSource.REGISTER -> "注册"
    SyncTriggerSource.MANUAL -> "手动同步"
    SyncTriggerSource.PERIODIC -> "周期同步"
    SyncTriggerSource.WORKER -> "后台任务"
    null -> "暂无"
}
