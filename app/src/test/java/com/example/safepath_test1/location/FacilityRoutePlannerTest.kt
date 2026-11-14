package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class FacilityRoutePlannerTest {
    private val origin = GeoPoint(37.0, 127.0)
    private val destination = GeoPoint(37.0, 127.01)

    @Test fun circleUsesStraightMidpointAndTwiceEndpointDistance() {
        val area = FacilityRoutePlanner.area(origin, destination)
        assertEquals(37.0, area.center.latitude, 0.000001)
        assertEquals(127.005, area.center.longitude, 0.000001)
        assertEquals(2 * FacilityRoutePlanner.distance(origin, destination), area.radiusMeters, 0.001)
        assertTrue(area.contains(origin)); assertTrue(area.contains(destination))
        assertFalse(area.contains(GeoPoint(38.0, 127.005)))
    }

    @Test fun everyInsideFacilityContributesWithoutMarkerLimit() {
        val cameras = List(1500) { SafetyFacility(37.005, 127.005) }
        val result = FacilityRoutePlanner.plan(origin, destination, cameras + SafetyFacility(38.0, 127.0), emptyList())
        assertEquals(1500, result.cctvs.size)
        assertEquals(0, result.lights.size)
    }

    @Test fun offAxisFacilityFieldCreatesDetourCorridorWithBoundedWaypoints() {
        val cameras = (0..20).flatMap { x -> List(30) { SafetyFacility(37.004, 127.0 + x * 0.0005) } }
        val plan = FacilityRoutePlanner.plan(origin, destination, cameras, emptyList())
        assertTrue(plan.corridors.isNotEmpty())
        assertTrue(plan.corridors.any { corridor -> corridor.any { it.latitude > 37.002 } })
        plan.corridors.forEach { corridor ->
            assertEquals(origin, corridor.first()); assertEquals(destination, corridor.last())
            assertTrue(corridor.size <= 25)
            assertTrue(corridor.all { plan.area.contains(it) })
        }
    }

    @Test fun emptyOrCoincidentAreaDoesNotInventDetours() {
        assertTrue(FacilityRoutePlanner.plan(origin, destination, emptyList(), emptyList()).corridors.isEmpty())
        assertTrue(FacilityRoutePlanner.plan(origin, origin, listOf(SafetyFacility(37.0, 127.0)), emptyList()).corridors.isEmpty())
    }
}
