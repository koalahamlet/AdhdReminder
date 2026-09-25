package com.koalahamlet.adhdreminder.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.koalahamlet.adhdreminder.data.LocationReminder
import com.koalahamlet.adhdreminder.data.ReminderStore

class GeofenceRegistrar(private val context: Context) {
    private val client = LocationServices.getGeofencingClient(context)

    val hasForegroundLocation: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    val hasBackgroundLocation: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    val canRegister: Boolean get() = hasForegroundLocation && hasBackgroundLocation

    @SuppressLint("MissingPermission")
    fun register(reminder: LocationReminder, onResult: (Result<Unit>) -> Unit = {}) {
        if (!canRegister) {
            onResult(Result.failure(SecurityException("Always-on precise location is required")))
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId(reminder.id)
            .setCircularRegion(reminder.latitude, reminder.longitude, reminder.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        // The builder defaults to ENTER | DWELL, so explicitly use zero. A reminder
        // created while already inside its radius must wait for a real re-entry.
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()

        client.addGeofences(request, transitionPendingIntent)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun registerAll(onComplete: (Result<Unit>) -> Unit = {}) {
        if (!canRegister) {
            onComplete(Result.failure(SecurityException("Always-on precise location is required")))
            return
        }
        val active = ReminderStore(context).getAll().filter { it.isArmed }
        if (active.isEmpty()) {
            onComplete(Result.success(Unit))
            return
        }

        var remaining = active.size
        var firstFailure: Throwable? = null
        active.forEach { reminder ->
            register(reminder) { result ->
                result.exceptionOrNull()?.let { if (firstFailure == null) firstFailure = it }
                remaining -= 1
                if (remaining == 0) {
                    onComplete(firstFailure?.let(Result.Companion::failure) ?: Result.success(Unit))
                }
            }
        }
    }

    fun remove(id: String) {
        client.removeGeofences(listOf(id))
    }

    private val transitionPendingIntent: PendingIntent
        get() {
            val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
                action = ACTION_GEOFENCE_TRANSITION
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        }

    companion object {
        const val ACTION_GEOFENCE_TRANSITION =
            "com.koalahamlet.adhdreminder.action.GEOFENCE_TRANSITION"
    }
}
