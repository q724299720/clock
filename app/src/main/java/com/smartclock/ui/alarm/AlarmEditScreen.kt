package com.smartclock.ui.alarm

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmTemplateIds
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.CalendarType
import com.smartclock.domain.model.ScheduleMode
import com.smartclock.domain.model.defaultAlertPolicy
import com.smartclock.domain.model.defaultTimeAnchorMode
import com.smartclock.ui.component.DurationPicker
import com.smartclock.ui.component.TimeWheelPicker
import com.smartclock.ui.component.WeekChipRow
import com.smartclock.ui.theme.labelColorHex
import com.smartclock.util.ReminderScheduleResolver
import com.smartclock.util.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private enum class RepeatMode(val label: String) {
    ONCE("仅一次"),
    DAILY("每天"),
    WEEKLY("自定义"),
    MONTHLY("每月"),
    YEARLY("每年")
}

private enum class QuickTimePreset(val label: String) {
    PLUS_TEN("+10 分钟"),
    TONIGHT("今晚"),
    TOMORROW_MORNING("明早"),
    NEXT_WEEK("下周同一时间")
}

private data class TemplatePreset(
    val type: AlarmType,
    val title: String,
    val repeatMode: RepeatMode,
    val scheduleMode: ScheduleMode = ScheduleMode.NORMAL,
    val intervalMonths: Int = 1,
    val intervalYears: Int = 1,
    val label: String? = null,
    val useLunar: Boolean = false
)

private data class TemplateTimeSlot(
    val id: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val defaultSelected: Boolean = false
)

private data class TimeSelection(
    val dateMillis: Long,
    val hour: Int,
    val minute: Int,
    val message: String? = null
)

