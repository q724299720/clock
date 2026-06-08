package com.smartclock.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.smartclock.domain.model.AlarmType
import com.smartclock.ui.home.calendar.ReminderCalendarScreen
import com.smartclock.ui.home.reminder.ReminderInboxScreen
import com.smartclock.ui.settings.SettingsPanel
import com.smartclock.ui.template.TemplateScreen

private enum class HomeSection {
    REMINDERS,
    CALENDAR,
    TEMPLATES,
    SETTINGS
}

@Composable
fun HomeScreen(
    onAddReminder: (AlarmType) -> Unit,
    onAddTemplate: (AlarmType, String) -> Unit,
    onEditReminder: (Long) -> Unit,
    onEditNextReminder: (Long) -> Unit,
    onLogin: () -> Unit,
    onModeChanged: () -> Unit,
    onOpenSyncCenter: () -> Unit,
    onOpenDeletedReminders: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(HomeSection.REMINDERS) }
    var quickAddExpanded by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
        ),
        floatingActionButton = {
            if (section == HomeSection.REMINDERS || section == HomeSection.CALENDAR) {
                Box {
                    QuickAddFab(
                        onClick = { onAddReminder(AlarmType.ONCE) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            quickAddExpanded = true
                        }
                    )
                    DropdownMenu(
                        expanded = quickAddExpanded,
                        onDismissRequest = { quickAddExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("倒计时") },
                            onClick = {
                                quickAddExpanded = false
                                onAddReminder(AlarmType.COUNTDOWN)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("纪念日") },
                            onClick = {
                                quickAddExpanded = false
                                onAddReminder(AlarmType.ANNIVERSARY)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("模板提醒") },
                            onClick = {
                                quickAddExpanded = false
                                section = HomeSection.TEMPLATES
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = section == HomeSection.REMINDERS,
                        onClick = { section = HomeSection.REMINDERS },
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                        label = { Text("提醒") },
                        colors = navigationItemColors()
                    )
                    NavigationBarItem(
                        selected = section == HomeSection.CALENDAR,
                        onClick = { section = HomeSection.CALENDAR },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                        label = { Text("日历") },
                        colors = navigationItemColors()
                    )
                    NavigationBarItem(
                        selected = section == HomeSection.TEMPLATES,
                        onClick = { section = HomeSection.TEMPLATES },
                        icon = { Icon(Icons.Default.Widgets, contentDescription = null) },
                        label = { Text("模板") },
                        colors = navigationItemColors()
                    )
                    NavigationBarItem(
                        selected = section == HomeSection.SETTINGS,
                        onClick = { section = HomeSection.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("设置") },
                        colors = navigationItemColors()
                    )
                }
            }
        }
    ) { padding ->
        when (section) {
            HomeSection.REMINDERS -> ReminderInboxScreen(
                modifier = Modifier.padding(padding),
                onEdit = onEditReminder,
                onEditNext = onEditNextReminder,
                onOpenDeleted = onOpenDeletedReminders
            )

            HomeSection.CALENDAR -> ReminderCalendarScreen(
                modifier = Modifier.padding(padding),
                onEdit = onEditReminder,
                onAdd = onAddReminder
            )

            HomeSection.TEMPLATES -> TemplateScreen(
                modifier = Modifier.padding(padding),
                onSelectTemplate = onAddTemplate
            )

            HomeSection.SETTINGS -> SettingsPanel(
                modifier = Modifier.padding(padding),
                onLogin = onLogin,
                onModeChanged = onModeChanged,
                onOpenSyncCenter = onOpenSyncCenter
            )
        }
    }
}

@Composable
private fun navigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAddFab(
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(60.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "新建提醒"
            )
        }
    }
}
