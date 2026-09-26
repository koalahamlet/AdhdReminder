package com.koalahamlet.adhdreminder.ui.reminder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koalahamlet.adhdreminder.data.LocationReminder
import com.koalahamlet.adhdreminder.ui.components.ReminderActionsDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReminderListScreen(
    reminders: List<LocationReminder>,
    onBack: () -> Unit,
    onEdit: (LocationReminder) -> Unit,
    onDelete: (LocationReminder) -> Unit,
) {
    var reminderPendingAction by remember { mutableStateOf<LocationReminder?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My reminders") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        if (reminders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No reminders yet. Long-press the map to add one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    Card(
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { reminderPendingAction = reminder },
                        ),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when {
                                        reminder.isTriggered -> "READY TO MARK DONE"
                                        reminder.isActive -> "ACTIVE"
                                        else -> "COMPLETED"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        reminder.isTriggered -> MaterialTheme.colorScheme.tertiary
                                        reminder.isActive -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { onDelete(reminder) }) { Text("Delete") }
                            }
                            Text(
                                reminder.message,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(reminder.placeName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${reminder.radiusMeters.toInt()} m radius", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    reminderPendingAction?.let { reminder ->
        ReminderActionsDialog(
            reminder = reminder,
            onDismiss = { reminderPendingAction = null },
            onEdit = {
                reminderPendingAction = null
                onEdit(reminder)
            },
            onDelete = {
                reminderPendingAction = null
                onDelete(reminder)
            },
        )
    }
}
