package com.example.safepath_test1.wear.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressTest {
    // 위도 0.001도는 약 111.19m. 위도만 달라지는 세 점이면 구간마다 약 111.19m, 전체 약 222.39m.
    private val route = listOf(37.000 to 127.0, 37.001 to 127.0, 37.002 to 127.0)

    @Test
    fun currentAtMiddle_isHalfwayWithHalfRemaining() {
        val result = computeRouteProgress(route, current = 37.001 to 127.0)
        assertEquals(0.5f, result.progress, 0.01f)
        assertEquals(111.19, result.remainingMeters, 1.0)
        assertEquals(111.19, result.traveledMeters, 1.0)
    }

    @Test
    fun currentBetweenPoints_isMeasuredAlongTheSegment() {
        // 첫 구간 한가운데: 점 사이에 있어도 가까운 점으로 반올림하지 않는다.
        val result = computeRouteProgress(route, current = 37.0005 to 127.0)
        assertEquals(0, result.traveledIndex)
        assertEquals(0.25f, result.progress, 0.01f)
        assertEquals(55.6, result.traveledMeters, 1.0)
        assertEquals(166.8, result.remainingMeters, 1.0)
    }

    @Test
    fun currentOffToTheSide_projectsOntoTheRoute() {
        // 길에서 약 26m 옆으로 벗어나 있어도 같은 지점으로 본다.
        val result = computeRouteProgress(route, current = 37.0005 to 127.0003)
        assertEquals(0, result.traveledIndex)
        assertEquals(0.25f, result.progress, 0.01f)
    }

    @Test
    fun currentInSecondSegment_reportsThatSegment() {
        val result = computeRouteProgress(route, current = 37.0015 to 127.0)
        assertEquals(1, result.traveledIndex)
        assertEquals(0.75f, result.progress, 0.01f)
    }

    @Test
    fun noCurrentPosition_remainingIsWholeRoute() {
        val result = computeRouteProgress(route, current = null)
        assertEquals(0f, result.progress, 0.0001f)
        assertEquals(222.39, result.remainingMeters, 1.0)
        assertEquals(0.0, result.traveledMeters, 0.0001)
    }

    @Test
    fun currentAtEnd_isFinished() {
        val result = computeRouteProgress(route, current = 37.002 to 127.0)
        assertEquals(1f, result.progress, 0.0001f)
        assertEquals(0.0, result.remainingMeters, 0.5)
    }

    @Test
    fun cumulativeMeters_startsAtZeroAndGrows() {
        val cumulative = cumulativeMeters(route)
        assertEquals(0.0, cumulative[0], 0.0001)
        assertEquals(111.19, cumulative[1], 1.0)
        assertTrue(cumulative[2] > cumulative[1])
    }

    @Test
    fun formatDistance_usesMetersUnderOneKilometer() {
        assertEquals("430m", formatDistance(432.0))
        assertEquals("1.2km", formatDistance(1234.0))
    }
}
