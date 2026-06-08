package com.smartclock.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartclock.MainLaunchRequest
import com.smartclock.domain.model.AlarmType
import com.smartclock.ui.alarm.AlarmSaveMode
import com.smartclock.ui.alarm.AlarmEditScreen
import com.smartclock.ui.auth.AuthScreen
import com.smartclock.ui.home.HomeScreen
import com.smartclock.ui.home.reminder.DeletedRemindersScreen
import com.smartclock.ui.settings.PermissionGuideScreen
import com.smartclock.ui.settings.SettingsScreen
import com.smartclock.ui.settings.SyncStatusCenterScreen

object Routes {
    const val AUTH = "auth"
    const val GUIDE = "guide"
    const val HOME = "home"
    const val EDIT = "edit/{type}/{id}?template={template}&mode={mode}"
    const val SETTINGS = "settings"
    const val SYNC_CENTER = "sync-center"
    const val DELETED_REMINDERS = "deleted-reminders"

    fun edit(
        type: AlarmType,
        id: Long,
        template: String? = null,
        saveMode: AlarmSaveMode = AlarmSaveMode.NORMAL
    ): String {
        val encodedTemplate = template ?: ""
        return "edit/${type.code}/$id?template=$encodedTemplate&mode=${saveMode.name}"
    }

    fun editExisting(id: Long, saveMode: AlarmSaveMode = AlarmSaveMode.NORMAL): String =
        "edit/0/$id?template=&mode=${saveMode.name}"
}

@Composable
fun SmartClockNav(
    needsPermissionGuide: Boolean,
    launchRequest: MainLaunchRequest? = null,
    onLaunchHandled: () -> Unit = {}
) {
    val nav = rememberNavController()
    val startDestination = if (needsPermissionGuide) Routes.GUIDE else Routes.HOME

    LaunchedEffect(launchRequest?.editAlarmId) {
        val alarmId = launchRequest?.editAlarmId ?: return@LaunchedEffect
        nav.navigate(Routes.editExisting(alarmId))
        onLaunchHandled()
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.AUTH) {
            AuthScreen(
                onAuthed = {
                    val next = if (needsPermissionGuide) Routes.GUIDE else Routes.HOME
                    nav.navigate(next) { popUpTo(Routes.AUTH) { inclusive = true } }
                }
            )
        }

        composable(Routes.GUIDE) {
            PermissionGuideScreen(
                onDone = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.GUIDE) { inclusive = true } }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onAddReminder = { type -> nav.navigate(Routes.edit(type, 0L)) },
                onAddTemplate = { type, template -> nav.navigate(Routes.edit(type, 0L, template)) },
                onEditReminder = { id -> nav.navigate(Routes.editExisting(id)) },
                onEditNextReminder = { id ->
                    nav.navigate(Routes.editExisting(id, AlarmSaveMode.OVERRIDE_NEXT))
                },
                onLogin = { nav.navigate(Routes.AUTH) },
                onModeChanged = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onOpenSyncCenter = { nav.navigate(Routes.SYNC_CENTER) },
                onOpenDeletedReminders = { nav.navigate(Routes.DELETED_REMINDERS) }
            )
        }

        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument("type") { type = NavType.IntType },
                navArgument("id") { type = NavType.LongType },
                navArgument("template") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = AlarmSaveMode.NORMAL.name
                }
            )
        ) { entry ->
            val typeCode = entry.arguments?.getInt("type") ?: 1
            val id = entry.arguments?.getLong("id") ?: 0L
            val template = entry.arguments?.getString("template").orEmpty().ifBlank { null }
            val saveMode = entry.arguments?.getString("mode")
                ?.let { runCatching { AlarmSaveMode.valueOf(it) }.getOrNull() }
                ?: AlarmSaveMode.NORMAL
            val alarmType = if (typeCode == 0) AlarmType.ONCE else AlarmType.fromCode(typeCode)
            AlarmEditScreen(
                alarmId = id,
                type = alarmType,
                templateId = template,
                saveMode = saveMode,
                onDone = { nav.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onLogin = { nav.navigate(Routes.AUTH) },
                onModeChanged = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onOpenSyncCenter = { nav.navigate(Routes.SYNC_CENTER) }
            )
        }

        composable(Routes.SYNC_CENTER) {
            SyncStatusCenterScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.DELETED_REMINDERS) {
            DeletedRemindersScreen(onBack = { nav.popBackStack() })
        }
    }
}
