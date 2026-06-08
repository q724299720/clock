package com.smartclock.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartclock.domain.model.AlarmLog
import com.smartclock.domain.model.AlarmLogAction
import com.smartclock.ui.component.BrandAction
import com.smartclock.ui.component.PageHero
import com.smartclock.ui.component.SectionHeading
import com.smartclock.ui.component.TimelyBrandBar
import com.smartclock.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onModeChanged: () -> Unit,
    onOpenSyncCenter: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
        SettingsPanel(
            modifier = Modifier.padding(padding),
            onLogin = onLogin,
            onModeChanged = onModeChanged,
            onOpenSyncCenter = onOpenSyncCenter,
            vm = vm
        )
    }
}

@Composable
fun SettingsPanel(
    modifier: Modifier = Modifier,
    onLogin: () -> Unit,
    onModeChanged: () -> Unit,
    onOpenSyncCenter: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = vm.uiState.collectAsStateWithLifecycle().value
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val isHuawei = Build.MANUFACTURER.contains("HUAWEI", ignoreCase = true) ||
        Build.BRAND.contains("HUAWEI", ignoreCase = true) ||
        Build.BRAND.contains("HONOR", ignoreCase = true)
    val permissionRows = remember(state, searchQuery) {
        permissionItems(state).filter { it.matchesSearch(searchQuery) }
    }
    val filteredLogs = remember(state.recentLogs, searchQuery) {
        state.recentLogs.filter { it.matchesSearch(searchQuery) }
    }
    val showSearchField = searchVisible || searchQuery.isNotBlank()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshPermissionChecks()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            TimelyBrandBar(
                trailingActions = listOf(
                    BrandAction(
                        icon = Icons.Default.Search,
                        contentDescription = "搜索设置",
                        onClick = { searchVisible = true }
                    ),
                    BrandAction(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "刷新权限状态",
                        onClick = { vm.refreshPermissionChecks() }
                    )
                )
            )
        }

        item {
            PageHero(
                title = "设置",
                subtitle = "管理同步、权限和最近提醒记录"
            )
        }

        if (showSearchField) {
            item {
                SettingsSearchField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        searchVisible = true
                    },
                    onClear = {
                        searchQuery = ""
                        searchVisible = false
                    }
                )
            }
        }

        item { SectionHeading("使用模式") }

        item { ModeStatusCard(loggedIn = state.loggedIn) }

        item {
            ActionRowCard(
                icon = Icons.Default.CloudUpload,
                title = if (state.loggedIn) "退出登录并切换回本地模式" else "登录并开启云同步",
                onClick = if (state.loggedIn) {
                    { vm.switchToLocalMode(onModeChanged) }
                } else {
                    onLogin
                }
            )
        }

        item {
            SyncActionCard(
                loggedIn = state.loggedIn,
                syncing = state.syncing,
                syncMessage = state.syncMessage,
                syncMessageIsError = state.syncMessageIsError,
                onClick = { vm.syncNow() }
            )
        }

        item {
            ActionRowCard(
                icon = Icons.Default.Security,
                title = "同步状态中心",
                onClick = onOpenSyncCenter
            )
        }

        item { SectionHeading("权限自检") }

        if (permissionRows.isEmpty()) {
            item {
                EmptyLogsCard(
                    text = if (searchQuery.isBlank()) {
                        "没有可展示的权限项。"
                    } else {
                        "没有找到匹配的权限项。"
                    }
                )
            }
        } else {
            items(permissionRows, key = { it.title }) { item ->
                PermissionItemCard(
                    item = item,
                    onClick = {
                        when (item.title) {
                            state.exactAlarm.title -> openExactAlarmSettings(context)
                            state.fullScreenIntent.title -> openFullScreenIntentSettings(context)
                            state.notifications.title -> openNotificationSettings(context)
                            state.overlay.title -> openOverlaySettings(context)
                            else -> openBatteryOptimizationSettings(context)
                        }
                    }
                )
            }
        }

        item {
            PermissionSummaryCard(allGranted = state.missingChecks.isEmpty())
        }

        if (isHuawei) {
            item { SectionHeading("华为设备排查") }
            item {
                HuaweiTipsCard(
                    onOpenSystemSettings = { openSystemSettings(context) },
                    onOpenNotificationSettings = { openNotificationSettings(context) },
                    onOpenBatterySettings = { openBatteryOptimizationSettings(context) },
                    onOpenAppDetails = { openAppDetails(context) }
                )
            }
        }

        item { SectionHeading("最近提醒日志") }

        if (filteredLogs.isEmpty()) {
            item {
                EmptyLogsCard(
                    text = if (searchQuery.isBlank()) {
                        "最近还没有提醒日志。"
                    } else {
                        "没有找到匹配的提醒日志。"
                    }
                )
            }
        } else {
            items(filteredLogs, key = { "${it.id}_${it.firedAt}" }) { log ->
                RecentLogCard(log)
            }
        }
    }
}

private data class PermissionRowUi(
    val title: String,
    val granted: Boolean,
    val icon: ImageVector
)

