package com.koalahamlet.adhdreminder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.koalahamlet.adhdreminder.data.LocationReminder
import com.koalahamlet.adhdreminder.data.ReminderStore
import com.koalahamlet.adhdreminder.data.SelectedPlace
import com.koalahamlet.adhdreminder.geofence.GeofenceRegistrar
import com.koalahamlet.adhdreminder.geofence.ReminderNotifications
import com.koalahamlet.adhdreminder.ui.theme.ADHDReminderTheme
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var store: ReminderStore
    private lateinit var registrar: GeofenceRegistrar
    private val reminders = mutableStateListOf<LocationReminder>()
    private var screen by mutableStateOf(Screen.MAP)
    private var selectedPlace by mutableStateOf<SelectedPlace?>(null)
    private var statusMessage by mutableStateOf<String?>(null)
    private var showBackgroundLocationDialog by mutableStateOf(false)
    private var permissionRefresh by mutableIntStateOf(0)
    private var currentLocation by mutableStateOf<LatLng?>(null)

    private val autocompleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == PlaceAutocompleteActivity.RESULT_OK && result.data != null) {
                resolvePrediction(result.data!!)
            } else if (result.resultCode == PlaceAutocompleteActivity.RESULT_ERROR && result.data != null) {
                val status = PlaceAutocomplete.getResultStatusFromIntent(result.data!!)
                statusMessage = status?.statusMessage ?: "Address search failed"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ReminderStore(applicationContext)
        registrar = GeofenceRegistrar(applicationContext)
        reloadReminders()
        if (hasApiKey && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        enableEdgeToEdge()
        setContent {
            ADHDReminderTheme(dynamicColor = false) {
                ADHDReminderApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) reloadReminders()
        if (::registrar.isInitialized && registrar.canRegister) registrar.registerAll()
        if (::registrar.isInitialized && registrar.hasForegroundLocation) refreshCurrentLocation()
        permissionRefresh += 1
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::store.isInitialized) reloadReminders()
    }

    private val hasApiKey: Boolean
        get() = BuildConfig.MAPS_API_KEY.isNotBlank() &&
            BuildConfig.MAPS_API_KEY != "DEFAULT_API_KEY"

    private fun launchAddressSearch() {
        if (!hasApiKey) {
            statusMessage = "Add MAPS_API_KEY to local.properties to enable the map and address search."
            return
        }
        autocompleteLauncher.launch(PlaceAutocomplete.IntentBuilder().build(this))
    }

    private fun resolvePrediction(intent: Intent) {
        val prediction = PlaceAutocomplete.getPredictionFromIntent(intent) ?: return
        val token = PlaceAutocomplete.getSessionTokenFromIntent(intent)
        val builder = FetchPlaceRequest.builder(
            prediction.placeId,
            listOf(Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION),
        )
        token?.let(builder::setSessionToken)
        Places.createClient(this).fetchPlace(builder.build())
            .addOnSuccessListener { response ->
                val place = response.place
                val location = place.location
                if (location == null) {
                    statusMessage = "That result does not have a map location."
                    return@addOnSuccessListener
                }
                selectedPlace = SelectedPlace(
                    name = place.displayName ?: place.formattedAddress ?: prediction.getFullText(null).toString(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                )
                screen = Screen.CREATE
            }
            .addOnFailureListener { error ->
                statusMessage = (error as? ApiException)?.localizedMessage ?: "Could not load that place."
            }
    }

    private fun saveReminder(message: String, radiusMeters: Float) {
        val place = selectedPlace ?: return
        val reminder = LocationReminder(
            id = UUID.randomUUID().toString(),
            message = message.trim(),
            placeName = place.name,
            latitude = place.latitude,
            longitude = place.longitude,
            radiusMeters = radiusMeters,
        )
        store.save(reminder)
        reloadReminders()
        screen = Screen.MAP
        selectedPlace = null
        requestReminderPermissions()
    }

    private fun reloadReminders() {
        reminders.clear()
        reminders.addAll(store.getAll().sortedByDescending { it.createdAtMillis })
    }

    private fun deleteReminder(reminder: LocationReminder) {
        registrar.remove(reminder.id)
        ReminderNotifications.cancel(this, reminder.id)
        store.delete(reminder.id)
        reloadReminders()
    }

    private fun requestReminderPermissions() {
        permissionRefresh += 1
        if (!registrar.hasForegroundLocation) {
            statusMessage = "Reminder saved. Tap Enable to activate location alerts."
            return
        }
        if (!registrar.hasBackgroundLocation) showBackgroundLocationDialog = true
        else registrar.registerAll { result ->
            statusMessage = if (result.isSuccess) "Reminder is active."
            else "Could not activate location monitoring: ${result.exceptionOrNull()?.localizedMessage}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshCurrentLocation() {
        if (!registrar.hasForegroundLocation) return
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                } else {
                    statusMessage = "Turn on device location to show your position on the map."
                }
            }
            .addOnFailureListener {
                statusMessage = "Your current location could not be determined."
            }
    }

    private enum class Screen { MAP, CREATE, REMINDERS }

    @Composable
    @SuppressLint("InlinedApi")
    private fun ADHDReminderApp() {
        var activateAfterForegroundPermission by rememberSaveable { mutableStateOf(false) }
        var requestedInitialMapLocation by rememberSaveable { mutableStateOf(false) }
        val foregroundPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            permissionRefresh += 1
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                refreshCurrentLocation()
                if (activateAfterForegroundPermission) {
                    if (!registrar.hasBackgroundLocation) showBackgroundLocationDialog = true
                    else registrar.registerAll()
                }
            } else {
                statusMessage = "Allow precise location to show your position and monitor reminders."
            }
            activateAfterForegroundPermission = false
        }
        val backgroundPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            permissionRefresh += 1
            if (granted) registrar.registerAll()
            else statusMessage = "Saved, but inactive until “Allow all the time” is enabled."
        }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (!granted) statusMessage = "Notifications are off; location events cannot alert you."
        }

        fun requestMapLocation() {
            activateAfterForegroundPermission = false
            foregroundPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }

        fun activateReminders() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!registrar.hasForegroundLocation) {
                activateAfterForegroundPermission = true
                foregroundPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            } else if (!registrar.hasBackgroundLocation) {
                showBackgroundLocationDialog = true
            } else {
                registrar.registerAll { result ->
                    statusMessage = if (result.isSuccess) "All reminders are active."
                    else "Could not activate location monitoring: ${result.exceptionOrNull()?.localizedMessage}"
                }
            }
        }

        LaunchedEffect(screen, permissionRefresh) {
            if (
                screen == Screen.MAP &&
                !registrar.hasForegroundLocation &&
                !requestedInitialMapLocation
            ) {
                requestedInitialMapLocation = true
                requestMapLocation()
            }
        }

        when (screen) {
            Screen.MAP -> MapScreen(
                reminders = reminders,
                hasApiKey = hasApiKey,
                hasLocationPermission = registrar.hasForegroundLocation.also { permissionRefresh },
                currentLocation = currentLocation,
                remindersActive = registrar.canRegister,
                statusMessage = statusMessage,
                onDismissStatus = { statusMessage = null },
                onSearch = ::launchAddressSearch,
                onLongPress = { point ->
                    selectedPlace = SelectedPlace(
                        name = String.format(Locale.US, "Dropped pin · %.5f, %.5f", point.latitude, point.longitude),
                        latitude = point.latitude,
                        longitude = point.longitude,
                    )
                    screen = Screen.CREATE
                },
                onManage = { screen = Screen.REMINDERS },
                onActivate = ::activateReminders,
                onRequestLocation = ::requestMapLocation,
                onDelete = ::deleteReminder,
            )
            Screen.CREATE -> ReminderCreationScreen(
                place = selectedPlace ?: return,
                onBack = { screen = Screen.MAP },
                onSave = ::saveReminder,
            )
            Screen.REMINDERS -> ReminderListScreen(
                reminders = reminders,
                onBack = { screen = Screen.MAP },
                onDelete = ::deleteReminder,
            )
        }

        if (showBackgroundLocationDialog) {
            BackgroundLocationDialog(
                onDismiss = {
                    showBackgroundLocationDialog = false
                    statusMessage = "Saved, but inactive until background location is enabled."
                },
                onContinue = {
                    showBackgroundLocationDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                "package:$packageName".toUri(),
                            )
                        )
                    } else {
                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MapScreen(
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
    onDelete: (LocationReminder) -> Unit,
) {
    var reminderPendingDelete by remember { mutableStateOf<LocationReminder?>(null) }
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
                        Text(
                            "Active reminders",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${activeReminders.size} location${if (activeReminders.size == 1) "" else "s"} armed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onManage) { Text("View all") }
                }

            if (statusMessage != null) {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(statusMessage, modifier = Modifier.weight(1f))
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(activeReminders, key = { it.id }) { reminder ->
                            Card(
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { reminderPendingDelete = reminder },
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                )
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
                                        Text(
                                            reminder.message,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                        )
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

    reminderPendingDelete?.let { reminder ->
        DeleteReminderDialog(
            reminder = reminder,
            onDismiss = { reminderPendingDelete = null },
            onConfirm = {
                reminderPendingDelete = null
                onDelete(reminder)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCreationScreen(
    place: SelectedPlace,
    onBack: () -> Unit,
    onSave: (String, Float) -> Unit,
) {
    var message by rememberSaveable(place.latitude, place.longitude) { mutableStateOf("") }
    var radius by rememberSaveable { mutableFloatStateOf(150f) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New location reminder") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
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
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 100f..500f,
                    steps = 7,
                )
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
                Text("Save reminder")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ReminderListScreen(
    reminders: List<LocationReminder>,
    onBack: () -> Unit,
    onDelete: (LocationReminder) -> Unit,
) {
    var reminderPendingDelete by remember { mutableStateOf<LocationReminder?>(null) }
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    Card(
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { reminderPendingDelete = reminder },
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
                            Text(reminder.message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(reminder.placeName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${reminder.radiusMeters.toInt()} m radius", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    reminderPendingDelete?.let { reminder ->
        DeleteReminderDialog(
            reminder = reminder,
            onDismiss = { reminderPendingDelete = null },
            onConfirm = {
                reminderPendingDelete = null
                onDelete(reminder)
            },
        )
    }
}

@Composable
private fun DeleteReminderDialog(
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
private fun BackgroundLocationDialog(onDismiss: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow reminders in the background") },
        text = {
            Text(
                "ADHD Reminder needs location access even when you aren’t using the app so it can notice " +
                    "when you enter a saved area. On the next screen, choose Location, then “Allow all the time”."
            )
        },
        confirmButton = { Button(onClick = onContinue) { Text("Open settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
