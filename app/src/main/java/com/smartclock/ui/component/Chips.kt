package com.smartclock.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.smartclock.ui.theme.labelColorHex

@Composable
fun LabelChip(label: String, color: String? = null, modifier: Modifier = Modifier) {
    val chipColor = runCatching { Color((color ?: labelColorHex(label)).toColorInt()) }
        .getOrDefault(Color.Gray)
    Surface(
        color = chipColor.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(color = chipColor, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
            }
            Text("  $label", color = chipColor, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private val WEEK_LABELS = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekChipRow(
    selectedBits: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WEEK_LABELS.forEachIndexed { bit, label ->
            val selected = (selectedBits and (1 shl bit)) != 0
            FilterChip(
                selected = selected,
                onClick = { onChange(selectedBits xor (1 shl bit)) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors()
            )
        }
    }
}

@Composable
fun MonthDateGrid(
    selectedBits: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items((1..31).toList()) { day ->
            val selected = (selectedBits and (1 shl day)) != 0
            FilterChip(
                selected = selected,
                onClick = { onChange(selectedBits xor (1 shl day)) },
                label = { Text(day.toString()) }
            )
        }
    }
}
