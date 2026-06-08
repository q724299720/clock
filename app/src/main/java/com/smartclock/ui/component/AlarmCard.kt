package com.smartclock.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmType
import com.smartclock.domain.model.AlertPolicy
import com.smartclock.domain.model.CountdownRuntime
import com.smartclock.domain.model.CountdownStatus
import com.smartclock.domain.model.ScheduleMode
import com.smartclock.ui.theme.TimeDisplayStyle
import com.smartclock.util.TimeFormat
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmCard(
    alarm: Alarm,
    countdownRuntime: CountdownRuntime? = null,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onPauseCountdown: () -> Unit = {},
    onResumeCountdown: () -> Unit = {},
    onExtendCountdown: () -> Unit = {},
    onResetCountdown: () -> Unit = {},
    modifier: Modifier = Modifier
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
                delay(1000)
                value = System.currentTimeMillis()
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeText(alarm, countdownRuntime, now),
                    style = TimeDisplayStyle,
                    color = if (alarm.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1
                )

                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    MetaPill(
                        text = alarmTypeLabel(alarm),
                        background = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (!alarm.enabled) {
                        MetaPill(
                            text = "已关闭",
                            background = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = alarm.title.ifBlank { "未命名提醒" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Text(
                    text = subtitleText(alarm, countdownRuntime, now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )

                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    if (!alarm.label.isNullOrBlank()) {
                        LabelChip(label = alarm.label, color = alarm.color)
                    }
                    if (alarm.scheduleMode == ScheduleMode.WORKDAY_CN) {
                        MetaPill(
                            text = "工作日",
                            background = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (alarm.alertPolicy == AlertPolicy.QUIET_REMINDER) {
                        MetaPill(
                            text = "轻提醒",
                            background = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (alarm.type == AlarmType.COUNTDOWN && countdownRuntime != null) {
                        MetaPill(
                            text = if (countdownRuntime.status == CountdownStatus.RUNNING) {
                                "进行中"
                            } else {
                                "已暂停"
                            }
                        )
                    }
                }

                if (alarm.type == AlarmType.COUNTDOWN && countdownRuntime != null) {
                    FlowRow(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
                    ) {
                        ActionChip(
                            text = if (countdownRuntime.status == CountdownStatus.RUNNING) "暂停" else "继续",
                            onClick = if (countdownRuntime.status == CountdownStatus.RUNNING) {
                                onPauseCountdown
                            } else {
                                onResumeCountdown
                            }
                        )
                        ActionChip(text = "+1 分钟", onClick = onExtendCountdown)
                        ActionChip(text = "重置", onClick = onResetCountdown)
                    }
                }
            }

            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
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

@Composable
private fun ActionChip(
    text: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MetaPill(
    text: String,
    background: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        color = background,
        contentColor = contentColor,
        shape = CircleShape
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

private fun timeText(
    alarm: Alarm,
    countdownRuntime: CountdownRuntime?,
    now: Long
): String = when (alarm.type) {
    AlarmType.COUNTDOWN -> {
        val seconds = countdownRuntime?.remainingAt(now) ?: alarm.durationSec ?: 0
        TimeFormat.duration(seconds)
    }

    else -> TimeFormat.hhmm(alarm.triggerTime)
}

private fun subtitleText(
    alarm: Alarm,
    countdownRuntime: CountdownRuntime?,
    now: Long
): String = when {
    alarm.type != AlarmType.COUNTDOWN || countdownRuntime == null -> TimeFormat.subtitle(alarm)
    countdownRuntime.status == CountdownStatus.RUNNING ->
        "进行中 · 预计 ${TimeFormat.hhmm(countdownRuntime.endAt)}"

    else ->
        "已暂停 · 剩余 ${TimeFormat.duration(countdownRuntime.remainingAt(now))}"
}

private fun alarmTypeLabel(alarm: Alarm): String = when (alarm.type) {
    AlarmType.ONCE -> "单次"
    AlarmType.COUNTDOWN -> "倒计时"
    AlarmType.WEEKLY -> if (alarm.scheduleMode == ScheduleMode.WORKDAY_CN) "工作日" else "每周"
    AlarmType.MONTHLY -> if (alarm.intervalMonths > 1) "每 ${alarm.intervalMonths} 个月" else "每月"
    AlarmType.ANNIVERSARY -> if (alarm.intervalYears > 1) "每 ${alarm.intervalYears} 年" else "每年"
}
