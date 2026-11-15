package com.example.safepath_test1.wear.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePathTest {
    // 위도 0.001도는 약 111.19m. 위도만 달라지는 세 점이면 구간마다 약 111.19m.
    private val points = listOf(37.000 to 127.0, 37.001 to 127.0, 37.002 to 127.0)

    @Test
    fun totalMeters_isTheLastCumulativeValue() {
        val path = RoutePath(points)
        assertEquals(path.cumulative.last(), path.totalMeters, 0.0001)
        assertEquals(222.39, path.totalMeters, 1.0)
    }

    @Test
    fun progressAt_matchesTheOneShotFunction() {
        val path = RoutePath(points)
        val positions = listOf(null, 37.000 to 127.0, 37.0005 to 127.0, 37.001 to 127.00005, 37.002 to 127.0)
        for (position in positions) {
            val cached = path.progressAt(position)
            val oneShot = computeRouteProgress(points, position)
            assertEquals(oneShot, cached)
        }
    }

    @Test
    fun repeatedCalls_giveTheSameAnswer() {
        val path = RoutePath(points)
        val first = path.progressAt(37.0005 to 127.0)
        val second = path.progressAt(37.0005 to 127.0)
        assertEquals(first, second)
    }

    @Test
    fun tooFewPoints_isEmptyProgress() {
        assertEquals(0.0, RoutePath(emptyList()).totalMeters, 0.0)
        assertEquals(0.0, RoutePath(listOf(37.0 to 127.0)).totalMeters, 0.0)
        assertEquals(RouteProgress(0f, 0.0, 0, 0.0), RoutePath(listOf(37.0 to 127.0)).progressAt(37.0 to 127.0))
    }

    @Test
    fun currentBeyondTheEnd_isClampedToTheEnd() {
        val result = RoutePath(points).progressAt(37.01 to 127.0)
        assertEquals(1f, result.progress, 0.0001f)
        assertTrue(result.remainingMeters < 0.0001)
    }
}
