package com.koalahamlet.adhdreminder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.koalahamlet.adhdreminder.data.LocationReminder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeReminderActions(
    reminder: LocationReminder,
    onDone: (LocationReminder) -> Unit,
    onDelete: (LocationReminder) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    // A completed item remains in the full-history list, so return it to rest after
    // the native dismiss callback changes its status. Deleted and active-list items
    // leave composition instead.
    LaunchedEffect(reminder.isActive) {
        if (!reminder.isActive && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }
    // dismissDirection follows the user's finger. targetValue only changes after the
    // threshold, which makes the background appear to pop between two actions.
    val deleting = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = reminder.isActive,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onDone(reminder)
                SwipeToDismissBoxValue.EndToStart -> onDelete(reminder)
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = if (deleting) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = if (deleting) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Text(
                    text = if (deleting) "DELETE" else "DONE",
                    color = if (deleting) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        content = content,
    )
}
