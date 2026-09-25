package com.koalahamlet.adhdreminder.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class ReminderStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<LocationReminder> {
        val raw = preferences.getString(KEY_REMINDERS, null) ?: return emptyList()
        return ReminderJsonCodec.decode(raw)
    }

    fun save(reminder: LocationReminder) {
        write(getAll().filterNot { it.id == reminder.id } + reminder)
    }

    fun markCompleted(id: String) {
        write(
            getAll().map { reminder ->
                if (reminder.id == id && reminder.isActive) {
                    reminder.copy(completedAtMillis = System.currentTimeMillis())
                } else {
                    reminder
                }
            }
        )
    }

    fun markTriggered(id: String) {
        write(
            getAll().map { reminder ->
                if (reminder.id == id && reminder.isArmed) {
                    reminder.copy(triggeredAtMillis = System.currentTimeMillis())
                } else {
                    reminder
                }
            }
        )
    }

    fun delete(id: String) {
        write(getAll().filterNot { it.id == id })
    }

    private fun write(reminders: List<LocationReminder>) {
        preferences.edit(commit = true) { putString(KEY_REMINDERS, ReminderJsonCodec.encode(reminders)) }
    }

    private companion object {
        const val PREFERENCES_NAME = "location_reminders"
        const val KEY_REMINDERS = "reminders"
    }
}

internal object ReminderJsonCodec {
    fun decode(raw: String): List<LocationReminder> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    LocationReminder(
                        id = item.getString("id"),
                        message = item.getString("message"),
                        placeName = item.getString("placeName"),
                        latitude = item.getDouble("latitude"),
                        longitude = item.getDouble("longitude"),
                        radiusMeters = item.optDouble("radiusMeters", 150.0).toFloat(),
                        createdAtMillis = item.optLong("createdAtMillis", 0L),
                        triggeredAtMillis = if (item.isNull("triggeredAtMillis")) {
                            null
                        } else {
                            item.getLong("triggeredAtMillis")
                        },
                        completedAtMillis = if (item.isNull("completedAtMillis")) {
                            null
                        } else {
                            item.getLong("completedAtMillis")
                        },
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun encode(reminders: List<LocationReminder>): String {
        val array = JSONArray()
        reminders.forEach { reminder ->
            array.put(
                JSONObject().apply {
                    put("id", reminder.id)
                    put("message", reminder.message)
                    put("placeName", reminder.placeName)
                    put("latitude", reminder.latitude)
                    put("longitude", reminder.longitude)
                    put("radiusMeters", reminder.radiusMeters.toDouble())
                    put("createdAtMillis", reminder.createdAtMillis)
                    put("triggeredAtMillis", reminder.triggeredAtMillis ?: JSONObject.NULL)
                    put("completedAtMillis", reminder.completedAtMillis ?: JSONObject.NULL)
                }
            )
        }
        return array.toString()
    }
}
