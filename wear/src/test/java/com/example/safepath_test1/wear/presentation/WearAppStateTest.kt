package com.example.safepath_test1.wear.presentation

import com.example.safepath_test1.wear.comm.StatusUpdate
import com.example.safepath_test1.wear.state.AlertStage
import com.example.safepath_test1.wear.state.CHECK_IN_COUNTDOWN_MS
import com.example.safepath_test1.wear.state.RouteMapState
import com.example.safepath_test1.wear.state.RouteStatus
import com.example.safepath_test1.wear.state.SOS_ACK_TIMEOUT_MS
import com.example.safepath_test1.wear.state.STALE_AFTER_MS
import com.example.safepath_test1.wear.state.SosDelivery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearAppStateTest {
    private val found = RouteStatus.Found("경로", "1.2", "도서관")
    private val map = RouteMapState(listOf(37.0 to 127.0, 37.001 to 127.0), null, null)

    @Test
    fun startsIdleWithNoAlert() {
        val state = WearAppState()
        assertEquals(RouteStatus.Idle, state.status)
        assertNull(state.routeMap)
        assertEquals(AlertStage.None, state.alert.stage)
        assertFalse(state.isNavigating)
    }

    @Test
    fun freshFoundStatus_startsNavigating() {
        val state = WearAppState()
        state.onStatus(StatusUpdate(updatedAt = 1_000L, status = found, routeMap = map), now = 2_000L)
        assertEquals(found, state.status)
        assertNotNull(state.routeMap)
        assertTrue(state.isNavigating)
    }

    @Test
    fun oldStatus_isShownAsStale() {
        val state = WearAppState()
        state.onStatus(StatusUpdate(1_000L, found, map), now = 2_000L + STALE_AFTER_MS)
        assertEquals(RouteStatus.Stale, state.status)
        assertFalse(state.isNavigating)
    }

    @Test
    fun markStale_onlyAfterSomethingWasReceivedAndItGotOld() {
        val state = WearAppState()
        state.markStaleIfNeeded(now = 10_000_000L)
        assertEquals(RouteStatus.Idle, state.status) // 아직 아무것도 못 받았다

        // 오래됐는지는 폰이 보낸 시각(updatedAt = 1_000)부터 센다. 딱 STALE_AFTER_MS가 지난 시점까지는 괜찮다.
        state.onStatus(StatusUpdate(1_000L, found, map), now = 2_000L)
        state.markStaleIfNeeded(now = 1_000L + STALE_AFTER_MS)
        assertEquals(found, state.status)
        state.markStaleIfNeeded(now = 1_000L + STALE_AFTER_MS + 1L)
        assertEquals(RouteStatus.Stale, state.status)
    }

    @Test
    fun checkIn_thenOkay_returnsToNormal() {
        val state = WearAppState()
        state.startCheckIn(now = 1_000L)
        assertEquals(AlertStage.CheckIn, state.alert.stage)
        assertEquals(CHECK_IN_COUNTDOWN_MS, state.checkInRemainingMs)
        state.showCheckInRemaining(12_000L)
        assertEquals(12_000L, state.checkInRemainingMs)
        state.dismissAlert()
        assertEquals(AlertStage.None, state.alert.stage)
    }

    @Test
    fun startingCheckInAgain_resetsTheDisplayedRemainingTime() {
        val state = WearAppState()
        state.startCheckIn(1_000L)
        state.showCheckInRemaining(3_000L)
        state.startCheckIn(5_000L)
        assertEquals(CHECK_IN_COUNTDOWN_MS, state.checkInRemainingMs)
    }

    @Test
    fun helpThenReleaseSignal_closesTheAlert() {
        val state = WearAppState()
        state.requestHelp(now = 100_000L)
        assertEquals(AlertStage.Alerted, state.alert.stage)

        state.releaseIfSignalled() // 신호 없음: 그대로
        assertEquals(AlertStage.Alerted, state.alert.stage)

        state.onRelease(100_500L)
        state.releaseIfSignalled()
        assertEquals(AlertStage.None, state.alert.stage)
    }

    @Test
    fun sosAckAndNotConnected_updateTheCurrentAlert() {
        val state = WearAppState()
        state.requestHelp(now = 100_000L)
        assertEquals(SosDelivery.Sending, state.alert.delivery)
        state.markSosNotConnected(requestedAt = 100_000L)
        assertEquals(SosDelivery.NotConnected, state.alert.delivery)
        state.onSosAck(triggeredAt = 100_000L)
        assertEquals(SosDelivery.Delivered, state.alert.delivery)
    }

    @Test
    fun noAckWithinTheTimeout_showsNotDeliveredYet() {
        val state = WearAppState()
        state.requestHelp(now = 100_000L)
        state.checkSosAckOverdue(now = 100_000L + SOS_ACK_TIMEOUT_MS)
        assertEquals(SosDelivery.NotConnected, state.alert.delivery)
    }
}
