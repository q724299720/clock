package com.smartclock.ui.home.reminder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.CalendarType
import com.smartclock.domain.model.CountdownRuntime
import com.smartclock.domain.model.CountdownStatus
import com.smartclock.domain.model.ScheduleMode
import com.smartclock.ui.alarm.AlarmUiEvent
import com.smartclock.ui.alarm.AlarmViewModel
import com.smartclock.ui.theme.TimeDisplayStyle
import com.smartclock.util.ReminderScheduleResolver
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

private enum class ReminderTimeFilter(val label: String) {
    ALL("全部"),
    TODAY("今天"),
    WEEK("本周"),
    PAST("已过期")
}

private enum class ReminderTypeFilter(val label: String) {
    ALL("全部"),
    NORMAL("普通"),
    ANNIVERSARY("纪念日"),
    COUNTDOWN("倒计时")
}

private data class ReminderSection(
    val key: String,
    val title: String,
    val alarms: List<Alarm>
)

private val sectionDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
private val monthDayFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReminderInboxScreen(
    onEdit: (Long) -> Unit,
    onEditNext: (Long) -> Unit,
    onOpenDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    vm: AlarmViewModel = hiltViewModel()
) {
    val alarms by vm.alarms.collectAsState()
    val countdownRuntime by vm.countdownRuntime.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var timeFilter by rememberSaveable { mutableStateOf(ReminderTimeFilter.ALL) }
    var typeFilter by rememberSaveable { mutableStateOf(ReminderTypeFilter.ALL) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showTypeSheet by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

    LaunchedEffect(vm) {
        vm.events.collectLatest { event ->
            when (event) {
                is AlarmUiEvent.Deleted -> {
                    val message = if (event.count == 1) {
                        "已删除 1 条提醒"
                    } else {
                        "已删除 ${event.count} 条提醒"
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = "撤销"
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.restoreDeletedMany(event.trashIds)
                    }
                }
            }
        }
    }

    val filteredAlarms = remember(alarms, countdownRuntime, timeFilter, typeFilter, searchQuery) {
        alarms
            .filterBy(typeFilter)
            .filterBy(timeFilter, countdownRuntime)
            .filterBySearch(searchQuery, countdownRuntime)
            .sortedBy { reminderSortTime(it, countdownRuntime) }
    }

    val sections = remember(filteredAlarms, countdownRuntime) {
        buildReminderSections(filteredAlarms, countdownRuntime)
    }

    val enabledCount = remember(alarms) { alarms.count { it.enabled } }
    val visibleSelectedAlarms = remember(filteredAlarms, selectedIds) {
        filteredAlarms.filter { it.id in selectedIds }
    }
    val allSelected = filteredAlarms.isNotEmpty() && filteredAlarms.all { it.id in selectedIds }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            ReminderHeader(
                enabledCount = enabledCount,
                searchVisible = searchVisible,
                onToggleSearch = {
                    searchVisible = !searchVisible
                    if (!searchVisible) searchQuery = ""
                },
                onOpenTypeFilter = { showTypeSheet = true },
                showMoreMenu = showMoreMenu,
                onShowMoreMenu = { showMoreMenu = true },
                onDismissMoreMenu = { showMoreMenu = false },
                onOpenDeleted = {
                    showMoreMenu = false
                    onOpenDeleted()
                },
                onToggleSelectionMode = {
                    showMoreMenu = false
                    selectionMode = !selectionMode
                    if (!selectionMode) selectedIds = emptySet()
                }
            )

            if (searchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    singleLine = true,
                    placeholder = { Text("搜索提醒标题、备注、时间") },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清空搜索")
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReminderTimeFilter.entries.forEach { item ->
                    TimeFilterPill(
                        text = item.label,
                        selected = item == timeFilter,
                        modifier = Modifier.weight(1f),
                        onClick = { timeFilter = item }
                    )
                }
            }

            if (selectionMode) {
                SelectionActionBar(
                    count = selectedIds.size,
                    allSelected = allSelected,
                    hasSelection = selectedIds.isNotEmpty(),
                    onToggleSelectAll = {
                        selectedIds = if (allSelected) emptySet() else filteredAlarms.mapTo(linkedSetOf()) { it.id }
                    },
                    onEnable = { vm.toggleMany(visibleSelectedAlarms, true) },
                    onDisable = { vm.toggleMany(visibleSelectedAlarms, false) },
                    onDelete = {
                        vm.deleteMany(visibleSelectedAlarms)
                        selectedIds = emptySet()
                        selectionMode = false
                    }
                )
            }

            if (sections.isEmpty()) {
                EmptyReminderState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 32.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = if (selectionMode) 12.dp else 18.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    sections.forEach { section ->
                        stickyHeader(key = "${section.key}_header") {
                            ReminderSectionHeader(section.title)
                        }
                        items(section.alarms, key = { it.id }) { alarm ->
                            ReminderCardCompact(
                                alarm = alarm,
                                countdownRuntime = countdownRuntime?.takeIf { it.alarmId == alarm.id },
                                selectionMode = selectionMode,
                                selected = alarm.id in selectedIds,
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = selectedIds.toggle(alarm.id)
                                    } else {
                                        onEdit(alarm.id)
                                    }
                                },
                                onLongPress = {
                                    if (selectionMode) {
                                        selectedIds = selectedIds.toggle(alarm.id)
                                    } else {
                                        selectionMode = true
                                        selectedIds = selectedIds + alarm.id
                                    }
                                },
                                onToggle = { enabled -> vm.toggle(alarm, enabled) },
                                onSkipToday = if (alarm.type == AlarmType.ONCE || alarm.type == AlarmType.COUNTDOWN) {
                                    null
                                } else {
                                    { vm.skipToday(alarm) }
                                },
                                onEditNext = if (alarm.type == AlarmType.ONCE || alarm.type == AlarmType.COUNTDOWN) {
                                    null
                                } else {
                                    { onEditNext(alarm.id) }
                                },
                                onDelete = { vm.delete(alarm) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTypeSheet) {
        TypeFilterSheet(
            selected = typeFilter,
            onDismiss = { showTypeSheet = false },
            onSelected = {
                typeFilter = it
                showTypeSheet = false
            }
        )
    }
}

@Composable
private fun ReminderHeader(
    enabledCount: Int,
    searchVisible: Boolean,
    onToggleSearch: () -> Unit,
    onOpenTypeFilter: () -> Unit,
    showMoreMenu: Boolean,
    onShowMoreMenu: () -> Unit,
    onDismissMoreMenu: () -> Unit,
    onOpenDeleted: () -> Unit,
    onToggleSelectionMode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "提醒",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 36.sp),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "已启用 $enabledCount 条提醒",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (searchVisible) "关闭搜索" else "搜索"
                )
            }
            IconButton(onClick = onOpenTypeFilter) {
                Icon(Icons.Default.Tune, contentDescription = "类型筛选")
            }
            Box {
                IconButton(onClick = onShowMoreMenu) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = onDismissMoreMenu
                ) {
                    DropdownMenuItem(
                        text = { Text("批量选择") },
                        onClick = onToggleSelectionMode
                    )
                    DropdownMenuItem(
                        text = { Text("最近删除") },
                        onClick = onOpenDeleted
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeFilterPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    allSelected: Boolean,
    hasSelection: Boolean,
    onToggleSelectAll: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = "已选择 $count 项",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onToggleSelectAll) {
                    Text(if (allSelected) "取消全选" else "全选")
                }
                Row {
                    TextButton(onClick = onEnable, enabled = hasSelection) { Text("启用") }
                    TextButton(onClick = onDisable, enabled = hasSelection) { Text("停用") }
                    TextButton(onClick = onDelete, enabled = hasSelection) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun ReminderSectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 6.dp, bottom = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 18.sp),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyReminderState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "暂无提醒",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "点击右下角加号创建提醒",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReminderCardCompact(
    alarm: Alarm,
    countdownRuntime: CountdownRuntime?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {},
    onSkipToday: (() -> Unit)? = null,
    onEditNext: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    showActions: Boolean = true
) {
    val now by produceState(
        initialValue = System.currentTimeMillis(),
        countdownRuntime?.alarmId,
        countdownRuntime?.status,
        countdownRuntime?.endAt,
        countdownRuntime?.remainingSec
    ) {
        value = System.currentTimeMillis()
        if (countdownRuntime?.status == CountdownStatus.RUNNING) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                value = System.currentTimeMillis()
            }
        }
    }

    var showActionsMenu by remember(alarm.id) { mutableStateOf(false) }
    val supportsNextActions = alarm.type != AlarmType.ONCE && alarm.type != AlarmType.COUNTDOWN
    val dividerHeight = 42.dp
    val timeAreaWidth = if (alarm.type == AlarmType.COUNTDOWN) 136.dp else 128.dp
    val timeFontSize = if (alarm.type == AlarmType.COUNTDOWN) 22.sp else 31.5.sp
    val timeLineHeight = if (alarm.type == AlarmType.COUNTDOWN) 24.sp else 31.5.sp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f)
        ),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(timeAreaWidth)
                        .height(dividerHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = timeText(alarm, countdownRuntime, now),
                        style = TimeDisplayStyle.copy(
                            fontSize = timeFontSize,
                            lineHeight = timeLineHeight
                        ),
                        color = if (alarm.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(end = if (!selectionMode && showActions) 24.dp else 0.dp)
                    )

                    if (!selectionMode && showActions) {
                        Box(modifier = Modifier.align(Alignment.TopEnd)) {
                            Surface(
                                modifier = Modifier.combinedClickable(onClick = { showActionsMenu = true }),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "更多",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showActionsMenu,
                                onDismissRequest = { showActionsMenu = false }
                            ) {
                                if (supportsNextActions && onSkipToday != null) {
                                    DropdownMenuItem(
                                        text = { Text("跳过今天") },
                                        onClick = {
                                            showActionsMenu = false
                                            onSkipToday()
                                        }
                                    )
                                }
                                if (supportsNextActions && onEditNext != null) {
                                    DropdownMenuItem(
                                        text = { Text("仅本次修改") },
                                        onClick = {
                                            showActionsMenu = false
                                            onEditNext()
                                        }
                                    )
                                }
                                if (onDelete != null) {
                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = {
                                            showActionsMenu = false
                                            onDelete()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(1.dp)
                        .height(dividerHeight)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                            RoundedCornerShape(999.dp)
                        )
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = alarm.title.ifBlank {
                                if (alarm.type == AlarmType.COUNTDOWN) "倒计时" else "闹钟"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 18.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        ) {
                            Text(
                                text = alarmTypeLabel(alarm),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = reminderSubtitle(alarm, countdownRuntime, now),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp, lineHeight = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (!selectionMode) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Switch(
                        checked = alarm.enabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.graphicsLayer {
                            scaleX = 0.82f
                            scaleY = 0.82f
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
        }
    }
}

/*
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReminderCard(
    alarm: Alarm,
    countdownRuntime: CountdownRuntime?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {},
    onSkipToday: (() -> Unit)? = null,
    onEditNext: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    showActions: Boolean = true
) {
    val now by produceState(
        initialValue = System.currentTimeMillis(),
        countdownRuntime?.alarmId,
        countdownRuntime?.status,
        countdownRuntime?.endAt,
        countdownRuntime?.remainingSec
    ) {
        value = System.currentTimeMillis()
        if (countdownRuntime?.status == CountdownStatus.RUNNING) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                value = System.currentTimeMillis()
            }
        }
    }

    var showActionsMenu by remember(alarm.id) { mutableStateOf(false) }
    val supportsNextActions = alarm.type != AlarmType.ONCE && alarm.type != AlarmType.COUNTDOWN
    val dividerHeight = 42.dp
    val timeAreaWidth = if (alarm.type == AlarmType.COUNTDOWN) 126.dp else 112.dp
    val timeFontSize = if (alarm.type == AlarmType.COUNTDOWN) 22.sp else 31.5.sp
    val timeLineHeight = if (alarm.type == AlarmType.COUNTDOWN) 24.sp else 31.5.sp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f)
        ),
        shadowElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = if (selectionMode) 0.dp else 60.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(timeAreaWidth)
                            .height(dividerHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = timeText(alarm, countdownRuntime, now),
                            style = TimeDisplayStyle.copy(
                                fontSize = timeFontSize,
                                lineHeight = timeLineHeight
                            ),
                            color = if (alarm.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            textAlign = TextAlign.Start
                        )
                        if (!selectionMode && showActions) {
                            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                Surface(
                                    modifier = Modifier.combinedClickable(onClick = { showActionsMenu = true }),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "鏇村",
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showActionsMenu,
                                    onDismissRequest = { showActionsMenu = false }
                                ) {
                                    if (supportsNextActions && onSkipToday != null) {
                                        DropdownMenuItem(
                                            text = { Text("璺宠繃浠婂ぉ") },
                                            onClick = {
                                                showActionsMenu = false
                                                onSkipToday()
                                            }
                                        )
                                    }
                                    if (supportsNextActions && onEditNext != null) {
                                        DropdownMenuItem(
                                            text = { Text("浠呮湰娆′慨鏀?) },
                                            onClick = {
                                                showActionsMenu = false
                                                onEditNext()
                                            }
                                        )
                                    }
                                    if (onDelete != null) {
                                        DropdownMenuItem(
                                            text = { Text("鍒犻櫎") },
                                            onClick = {
                                                showActionsMenu = false
                                                onDelete()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .width(1.dp)
                            .height(dividerHeight)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                                RoundedCornerShape(999.dp)
                            )
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = alarm.title.ifBlank {
                                    if (alarm.type == AlarmType.COUNTDOWN) "倒计时" else "闹钟"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 18.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            ) {
                                Text(
                                    text = alarmTypeLabel(alarm),
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = reminderSubtitle(alarm, countdownRuntime, now),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp, lineHeight = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (!selectionMode) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(52.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (showActions) {
                        Box {
                            Surface(
                                modifier = Modifier.combinedClickable(onClick = { showActionsMenu = true }),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "更多",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showActionsMenu,
                                onDismissRequest = { showActionsMenu = false }
                            ) {
                                if (supportsNextActions && onSkipToday != null) {
                                    DropdownMenuItem(
                                        text = { Text("跳过今天") },
                                        onClick = {
                                            showActionsMenu = false
                                            onSkipToday()
                                        }
                                    )
                                }
                                if (supportsNextActions && onEditNext != null) {
                                    DropdownMenuItem(
                                        text = { Text("仅本次修改") },
                                        onClick = {
                                            showActionsMenu = false
                                            onEditNext()
                                        }
                                    )
                                }
                                if (onDelete != null) {
                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = {
                                            showActionsMenu = false
                                            onDelete()
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Switch(
                        checked = alarm.enabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.graphicsLayer {
                            scaleX = 0.82f
                            scaleY = 0.82f
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
        }
    }
}
*/

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeFilterSheet(
    selected: ReminderTypeFilter,
    onDismiss: () -> Unit,
    onSelected: (ReminderTypeFilter) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "筛选类型",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            ReminderTypeFilter.entries.forEachIndexed { index, item ->
                ListItem(
                    headlineContent = { Text(item.label) },
                    supportingContent = {
                        if (item == selected) {
                            Text("当前筛选")
                        }
                    },
                    trailingContent = {
                        if (item == selected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.clickable(onClick = { onSelected(item) })
                )
                if (index != ReminderTypeFilter.entries.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun List<Alarm>.filterBy(typeFilter: ReminderTypeFilter): List<Alarm> = filter { alarm ->
    when (typeFilter) {
        ReminderTypeFilter.ALL -> true
        ReminderTypeFilter.NORMAL -> alarm.type != AlarmType.ANNIVERSARY && alarm.type != AlarmType.COUNTDOWN
        ReminderTypeFilter.ANNIVERSARY -> alarm.type == AlarmType.ANNIVERSARY
        ReminderTypeFilter.COUNTDOWN -> alarm.type == AlarmType.COUNTDOWN
    }
}

private fun List<Alarm>.filterBy(
    timeFilter: ReminderTimeFilter,
    countdownRuntime: CountdownRuntime?
): List<Alarm> {
    if (timeFilter == ReminderTimeFilter.ALL) return this

    val today = LocalDate.now()
    val weekEnd = today.plusDays(6)
    val now = System.currentTimeMillis()
    return filter { alarm ->
        val runtime = countdownRuntime?.takeIf { it.alarmId == alarm.id }
        val triggerAt = reminderReferenceTime(alarm, runtime, now)
        when (timeFilter) {
            ReminderTimeFilter.ALL -> true
            ReminderTimeFilter.TODAY -> triggerAt?.toLocalDate() == today
            ReminderTimeFilter.WEEK -> triggerAt?.toLocalDate()?.let { !it.isBefore(today) && !it.isAfter(weekEnd) } == true
            ReminderTimeFilter.PAST -> isPastAlarm(alarm, runtime, now)
        }
    }
}

private fun List<Alarm>.filterBySearch(
    query: String,
    countdownRuntime: CountdownRuntime?
): List<Alarm> {
    val normalized = query.trim()
    if (normalized.isBlank()) return this
    return filter { alarm ->
        val runtime = countdownRuntime?.takeIf { it.alarmId == alarm.id }
        alarm.searchBlob(runtime).contains(normalized, ignoreCase = true)
    }
}

private fun buildReminderSections(
    alarms: List<Alarm>,
    countdownRuntime: CountdownRuntime?
): List<ReminderSection> {
    if (alarms.isEmpty()) return emptyList()

    val now = System.currentTimeMillis()
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val weekEnd = today.plusDays(6)
    val monthEnd = today.plusDays(29)
    val yearEnd = today.plusDays(364)

    val countdowns = mutableListOf<Alarm>()
    val todayItems = mutableListOf<Alarm>()
    val tomorrowItems = mutableListOf<Alarm>()
    val weekItems = mutableListOf<Alarm>()
    val monthItems = mutableListOf<Alarm>()
    val yearItems = mutableListOf<Alarm>()
    val pastItems = mutableListOf<Alarm>()

    alarms.forEach { alarm ->
        val runtime = countdownRuntime?.takeIf { it.alarmId == alarm.id }
        if (alarm.type == AlarmType.COUNTDOWN && runtime?.status == CountdownStatus.RUNNING) {
            countdowns += alarm
            return@forEach
        }

        if (isPastAlarm(alarm, runtime, now)) {
            pastItems += alarm
            return@forEach
        }

        val triggerDate = reminderReferenceTime(alarm, runtime, now)?.toLocalDate() ?: return@forEach
        when {
            triggerDate == today -> todayItems += alarm
            triggerDate == tomorrow -> tomorrowItems += alarm
            triggerDate <= weekEnd -> weekItems += alarm
            triggerDate <= monthEnd -> monthItems += alarm
            else -> {
                if (triggerDate <= yearEnd) {
                    yearItems += alarm
                } else {
                    yearItems += alarm
                }
            }
        }
    }

    return buildList {
        if (countdowns.isNotEmpty()) add(ReminderSection("countdown", "进行中倒计时", countdowns))
        if (todayItems.isNotEmpty()) add(ReminderSection("today", "今天", todayItems))
        if (tomorrowItems.isNotEmpty()) add(ReminderSection("tomorrow", "明天", tomorrowItems))
        if (weekItems.isNotEmpty()) add(ReminderSection("week", "一周内", weekItems))
        if (monthItems.isNotEmpty()) add(ReminderSection("month", "一月内", monthItems))
        if (yearItems.isNotEmpty()) add(ReminderSection("year", "一年内", yearItems))
        if (pastItems.isNotEmpty()) add(ReminderSection("past", "已过期", pastItems))
    }
}

private fun reminderSortTime(alarm: Alarm, countdownRuntime: CountdownRuntime?): Long {
    val runtime = countdownRuntime?.takeIf { it.alarmId == alarm.id }
    return reminderReferenceTime(alarm, runtime, System.currentTimeMillis()) ?: Long.MAX_VALUE
}

private fun reminderReferenceTime(
    alarm: Alarm,
    runtime: CountdownRuntime?,
    now: Long
): Long? = when (alarm.type) {
    AlarmType.COUNTDOWN -> when (runtime?.status) {
        CountdownStatus.RUNNING -> runtime.endAt ?: alarm.triggerTime
        CountdownStatus.PAUSED -> now + runtime.remainingSec * 1000L
        null -> alarm.triggerTime
    }
    AlarmType.ONCE -> alarm.triggerTime
    else -> ReminderScheduleResolver.nextTrigger(alarm, now) ?: alarm.triggerTime
}

private fun isPastAlarm(
    alarm: Alarm,
    runtime: CountdownRuntime?,
    now: Long
): Boolean = when (alarm.type) {
    AlarmType.COUNTDOWN -> runtime?.status != CountdownStatus.RUNNING &&
        (alarm.triggerTime ?: Long.MAX_VALUE) <= now
    AlarmType.ONCE -> (alarm.triggerTime ?: Long.MAX_VALUE) <= now
    else -> false
}

private fun Alarm.searchBlob(runtime: CountdownRuntime?): String = buildString {
    append(title)
    append(' ')
    append(note.orEmpty())
    append(' ')
    append(label.orEmpty())
    append(' ')
    append(alarmTypeLabel(this@searchBlob))
    append(' ')
    append(reminderSubtitle(this@searchBlob, runtime, System.currentTimeMillis()))
    append(' ')
    append(timeText(this@searchBlob, runtime, System.currentTimeMillis()))
}

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun timeText(
    alarm: Alarm,
    countdownRuntime: CountdownRuntime?,
    now: Long
): String = when (alarm.type) {
    AlarmType.COUNTDOWN -> formatDuration(countdownRuntime?.remainingAt(now) ?: alarm.durationSec ?: 0)
    else -> formatHm(alarm.triggerTime)
}

private fun reminderSubtitle(
    alarm: Alarm,
    runtime: CountdownRuntime?,
    now: Long
): String = when (alarm.type) {
    AlarmType.ONCE -> formatFull(alarm.triggerTime)
    AlarmType.COUNTDOWN -> when (runtime?.status) {
        CountdownStatus.RUNNING -> "剩余 ${formatDuration(runtime.remainingAt(now))}"
        CountdownStatus.PAUSED -> "已暂停 ${formatDuration(runtime.remainingSec)}"
        null -> "倒计时 ${formatDuration(alarm.durationSec ?: 0)}"
    }
    AlarmType.WEEKLY -> {
        if (alarm.scheduleMode == ScheduleMode.WORKDAY_CN) {
            "工作日"
        } else {
            weekdaysText(alarm.repeatWeekdays)
        }
    }
    AlarmType.MONTHLY -> monthDaysTextFixed(alarm.repeatMonthDays, alarm.intervalMonths)
    AlarmType.ANNIVERSARY -> {
        val prefix = when (alarm.anniversaryCalendar) {
            CalendarType.LUNAR -> "农历"
            CalendarType.SOLAR -> "公历"
        }
        val interval = if (alarm.intervalYears > 1) "每 ${alarm.intervalYears} 年" else "每年"
        "$interval $prefix ${monthDayFormat.format(Date(alarm.triggerTime ?: 0L))}"
    }
}

private fun alarmTypeLabel(alarm: Alarm): String = when (alarm.type) {
    AlarmType.ONCE -> "单次"
    AlarmType.COUNTDOWN -> "倒计时"
    AlarmType.WEEKLY -> if (alarm.scheduleMode == ScheduleMode.WORKDAY_CN) "工作日" else "每周"
    AlarmType.MONTHLY -> if (alarm.intervalMonths > 1) "每 ${alarm.intervalMonths} 月" else "每月"
    AlarmType.ANNIVERSARY -> if (alarm.intervalYears > 1) "每 ${alarm.intervalYears} 年" else "每年"
}

private fun weekdaysText(bits: Int): String {
    if (bits == 0) return ""
    val names = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    val all = (0..6).all { (bits and (1 shl it)) != 0 }
    if (all) return "每天"
    return (0..6)
        .filter { (bits and (1 shl it)) != 0 }
        .joinToString(" ") { names[it] }
}

private fun monthDaysTextFixed(bits: Int, intervalMonths: Int): String {
    val days = (1..31).filter { (bits and (1 shl it)) != 0 }
    if (days.isEmpty()) return ""
    val prefix = if (intervalMonths > 1) "每 $intervalMonths 个月" else "每月"
    return "$prefix ${days.joinToString(" ")} 日"
}

/*
private fun monthDaysText(bits: Int, intervalMonths: Int): String {
    val days = (1..31).filter { (bits and (1 shl it)) != 0 }
    if (days.isEmpty()) return ""
    val prefix = if (intervalMonths > 1) "每 $intervalMonths 月" else "每月"
    return "$prefix ${days.joinToString(" ")} 日"
}

*/

private fun formatHm(epoch: Long?): String =
    if (epoch == null) "--:--" else timeFormat.format(Date(epoch))

private fun formatFull(epoch: Long?): String =
    if (epoch == null) "--" else sectionDateFormat.format(Date(epoch))

private fun formatDuration(totalSec: Int): String {
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun Set<Long>.toggle(id: Long): Set<Long> =
    if (id in this) this - id else this + id
