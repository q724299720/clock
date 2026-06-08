package com.smartclock.ui.home.reminder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartclock.domain.model.DeletedAlarmSnapshot
import com.smartclock.ui.alarm.AlarmViewModel
import com.smartclock.ui.component.EmptyState
import com.smartclock.util.TimeFormat
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeletedRemindersScreen(
    onBack: () -> Unit,
    vm: AlarmViewModel = hiltViewModel()
) {
    val deleted by vm.deletedAlarms.collectAsStateWithLifecycle()
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }

    val allSelected = deleted.isNotEmpty() && deleted.all { it.trashId in selectedIds }
    val selectedTrashIds = remember(deleted, selectedIds) {
        deleted.filter { it.trashId in selectedIds }.map { it.trashId }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectionMode) "Selected ${selectedIds.size}" else "Recently Deleted")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selectedIds = emptySet()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            selectedIds = if (allSelected) {
                                emptySet()
                            } else {
                                deleted.mapTo(linkedSetOf()) { it.trashId }
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(
                            onClick = {
                                vm.restoreDeletedMany(selectedTrashIds)
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                            enabled = selectedTrashIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = "Restore")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (deleted.isEmpty()) {
                item {
                    EmptyState(
                        text = "No deleted reminders yet.\nDeleted items stay on this device for 30 days.",
                        icon = Icons.Default.History,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp)
                    )
                }
            } else {
                items(deleted, key = { it.trashId }) { snapshot ->
                    val selected = snapshot.trashId in selectedIds
                    DeletedReminderCard(
                        snapshot = snapshot,
                        selected = selected,
                        selectionMode = selectionMode,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = selectedIds.toggle(snapshot.trashId)
                            } else {
                                vm.restoreDeleted(snapshot.trashId)
                            }
                        },
                        onLongPress = {
                            selectionMode = true
                            selectedIds = selectedIds + snapshot.trashId
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeletedReminderCard(
    snapshot: DeletedAlarmSnapshot,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(
                        modifier = Modifier.padding(10.dp),
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
            } else {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = snapshot.alarm.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = snapshot.alarm.triggerTime?.let(TimeFormat::full) ?: "No schedule",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Deleted ${TimeFormat.full(snapshot.deletedAt)} | ${remainingDays(snapshot)} days left",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (!selectionMode) {
                Text(
                    text = "Restore",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun remainingDays(snapshot: DeletedAlarmSnapshot): Long {
    val remaining = snapshot.expiresAt - System.currentTimeMillis()
    return TimeUnit.MILLISECONDS.toDays(remaining.coerceAtLeast(0L)).coerceAtLeast(0L)
}

private fun Set<Long>.toggle(id: Long): Set<Long> =
    if (id in this) this - id else this + id
