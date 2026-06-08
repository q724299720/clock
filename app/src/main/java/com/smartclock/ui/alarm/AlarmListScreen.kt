package com.smartclock.ui.alarm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartclock.domain.model.AlarmType
import com.smartclock.ui.component.AlarmCard
import com.smartclock.ui.component.EmptyState

private val TABS = listOf(
    "闹钟" to listOf(AlarmType.ONCE, AlarmType.WEEKLY, AlarmType.MONTHLY),
    "倒计时" to listOf(AlarmType.COUNTDOWN),
    "纪念日" to listOf(AlarmType.ANNIVERSARY)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    onAdd: (AlarmType) -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
    vm: AlarmViewModel = hiltViewModel()
) {
    val all by vm.alarms.collectAsStateWithLifecycle()
    val countdownRuntime by vm.countdownRuntime.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    val types = TABS[tab].second
    val list = all.filter { it.type in types }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能闹钟") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val defaultType = when (tab) {
                    1 -> AlarmType.COUNTDOWN
                    2 -> AlarmType.ANNIVERSARY
                    else -> AlarmType.ONCE
                }
                onAdd(defaultType)
            }) { Icon(Icons.Default.Add, "新建") }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.foundation.layout.Column {
                TabRow(selectedTabIndex = tab) {
                    TABS.forEachIndexed { i, (name, _) ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name) })
                    }
                }
                if (list.isEmpty()) {
                    EmptyState("暂无${TABS[tab].first}，点击 + 新建", Icons.Default.Notifications)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { alarm ->
                            AlarmCard(
                                alarm = alarm,
                                countdownRuntime = countdownRuntime?.takeIf { it.alarmId == alarm.id },
                                onToggle = { vm.toggle(alarm, it) },
                                onClick = { onEdit(alarm.id) },
                                onPauseCountdown = { vm.pauseCountdown(alarm) },
                                onResumeCountdown = { vm.resumeCountdown(alarm) },
                                onExtendCountdown = { vm.extendCountdown(alarm) },
                                onResetCountdown = { vm.resetCountdown(alarm) }
                            )
                        }
                    }
                }
            }
        }
    }
}
