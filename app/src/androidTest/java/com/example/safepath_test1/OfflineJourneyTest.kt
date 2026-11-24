package com.example.safepath_test1

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.safepath_test1.session.LocationQueue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineJourneyTest {
    @Test fun pendingLocationSurvivesReopenAndOnlyItsOwnerCanAcknowledge() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val user = UUID.randomUUID().toString()
        val channel = UUID.randomUUID().toString()
        LocationQueue(context).use {
            it.add(user, channel, Location("test").apply { latitude = 37.0; longitude = 127.0; time = 12345L })
        }
        LocationQueue(context).use {
            val pending = requireNotNull(it.first(user, channel))
            assertEquals(12345L, pending.capturedAt)
            it.remove("other", channel, pending.id)
            assertEquals(pending, it.first(user, channel))
            it.remove(user, channel, pending.id)
            assertNull(it.first(user, channel))
        }
    }
}
