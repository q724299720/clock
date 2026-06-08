package com.smartclock.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelPickerCard(
            label = "时",
            value = hour,
            minValue = 0,
            maxValue = 23,
            onValueChange = { onChange(it, minute) }
        )

        Text(
            text = ":",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        WheelPickerCard(
            label = "分",
            value = minute,
            minValue = 0,
            maxValue = 59,
            onValueChange = { onChange(hour, it) }
        )
    }
}

@Composable
private fun WheelPickerCard(
    label: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit
) {
    val values = remember(minValue, maxValue) {
        (minValue..maxValue).toList()
    }
    val currentIndex = (value - minValue).coerceIn(0, values.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    val selectedIndex by remember(listState, currentIndex) {
        derivedStateOf { listState.centeredItemIndex(currentIndex) }
    }

    LaunchedEffect(value, minValue, maxValue) {
        if (!listState.isScrollInProgress) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    LaunchedEffect(listState.isScrollInProgress, selectedIndex) {
        if (!listState.isScrollInProgress) {
            values.getOrNull(selectedIndex)?.let { selectedValue ->
                if (selectedValue != value) {
                    onValueChange(selectedValue)
                }
            }
            listState.animateScrollToItem(selectedIndex.coerceIn(0, values.lastIndex))
        }
    }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .width(128.dp)
            .height(284.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    onValueChange(stepValue(value, -1, minValue, maxValue))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 8.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 65.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(values) { index, item ->
                        WheelValueRow(
                            value = item,
                            selected = index == selectedIndex,
                            onClick = { onValueChange(item) }
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    onValueChange(stepValue(value, 1, minValue, maxValue))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                )
            }
        }
    }
}

private fun LazyListState.centeredItemIndex(fallbackIndex: Int): Int {
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return fallbackIndex

    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    return visibleItems.minByOrNull { item ->
        abs((item.offset + (item.size / 2f)) - viewportCenter)
    }?.index ?: fallbackIndex
}

@Composable
private fun WheelValueRow(
    value: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }
    val fontSize = if (selected) 40.sp else 22.sp
    val fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString().padStart(2, '0'),
            style = MaterialTheme.typography.displayMedium,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = textColor
        )
    }
}

private fun stepValue(current: Int, delta: Int, minValue: Int, maxValue: Int): Int {
    val range = maxValue - minValue + 1
    val normalized = (current - minValue + delta).floorMod(range)
    return minValue + normalized
}

private fun Int.floorMod(modulus: Int): Int {
    val result = this % modulus
    return if (result < 0) result + modulus else result
}

private fun clampInt(s: String, max: Int): Int =
    s.filter { it.isDigit() }.take(2).toIntOrNull()?.coerceIn(0, max) ?: 0

@Composable
fun DurationPicker(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onChange: (h: Int, m: Int, s: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = hours.toString(),
            onValueChange = { onChange(clampInt(it, 99), minutes, seconds) },
            label = { Text("时") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(86.dp)
        )
        Text(" : ", modifier = Modifier.padding(horizontal = 4.dp))
        OutlinedTextField(
            value = minutes.toString(),
            onValueChange = { onChange(hours, clampInt(it, 59), seconds) },
            label = { Text("分") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(86.dp)
        )
        Text(" : ", modifier = Modifier.padding(horizontal = 4.dp))
        OutlinedTextField(
            value = seconds.toString(),
            onValueChange = { onChange(hours, minutes, clampInt(it, 59)) },
            label = { Text("秒") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(86.dp)
        )
    }
}
