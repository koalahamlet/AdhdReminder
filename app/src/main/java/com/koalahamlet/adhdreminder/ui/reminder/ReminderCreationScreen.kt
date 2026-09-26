package com.koalahamlet.adhdreminder.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.koalahamlet.adhdreminder.data.SelectedPlace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderCreationScreen(
    place: SelectedPlace,
    initialMessage: String = "",
    initialRadiusMeters: Float = 150f,
    isEditing: Boolean = false,
    onBack: () -> Unit,
    onSave: (String, Float) -> Unit,
) {
    var message by rememberSaveable(place.latitude, place.longitude, initialMessage) {
        mutableStateOf(initialMessage)
    }
    var radius by rememberSaveable(place.latitude, place.longitude, initialRadiusMeters) {
        mutableFloatStateOf(initialRadiusMeters)
    }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit reminder" else "New location reminder") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) { Text("●", color = MaterialTheme.colorScheme.primary) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Remind me near", style = MaterialTheme.typography.labelLarge)
                Text(place.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What should I remind you?") },
                placeholder = { Text("Pick up the prescription") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Trigger distance")
                    Text("${radius.toInt()} m", fontWeight = FontWeight.SemiBold)
                }
                Slider(value = radius, onValueChange = { radius = it }, valueRange = 100f..500f, steps = 7)
                Text(
                    "A larger area is more reliable when GPS or Wi-Fi accuracy is limited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onSave(message, radius) },
                enabled = message.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(if (isEditing) "Save changes" else "Save reminder")
            }
        }
    }
}
