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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity
import com.koalahamlet.adhdreminder.data.LocationReminder
import com.koalahamlet.adhdreminder.data.ReminderStore
import com.koalahamlet.adhdreminder.data.SelectedPlace
import com.koalahamlet.adhdreminder.geofence.GeofenceRegistrar
import com.koalahamlet.adhdreminder.geofence.ReminderNotifications
import com.koalahamlet.adhdreminder.ui.components.BackgroundLocationDialog
import com.koalahamlet.adhdreminder.ui.map.MapScreen
import com.koalahamlet.adhdreminder.ui.reminder.ReminderCreationScreen
import com.koalahamlet.adhdreminder.ui.reminder.ReminderListScreen
import com.koalahamlet.adhdreminder.ui.theme.ADHDReminderTheme
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var store: ReminderStore
    private lateinit var registrar: GeofenceRegistrar
    private val reminders = mutableStateListOf<LocationReminder>()
    private var screen by mutableStateOf(Screen.MAP)
    private var selectedPlace by mutableStateOf<SelectedPlace?>(null)
    private var editingReminder by mutableStateOf<LocationReminder?>(null)
    private var editorReturnScreen by mutableStateOf(Screen.MAP)
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
                editingReminder = null
                screen = Screen.CREATE
            }
            .addOnFailureListener { error ->
                statusMessage = (error as? ApiException)?.localizedMessage ?: "Could not load that place."
            }
    }

    private fun saveReminder(message: String, radiusMeters: Float) {
        val place = selectedPlace ?: return
        val original = editingReminder
        val reminder = original?.copy(
            message = message.trim(),
            radiusMeters = radiusMeters,
        ) ?: LocationReminder(
                id = UUID.randomUUID().toString(),
                message = message.trim(),
                placeName = place.name,
                latitude = place.latitude,
                longitude = place.longitude,
                radiusMeters = radiusMeters,
            )
        store.save(reminder)
        reloadReminders()
        screen = if (original == null) Screen.MAP else editorReturnScreen
        selectedPlace = null
        editingReminder = null

        if (original == null) {
            requestReminderPermissions()
        } else {
            when {
                reminder.isArmed && registrar.canRegister -> registrar.register(reminder)
                reminder.isTriggered -> ReminderNotifications.show(this, reminder)
            }
            statusMessage = "Reminder updated."
        }
    }

    private fun editReminder(reminder: LocationReminder) {
        editorReturnScreen = screen
        editingReminder = reminder
        selectedPlace = SelectedPlace(
            name = reminder.placeName,
            latitude = reminder.latitude,
            longitude = reminder.longitude,
        )
        screen = Screen.CREATE
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
                    editingReminder = null
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
                onEdit = ::editReminder,
                onDelete = ::deleteReminder,
            )
            Screen.CREATE -> ReminderCreationScreen(
                place = selectedPlace ?: return,
                initialMessage = editingReminder?.message.orEmpty(),
                initialRadiusMeters = editingReminder?.radiusMeters ?: 150f,
                isEditing = editingReminder != null,
                onBack = {
                    screen = if (editingReminder == null) Screen.MAP else editorReturnScreen
                    selectedPlace = null
                    editingReminder = null
                },
                onSave = ::saveReminder,
            )
            Screen.REMINDERS -> ReminderListScreen(
                reminders = reminders,
                onBack = { screen = Screen.MAP },
                onEdit = ::editReminder,
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
