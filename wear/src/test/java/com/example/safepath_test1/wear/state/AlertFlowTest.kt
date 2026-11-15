package com.example.safepath_test1.wear.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertFlowTest {
    @Test
    fun startsInTheNormalStage() {
        assertEquals(AlertStage.None, AlertState().stage)
    }

    @Test
    fun startCheckIn_entersCheckInAndRemembersWhen() {
        val state = AlertState().startCheckIn(now = 1_000L)
        assertEquals(AlertStage.CheckIn, state.stage)
        assertEquals(1_000L, state.checkInStartedAt)
    }

    @Test
    fun checkInRemainingMs_countsDownAndExpires() {
        val state = AlertState().startCheckIn(now = 1_000L)
        assertEquals(CHECK_IN_COUNTDOWN_MS, state.checkInRemainingMs(now = 1_000L))
        assertEquals(CHECK_IN_COUNTDOWN_MS - 10_000L, state.checkInRemainingMs(now = 11_000L))
        assertTrue(state.checkInRemainingMs(now = 1_000L + CHECK_IN_COUNTDOWN_MS) <= 0L)
    }

    @Test
    fun okay_returnsToNormal() {
        val state = AlertState().startCheckIn(1_000L).dismiss()
        assertEquals(AlertStage.None, state.stage)
    }

    @Test
    fun requestHelp_entersAlertedAndRemembersWhen() {
        val fromCheckIn = AlertState().startCheckIn(1_000L).requestHelp(now = 5_000L)
        assertEquals(AlertStage.Alerted, fromCheckIn.stage)
        assertEquals(5_000L, fromCheckIn.helpRequestedAt)

        // SOS는 어느 상태에서든 바로 위험상황으로 간다(명세).
        val fromNormal = AlertState().requestHelp(now = 7_000L)
        assertEquals(AlertStage.Alerted, fromNormal.stage)
        assertEquals(7_000L, fromNormal.helpRequestedAt)
    }

    @Test
    fun releaseSignalAfterTheRequest_closesTheAlert() {
        val alerted = AlertState().requestHelp(now = 100_000L)
        assertEquals(AlertStage.None, alerted.releasedBy(releaseSignalAt = 100_500L).stage)
    }

    @Test
    fun releaseSignalWithinTheClockSkew_stillCloses_butOlderOnesDoNot() {
        val alerted = AlertState().requestHelp(now = 100_000L)
        assertEquals(AlertStage.None, alerted.releasedBy(100_000L - RELEASE_CLOCK_SKEW_MS).stage)
        assertEquals(AlertStage.Alerted, alerted.releasedBy(100_000L - RELEASE_CLOCK_SKEW_MS - 1).stage)
    }

    @Test
    fun missingReleaseSignal_isIgnored() {
        val alerted = AlertState().requestHelp(now = 100_000L)
        assertEquals(AlertStage.Alerted, alerted.releasedBy(releaseSignalAt = 0L).stage)
    }

    @Test
    fun releaseSignal_isIgnoredOutsideTheAlertedStage() {
        val checkIn = AlertState().startCheckIn(now = 100_000L)
        assertEquals(AlertStage.CheckIn, checkIn.releasedBy(200_000L).stage)
        val normal = AlertState()
        assertEquals(AlertStage.None, normal.releasedBy(200_000L).stage)
    }

    @Test
    fun shouldReleaseAlert_rules() {
        assertFalse(shouldReleaseAlert(releaseSignalAt = 0L, alertRequestedAt = 0L))
        assertTrue(shouldReleaseAlert(releaseSignalAt = 1L, alertRequestedAt = 0L))
        assertFalse(shouldReleaseAlert(releaseSignalAt = 10_000L, alertRequestedAt = 20_000L))
    }
}