private fun permissionItems(state: SettingsUiState): List<PermissionRowUi> = listOf(
    PermissionRowUi(state.exactAlarm.title, state.exactAlarm.granted, Icons.Default.NotificationsActive),
    PermissionRowUi(state.fullScreenIntent.title, state.fullScreenIntent.granted, Icons.Default.PhoneAndroid),
    PermissionRowUi(state.notifications.title, state.notifications.granted, Icons.AutoMirrored.Filled.Message),
    PermissionRowUi(state.overlay.title, state.overlay.granted, Icons.Default.CropSquare),
    PermissionRowUi(state.batteryOptimization.title, state.batteryOptimization.granted, Icons.Default.BatteryFull)
)

private fun PermissionRowUi.matchesSearch(query: String): Boolean {
    val needle = query.trim()
    if (needle.isBlank()) return true
    return title.contains(needle, ignoreCase = true)
}

private fun AlarmLog.matchesSearch(query: String): Boolean {
    val needle = query.trim()
    if (needle.isBlank()) return true
    return listOf(
        logActionLabel(action),
        TimeFormat.full(firedAt),
        TimeFormat.hhmm(firedAt),
        alarmId.toString()
    ).any { it.contains(needle, ignoreCase = true) }
}

private fun logActionLabel(action: AlarmLogAction): String = when (action) {
    AlarmLogAction.DISMISS -> "已关闭"
    AlarmLogAction.SNOOZE -> "已稍后提醒"
    AlarmLogAction.MISSED -> "未处理"
}

@Composable
private fun ModeStatusCard(loggedIn: Boolean) {
    val title = if (loggedIn) "当前为云同步模式" else "当前为本地模式"
    val body = if (loggedIn) {
        "提醒会按当前账号同步到云端，并在本机恢复调度。"
    } else {
        "当前无需登录，提醒和日志只保存在本机。"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIcon(icon = Icons.Default.PhoneAndroid, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = title, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionRowCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SyncActionCard(
    loggedIn: Boolean,
    syncing: Boolean,
    syncMessage: String?,
    syncMessageIsError: Boolean,
    onClick: () -> Unit
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
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(icon = Icons.Default.Sync, tint = MaterialTheme.colorScheme.primary)
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(text = "云端同步", style = MaterialTheme.typography.titleLarge)
                    if (!syncMessage.isNullOrBlank()) {
                        Text(
                            text = syncMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (syncMessageIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                Button(onClick = onClick, enabled = loggedIn && !syncing) {
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                        Text("同步中")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                        Text(if (loggedIn) "立即同步" else "请先登录")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    item: PermissionRowUi,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIcon(icon = item.icon, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            )
            Text(
                text = if (item.granted) "已开启" else "未开启",
                color = if (item.granted) Color(0xFF20B15A) else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun PermissionSummaryCard(allGranted: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIcon(icon = Icons.Default.VerifiedUser, tint = MaterialTheme.colorScheme.primary, large = true)
            Column(modifier = Modifier.padding(start = 18.dp)) {
                Text(
                    text = if (allGranted) "关键权限已就绪" else "仍有权限未完成",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = if (allGranted) {
                        "精准闹钟、通知、悬浮窗和电池优化相关权限已配置完成。"
                    } else {
                        "建议先把关键权限补齐，再做真实到点提醒测试。"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun HuaweiTipsCard(
    onOpenSystemSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppDetails: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text("华为常见原因", style = MaterialTheme.typography.headlineLarge)
            Text(
                "1. 应用启动管理仍是自动管理，后台活动被系统拦截。\n" +
                    "2. 通知没有开启横幅、锁屏显示或提醒权限。\n" +
                    "3. 电池优化未放行，息屏后后台提醒受限。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
            FlowButtonsRow(
                titles = listOf(
                    "通知设置" to onOpenNotificationSettings,
                    "电池放行" to onOpenBatterySettings,
                    "系统设置" to onOpenSystemSettings,
                    "应用详情" to onOpenAppDetails
                ),
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowButtonsRow(
    titles: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        titles.forEach { (title, action) ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.clickable(onClick = action)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyLogsCard(text: String = "最近还没有提醒日志。") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp)
        )
    }
}

@Composable
private fun RecentLogCard(log: AlarmLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(logActionLabel(log.action), style = MaterialTheme.typography.titleLarge)
            Text(
                text = TimeFormat.full(log.firedAt),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun RoundIcon(
    icon: ImageVector,
    tint: Color,
    large: Boolean = false
) {
    Surface(
        color = tint.copy(alpha = 0.10f),
        shape = RoundedCornerShape(if (large) 30.dp else 24.dp)
    ) {
        Box(
            modifier = Modifier.size(if (large) 92.dp else 64.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(if (large) 42.dp else 28.dp)
            )
        }
    }
}

private fun openExactAlarmSettings(context: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        } else {
            openAppDetails(context)
        }
    }
}

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        )
    }
}

private fun openFullScreenIntentSettings(context: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:${context.packageName}")
                )
            )
        } else {
            openNotificationSettings(context)
        }
    }
}

private fun openOverlaySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        } else {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

private fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
}

private fun openSystemSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}

@Composable
private fun SettingsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("搜索权限、时间或动作") },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "清除搜索")
                }
            }
        }
    )
}
