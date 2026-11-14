package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class RouteContinuityTest {
    // Coordinates expressed in meters in a small local plane.
    private fun p(x: Double,y: Double=0.0) = GeoPoint(37+y/111320,127+x/(111320*kotlin.math.cos(Math.toRadians(37.0))))

    @Test fun rejectsOutAndBackEvenWithDifferentVertexSpacing() {
        assertTrue(RouteContinuity.hasRetracing(listOf(p(0.0),p(120.0),p(95.0),p(20.0),p(20.0,80.0))))
    }
    @Test fun rejectsClosedDetourReturningToJunction() {
        assertTrue(RouteContinuity.hasRetracing(listOf(p(0.0),p(50.0),p(50.0,60.0),p(110.0,60.0),p(50.0),p(180.0))))
    }
    @Test fun allowsContinuousSafeSideDetour() {
        assertFalse(RouteContinuity.hasRetracing(listOf(p(0.0),p(40.0,60.0),p(140.0,60.0),p(200.0))))
    }
    @Test fun nearbyParallelStreetsAndSingleCrossingAreNotRetracedEdges() {
        assertFalse(RouteContinuity.hasRetracing(listOf(p(0.0),p(120.0),p(120.0,12.0),p(0.0,12.0))))
        assertFalse(RouteContinuity.hasRetracing(listOf(p(0.0),p(100.0,100.0),p(100.0),p(0.0,100.0))))
    }
    @Test fun ignoresDuplicateVerticesAndTinyGpsJitter() {
        assertFalse(RouteContinuity.hasRetracing(listOf(p(0.0),p(0.0),p(10.0),p(9.0),p(150.0))))
    }
    @Test fun removesForcingJitterAndDoesNotOvershootDestination() {
        val points=listOf(p(0.0),p(1.0,1.0),p(50.0,1.0),p(40.0,1.0),p(205.0),p(200.0))
        assertEquals(listOf(points.first(),points.last()), RouteContinuity.simplifyWaypoints(points))
    }
    @Test fun preservesSubstantialLateralDetourAndEndpoints() {
        val points=listOf(p(0.0),p(20.0,1.0),p(70.0,100.0),p(200.0,100.0),p(300.0))
        val simplified=RouteContinuity.simplifyWaypoints(points)
        assertEquals(points.first(),simplified.first()); assertEquals(points.last(),simplified.last())
        assertTrue(simplified.size>2); assertTrue(simplified.size<=10)
    }
}
