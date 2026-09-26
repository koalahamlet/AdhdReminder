package com.koalahamlet.adhdreminder.ui.map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.koalahamlet.adhdreminder.data.LocationReminder
import com.koalahamlet.adhdreminder.ui.components.ReminderActionsDialog

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MapScreen(
    reminders: List<LocationReminder>,
    hasApiKey: Boolean,
    hasLocationPermission: Boolean,
    currentLocation: LatLng?,
    remindersActive: Boolean,
    statusMessage: String?,
    onDismissStatus: () -> Unit,
    onSearch: () -> Unit,
    onLongPress: (LatLng) -> Unit,
    onManage: () -> Unit,
    onActivate: () -> Unit,
    onRequestLocation: () -> Unit,
    onEdit: (LocationReminder) -> Unit,
    onDelete: (LocationReminder) -> Unit,
) {
    var reminderPendingAction by remember { mutableStateOf<LocationReminder?>(null) }
    val activeReminders = reminders.filter { it.isActive }
    val defaultLocation = LatLng(37.7749, -122.4194)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            activeReminders.firstOrNull()?.let { LatLng(it.latitude, it.longitude) } ?: defaultLocation,
            if (activeReminders.isEmpty()) 11f else 14f,
        )
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let { location ->
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(location, 15f), 700)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(
                    compassEnabled = true,
                    myLocationButtonEnabled = hasLocationPermission,
                    zoomControlsEnabled = false,
                ),
                onMapLongClick = onLongPress,
            ) {
                activeReminders.forEach { reminder ->
                    val point = LatLng(reminder.latitude, reminder.longitude)
                    Marker(
                        state = rememberUpdatedMarkerState(position = point),
                        title = reminder.message,
                        snippet = reminder.placeName,
                    )
                    Circle(
                        center = point,
                        radius = reminder.radiusMeters.toDouble(),
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                        strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        strokeWidth = 2f,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 42.dp)
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(28.dp))
                    .clickable(onClick = onSearch),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("⌕", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (hasApiKey) "Search for an address or place" else "Add a Google Maps API key",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!hasLocationPermission) {
                    Button(onClick = onRequestLocation) { Text("Show my location") }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ) {
                    Text(
                        "Long-press the map to drop a reminder pin",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Active reminders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${activeReminders.size} location${if (activeReminders.size == 1) "" else "s"} armed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onManage) { Text("View all") }
                }

                statusMessage?.let { message ->
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(message, modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismissStatus) { Text("OK") }
                        }
                    }
                }

                if (activeReminders.isNotEmpty() && !remindersActive) {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Location access needed", fontWeight = FontWeight.SemiBold)
                                Text("Allow all-the-time access so reminders work when the app is closed.")
                            }
                            Button(onClick = onActivate) { Text("Enable") }
                        }
                    }
                }

                if (activeReminders.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No active reminders", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Search above or long-press the map to add one.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(activeReminders, key = { it.id }) { reminder ->
                            Card(
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { reminderPendingAction = reminder },
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier.size(38.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("●", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(reminder.message, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                        Text(
                                            reminder.placeName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                    Text(
                                        if (reminder.isTriggered) "ARRIVED" else "${reminder.radiusMeters.toInt()} m",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (reminder.isTriggered) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
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