private const val EVERY_DAY_BITS = 0b1111111
private val BATCH_TEMPLATE_IDS = setOf(AlarmTemplateIds.MEDICINE, AlarmTemplateIds.WATER)
private val ROW_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd EEE", Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarmId: Long,
    type: AlarmType,
    templateId: String? = null,
    saveMode: AlarmSaveMode = AlarmSaveMode.NORMAL,
    onDone: () -> Unit,
    vm: AlarmViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var loaded by remember { mutableStateOf(alarmId == 0L) }
    var sourceType by remember { mutableStateOf(type) }
    var selectedTemplateId by remember { mutableStateOf(templateId) }
    var originalAlarm by remember { mutableStateOf<Alarm?>(null) }

    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var label by remember { mutableStateOf<String?>(null) }
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(0) }
    var repeatMode by remember { mutableStateOf(defaultRepeatMode(type)) }
    var weekBits by remember { mutableIntStateOf(defaultWeekdayBits(System.currentTimeMillis())) }
    var durationHours by remember { mutableIntStateOf(0) }
    var durationMinutes by remember { mutableIntStateOf(5) }
    var durationSeconds by remember { mutableIntStateOf(0) }
    var triggerDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lunar by remember { mutableStateOf(false) }
    var vibrate by remember { mutableStateOf(true) }
    var scheduleMode by remember { mutableStateOf(ScheduleMode.NORMAL) }
    var intervalMonths by remember { mutableIntStateOf(1) }
    var intervalYears by remember { mutableIntStateOf(1) }
    var snoozeMinutes by remember { mutableIntStateOf(5) }
    var selectedBatchSlotIds by remember { mutableStateOf(setOf<String>()) }
    var showRepeatSheet by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var autoAdjustMessage by remember { mutableStateOf<String?>(null) }

    val templateSlots = remember(selectedTemplateId) { templateTimeSlots(selectedTemplateId) }
    val isBatchTemplate =
        alarmId == 0L && selectedTemplateId != null && selectedTemplateId in BATCH_TEMPLATE_IDS
    val overrideOnly = saveMode == AlarmSaveMode.OVERRIDE_NEXT && alarmId > 0L

    LaunchedEffect(autoAdjustMessage) {
        if (!autoAdjustMessage.isNullOrBlank()) {
            delay(2400)
            autoAdjustMessage = null
        }
    }

    LaunchedEffect(alarmId, type, templateId) {
        if (alarmId == 0L) {
            val now = System.currentTimeMillis()
            originalAlarm = null
            sourceType = type
            selectedTemplateId = templateId
            title = ""
            note = ""
            label = null
            hour = 7
            minute = 0
            repeatMode = defaultRepeatMode(type)
            weekBits = defaultWeekdayBits(now)
            durationHours = 0
            durationMinutes = 5
            durationSeconds = 0
            triggerDate = now
            lunar = false
            vibrate = true
            scheduleMode = ScheduleMode.NORMAL
            intervalMonths = 1
            intervalYears = 1
            snoozeMinutes = 5
            selectedBatchSlotIds = defaultTemplateSlotIds(templateId)
            applyTemplate(templateId)?.let { preset ->
                sourceType = preset.type
                title = preset.title
                label = preset.label
                repeatMode = preset.repeatMode
                scheduleMode = preset.scheduleMode
                intervalMonths = preset.intervalMonths
                intervalYears = preset.intervalYears
                lunar = preset.useLunar
                if (preset.repeatMode == RepeatMode.DAILY) {
                    weekBits = EVERY_DAY_BITS
                }
            }
            advancedExpanded = false
            loaded = true
            return@LaunchedEffect
        }

        vm.getById(alarmId)?.let { alarm ->
            originalAlarm = alarm
            sourceType = alarm.type
            selectedTemplateId = alarm.templateId
            title = alarm.title
            note = alarm.note.orEmpty()
            label = alarm.label
            vibrate = alarm.vibrate
            lunar = alarm.anniversaryCalendar == CalendarType.LUNAR
            scheduleMode = alarm.scheduleMode
            intervalMonths = alarm.intervalMonths
            intervalYears = alarm.intervalYears
            snoozeMinutes = alarm.snoozeMinutes
            selectedBatchSlotIds = emptySet()
            repeatMode = inferRepeatMode(alarm)
            alarm.triggerTime?.let {
                val calendar = Calendar.getInstance().apply { timeInMillis = it }
                hour = calendar.get(Calendar.HOUR_OF_DAY)
                minute = calendar.get(Calendar.MINUTE)
                triggerDate = it
            }
            weekBits = when {
                repeatMode == RepeatMode.DAILY -> EVERY_DAY_BITS
                alarm.repeatWeekdays != 0 -> alarm.repeatWeekdays
                else -> defaultWeekdayBits(triggerDate)
            }
            alarm.durationSec?.let {
                durationHours = it / 3600
                durationMinutes = (it % 3600) / 60
                durationSeconds = it % 60
            }
            advancedExpanded = note.isNotBlank() || !vibrate || snoozeMinutes != 5
        }
        loaded = true
    }

    if (!loaded) return

    val isCountdown = sourceType == AlarmType.COUNTDOWN
    val batchSelectionInvalid = isBatchTemplate && selectedBatchSlotIds.isEmpty()
    val countdownInvalid = isCountdown && durationHours + durationMinutes + durationSeconds == 0
    val canSave = !batchSelectionInvalid && !countdownInvalid

    fun applyTimeSelection(dateMillis: Long, newHour: Int, newMinute: Int) {
        val adjusted = if (!isCountdown && repeatMode == RepeatMode.ONCE) {
            adjustOnceSelection(dateMillis, newHour, newMinute)
        } else {
            TimeSelection(dateMillis = dateMillis, hour = newHour, minute = newMinute)
        }
        triggerDate = adjusted.dateMillis
        hour = adjusted.hour
        minute = adjusted.minute
        autoAdjustMessage = adjusted.message
    }

    val saveAction = saveAction@{
        if (overrideOnly) {
            val baseAlarm = originalAlarm ?: return@saveAction
            val targetTrigger = composeExactTrigger(triggerDate, hour, minute)
            val anchorTrigger = ReminderScheduleResolver.nextTrigger(baseAlarm)
                ?: baseAlarm.triggerTime
                ?: targetTrigger
            vm.save(
                ReminderScheduleResolver.rescheduleNextOccurrence(
                    alarm = baseAlarm,
                    anchorTriggerAt = anchorTrigger,
                    nextTriggerAt = targetTrigger
                ),
                saveMode = AlarmSaveMode.OVERRIDE_NEXT
            )
        } else if (isCountdown) {
            vm.save(
                Alarm(
                    id = alarmId,
                    userId = 0L,
                    type = AlarmType.COUNTDOWN,
                    title = title.ifBlank { "倒计时" },
                    note = note.ifBlank { null },
                    durationSec = durationHours * 3600 + durationMinutes * 60 + durationSeconds,
                    vibrate = vibrate,
                    snoozeMinutes = snoozeMinutes,
                    label = label,
                    color = label?.let { labelColorHex(it) },
                    templateId = selectedTemplateId,
                    alertPolicy = defaultAlertPolicy(AlarmType.COUNTDOWN, selectedTemplateId),
                    timeAnchorMode = defaultTimeAnchorMode(AlarmType.COUNTDOWN)
                )
            )
        } else if (isBatchTemplate) {
            val selectedSlots = templateSlots.filter { it.id in selectedBatchSlotIds }
            vm.saveBatch(
                selectedSlots.map { slot ->
                    buildAlarmForSave(
                        alarmId = 0L,
                        title = batchTitle(
                            baseTitle = title,
                            templateId = selectedTemplateId,
                            slot = slot,
                            slotCount = selectedSlots.size
                        ),
                        note = note.ifBlank { null },
                        label = label,
                        hour = slot.hour,
                        minute = slot.minute,
                        triggerDate = triggerDate,
                        repeatMode = repeatMode,
                        weekBits = weekBits,
                        lunar = lunar,
                        vibrate = vibrate,
                        snoozeMinutes = snoozeMinutes,
                        scheduleMode = scheduleMode,
                        intervalMonths = intervalMonths,
                        intervalYears = intervalYears,
                        selectedTemplateId = selectedTemplateId,
                        fallbackType = sourceType
                    )
                }
            )
        } else {
            vm.save(
                buildAlarmForSave(
                    alarmId = alarmId,
                    title = title,
                    note = note.ifBlank { null },
                    label = label,
                    hour = hour,
                    minute = minute,
                    triggerDate = triggerDate,
                    repeatMode = repeatMode,
                    weekBits = weekBits,
                    lunar = lunar,
                    vibrate = vibrate,
                    snoozeMinutes = snoozeMinutes,
                    scheduleMode = scheduleMode,
                    intervalMonths = intervalMonths,
                    intervalYears = intervalYears,
                    selectedTemplateId = selectedTemplateId,
                    fallbackType = sourceType
                )
            )
        }
        onDone()
    }

    if (showRepeatSheet && !isCountdown && !overrideOnly) {
        ModalBottomSheet(onDismissRequest = { showRepeatSheet = false }) {
            RepeatSettingSheet(
                repeatMode = repeatMode,
                weekBits = weekBits,
                lunar = lunar,
                scheduleMode = scheduleMode,
                triggerDate = triggerDate,
                intervalMonths = intervalMonths,
                intervalYears = intervalYears,
                onRepeatModeChange = { mode ->
                    repeatMode = mode
                    when (mode) {
                        RepeatMode.ONCE -> {
                            scheduleMode = ScheduleMode.NORMAL
                        }

                        RepeatMode.DAILY -> {
                            weekBits = EVERY_DAY_BITS
                            scheduleMode = ScheduleMode.NORMAL
                        }

                        RepeatMode.WEEKLY -> {
                            if (weekBits == 0 || weekBits == EVERY_DAY_BITS) {
                                weekBits = defaultWeekdayBits(triggerDate)
                            }
                            scheduleMode = ScheduleMode.NORMAL
                        }

                        RepeatMode.MONTHLY -> {
                            scheduleMode = ScheduleMode.NORMAL
                            intervalMonths = intervalMonths.coerceAtLeast(1)
                        }

                        RepeatMode.YEARLY -> {
                            scheduleMode = ScheduleMode.NORMAL
                            intervalYears = intervalYears.coerceAtLeast(1)
                        }
                    }
                },
                onSelectWorkday = {
                    repeatMode = RepeatMode.WEEKLY
                    scheduleMode = ScheduleMode.WORKDAY_CN
                },
                onWeekBitsChange = {
                    repeatMode = RepeatMode.WEEKLY
                    scheduleMode = ScheduleMode.NORMAL
                    weekBits = it
                },
                onLunarChange = { lunar = it }
            )
        }
    }

    val previewText = remember(
        isCountdown,
        title,
        note,
        label,
        hour,
        minute,
        triggerDate,
        repeatMode,
        weekBits,
        lunar,
        vibrate,
        snoozeMinutes,
        scheduleMode,
        intervalMonths,
        intervalYears,
        selectedTemplateId,
        sourceType
    ) {
        if (isCountdown) {
            null
        } else {
            val previewAlarm = buildAlarmForSave(
                alarmId = alarmId,
                title = title,
                note = note.ifBlank { null },
                label = label,
                hour = hour,
                minute = minute,
                triggerDate = triggerDate,
                repeatMode = repeatMode,
                weekBits = weekBits,
                lunar = lunar,
                vibrate = vibrate,
                snoozeMinutes = snoozeMinutes,
                scheduleMode = scheduleMode,
                intervalMonths = intervalMonths,
                intervalYears = intervalYears,
                selectedTemplateId = selectedTemplateId,
                fallbackType = sourceType
            )
            val effectivePreviewAlarm = if (overrideOnly) {
                val baseAlarm = originalAlarm ?: return@remember null
                val targetTrigger = composeExactTrigger(triggerDate, hour, minute)
                val anchorTrigger = ReminderScheduleResolver.nextTrigger(baseAlarm)
                    ?: baseAlarm.triggerTime
                    ?: targetTrigger
                ReminderScheduleResolver.rescheduleNextOccurrence(
                    alarm = baseAlarm,
                    anchorTriggerAt = anchorTrigger,
                    nextTriggerAt = targetTrigger
                )
            } else {
                previewAlarm
            }
            val next = if (effectivePreviewAlarm.type == AlarmType.ONCE) {
                effectivePreviewAlarm.triggerTime
            } else {
                ReminderScheduleResolver.nextTrigger(effectivePreviewAlarm)
            }
            next?.let {
                if (previewAlarm.type == AlarmType.ONCE) {
                    "计划提醒：${TimeFormat.full(it)}"
                } else {
                    "下次提醒：${TimeFormat.full(it)}"
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
        ),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (overrideOnly) "浠呮湰娆′慨鏀?" else topBarTitle(sourceType, repeatMode, selectedTemplateId),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = saveAction, enabled = canSave) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "保存",
                            tint = if (canSave) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (selectedTemplateId != null && !overrideOnly) {
                TemplateInfoCard(selectedTemplateId = selectedTemplateId!!)
            }

            if (isCountdown) {
                DurationPicker(
                    hours = durationHours,
                    minutes = durationMinutes,
                    seconds = durationSeconds,
                    onChange = { h, m, s ->
                        durationHours = h
                        durationMinutes = m
                        durationSeconds = s
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            } else {
                TimeWheelPicker(
                    hour = hour,
                    minute = minute,
                    onChange = { h, m -> applyTimeSelection(triggerDate, h, m) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                QuickTimeActionRow(
                    onSelect = { preset ->
                        val selection = quickTimeSelection(
                            preset = preset,
                            dateMillis = triggerDate,
                            hour = hour,
                            minute = minute
                        )
                        applyTimeSelection(selection.dateMillis, selection.hour, selection.minute)
                    },
                    modifier = Modifier.padding(top = 12.dp)
                )

                AnimatedVisibility(visible = autoAdjustMessage != null) {
                    InlineNotice(
                        text = autoAdjustMessage.orEmpty(),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                if (overrideOnly) {
                    InlineNotice(
                        text = "鍙奖鍝嶄笅涓€娆℃彁閱掞紝涓嶄細淇敼榛樿鐨勯噸澶嶈鍒欍€?",
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                if (templateSlots.isNotEmpty() && !overrideOnly) {
                    Text(
                        text = if (isBatchTemplate) "批量时段" else "快捷时段",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                    )
                    TemplateTimeSlotRow(
                        slots = templateSlots,
                        selectedIds = selectedBatchSlotIds,
                        currentHour = hour,
                        currentMinute = minute,
                        multiSelect = isBatchTemplate,
                        onSelect = { slot ->
                            if (isBatchTemplate) {
                                selectedBatchSlotIds = if (slot.id in selectedBatchSlotIds) {
                                    selectedBatchSlotIds - slot.id
                                } else {
                                    selectedBatchSlotIds + slot.id
                                }
                            } else {
                                applyTimeSelection(triggerDate, slot.hour, slot.minute)
                            }
                        }
                    )
                    Text(
                        text = templateSlotSummary(
                            templateId = selectedTemplateId,
                            selectedIds = selectedBatchSlotIds,
                            slots = templateSlots
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Column {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("标题") },
                            placeholder = {
                                Text(templateTitleHint(selectedTemplateId, sourceType))
                            },
                            singleLine = true,
                            readOnly = overrideOnly,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!isCountdown) {
                        GroupDivider()
                        SettingValueRow(
                            label = "日期",
                            value = dateRowValue(triggerDate),
                            onClick = {
                                showDatePicker(
                                    context = context,
                                    dateMillis = triggerDate,
                                    minDateMillis = if (repeatMode == RepeatMode.ONCE) {
                                        System.currentTimeMillis()
                                    } else {
                                        null
                                    }
                                ) { picked ->
                                    applyTimeSelection(picked, hour, minute)
                                    if (repeatMode == RepeatMode.WEEKLY && weekBits == 0) {
                                        weekBits = defaultWeekdayBits(picked)
                                    }
                                }
                            }
                        )

                        GroupDivider()
                        SettingValueRow(
                            label = "重复",
                            value = repeatRowValue(
                                repeatMode = repeatMode,
                                triggerDate = triggerDate,
                                weekBits = weekBits,
                                lunar = lunar,
                                scheduleMode = scheduleMode,
                                intervalMonths = intervalMonths,
                                intervalYears = intervalYears
                            ),
                            onClick = { showRepeatSheet = true }
                        )
                    }
                }
            }

            if (!previewText.isNullOrBlank()) {
                PreviewCard(
                    text = previewText,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { advancedExpanded = !advancedExpanded }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "高级设置",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = advancedSummary(note, vibrate, snoozeMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = if (advancedExpanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    AnimatedVisibility(visible = advancedExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            GroupDivider()
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("备注") },
                                placeholder = { Text("添加备注") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
                                minLines = 3
                            )

                            SettingSwitchRow(
                                label = "震动提醒",
                                checked = vibrate,
                                onCheckedChange = { vibrate = it },
                                modifier = Modifier.padding(top = 14.dp)
                            )

                            Text(
                                text = "贪睡时长",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                            )
                            SnoozeChipRow(
                                selected = snoozeMinutes,
                                onSelected = { snoozeMinutes = it }
                            )
                        }
                    }
                }
            }

            if (batchSelectionInvalid) {
                Text(
                    text = "请至少选择一个批量时段。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                )
            }

            if (countdownInvalid) {
                Text(
                    text = "倒计时时长不能为 0。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickTimeActionRow(
    onSelect: (QuickTimePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickTimePreset.entries.forEach { preset ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(preset) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun InlineNotice(
    text: String,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun PreviewCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateTimeSlotRow(
    slots: List<TemplateTimeSlot>,
    selectedIds: Set<String>,
    currentHour: Int,
    currentMinute: Int,
    multiSelect: Boolean,
    onSelect: (TemplateTimeSlot) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slots.forEach { slot ->
            val selected = if (multiSelect) {
                slot.id in selectedIds
            } else {
                slot.hour == currentHour && slot.minute == currentMinute
            }
            FilterChip(
                selected = selected,
                onClick = { onSelect(slot) },
                label = {
                    Text(
                        "${slot.label} ${slot.hour.toString().padStart(2, '0')}:${slot.minute.toString().padStart(2, '0')}"
                    )
                }
            )
        }
    }
}

@Composable
private fun TemplateInfoCard(selectedTemplateId: String) {
    val message = when (selectedTemplateId) {
        AlarmTemplateIds.BIRTHDAY -> "适合生日或纪念日提醒，可按年重复，也可以切换公历和农历。"
        AlarmTemplateIds.WORKDAY -> "适合法定工作日提醒，补班日也会响。"
        AlarmTemplateIds.CREDIT_CARD -> "适合每月固定日期提醒，例如还款日。"
        AlarmTemplateIds.RENT -> "适合两个月一次的固定支出提醒。"
        AlarmTemplateIds.MEDICINE -> "适合按饭后时段批量创建每日提醒。"
        AlarmTemplateIds.WATER -> "适合分散到一天中不同时间段，帮助你规律喝水。"
        AlarmTemplateIds.LICENSE_REVIEW -> "适合两年一次的证件检查提醒。"
        AlarmTemplateIds.SHIFT -> "适合轮班场景，先选班次，再微调时间。"
        else -> ""
    }

    if (message.isBlank()) return

    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SettingValueRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnoozeChipRow(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            0 to "不贪睡",
            5 to "5 分钟",
            10 to "10 分钟",
            15 to "15 分钟"
        ).forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatSettingSheet(
    repeatMode: RepeatMode,
    weekBits: Int,
    lunar: Boolean,
    scheduleMode: ScheduleMode,
    triggerDate: Long,
    intervalMonths: Int,
    intervalYears: Int,
    onRepeatModeChange: (RepeatMode) -> Unit,
    onSelectWorkday: () -> Unit,
    onWeekBitsChange: (Int) -> Unit,
    onLunarChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "重复设置",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "常用预设在上面，复杂规则在下面展开。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = repeatMode == RepeatMode.ONCE && scheduleMode == ScheduleMode.NORMAL,
                onClick = { onRepeatModeChange(RepeatMode.ONCE) },
                label = { Text("仅一次") }
            )
            FilterChip(
                selected = scheduleMode == ScheduleMode.WORKDAY_CN,
                onClick = onSelectWorkday,
                label = { Text("工作日") }
            )
            FilterChip(
                selected = repeatMode == RepeatMode.DAILY,
                onClick = { onRepeatModeChange(RepeatMode.DAILY) },
                label = { Text("每天") }
            )
            FilterChip(
                selected = repeatMode == RepeatMode.WEEKLY && scheduleMode != ScheduleMode.WORKDAY_CN,
                onClick = { onRepeatModeChange(RepeatMode.WEEKLY) },
                label = { Text("自定义") }
            )
            FilterChip(
                selected = repeatMode == RepeatMode.MONTHLY,
                onClick = { onRepeatModeChange(RepeatMode.MONTHLY) },
                label = { Text("每月") }
            )
            FilterChip(
                selected = repeatMode == RepeatMode.YEARLY,
                onClick = { onRepeatModeChange(RepeatMode.YEARLY) },
                label = { Text("每年") }
            )
        }

        when {
            scheduleMode == ScheduleMode.WORKDAY_CN -> {
                Text(
                    text = "按中国法定工作日提醒，补班也会生效。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            repeatMode == RepeatMode.WEEKLY -> {
                Text(
                    text = "选择每周提醒的日期。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
                WeekChipRow(
                    selectedBits = weekBits,
                    onChange = onWeekBitsChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            repeatMode == RepeatMode.MONTHLY -> {
                val day = Calendar.getInstance().apply { timeInMillis = triggerDate }
                    .get(Calendar.DAY_OF_MONTH)
                Text(
                    text = if (intervalMonths > 1) {
                        "每 $intervalMonths 个月提醒一次，固定在 $day 日。"
                    } else {
                        "固定在每月的 $day 日提醒。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            repeatMode == RepeatMode.YEARLY -> {
                Text(
                    text = "年提醒可以切换公历或农历。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !lunar,
                        onClick = { onLunarChange(false) },
                        label = { Text("公历") }
                    )
                    FilterChip(
                        selected = lunar,
                        onClick = { onLunarChange(true) },
                        label = { Text("农历") }
                    )
                }
                if (intervalYears > 1) {
                    val day = Calendar.getInstance().apply { timeInMillis = triggerDate }
                    Text(
                        text = "每 $intervalYears 年提醒一次，日期为 ${day.get(Calendar.MONTH) + 1} 月 ${day.get(Calendar.DAY_OF_MONTH)} 日。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            repeatMode == RepeatMode.DAILY -> {
                Text(
                    text = "每天都会提醒。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            else -> {
                Text(
                    text = "只在指定时间提醒一次。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }
}

private fun topBarTitle(
    sourceType: AlarmType,
    repeatMode: RepeatMode,
    templateId: String?
): String = when {
    sourceType == AlarmType.COUNTDOWN -> "倒计时"
    templateId != null -> "模板闹钟"
    repeatMode == RepeatMode.YEARLY -> "纪念日"
    else -> "闹钟"
}

private fun dateRowValue(dateMillis: Long): String = ROW_DATE_FORMAT.format(Date(dateMillis))

private fun repeatRowValue(
    repeatMode: RepeatMode,
    triggerDate: Long,
    weekBits: Int,
    lunar: Boolean,
    scheduleMode: ScheduleMode,
    intervalMonths: Int,
    intervalYears: Int
): String = when {
    scheduleMode == ScheduleMode.WORKDAY_CN -> "工作日"
    repeatMode == RepeatMode.ONCE -> "仅一次"
    repeatMode == RepeatMode.DAILY -> "每天"
    repeatMode == RepeatMode.WEEKLY -> weekSummary(weekBits)
    repeatMode == RepeatMode.MONTHLY -> {
        val day = Calendar.getInstance().apply { timeInMillis = triggerDate }
            .get(Calendar.DAY_OF_MONTH)
        if (intervalMonths > 1) {
            "每 $intervalMonths 个月 · $day 日"
        } else {
            "每月 · $day 日"
        }
    }

    repeatMode == RepeatMode.YEARLY -> {
        val calendar = Calendar.getInstance().apply { timeInMillis = triggerDate }
        val prefix = if (lunar) "农历" else "公历"
        val interval = if (intervalYears > 1) "每 $intervalYears 年" else "每年"
        "$interval · $prefix ${calendar.get(Calendar.MONTH) + 1} 月 ${calendar.get(Calendar.DAY_OF_MONTH)} 日"
    }

    else -> "仅一次"
}

private fun advancedSummary(
    note: String,
    vibrate: Boolean,
    snoozeMinutes: Int
): String {
    val noteSummary = if (note.isBlank()) "无备注" else "有备注"
    val vibrateSummary = if (vibrate) "震动开" else "震动关"
    val snoozeSummary = if (snoozeMinutes == 0) "不贪睡" else "$snoozeMinutes 分钟"
    return "$noteSummary · $vibrateSummary · $snoozeSummary"
}

private fun templateTitleHint(templateId: String?, sourceType: AlarmType): String = when (templateId) {
    AlarmTemplateIds.BIRTHDAY -> "生日提醒"
    AlarmTemplateIds.WORKDAY -> "工作日提醒"
    AlarmTemplateIds.CREDIT_CARD -> "还信用卡"
    AlarmTemplateIds.RENT -> "交房租"
    AlarmTemplateIds.MEDICINE -> "按时吃药"
    AlarmTemplateIds.WATER -> "喝水提醒"
    AlarmTemplateIds.LICENSE_REVIEW -> "驾照年检"
    AlarmTemplateIds.SHIFT -> "轮班闹钟"
    else -> if (sourceType == AlarmType.COUNTDOWN) "倒计时" else "闹钟"
}

private fun defaultRepeatMode(type: AlarmType): RepeatMode = when (type) {
    AlarmType.ONCE, AlarmType.COUNTDOWN -> RepeatMode.ONCE
    AlarmType.WEEKLY -> RepeatMode.WEEKLY
    AlarmType.MONTHLY -> RepeatMode.MONTHLY
    AlarmType.ANNIVERSARY -> RepeatMode.YEARLY
}

private fun inferRepeatMode(alarm: Alarm): RepeatMode = when (alarm.type) {
    AlarmType.ONCE, AlarmType.COUNTDOWN -> RepeatMode.ONCE
    AlarmType.WEEKLY -> if (alarm.repeatWeekdays == EVERY_DAY_BITS) RepeatMode.DAILY else RepeatMode.WEEKLY
    AlarmType.MONTHLY -> RepeatMode.MONTHLY
    AlarmType.ANNIVERSARY -> RepeatMode.YEARLY
}

private fun applyTemplate(templateId: String?): TemplatePreset? = when (templateId) {
    AlarmTemplateIds.BIRTHDAY -> TemplatePreset(
        type = AlarmType.ANNIVERSARY,
        title = "生日提醒",
        repeatMode = RepeatMode.YEARLY,
        label = "生活"
    )

    AlarmTemplateIds.WORKDAY -> TemplatePreset(
        type = AlarmType.WEEKLY,
        title = "工作日提醒",
        repeatMode = RepeatMode.WEEKLY,
        scheduleMode = ScheduleMode.WORKDAY_CN,
        label = "工作"
    )

    AlarmTemplateIds.CREDIT_CARD -> TemplatePreset(
        type = AlarmType.MONTHLY,
        title = "还信用卡",
        repeatMode = RepeatMode.MONTHLY,
        label = "生活"
    )

    AlarmTemplateIds.RENT -> TemplatePreset(
        type = AlarmType.MONTHLY,
        title = "交房租",
        repeatMode = RepeatMode.MONTHLY,
        intervalMonths = 2,
        label = "生活"
    )

    AlarmTemplateIds.MEDICINE -> TemplatePreset(
        type = AlarmType.WEEKLY,
        title = "按时吃药",
        repeatMode = RepeatMode.DAILY,
        label = "生活"
    )

    AlarmTemplateIds.WATER -> TemplatePreset(
        type = AlarmType.WEEKLY,
        title = "喝水提醒",
        repeatMode = RepeatMode.DAILY,
        label = "生活"
    )

    AlarmTemplateIds.LICENSE_REVIEW -> TemplatePreset(
        type = AlarmType.ANNIVERSARY,
        title = "驾照年检",
        repeatMode = RepeatMode.YEARLY,
        intervalYears = 2,
        label = "生活"
    )

    AlarmTemplateIds.SHIFT -> TemplatePreset(
        type = AlarmType.WEEKLY,
        title = "轮班闹钟",
        repeatMode = RepeatMode.WEEKLY,
        label = "工作"
    )

    else -> null
}

private fun templateTimeSlots(templateId: String?): List<TemplateTimeSlot> = when (templateId) {
    AlarmTemplateIds.SHIFT -> listOf(
        TemplateTimeSlot("shift_morning", "早班", 7, 30),
        TemplateTimeSlot("shift_mid", "中班", 14, 0),
        TemplateTimeSlot("shift_night", "夜班", 22, 30)
    )

    AlarmTemplateIds.MEDICINE -> listOf(
        TemplateTimeSlot("med_breakfast", "早餐后", 8, 0),
        TemplateTimeSlot("med_lunch", "午饭后", 13, 0),
        TemplateTimeSlot("med_dinner", "晚饭后", 20, 0)
    )

    AlarmTemplateIds.WATER -> listOf(
        TemplateTimeSlot("water_morning", "上午", 9, 0),
        TemplateTimeSlot("water_noon", "中午", 11, 0),
        TemplateTimeSlot("water_afternoon", "下午", 14, 30),
        TemplateTimeSlot("water_evening", "傍晚", 17, 0)
    )

    else -> emptyList()
}

private fun defaultTemplateSlotIds(templateId: String?): Set<String> =
    templateTimeSlots(templateId)
        .filter { it.defaultSelected }
        .mapTo(linkedSetOf()) { it.id }

private fun templateSlotSummary(
    templateId: String?,
    selectedIds: Set<String>,
    slots: List<TemplateTimeSlot>
): String = when (templateId) {
    AlarmTemplateIds.SHIFT -> "点击班次预设可以快速带入常用时间，再按你的排班勾选星期。"
    AlarmTemplateIds.MEDICINE -> {
        val count = slots.count { it.id in selectedIds }
        if (count == 0) {
            "可以同时选择多个吃药时段，保存后会一次创建多条每日提醒。"
        } else {
            "已选 $count 个吃药时段，保存后会批量生成每日提醒。"
        }
    }

    AlarmTemplateIds.WATER -> {
        val count = slots.count { it.id in selectedIds }
        if (count == 0) {
            "建议把喝水提醒分散到一天中不同时间段。"
        } else {
            "已选 $count 个喝水时段，保存后会批量生成每日提醒。"
        }
    }

    else -> ""
}

private fun batchTitle(
    baseTitle: String,
    templateId: String?,
    slot: TemplateTimeSlot,
    slotCount: Int
): String {
    val resolvedBase = baseTitle.ifBlank { applyTemplate(templateId)?.title ?: slot.label }
    return if (slotCount > 1 || baseTitle.isBlank()) {
        "$resolvedBase · ${slot.label}"
    } else {
        resolvedBase
    }
}

private fun buildAlarmForSave(
    alarmId: Long,
    title: String,
    note: String?,
    label: String?,
    hour: Int,
    minute: Int,
    triggerDate: Long,
    repeatMode: RepeatMode,
    weekBits: Int,
    lunar: Boolean,
    vibrate: Boolean,
    snoozeMinutes: Int,
    scheduleMode: ScheduleMode,
    intervalMonths: Int,
    intervalYears: Int,
    selectedTemplateId: String?,
    fallbackType: AlarmType
): Alarm {
    val exactTrigger = composeExactTrigger(triggerDate, hour, minute)
    val selectedWeekBits = when {
        scheduleMode == ScheduleMode.WORKDAY_CN -> weekBits
        repeatMode == RepeatMode.DAILY -> EVERY_DAY_BITS
        repeatMode == RepeatMode.WEEKLY -> weekBits.takeIf { it != 0 } ?: defaultWeekdayBits(triggerDate)
        else -> 0
    }
    val selectedMonthBits = if (repeatMode == RepeatMode.MONTHLY) monthDayBit(triggerDate) else 0
    val resolvedType = when (repeatMode) {
        RepeatMode.ONCE -> AlarmType.ONCE
        RepeatMode.DAILY, RepeatMode.WEEKLY -> AlarmType.WEEKLY
        RepeatMode.MONTHLY -> AlarmType.MONTHLY
        RepeatMode.YEARLY -> AlarmType.ANNIVERSARY
    }
    val effectiveScheduleMode = if (resolvedType == AlarmType.WEEKLY) {
        scheduleMode
    } else {
        ScheduleMode.NORMAL
    }
    val effectiveIntervalMonths = if (resolvedType == AlarmType.MONTHLY) intervalMonths.coerceAtLeast(1) else 1
    val effectiveIntervalYears = if (resolvedType == AlarmType.ANNIVERSARY) intervalYears.coerceAtLeast(1) else 1
    val finalTitle = title.ifBlank {
        applyTemplate(selectedTemplateId)?.title ?: fallbackTitle(fallbackType)
    }

    return Alarm(
        id = alarmId,
        userId = 0L,
        type = resolvedType,
        title = finalTitle,
        note = note,
        triggerTime = exactTrigger,
        repeatWeekdays = selectedWeekBits,
        repeatMonthDays = selectedMonthBits,
        anniversaryCalendar = if (repeatMode == RepeatMode.YEARLY && lunar) {
            CalendarType.LUNAR
        } else {
            CalendarType.SOLAR
        },
        vibrate = vibrate,
        ringtone = null,
        snoozeMinutes = snoozeMinutes,
        label = label,
        color = label?.let { labelColorHex(it) },
        startDate = if (repeatMode == RepeatMode.ONCE) null else startOfDay(triggerDate),
        scheduleMode = effectiveScheduleMode,
        alertPolicy = defaultAlertPolicy(resolvedType, selectedTemplateId),
        timeAnchorMode = defaultTimeAnchorMode(resolvedType),
        intervalMonths = effectiveIntervalMonths,
        intervalYears = effectiveIntervalYears,
        templateId = selectedTemplateId
    )
}

private fun fallbackTitle(type: AlarmType): String = when (type) {
    AlarmType.ONCE -> "闹钟"
    AlarmType.COUNTDOWN -> "倒计时"
    AlarmType.WEEKLY -> "闹钟"
    AlarmType.MONTHLY -> "每月提醒"
    AlarmType.ANNIVERSARY -> "纪念日"
}

private fun composeExactTrigger(dateMillis: Long, hour: Int, minute: Int): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = dateMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

private fun startOfDay(dateMillis: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = dateMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

private fun defaultWeekdayBits(dateMillis: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
    return 1 shl (calendar.get(Calendar.DAY_OF_WEEK) - 1)
}

private fun monthDayBit(dateMillis: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
    return 1 shl calendar.get(Calendar.DAY_OF_MONTH)
}

private fun weekSummary(bits: Int): String {
    if (bits == EVERY_DAY_BITS) return "每天"
    val labels = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    val selected = labels.filterIndexed { index, _ -> (bits and (1 shl index)) != 0 }
    return if (selected.isEmpty()) "未选择" else selected.joinToString("、")
}

private fun quickTimeSelection(
    preset: QuickTimePreset,
    dateMillis: Long,
    hour: Int,
    minute: Int
): TimeSelection {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = composeExactTrigger(dateMillis, hour, minute)
    }
    return when (preset) {
        QuickTimePreset.PLUS_TEN -> {
            calendar.add(Calendar.MINUTE, 10)
            TimeSelection(
                dateMillis = calendar.timeInMillis,
                hour = calendar.get(Calendar.HOUR_OF_DAY),
                minute = calendar.get(Calendar.MINUTE)
            )
        }

        QuickTimePreset.TONIGHT -> {
            val now = Calendar.getInstance()
            calendar.timeInMillis = now.timeInMillis
            calendar.set(Calendar.HOUR_OF_DAY, 21)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            if (calendar.timeInMillis <= now.timeInMillis) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            TimeSelection(calendar.timeInMillis, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
        }

        QuickTimePreset.TOMORROW_MORNING -> {
            val now = Calendar.getInstance()
            calendar.timeInMillis = now.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 7)
            calendar.set(Calendar.MINUTE, 30)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            TimeSelection(calendar.timeInMillis, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
        }

        QuickTimePreset.NEXT_WEEK -> {
            calendar.add(Calendar.DAY_OF_MONTH, 7)
            TimeSelection(calendar.timeInMillis, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
        }
    }
}

private fun adjustOnceSelection(
    dateMillis: Long,
    hour: Int,
    minute: Int
): TimeSelection {
    val now = System.currentTimeMillis()
    var adjustedDate = dateMillis
    var exactTrigger = composeExactTrigger(adjustedDate, hour, minute)
    if (exactTrigger > now) {
        return TimeSelection(adjustedDate, hour, minute)
    }

    val originalDay = startOfDay(dateMillis)
    while (exactTrigger <= now) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = adjustedDate
            add(Calendar.DAY_OF_MONTH, 1)
        }
        adjustedDate = calendar.timeInMillis
        exactTrigger = composeExactTrigger(adjustedDate, hour, minute)
    }

    val message = if (startOfDay(adjustedDate) - originalDay == 86_400_000L) {
        "当前时间已过去，已自动顺延到明天。"
    } else {
        "当前时间已过去，已自动顺延到下一个可用日期。"
    }
    return TimeSelection(
        dateMillis = adjustedDate,
        hour = hour,
        minute = minute,
        message = message
    )
}

private fun showDatePicker(
    context: Context,
    dateMillis: Long,
    minDateMillis: Long? = null,
    onPicked: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val picked = Calendar.getInstance().apply {
                timeInMillis = dateMillis
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            onPicked(picked.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).apply {
        minDateMillis?.let { datePicker.minDate = startOfDay(it) }
    }.show()
}
