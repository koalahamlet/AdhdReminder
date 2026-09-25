# ADHD Reminder

An Android location-reminder app built with Kotlin and Jetpack Compose. Search for an address or
long-press the map to drop a pin, enter a reminder, and receive a vibrating notification when the
device enters that location's geofence.

## Google Maps setup

1. Create or select a billing-enabled project in Google Cloud Console.
2. Enable **Maps SDK for Android** and **Places API (New)**.
3. Create an Android API key and restrict it to this app's package name:
   `com.koalahamlet.adhdreminder` and the signing certificate SHA-1.
4. Add the key to the existing, git-ignored `local.properties` file:

   ```properties
   MAPS_API_KEY=your_key_here
   ```

The Gradle build injects this value into both the Maps manifest metadata and `BuildConfig`; the key
does not need to be committed to source control.

## Running

Use a physical Android phone or a Google Play-enabled emulator. On the first reminder, tap
**Enable**, grant precise location and notifications, then choose **Allow all the time** from the
app's Location settings. Background location is essential to geofencing and is requested separately,
as required on current Android versions.

Geofence delivery is intentionally power-efficient rather than instant. Android may delay an event
by a couple of minutes, especially when the phone has been stationary. Active geofences are restored
after reboot. An entry alert removes the geofence but keeps a persistent notification visible; the
reminder is completed only when **Mark done** is selected from that notification.

## Storage

Reminders are stored locally in `SharedPreferences` as JSON through `ReminderStore`. The storage
interface is isolated so it can be replaced with Room later if the data model or query needs grow.
