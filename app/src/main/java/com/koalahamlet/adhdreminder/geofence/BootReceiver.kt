package com.koalahamlet.adhdreminder.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.koalahamlet.adhdreminder.data.ReminderStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        ReminderStore(context).getAll()
            .filter { it.isTriggered }
            .forEach { ReminderNotifications.show(context, it) }
        val pendingResult = goAsync()
        GeofenceRegistrar(context).registerAll { pendingResult.finish() }
    }
}
