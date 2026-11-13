package com.example.safepath_test1.auth

import org.junit.Assert.*
import org.junit.Test

class PhoneAuthManagerTest {
    private class Fixture : PhoneAuthGateway<String, String, Unit> {
        var uid: String? = "google-user"
        var time = 1_000L
        var calls = 0
        var token: String? = null
        var phone: String? = null
        lateinit var callbacks: PhoneVerificationCallbacks<String, String>
        val manager = PhoneAuthManager(this) { time }
        override fun currentGoogleUid() = uid
        override fun verify(host: Unit, phone: String, token: String?, callbacks: PhoneVerificationCallbacks<String, String>) {
            calls++
            this.phone = phone
            this.token = token
            this.callbacks = callbacks
        }
        override fun createCredential(verificationId: String, code: String) = "fake-credential"
        fun send() = manager.sendVerificationCode(Unit, "010-1234-5678")
        fun sent() { send(); callbacks.codeSent("fake-id", "fake-token") }
    }

    @Test fun normalizationUsesExistingKoreanMobileContract() {
        listOf("01012345678", "010-1234-5678", "+82 (10) 1234-5678", "0082 10 1234 5678")
            .forEach { assertEquals("+821012345678", normalizeKoreanPhoneNumber(it)) }
        listOf("", "02-123-4567", "0101234567", "010１２３４５６７８", "010+12345678",
            "++821012345678", "+8201012345678", "abc01012345678", "010/1234/5678", "+811012345678")
            .forEach { assertNull(normalizeKoreanPhoneNumber(it)) }
    }

    @Test fun sendNormalizesNumberAndBlocksDuplicateRequests() {
        val f = Fixture()
        f.send(); f.send()
        assertEquals(1, f.calls)
        assertEquals("+821012345678", f.phone)
        assertEquals(PhoneAuthState.Sending, f.manager.state.value)
        assertFalse(f.manager.canResend)
    }

    @Test fun codeSentExposesInputButNotVerificationSuccess() {
        val f = Fixture(); f.sent()
        assertEquals(PhoneAuthState.CodeSent(), f.manager.state.value)
        assertNull(f.manager.consumePendingCredential())
    }

    @Test fun manualCodeProducesOneTimeCredentialForOriginalUidAndPhone() {
        val f = Fixture(); f.sent()
        f.manager.createCredential("123456")
        assertEquals(PhoneAuthState.CredentialReady(PhoneCredentialSource.MANUAL_CODE), f.manager.state.value)
        val pending = f.manager.consumePendingCredential()!!
        assertEquals("google-user", pending.userUid)
        assertEquals("+821012345678", pending.phone)
        assertEquals("fake-credential", pending.credential)
        assertFalse(pending.toString().contains("fake-credential"))
        assertNull(f.manager.consumePendingCredential())
    }

    @Test fun automaticVerificationAndLateCallbacksCannotDuplicateCredential() {
        val f = Fixture(); f.sent()
        f.callbacks.completed("automatic")
        f.callbacks.codeSent("late-id", "late-token")
        f.callbacks.completed("duplicate")
        assertEquals(PhoneAuthState.CredentialReady(PhoneCredentialSource.AUTOMATIC_VERIFICATION), f.manager.state.value)
        assertEquals("automatic", f.manager.consumePendingCredential()?.credential)
        f.callbacks.timedOut("late-id")
        assertNull(f.manager.consumePendingCredential())
    }

    @Test fun resendEnforcesCooldownAndPassesForceResendingToken() {
        val f = Fixture(); f.sent()
        f.manager.resendVerificationCode(Unit)
        assertEquals(1, f.calls)
        assertEquals(60, f.manager.resendSeconds)
        f.time += 59_001
        assertEquals(1, f.manager.resendSeconds)
        f.time += 999
        assertTrue(f.manager.canResend)
        val oldCallbacks = f.callbacks
        f.manager.resendVerificationCode(Unit)
        assertEquals(2, f.calls)
        assertEquals("fake-token", f.token)
        oldCallbacks.completed("stale")
        assertEquals(PhoneAuthState.Sending, f.manager.state.value)
    }

