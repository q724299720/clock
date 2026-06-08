package com.smartclock.ui.template

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smartclock.domain.model.AlarmTemplateIds
import com.smartclock.domain.model.AlarmType
import com.smartclock.ui.component.PageHero
import com.smartclock.ui.component.TimelyBrandBar

@Composable
fun TemplateScreen(
    onSelectTemplate: (AlarmType, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = 110.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            TimelyBrandBar()
        }

        item {
            PageHero(
                title = "模板",
                subtitle = "用模板快速创建中国生活提醒"
            )
        }

        items(TEMPLATES, key = { it.id }) { template ->
            TemplateCard(
                template = template,
                onClick = { onSelectTemplate(template.type, template.id) }
            )
        }
    }
}

@Composable
private fun TemplateCard(
    template: AlarmTemplateCard,
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
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(
                    modifier = Modifier.size(84.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = template.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp)
            ) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class AlarmTemplateCard(
    val id: String,
    val title: String,
    val description: String,
    val type: AlarmType,
    val icon: ImageVector
)

private val TEMPLATES = listOf(
    AlarmTemplateCard(
        id = AlarmTemplateIds.BIRTHDAY,
        title = "生日管家",
        description = "每年提醒，支持公历和农历日期",
        type = AlarmType.ANNIVERSARY,
        icon = Icons.Default.Cake
    ),
    AlarmTemplateCard(
        id = AlarmTemplateIds.WORKDAY,
        title = "法定工作日提醒",
        description = "按中国调休规则提醒，补班日也会响",
        type = AlarmType.WEEKLY,
        icon = Icons.Default.WorkHistory
    ),
    AlarmTemplateCard(
        id = AlarmTemplateIds.CREDIT_CARD,
        title = "还信用卡",
        description = "每月固定日期提醒，适合还款日",
        type = AlarmType.MONTHLY,
        icon = Icons.Default.CreditCard
    ),
    AlarmTemplateCard(
        id = AlarmTemplateIds.RENT,
        title = "交房租",
        description = "按每两个月一次的节奏提醒",
        type = AlarmType.MONTHLY,
        icon = Icons.Default.Payments
    ),
    AlarmTemplateCard(
        id = AlarmTemplateIds.MEDICINE,
        title = "按时吃药",
        description = "每日定时提醒，关爱健康",
        type = AlarmType.WEEKLY,
        icon = Icons.Default.Medication
    ),
    AlarmTemplateCard(
        id = AlarmTemplateIds.WATER,
        title = "喝水提醒",
        description = "预设常用喝水时段，支持批量创建",
        type = AlarmType.WEEKLY,
        icon = Icons.Default.LocalDrink
    ),
    AlarmTemplateCard(
        id = AlarmTemplateIds.LICENSE_REVIEW,
        title = "驾照年检",
        description = "按两年周期提醒一次",
        type = AlarmType.ANNIVERSARY,
        icon = Icons.Default.EventRepeat
    ),
    AlarmTemplateCard(
        id = AlarmTemplateIds.SHIFT,
        title = "轮班闹钟",
        description = "预设早班、中班、夜班时间，再细调星期",
        type = AlarmType.WEEKLY,
        icon = Icons.Default.CalendarMonth
    )
)
