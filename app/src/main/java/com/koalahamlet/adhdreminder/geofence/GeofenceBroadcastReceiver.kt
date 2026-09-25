package com.koalahamlet.adhdreminder.geofence

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.koalahamlet.adhdreminder.MainActivity
import com.koalahamlet.adhdreminder.R
import com.koalahamlet.adhdreminder.data.LocationReminder
import com.koalahamlet.adhdreminder.data.ReminderStore

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MARK_DONE -> markDone(context, intent.getStringExtra(EXTRA_REMINDER_ID))
            GeofenceRegistrar.ACTION_GEOFENCE_TRANSITION -> handleTransition(context, intent)
        }
    }

    private fun handleTransition(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val store = ReminderStore(context)
        val byId = store.getAll().associateBy { it.id }
        event.triggeringGeofences.orEmpty().forEach { geofence ->
            val reminder = byId[geofence.requestId] ?: return@forEach
            if (!reminder.isArmed) return@forEach

            // Do not consume the reminder if notifications are disabled. Keeping the
            // geofence armed lets a later entry try again after permission is restored.
            if (ReminderNotifications.show(context, reminder)) {
                store.markTriggered(reminder.id)
                GeofenceRegistrar(context).remove(reminder.id)
            }
        }
    }

    private fun markDone(context: Context, reminderId: String?) {
        if (reminderId == null) return
        ReminderStore(context).markCompleted(reminderId)
        GeofenceRegistrar(context).remove(reminderId)
        ReminderNotifications.cancel(context, reminderId)
    }

    companion object {
        const val ACTION_MARK_DONE = "com.koalahamlet.adhdreminder.action.MARK_DONE"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}

internal object ReminderNotifications {
    private const val CHANNEL_ID = "location_reminders"

    fun show(context: Context, reminder: LocationReminder): Boolean {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val openApp = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markDone = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            Intent(context, GeofenceBroadcastReceiver::class.java).apply {
                action = GeofenceBroadcastReceiver.ACTION_MARK_DONE
                putExtra(GeofenceBroadcastReceiver.EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pin)
            .setContentTitle("You’re near ${reminder.placeName}")
            .setContentText(reminder.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_notification_pin, "Mark done", markDone)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(reminder.id), notification)
        return true
    }

    fun cancel(context: Context, reminderId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notificationId(reminderId: String): Int = reminderId.hashCode()
}
