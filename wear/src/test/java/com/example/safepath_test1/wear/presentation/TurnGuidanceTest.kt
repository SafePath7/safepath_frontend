package com.example.safepath_test1.wear.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnGuidanceTest {
    // 북쪽으로 약 111m 가다가 동쪽으로 약 107m: 모퉁이(점 1)에서 우회전.
    private val rightTurn = listOf(37.000 to 127.000, 37.001 to 127.000, 37.001 to 127.0012)

    // 북쪽으로 가다가 서쪽으로: 좌회전.
    private val leftTurn = listOf(37.000 to 127.000, 37.001 to 127.000, 37.001 to 126.9988)

    private val straight = listOf(37.000 to 127.0, 37.001 to 127.0, 37.002 to 127.0)

    @Test
    fun bearing_northIsZeroAndEastIsNinety() {
        assertEquals(0.0, bearingDegrees(37.0 to 127.0, 37.001 to 127.0), 0.5)
        assertEquals(90.0, bearingDegrees(37.0 to 127.0, 37.0 to 127.001), 0.5)
    }

    @Test
    fun turnAngle_isPositiveForRightAndNegativeForLeft() {
        assertTrue(turnAngleDegrees(37.000 to 127.0, 37.001 to 127.0, 37.001 to 127.001) > 80.0)
        assertTrue(turnAngleDegrees(37.000 to 127.0, 37.001 to 127.0, 37.001 to 126.999) < -80.0)
    }

    @Test
    fun rightTurnAhead_reportsDistanceToTheCorner() {
        val turn = computeNextTurn(rightTurn, alongMeters = 0.0)!!
        assertEquals(TurnDirection.Right, turn.direction)
        assertEquals(111.19, turn.distanceMeters, 1.0)
    }

    @Test
    fun distanceShrinksAsWeWalk() {
        val turn = computeNextTurn(rightTurn, alongMeters = 50.0)!!
        assertEquals(TurnDirection.Right, turn.direction)
        assertEquals(61.19, turn.distanceMeters, 1.0)
    }

    @Test
    fun leftTurnAhead_isLeft() {
        assertEquals(TurnDirection.Left, computeNextTurn(leftTurn, alongMeters = 0.0)!!.direction)
    }

    @Test
    fun afterPassingTheCorner_goesStraightToTheEnd() {
        val turn = computeNextTurn(rightTurn, alongMeters = 120.0)!!
        assertEquals(TurnDirection.Straight, turn.direction)
        assertEquals(98.0, turn.distanceMeters, 3.0)
    }

    @Test
    fun noCorner_isStraightForTheWholeRoute() {
        val turn = computeNextTurn(straight, alongMeters = 0.0)!!
        assertEquals(TurnDirection.Straight, turn.direction)
        assertEquals(222.39, turn.distanceMeters, 1.0)
    }

    @Test
    fun gentleBend_isNotATurn() {
        // 약 10도 꺾임.
        val bend = listOf(37.000 to 127.0, 37.001 to 127.0, 37.002 to 127.0002)
        assertEquals(TurnDirection.Straight, computeNextTurn(bend, alongMeters = 0.0)!!.direction)
    }

    @Test
    fun tooFewPoints_hasNoTurn() {
        assertEquals(null, computeNextTurn(listOf(37.0 to 127.0), alongMeters = 0.0))
    }

    @Test
    fun bannerText_readsNaturally() {
        assertEquals("120m 후 우회전", turnBannerText(NextTurn(TurnDirection.Right, 118.0)))
        assertEquals("지금 좌회전", turnBannerText(NextTurn(TurnDirection.Left, 8.0)))
        assertEquals("650m 직진", turnBannerText(NextTurn(TurnDirection.Straight, 650.0)))
        assertEquals("80m 후 오른쪽 방향", turnBannerText(NextTurn(TurnDirection.SlightRight, 80.0)))
        assertEquals("지금 유턴", turnBannerText(NextTurn(TurnDirection.UTurn, 5.0)))
    }

    // ---- 폰이 보낸 회전 안내 목록(routeSteps) ----

    private val steps =
        listOf(
            RouteStep(37.000 to 127.000, "depart", ""),
            RouteStep(37.001 to 127.000, "turn", "right"),
            RouteStep(37.001 to 127.0012, "arrive", ""),
        )

    @Test
    fun decodeRouteSteps_readsThePhoneFormat() {
        val decoded = decodeRouteSteps("37.000000,127.000000,depart,;37.001000,127.000000,turn,slight left")
        assertEquals(2, decoded.size)
        assertEquals("depart", decoded[0].type)
        assertEquals("", decoded[0].modifier)
        assertEquals(TurnDirection.SlightLeft, stepDirection(decoded[1]))
        assertTrue(decodeRouteSteps(null).isEmpty())
        assertTrue(decodeRouteSteps("고장난값").isEmpty())
    }

    @Test
    fun steps_reportTheNextTurnAndItsDistance() {
        val turn = computeNextTurnFromSteps(rightTurn, steps, alongMeters = 50.0)!!
        assertEquals(TurnDirection.Right, turn.direction)
        assertEquals(61.19, turn.distanceMeters, 1.0)
    }

    @Test
    fun steps_afterTheLastTurn_goStraightToTheEnd() {
        val turn = computeNextTurnFromSteps(rightTurn, steps, alongMeters = 120.0)!!
        assertEquals(TurnDirection.Straight, turn.direction)
        assertEquals(98.0, turn.distanceMeters, 3.0)
    }

    @Test
    fun steps_thatAreNotTurns_areSkipped() {
        val withContinue =
            listOf(
                RouteStep(37.000 to 127.000, "depart", ""),
                RouteStep(37.0005 to 127.000, "new name", "straight"),
                RouteStep(37.001 to 127.000, "turn", "left"),
            )
        val turn = computeNextTurnFromSteps(leftTurn, withContinue, alongMeters = 0.0)!!
        assertEquals(TurnDirection.Left, turn.direction)
        assertEquals(111.19, turn.distanceMeters, 1.0)
    }
}
