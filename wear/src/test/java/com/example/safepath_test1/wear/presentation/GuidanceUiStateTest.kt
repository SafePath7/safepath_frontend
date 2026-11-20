package com.example.safepath_test1.wear.presentation

import com.example.safepath_test1.wear.geo.RoutePath
import com.example.safepath_test1.wear.geo.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceUiStateTest {
    // 북쪽으로 약 222m 직진.
    private val straight = RoutePath(listOf(37.000 to 127.0, 37.001 to 127.0, 37.002 to 127.0))

    // 북쪽으로 약 111m 가다가 동쪽으로 약 107m: 모양만 보면 모퉁이에서 우회전.
    private val rightTurn =
        RoutePath(listOf(37.000 to 127.000, 37.001 to 127.000, 37.001 to 127.0012))

    @Test
    fun noRoute_showsTotalDistanceAndWaitsForPosition() {
        val state = buildGuidanceUiState(null, null, emptyList(), "1.2")
        assertEquals("총 1.2km", state.summaryText)
        assertEquals("위치 확인 중", state.bannerText)
        assertEquals(TurnDirection.Straight, state.bannerDirection)
        assertEquals(0f, state.ringProgress, 0.0001f)
        assertFalse(state.arrived)
    }

    @Test
    fun routeWithoutCurrentPosition_stillWaitsForPosition() {
        val state = buildGuidanceUiState(straight, null, emptyList(), "0.2")
        assertEquals("총 0.2km", state.summaryText)
        assertEquals("위치 확인 중", state.bannerText)
        assertEquals(0f, state.ringProgress, 0.0001f)
    }

    @Test
    fun walkingAlongAStraightRoute_showsRemainingDistanceAndStraight() {
        val state = buildGuidanceUiState(straight, 37.0005 to 127.0, emptyList(), "0.2")
        assertEquals("남은 170m", state.summaryText)
        assertEquals("170m 직진", state.bannerText)
        assertEquals(TurnDirection.Straight, state.bannerDirection)
        assertEquals(0.25f, state.ringProgress, 0.01f)
        assertFalse(state.arrived)
    }

    @Test
    fun withoutSteps_theTurnIsGuessedFromTheRouteShape() {
        val state = buildGuidanceUiState(rightTurn, 37.000 to 127.000, emptyList(), "0.2")
        assertEquals(TurnDirection.Right, state.bannerDirection)
        assertEquals("110m 후 우회전", state.bannerText)
    }

    @Test
    fun withSteps_thePhoneSteps_winOverTheRouteShape() {
        // 모양은 우회전이지만 폰이 보낸 회전 안내가 좌회전이라면 그쪽을 따른다.
        val steps = listOf(RouteStep(37.001 to 127.000, "turn", "left"))
        val state = buildGuidanceUiState(rightTurn, 37.000 to 127.000, steps, "0.2")
        assertEquals(TurnDirection.Left, state.bannerDirection)
        assertEquals("110m 후 좌회전", state.bannerText)
    }

    @Test
    fun nearTheEnd_isArrived() {
        val state = buildGuidanceUiState(straight, 37.002 to 127.0, emptyList(), "0.2")
        assertTrue(state.arrived)
        assertEquals("목적지 도착", state.bannerText)
        assertEquals(TurnDirection.Straight, state.bannerDirection)
        assertEquals("남은 0m", state.summaryText)
        assertEquals(1f, state.ringProgress, 0.0001f)
    }

    @Test
    fun justOutsideTheArrivalDistance_isNotArrived() {
        // 도착 지점에서 약 22m(위도 0.0002도) 못 미친 곳.
        val state = buildGuidanceUiState(straight, 37.0018 to 127.0, emptyList(), "0.2")
        assertFalse(state.arrived)
    }
}
