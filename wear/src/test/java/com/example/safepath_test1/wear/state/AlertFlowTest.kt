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
        assertEquals(
            AlertStage.Alerted,
            alerted.releasedBy(100_000L - RELEASE_CLOCK_SKEW_MS - 1).stage,
        )
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
    fun requestHelp_startsAsSending() {
        assertEquals(SosDelivery.Sending, AlertState().requestHelp(now = 100_000L).delivery)
    }

    @Test
    fun matchingAck_marksDelivered_butOtherAcksAreIgnored() {
        val alerted = AlertState().requestHelp(now = 100_000L)
        assertEquals(SosDelivery.Delivered, alerted.acknowledgedBy(triggeredAt = 100_000L).delivery)
        // 예전 SOS의 답장, 값이 빠진 답장은 지금 위험상황을 바꾸지 않는다.
        assertEquals(SosDelivery.Sending, alerted.acknowledgedBy(triggeredAt = 90_000L).delivery)
        assertEquals(SosDelivery.Sending, alerted.acknowledgedBy(triggeredAt = 0L).delivery)
    }

    @Test
    fun ack_isIgnoredOutsideTheAlertedStage() {
        val dismissed = AlertState().requestHelp(now = 100_000L).dismiss()
        assertEquals(AlertStage.None, dismissed.acknowledgedBy(100_000L).stage)
        assertEquals(SosDelivery.Sending, dismissed.acknowledgedBy(100_000L).delivery)
    }

    @Test
    fun notConnected_appliesOnlyToTheCurrentRequestStillSending() {
        val alerted = AlertState().requestHelp(now = 100_000L)
        assertEquals(
            SosDelivery.NotConnected,
            alerted.markNotConnected(requestedAt = 100_000L).delivery,
        )
        // 다른 요청의 연결 확인 결과는 무시한다.
        assertEquals(SosDelivery.Sending, alerted.markNotConnected(requestedAt = 90_000L).delivery)
        // 답장이 먼저 왔으면 전달됨을 덮지 않는다.
        val delivered = alerted.acknowledgedBy(100_000L)
        assertEquals(SosDelivery.Delivered, delivered.markNotConnected(100_000L).delivery)
    }

    @Test
    fun ackAfterNotConnected_marksDelivered() {
        val notConnected = AlertState().requestHelp(now = 100_000L).markNotConnected(100_000L)
        assertEquals(SosDelivery.Delivered, notConnected.acknowledgedBy(100_000L).delivery)
    }

    @Test
    fun ackOverdue_marksNotConnectedOnlyAfterTheTimeout() {
        val alerted = AlertState().requestHelp(now = 100_000L)
        val beforeTimeout = alerted.ackOverdue(now = 100_000L + SOS_ACK_TIMEOUT_MS - 1)
        val atTimeout = alerted.ackOverdue(now = 100_000L + SOS_ACK_TIMEOUT_MS)
        assertEquals(SosDelivery.Sending, beforeTimeout.delivery)
        assertEquals(SosDelivery.NotConnected, atTimeout.delivery)
    }

    @Test
    fun ackOverdue_keepsDeliveredAndIgnoresOtherStages() {
        val late = 100_000L + SOS_ACK_TIMEOUT_MS * 3
        val delivered = AlertState().requestHelp(now = 100_000L).acknowledgedBy(100_000L)
        assertEquals(SosDelivery.Delivered, delivered.ackOverdue(late).delivery)
        val dismissed = AlertState().requestHelp(now = 100_000L).dismiss()
        assertEquals(SosDelivery.Sending, dismissed.ackOverdue(late).delivery)
    }

    @Test
    fun newRequest_resetsDelivery() {
        val delivered = AlertState().requestHelp(now = 100_000L).acknowledgedBy(100_000L)
        val again = delivered.dismiss().requestHelp(now = 200_000L)
        assertEquals(SosDelivery.Sending, again.delivery)
        assertEquals(200_000L, again.helpRequestedAt)
    }

    @Test
    fun shouldReleaseAlert_rules() {
        assertFalse(shouldReleaseAlert(releaseSignalAt = 0L, alertRequestedAt = 0L))
        assertTrue(shouldReleaseAlert(releaseSignalAt = 1L, alertRequestedAt = 0L))
        assertFalse(shouldReleaseAlert(releaseSignalAt = 10_000L, alertRequestedAt = 20_000L))
    }
}
