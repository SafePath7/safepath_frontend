package com.example.safepath_test1.ui.home

import com.example.safepath_test1.api.RouteCoordinate
import com.example.safepath_test1.api.SelectedRouteBody
import com.example.safepath_test1.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JourneyProgressTest {
    private val route = SelectedRouteBody(
        "RECOMMENDED",
        listOf(RouteCoordinate(37.0, 127.0), RouteCoordinate(37.0, 127.01)),
        1000.0, 600.0, null, "도착지",
    )

    @Test fun estimatesRemainingDistanceAtMidpoint() {
        val progress = calculateJourneyProgress(route, GeoPoint(37.0, 127.005))!!
        assertEquals(500.0, progress.remainingMeters, 1.0)
        assertEquals(300.0, progress.remainingSeconds, 1.0)
    }

    @Test fun clampsProgressBeyondDestination() {
        val progress = calculateJourneyProgress(route, GeoPoint(37.0, 127.02))!!
        assertEquals(0.0, progress.remainingMeters, 0.01)
    }

    @Test fun rejectsRouteWithoutGeometry() {
        assertNull(calculateJourneyProgress(route.copy(coordinates = emptyList()), GeoPoint(37.0, 127.0)))
    }
}
