package com.example.safepath_test1.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WatchAlertStateTest {
    @Before
    fun reset() {
        WatchAlertState.clear()
    }

    @Test
    fun newState_startsOff() {
        assertNull(WatchAlertState.requestedAt.value)
    }

    @Test
    fun sosReceived_turnsOnWithItsTime() {
        WatchAlertState.onSosReceived(1_000L)
        assertEquals(1_000L, WatchAlertState.requestedAt.value)
    }

    @Test
    fun secondSos_keepsOneAlertWithTheLatestTime() {
        WatchAlertState.onSosReceived(1_000L)
        WatchAlertState.onSosReceived(2_000L)
        assertEquals(2_000L, WatchAlertState.requestedAt.value)
    }

    @Test
    fun clear_turnsOff_andANewSosTurnsItOnAgain() {
        WatchAlertState.onSosReceived(1_000L)
        WatchAlertState.clear()
        assertNull(WatchAlertState.requestedAt.value)
        WatchAlertState.onSosReceived(5_000L)
        assertEquals(5_000L, WatchAlertState.requestedAt.value)
    }

    @Test
    fun restore_bringsBackTheAlertWhenOff() {
        WatchAlertState.restore(3_000L)
        assertEquals(3_000L, WatchAlertState.requestedAt.value)
    }

    @Test
    fun restore_doesNotOverwriteAnAlertThatIsAlreadyOn() {
        WatchAlertState.onSosReceived(4_000L)
        WatchAlertState.restore(3_000L)
        assertEquals(4_000L, WatchAlertState.requestedAt.value)
    }

    @Test
    fun restore_ignoresAnInvalidTime() {
        WatchAlertState.restore(0L)
        assertNull(WatchAlertState.requestedAt.value)
    }
}
