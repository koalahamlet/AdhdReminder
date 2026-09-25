package com.koalahamlet.adhdreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderJsonCodecTest {
    @Test
    fun `reminder remains active from armed through triggered until completed`() {
        val armed = LocationReminder(
            id = "state-test",
            message = "Test",
            placeName = "Somewhere",
            latitude = 1.0,
            longitude = 2.0,
        )
        assertTrue(armed.isArmed)

        val triggered = armed.copy(triggeredAtMillis = 100L)
        assertTrue(triggered.isTriggered)
        assertTrue(triggered.isActive)

        val completed = triggered.copy(completedAtMillis = 200L)
        assertFalse(completed.isActive)
        assertFalse(completed.isTriggered)
        assertFalse(completed.isArmed)
    }

    @Test
    fun `multiple reminders survive a persistence round trip`() {
        val reminders = listOf(
            LocationReminder(
                id = "first",
                message = "Pick up medication",
                placeName = "Pharmacy",
                latitude = 37.1,
                longitude = -122.1,
                radiusMeters = 150f,
                createdAtMillis = 1000L,
            ),
            LocationReminder(
                id = "second",
                message = "Buy milk",
                placeName = "Grocery store",
                latitude = 37.2,
                longitude = -122.2,
                radiusMeters = 250f,
                createdAtMillis = 2000L,
                triggeredAtMillis = 2500L,
                completedAtMillis = 3000L,
            ),
        )

        val restored = ReminderJsonCodec.decode(ReminderJsonCodec.encode(reminders))

        assertEquals(reminders, restored)
    }
}
