package com.example.safepath_test1.wear.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStateTest {
    @Test
    fun staleRules() {
        assertTrue(isStatusStale(updatedAt = 0L, now = 1_000L))
        assertFalse(isStatusStale(updatedAt = 1_000L, now = 1_000L + STALE_AFTER_MS))
        assertTrue(isStatusStale(updatedAt = 1_000L, now = 1_001L + STALE_AFTER_MS))
    }

    @Test
    fun statusOrStale_keepsAFreshStatus() {
        val found = RouteStatus.Found("경로", "1.2", null)
        assertEquals(found, statusOrStale(found, updatedAt = 1_000L, now = 2_000L))
    }

    @Test
    fun statusOrStale_turnsAnOldOrMissingTimestampIntoStale() {
        val found = RouteStatus.Found("경로", "1.2", null)
        assertEquals(
            RouteStatus.Stale,
            statusOrStale(found, updatedAt = 1_000L, now = 2_000L + STALE_AFTER_MS),
        )
        assertEquals(
            RouteStatus.Stale,
            statusOrStale(RouteStatus.Idle, updatedAt = 0L, now = 2_000L),
        )
    }
}
