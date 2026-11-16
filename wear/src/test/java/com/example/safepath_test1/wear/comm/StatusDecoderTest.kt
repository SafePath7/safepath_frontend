package com.example.safepath_test1.wear.comm

import com.example.safepath_test1.wear.state.RouteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StatusDecoderTest {
    private fun payload(
        state: String? = "FOUND",
        routePoints: String? = "37.0,127.0;37.001,127.0",
        routeSteps: String? = null,
        distanceMeters: Double = 1234.0,
        routeType: String? = "안전 경로",
        current: Pair<Double, Double>? = null,
    ) =
        StatusPayload(
            updatedAt = 1_000L,
            state = state,
            routeType = routeType,
            distanceMeters = distanceMeters,
            destinationName = "도서관",
            routePoints = routePoints,
            routeSteps = routeSteps,
            destination = 37.001 to 127.0,
            current = current,
        )

    @Test
    fun found_carriesTheRouteTypeAndDistanceInKilometers() {
        val update = decodeStatus(payload())
        assertEquals(1_000L, update.updatedAt)
        assertEquals(RouteStatus.Found("안전 경로", "1.2", "도서관"), update.status)
    }

    @Test
    fun foundWithoutRouteType_usesTheDefaultLabel() {
        val update = decodeStatus(payload(routeType = null))
        assertEquals(RouteStatus.Found("경로", "1.2", "도서관"), update.status)
    }

    @Test
    fun searching_andUnknownStates() {
        assertEquals(RouteStatus.Searching, decodeStatus(payload(state = "SEARCHING")).status)
        assertEquals(RouteStatus.Idle, decodeStatus(payload(state = "IDLE")).status)
        assertEquals(RouteStatus.Idle, decodeStatus(payload(state = null)).status)
    }

    @Test
    fun routeMap_needsAtLeastTwoPoints() {
        assertNull(decodeStatus(payload(routePoints = null)).routeMap)
        assertNull(decodeStatus(payload(routePoints = "37.0,127.0")).routeMap)
        assertNotNull(decodeStatus(payload()).routeMap)
    }

    @Test
    fun routeMap_keepsCurrentPositionDestinationAndSteps() {
        val update =
            decodeStatus(
                payload(
                    current = 37.0005 to 127.0,
                    routeSteps = "37.0,127.0,depart,;37.001,127.0,arrive,",
                )
            )
        val map = update.routeMap!!
        assertEquals(2, map.points.size)
        assertEquals(37.0005 to 127.0, map.current)
        assertEquals(37.001 to 127.0, map.destination)
        assertEquals(2, map.steps.size)
        assertEquals("arrive", map.steps[1].type)
        assertEquals(222.39 / 2, map.path.totalMeters, 1.0)
    }

    @Test
    fun decodeLatLngList_dropsMalformedItems() {
        assertEquals(emptyList<Pair<Double, Double>>(), decodeLatLngList(null))
        assertEquals(emptyList<Pair<Double, Double>>(), decodeLatLngList("  "))
        assertEquals(
            listOf(37.0 to 127.0, 37.5 to 127.5),
            decodeLatLngList("37.0,127.0;abc,1;37.5,127.5;1,2,3;5"),
        )
    }
}
