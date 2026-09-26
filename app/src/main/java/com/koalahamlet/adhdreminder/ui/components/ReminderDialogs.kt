package com.koalahamlet.adhdreminder.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.koalahamlet.adhdreminder.data.LocationReminder

@Composable
fun DeleteReminderDialog(
    reminder: LocationReminder,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete reminder?") },
        text = { Text("“${reminder.message}” will be permanently removed.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BackgroundLocationDialog(onDismiss: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow reminders in the background") },
        text = {
            Text(
                "ADHD Reminder needs location access even when you aren’t using the app so it can notice " +
                    "when you enter a saved area. On the next screen, choose Location, then “Allow all the time”.",
            )
        },
        confirmButton = { Button(onClick = onContinue) { Text("Open settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
