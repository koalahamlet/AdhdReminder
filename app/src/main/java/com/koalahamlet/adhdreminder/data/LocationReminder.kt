package com.koalahamlet.adhdreminder.data

data class LocationReminder(
    val id: String,
    val message: String,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val triggeredAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
) {
    val isActive: Boolean get() = completedAtMillis == null
    val isTriggered: Boolean get() = triggeredAtMillis != null && isActive
    val isArmed: Boolean get() = triggeredAtMillis == null && isActive
}

data class SelectedPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)