    @Test fun editingPhoneCannotBypassCooldownOrReuseOldIdAndToken() {
        val f = Fixture(); f.sent()
        val oldCallbacks = f.callbacks
        f.manager.reset()
        f.manager.sendVerificationCode(Unit, "01087654321")
        assertEquals(1, f.calls)
        oldCallbacks.completed("stale")
        assertEquals(PhoneAuthState.Idle, f.manager.state.value)
        f.time += 60_000
        f.manager.sendVerificationCode(Unit, "01087654321")
        assertEquals(2, f.calls)
        assertNull(f.token)
        assertEquals("+821087654321", f.phone)
    }

    @Test fun autoRetrievalTimeoutStillAllowsManualVerification() {
        val f = Fixture(); f.sent()
        f.callbacks.timedOut("fake-id")
        assertEquals(PhoneAuthState.CodeSent(true), f.manager.state.value)
        f.manager.createCredential("123456")
        assertNotNull(f.manager.consumePendingCredential())
    }

    @Test fun backgroundResumeWithinWindowReattachesWithoutForceResendOrLosingId() {
        val f = Fixture(); f.sent()
        f.manager.resumeVerification(Unit)
        assertEquals(2, f.calls)
        assertNull(f.token)
        assertEquals(PhoneAuthState.CodeSent(), f.manager.state.value)
        f.manager.createCredential("123456")
        assertNotNull(f.manager.consumePendingCredential())
    }

    @Test fun resumeAfterWindowNeverAutomaticallyRequestsAnotherSms() {
        val f = Fixture(); f.sent()
        f.time += 60_000
        f.manager.resumeVerification(Unit)
        assertEquals(1, f.calls)
        assertEquals(PhoneAuthState.CodeSent(true), f.manager.state.value)
    }

    @Test fun watchdogRecoversSendingWhenDetachedCallbacksNeverArrive() {
        val f = Fixture(); f.send()
        f.time += 60_000
        f.manager.tick()
        assertEquals(PhoneAuthState.Failed(PhoneAuthError.NO_ACTIVE_REQUEST), f.manager.state.value)
        f.send()
        assertEquals(2, f.calls)
    }

    @Test fun accountChangeRejectsOldCallbacksAndCredential() {
        val f = Fixture(); f.sent()
        f.uid = "other-user"
        f.callbacks.completed("stale")
        assertEquals(PhoneAuthState.Failed(PhoneAuthError.ACCOUNT_CHANGED), f.manager.state.value)
        assertNull(f.manager.consumePendingCredential())
    }

    @Test fun signedOutOrInvalidNumberCannotSendSms() {
        val f = Fixture(); f.uid = null; f.send()
        assertEquals(PhoneAuthState.Failed(PhoneAuthError.GOOGLE_LOGIN_REQUIRED), f.manager.state.value)
        f.uid = "google-user"
        f.manager.sendVerificationCode(Unit, "invalid")
        assertEquals(PhoneAuthState.Failed(PhoneAuthError.INVALID_PHONE), f.manager.state.value)
        assertEquals(0, f.calls)
    }

    @Test fun verificationFailureIsTerminalAndClearsSecrets() {
        val f = Fixture(); f.sent()
        f.callbacks.failed(PhoneAuthError.TOO_MANY_REQUESTS)
        f.callbacks.completed("late")
        assertEquals(PhoneAuthState.Failed(PhoneAuthError.TOO_MANY_REQUESTS), f.manager.state.value)
        assertFalse(f.manager.canResend)
        assertNull(f.manager.consumePendingCredential())
    }

    @Test fun invalidCodeCanBeCorrectedAfterLinkFailure() {
        val f = Fixture(); f.sent()
        f.manager.createCredential("12345")
        assertEquals(PhoneAuthState.Failed(PhoneAuthError.INVALID_CODE), f.manager.state.value)
        f.manager.createCredential("123456")
        assertNotNull(f.manager.consumePendingCredential())
        assertTrue(f.manager.retryAfterLinkFailure())
        f.manager.createCredential("654321")
        assertNotNull(f.manager.consumePendingCredential())
    }
}
