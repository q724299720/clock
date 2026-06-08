package com.smartclock.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 空列表占位 */
@Composable
fun EmptyState(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    }
}

/** 权限引导卡片：未授权时提示并提供一键跳转 */
@Composable
fun PermissionGuideCard(
    permission: String,
    granted: Boolean,
    onFix: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (granted) return
    OutlinedCard(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                Text("  需要授权：$permission", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "为保证闹钟准时提醒，请开启此权限。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = onFix) { Text("去设置") }
        }
    }
}
