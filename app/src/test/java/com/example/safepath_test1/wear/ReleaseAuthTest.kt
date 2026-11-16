package com.example.safepath_test1.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseAuthTest {
    @Test
    fun noSession_needsNoCheck_evenWhenBiometricIsReady() {
        assertEquals(ReleaseAuthMethod.NONE, chooseReleaseAuth(hasSession = false, biometricReady = true))
        assertEquals(ReleaseAuthMethod.NONE, chooseReleaseAuth(hasSession = false, biometricReady = false))
    }

    @Test
    fun session_prefersBiometric() {
        assertEquals(ReleaseAuthMethod.BIOMETRIC, chooseReleaseAuth(hasSession = true, biometricReady = true))
    }

    @Test
    fun session_withoutBiometric_fallsBackToPin() {
        assertEquals(ReleaseAuthMethod.PIN, chooseReleaseAuth(hasSession = true, biometricReady = false))
    }
}
