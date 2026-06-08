package com.smartclock.ui.home.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartclock.domain.model.Alarm
import com.smartclock.domain.model.AlarmType
import com.smartclock.ui.alarm.AlarmViewModel
import com.smartclock.ui.component.EmptyState
import com.smartclock.ui.component.PageHero
import com.smartclock.ui.component.TimelyBrandBar
import com.smartclock.util.ReminderScheduleResolver
import com.smartclock.util.TimeFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class CalendarViewMode(val label: String) {
    MONTH("月"),
    WEEK("周"),
    DAY("日")
}

private data class CalendarOccurrence(
    val alarm: Alarm,
    val triggerAt: Long
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderCalendarScreen(
    onEdit: (Long) -> Unit,
    onAdd: (AlarmType) -> Unit,
    modifier: Modifier = Modifier,
    vm: AlarmViewModel = hiltViewModel()
) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }

    val zone = ZoneId.systemDefault()
    val range = remember(viewMode, selectedDate, currentMonth, zone) {
        when (viewMode) {
            CalendarViewMode.MONTH -> {
                val start = currentMonth.atDay(1)
                val end = currentMonth.plusMonths(1).atDay(1)
                start to end
            }

            CalendarViewMode.WEEK -> {
                val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
                weekStart to weekStart.plusDays(7)
            }

            CalendarViewMode.DAY -> selectedDate to selectedDate.plusDays(1)
        }
    }
    val occurrences = remember(alarms, range) {
        expandOccurrences(
            alarms = alarms,
            startInclusive = range.first.atStartOfDay(zone).toInstant().toEpochMilli(),
            endExclusive = range.second.atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }
    val occurrenceByDate = remember(occurrences, zone) {
        occurrences.groupBy { Instant.ofEpochMilli(it.triggerAt).atZone(zone).toLocalDate() }
    }
    val selectedDayOccurrences = remember(occurrenceByDate, selectedDate) {
        occurrenceByDate[selectedDate].orEmpty()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            TimelyBrandBar(trailingIcons = listOf(Icons.Default.CalendarToday))
        }

        item {
            PageHero(title = "日历")
        }

        item {
            MonthSwitcherCard(
                title = when (viewMode) {
                    CalendarViewMode.MONTH -> currentMonth.format(MONTH_FORMAT)
                    CalendarViewMode.WEEK -> {
                        val weekStart = range.first
                        val weekEnd = range.second.minusDays(1)
                        "${weekStart.format(SHORT_DATE_FORMAT)} - ${weekEnd.format(SHORT_DATE_FORMAT)}"
                    }

                    CalendarViewMode.DAY -> selectedDate.format(DAY_TITLE_FORMAT)
                },
                onPrevious = {
                    when (viewMode) {
                        CalendarViewMode.MONTH -> {
                            currentMonth = currentMonth.minusMonths(1)
                            selectedDate = currentMonth.atDay(1)
                        }

                        CalendarViewMode.WEEK -> selectedDate = selectedDate.minusWeeks(1)
                        CalendarViewMode.DAY -> selectedDate = selectedDate.minusDays(1)
                    }
                },
                onNext = {
                    when (viewMode) {
                        CalendarViewMode.MONTH -> {
                            currentMonth = currentMonth.plusMonths(1)
                            selectedDate = currentMonth.atDay(1)
                        }

                        CalendarViewMode.WEEK -> selectedDate = selectedDate.plusWeeks(1)
                        CalendarViewMode.DAY -> selectedDate = selectedDate.plusDays(1)
                    }
                }
            )
        }

        item {
            ModeSwitchRow(
                viewMode = viewMode,
                onChange = {
                    viewMode = it
                    if (it == CalendarViewMode.MONTH) {
                        currentMonth = YearMonth.from(selectedDate)
                    }
                }
            )
        }

        when (viewMode) {
            CalendarViewMode.MONTH -> {
                item {
                    MonthViewCard(
                        currentMonth = currentMonth,
                        selectedDate = selectedDate,
                        occurrenceByDate = occurrenceByDate,
                        onSelectDate = { selectedDate = it }
                    )
                }
                item {
                    SelectedDayCard(
                        selectedDate = selectedDate,
                        occurrences = selectedDayOccurrences,
                        onEdit = onEdit
                    )
                }
            }

            CalendarViewMode.WEEK -> {
                item {
                    WeekViewCard(
                        selectedDate = selectedDate,
                        occurrenceByDate = occurrenceByDate,
                        onSelectDate = { selectedDate = it },
                        onEdit = onEdit
                    )
                }
            }

            CalendarViewMode.DAY -> {
                item {
                    DayViewCard(
                        selectedDate = selectedDate,
                        occurrences = selectedDayOccurrences,
                        onEdit = onEdit
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcherCard(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "上一个",
                modifier = Modifier.clickable(onClick = onPrevious)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "下一个",
                modifier = Modifier.clickable(onClick = onNext)
            )
        }
    }
}

@Composable
private fun ModeSwitchRow(
    viewMode: CalendarViewMode,
    onChange: (CalendarViewMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CalendarViewMode.entries.forEach { mode ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onChange(mode) },
                shape = MaterialTheme.shapes.medium,
                color = if (viewMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (viewMode == mode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    1.dp,
                    if (viewMode == mode) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = mode.label, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun MonthViewCard(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    occurrenceByDate: Map<LocalDate, List<CalendarOccurrence>>,
    onSelectDate: (LocalDate) -> Unit
) {
    val cells = remember(currentMonth) { buildMonthCells(currentMonth) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            WeekdayHeader()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                buildMonthRows(cells).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        week.forEach { date ->
                            CalendarCell(
                                date = date,
                                inMonth = date?.month == currentMonth.month,
                                isSelected = date == selectedDate,
                                count = date?.let { occurrenceByDate[it]?.size ?: 0 } ?: 0,
                                modifier = Modifier.weight(1f),
                                onClick = { date?.let(onSelectDate) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekViewCard(
    selectedDate: LocalDate,
    occurrenceByDate: Map<LocalDate, List<CalendarOccurrence>>,
    onSelectDate: (LocalDate) -> Unit,
    onEdit: (Long) -> Unit
) {
    val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                days.forEach { day ->
                    CalendarCell(
                        date = day,
                        inMonth = true,
                        isSelected = day == selectedDate,
                        count = occurrenceByDate[day]?.size ?: 0,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectDate(day) }
                    )
                }
            }

            val weekOccurrences = days.flatMap { occurrenceByDate[it].orEmpty() }
            if (weekOccurrences.isEmpty()) {
                EmptyState(
                    text = "这一周还没有安排提醒",
                    icon = Icons.Default.CalendarMonth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp)
                )
            } else {
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    days.forEach { day ->
                        val dayOccurrences = occurrenceByDate[day].orEmpty()
                        if (dayOccurrences.isNotEmpty()) {
                            Text(
                                text = day.format(DAY_TITLE_FORMAT),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            dayOccurrences.forEach { occurrence ->
                                OccurrenceCard(occurrence = occurrence, onEdit = onEdit)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayViewCard(
    selectedDate: LocalDate,
    occurrences: List<CalendarOccurrence>,
    onEdit: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            Text(
                text = selectedDate.format(DAY_TITLE_FORMAT),
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "当天提醒 ${occurrences.size} 条",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (occurrences.isEmpty()) {
                EmptyState(
                    text = "这一天很轻松，没有安排提醒哦",
                    icon = Icons.Default.CalendarToday,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                )
            } else {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    occurrences.forEach { occurrence ->
                        OccurrenceCard(occurrence = occurrence, onEdit = onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDayCard(
    selectedDate: LocalDate,
    occurrences: List<CalendarOccurrence>,
    onEdit: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = selectedDate.format(DAY_TITLE_FORMAT),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "当天提醒 ${occurrences.size} 条",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            if (occurrences.isEmpty()) {
                EmptyState(
                    text = "这一天很轻松\n没有安排提醒哦",
                    icon = Icons.Default.CalendarMonth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp, bottom = 10.dp)
                )
            } else {
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    occurrences.forEach { occurrence ->
                        OccurrenceCard(occurrence = occurrence, onEdit = onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalendarCell(
    date: LocalDate?,
    inMonth: Boolean,
    isSelected: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val textColor = when {
        !inMonth || date == null -> MaterialTheme.colorScheme.outline
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .clickable(enabled = date != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.dayOfMonth.toString(),
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                },
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = count.toString(),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OccurrenceCard(
    occurrence: CalendarOccurrence,
    onEdit: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable { onEdit(occurrence.alarm.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = occurrence.alarm.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = TimeFormat.subtitle(occurrence.alarm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = TimeFormat.hhmm(occurrence.triggerAt),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun expandOccurrences(
    alarms: List<Alarm>,
    startInclusive: Long,
    endExclusive: Long
): List<CalendarOccurrence> =
    alarms.asSequence()
        .filter { it.enabled }
        .flatMap { alarm ->
            ReminderScheduleResolver.occurrencesBetween(alarm, startInclusive, endExclusive)
                .asSequence()
                .map { CalendarOccurrence(alarm = alarm, triggerAt = it) }
        }
        .sortedBy { it.triggerAt }
        .toList()

private fun buildMonthCells(month: YearMonth): List<LocalDate?> {
    val firstDay = month.atDay(1)
    val offset = (firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    return List(offset) { null } + days
}

private fun buildMonthRows(cells: List<LocalDate?>): List<List<LocalDate?>> =
    cells.chunked(7).map { row ->
        if (row.size == 7) row else row + List(7 - row.size) { null }
    }

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
private val SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
private val DAY_TITLE_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
