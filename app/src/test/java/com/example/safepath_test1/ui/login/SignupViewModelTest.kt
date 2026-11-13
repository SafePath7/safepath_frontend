package com.example.safepath_test1.ui.login

import android.app.Activity
import androidx.lifecycle.ViewModelStore
import com.example.safepath_test1.auth.PhoneAuthState
import com.example.safepath_test1.auth.PhoneCredentialSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SignupViewModelTest {
    private val store = ViewModelStore()

    @After fun cleanup() { store.clear() }

    private fun model(backend: FakeBackend): SignupViewModel =
        SignupViewModel(backend, Dispatchers.Unconfined).also {
            store.put("signup", it)
            it.setPhone("010-1234-5678")
        }

    @Test fun cannotSaveBeforeServerLinkEvenWithSixDigitCode() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.deferred = CompletableDeferred()
        backend.events.value = PhoneAuthState.CodeSent()
        model.setCode("123456")
        model.verifyCode()
        assertEquals(SignupPhase.LINKING, model.state.value.phase)
        assertFalse(model.state.value.canComplete)
        assertFalse(model.completeSignup { _, _, _ -> fail("Must not save before server validation") })
        backend.completePending()
        assertEquals(SignupPhase.VERIFIED, model.state.value.phase)
    }

    @Test fun manualCodeLinksAndSavesCanonicalPhoneWithOriginalUid() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.events.value = PhoneAuthState.CodeSent()
        model.setCode("123456")
        model.verifyCode()
        assertEquals(1, backend.linkCalls)
        var saved = false
        assertTrue(model.completeSignup { uid, name, phone ->
            assertEquals("google-user", uid)
            assertEquals("사용자", name)
            assertEquals("+821012345678", phone)
            saved = true
        })
        assertTrue(saved)
        assertFalse(model.state.value.canComplete)
    }

    @Test fun automaticCredentialUsesTheSameServerLinkPath() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.AUTOMATIC_VERIFICATION)
        assertEquals(1, backend.linkCalls)
        assertTrue(model.state.value.automaticVerification)
        assertTrue(model.state.value.canComplete)
    }

    @Test fun changingPhoneInvalidatesVerifiedStateAndBlocksSave() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.AUTOMATIC_VERIFICATION)
        model.setPhone("01087654321")
        assertEquals(SignupPhase.IDLE, model.state.value.phase)
        assertFalse(model.state.value.canComplete)
        assertFalse(model.completeSignup { _, _, _ -> fail("Changed number needs verification") })
    }

    @Test fun rejectsDifferentUidReturnedByServer() {
        val backend = FakeBackend()
        backend.resultUid = "different-user"
        val model = model(backend)
        backend.events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.AUTOMATIC_VERIFICATION)
        assertFalse(model.state.value.canComplete)
        assertEquals(SignupPhase.FAILED, model.state.value.phase)
    }

    @Test fun accountChangeDuringLinkIgnoresLateSuccess() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.deferred = CompletableDeferred()
        backend.events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.AUTOMATIC_VERIFICATION)
        backend.account = SignupAccount("other-user", null)
        backend.accountListener?.invoke()
        backend.completePending()
        assertFalse(model.state.value.canComplete)
        assertFalse(model.completeSignup { _, _, _ -> fail("Account changed") })
    }

    @Test fun invalidCodeAndNetworkFailureAllowCodeRetry() {
        listOf(SignupLinkError.INVALID_CODE, SignupLinkError.NETWORK).forEach { error ->
            val backend = FakeBackend()
            val model = model(backend)
            backend.linkError = error
            backend.events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.MANUAL_CODE)
            assertTrue(model.state.value.canEnterCode)
            assertTrue(model.state.value.canResend)
            assertNotNull(model.state.value.error)
            assertFalse(model.state.value.canComplete)
            backend.linkError = null
            model.setCode("654321")
            model.verifyCode()
            assertTrue(model.state.value.canComplete)
        }
    }

    @Test fun expiredCodeAndAnotherAccountsPhoneRequireNewVerification() {
        listOf(SignupLinkError.EXPIRED_CODE, SignupLinkError.PHONE_IN_USE).forEach { error ->
            val backend = FakeBackend()
            val model = model(backend)
            backend.linkError = error
            backend.events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.MANUAL_CODE)
            assertEquals(SignupPhase.FAILED, model.state.value.phase)
            assertFalse(model.state.value.canEnterCode)
            assertFalse(model.state.value.canComplete)
            assertNotNull(model.state.value.error)
        }
    }

    @Test fun autoRetrievalTimeoutStillAllowsManualEntry() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.events.value = PhoneAuthState.CodeSent(autoRetrievalTimedOut = true)
        assertTrue(model.state.value.canEnterCode)
        assertTrue(model.state.value.autoRetrievalTimedOut)
        assertFalse(model.state.value.canComplete)
    }

    @Test fun processRestartDoesNotTrustPreviouslyLinkedPhoneAsNewVerification() {
        val backend = FakeBackend()
        backend.account = SignupAccount("google-user", "+821012345678", "사용자")
        val model = model(backend)
        assertFalse(model.state.value.canComplete)
        assertFalse(model.completeSignup { _, _, _ -> fail("Cached phone is not a new verification") })
    }

    @Test fun serverPhoneMustStillMatchAtCompletion() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.AUTOMATIC_VERIFICATION)
        backend.account = SignupAccount("google-user", "+821087654321")
        assertFalse(model.completeSignup { _, _, _ -> fail("Phone mismatch") })
        assertFalse(model.state.value.canComplete)
    }

    @Test fun verifiedPhoneStillRequiresNonBlankName() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.AUTOMATIC_VERIFICATION)
        model.setName("   ")
        assertFalse(model.state.value.canComplete)
        assertFalse(model.completeSignup { _, _, _ -> fail("A name is required") })
    }

    @Test fun leavingSignupInvalidatesAnInFlightLinkResult() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.deferred = CompletableDeferred()
        backend.events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.MANUAL_CODE)
        model.abandon()
        backend.completePending()
        assertEquals(SignupPhase.IDLE, model.state.value.phase)
        assertFalse(model.state.value.canComplete)
    }

    @Test fun serverErrorCodesAreClassifiedWithoutRawExceptionMessages() {
        assertEquals(SignupLinkError.INVALID_CODE, signupLinkErrorCode("ERROR_INVALID_VERIFICATION_CODE"))
        assertEquals(SignupLinkError.EXPIRED_CODE, signupLinkErrorCode("ERROR_SESSION_EXPIRED"))
        assertEquals(SignupLinkError.PHONE_IN_USE, signupLinkErrorCode("ERROR_CREDENTIAL_ALREADY_IN_USE"))
        assertEquals(SignupLinkError.NETWORK, signupLinkErrorCode("ERROR_NETWORK_REQUEST_FAILED"))
        assertEquals(SignupLinkError.UNKNOWN, signupLinkErrorCode("unrecognized"))
    }

    @Test fun serverConfirmedLinkedPhoneSurvivesProcessRestartWithoutAnotherSms() {
        val backend = FakeBackend()
        backend.account = SignupAccount("google-user", "+821012345678", "사용자")
        val model = model(backend)
        model.onEnter()
        assertEquals(SignupPhase.VERIFIED, model.state.value.phase)
        assertTrue(model.state.value.canComplete)
        assertEquals(0, backend.linkCalls)
    }

    @Test fun restoringUnlinkedAccountStillRequiresSms() {
        val backend = FakeBackend()
        val model = model(backend)
        model.onEnter()
        assertEquals(SignupPhase.IDLE, model.state.value.phase)
        assertFalse(model.state.value.canComplete)
    }

    @Test fun failedServerRestoreDoesNotTrustCachedPhone() {
        val backend = FakeBackend()
        backend.account = SignupAccount("google-user", "+821012345678", "사용자")
        backend.restoreFails = true
        val model = model(backend)
        model.onEnter()
        assertEquals(SignupPhase.FAILED, model.state.value.phase)
        assertFalse(model.state.value.canComplete)
    }

    @Test fun smsFailureIsVisibleAndCannotCompleteSignup() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.events.value = PhoneAuthState.Failed(com.example.safepath_test1.auth.PhoneAuthError.NETWORK)
        assertEquals(SignupPhase.FAILED, model.state.value.phase)
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.canComplete)
    }

    @Test fun lateRestoreCannotVerifyAnAccountAfterLeavingSignup() {
        val backend = FakeBackend()
        backend.restoreDeferred = CompletableDeferred()
        val model = model(backend)
        model.onEnter()
        assertEquals(SignupPhase.CHECKING, model.state.value.phase)
        model.abandon()
        backend.account = SignupAccount("google-user", "+821012345678", "사용자")
        backend.restoreDeferred!!.complete(backend.account)
        assertEquals(SignupPhase.IDLE, model.state.value.phase)
        assertFalse(model.state.value.canComplete)
    }

    @Test fun lateCredentialEventDuringLinkDoesNotLinkTwice() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.deferred = CompletableDeferred()
        backend.events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.MANUAL_CODE)
        backend.events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.AUTOMATIC_VERIFICATION)
        assertEquals(1, backend.linkCalls)
        backend.completePending()
        assertEquals(1, backend.linkCalls)
        assertTrue(model.state.value.canComplete)
    }

    @Test fun shortCodeIsRejectedBeforeCredentialCreation() {
        val backend = FakeBackend()
        val model = model(backend)
        backend.events.value = PhoneAuthState.CodeSent()
        model.setCode("12345")
        model.verifyCode()
        assertEquals(0, backend.linkCalls)
        assertNotNull(model.state.value.error)
    }

    private class FakeBackend : SignupPhoneBackend {
        val events = MutableStateFlow<PhoneAuthState>(PhoneAuthState.Idle)
        override val state = events
        override val canResend = true
        override val resendSeconds = 0
        override fun tick() {}
        var restoreFails = false
        var restoreDeferred: CompletableDeferred<SignupAccount?>? = null
        override suspend fun restoreAccount(): SignupAccount? {
            if (restoreFails) throw SignupLinkException(SignupLinkError.NETWORK)
            return restoreDeferred?.await() ?: account
        }
        var account: SignupAccount? = SignupAccount("google-user", null, "사용자")
        var accountListener: (() -> Unit)? = null
        var linkCalls = 0
        var resultUid = "google-user"
        var linkError: SignupLinkError? = null
        var deferred: CompletableDeferred<SignupAccount>? = null

        override fun currentAccount() = account
        override fun observeAccount(listener: () -> Unit): () -> Unit {
            accountListener = listener
            return { accountListener = null }
        }
        override fun send(activity: Activity, phone: String): Unit = error("No SMS in unit tests")
        override fun resend(activity: Activity): Unit = error("No SMS in unit tests")
        override fun resume(activity: Activity): Unit = error("No SMS in unit tests")
        override fun createCredential(code: String) {
            events.value = PhoneAuthState.CredentialReady(PhoneCredentialSource.MANUAL_CODE)
        }
        override suspend fun linkCredential(expectedUid: String, expectedPhone: String): SignupAccount {
            linkCalls++
            linkError?.let { throw SignupLinkException(it) }
            return deferred?.await() ?: SignupAccount(resultUid, expectedPhone, "사용자").also { account = it }
        }
        fun completePending() {
            val result = SignupAccount(resultUid, "+821012345678", "사용자")
            account = result
            deferred!!.complete(result)
        }
        override fun retryCode(): Boolean {
            events.value = PhoneAuthState.CodeSent()
            return true
        }
        override fun reset() { events.value = PhoneAuthState.Idle }
    }
}
