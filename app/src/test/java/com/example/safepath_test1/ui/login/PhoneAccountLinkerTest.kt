package com.example.safepath_test1.ui.login

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PhoneAccountLinkerTest {
    private val phone = "+821012345678"
    private class Operations : PhoneAccountOperations<String> {
        var account = SignupAccount("google-user", null)
        var resultUid = "google-user"
        var failLink: SignupLinkError? = null
        var failRefresh = false
        var switchAfterLink = false
        var switchDuringReload = false
        val calls = mutableListOf<String>()
        override fun currentAccount() = account
        override suspend fun link(credential: String): SignupAccount {
            calls += "link"
            failLink?.let { throw SignupLinkException(it) }
            val linked = SignupAccount(resultUid, "+821012345678")
            account = if (switchAfterLink) SignupAccount("other-user", null) else linked
            return linked
        }
        override suspend fun reload(): SignupAccount {
            calls += "reload"
            if (switchDuringReload) account = SignupAccount("other-user", null)
            return account
        }
        override suspend fun reauthenticate(credential: String) { calls += "reauthenticate" }
        override suspend fun updatePhone(credential: String) {
            calls += "updatePhone"
            account = SignupAccount("google-user", "+821012345678")
        }
        override suspend fun refreshToken() {
            calls += "refreshToken"
            if (failRefresh) throw SignupLinkException(SignupLinkError.NETWORK)
        }
    }

    @Test fun linkPreservesGoogleUidAndConfirmsServerPhoneBeforeRefreshingToken() = runBlocking {
        val ops = Operations()
        val result = PhoneAccountLinker(ops).link("google-user", phone, "fake-credential")
        assertEquals("google-user", result.uid)
        assertEquals(phone, result.phone)
        assertEquals(listOf("link", "reload", "refreshToken"), ops.calls)
    }

    @Test fun sameAlreadyLinkedPhoneRequiresServerReauthentication() = runBlocking {
        val ops = Operations()
        ops.account = SignupAccount("google-user", phone)
        ops.failLink = SignupLinkError.ALREADY_LINKED
        PhoneAccountLinker(ops).link("google-user", phone, "fake-credential")
        assertEquals(listOf("link", "reload", "reauthenticate", "reload", "reload", "refreshToken"), ops.calls)
        assertEquals("google-user", ops.account.uid)
    }

    @Test fun replacingLinkedPhoneUsesUpdateWithoutChangingUid() = runBlocking {
        val ops = Operations()
        ops.account = SignupAccount("google-user", "+821087654321")
        ops.failLink = SignupLinkError.ALREADY_LINKED
        PhoneAccountLinker(ops).link("google-user", phone, "fake-credential")
        assertTrue(ops.calls.contains("updatePhone"))
        assertFalse(ops.calls.contains("reauthenticate"))
        assertEquals("google-user", ops.account.uid)
        assertEquals(phone, ops.account.phone)
    }

    @Test fun phoneOwnedByAnotherAccountIsNotMergedOrSignedIn() = runBlocking {
        val ops = Operations(); ops.failLink = SignupLinkError.PHONE_IN_USE
        expectError(SignupLinkError.PHONE_IN_USE) { PhoneAccountLinker(ops).link("google-user", phone, "fake") }
        assertEquals(listOf("link"), ops.calls)
        assertEquals("google-user", ops.account.uid)
    }

    @Test fun wrongUidAndAccountChangesRejectLateSuccess() = runBlocking {
        listOf("wrong-result", "switch-after-link", "switch-during-reload").forEach { scenario ->
            val ops = Operations()
            ops.resultUid = if (scenario == "wrong-result") "wrong-user" else "google-user"
            ops.switchAfterLink = scenario == "switch-after-link"
            ops.switchDuringReload = scenario == "switch-during-reload"
            expectError(SignupLinkError.ACCOUNT_CHANGED) { PhoneAccountLinker(ops).link("google-user", phone, "fake") }
            assertFalse(ops.calls.contains("refreshToken"))
        }
    }

    @Test fun changedAccountBeforeLinkDoesNotInvokeFirebaseMutation() = runBlocking {
        val ops = Operations(); ops.account = SignupAccount("other-user", null)
        expectError(SignupLinkError.ACCOUNT_CHANGED) { PhoneAccountLinker(ops).link("google-user", phone, "fake") }
        assertTrue(ops.calls.isEmpty())
    }

    @Test fun tokenRefreshFailureCannotReportVerificationSuccess() = runBlocking {
        val ops = Operations(); ops.failRefresh = true
        expectError(SignupLinkError.NETWORK) { PhoneAccountLinker(ops).link("google-user", phone, "fake") }
    }

    @Test fun wrongPhoneInLinkResultCannotReportSuccess() = runBlocking {
        val ops = Operations()
        expectError(SignupLinkError.ACCOUNT_CHANGED) {
            PhoneAccountLinker(ops).link("google-user", "+821087654321", "fake")
        }
        assertFalse(ops.calls.contains("refreshToken"))
    }

    private suspend fun expectError(reason: SignupLinkError, block: suspend () -> Unit) {
        try { block(); fail("Expected a sanitized linking failure") }
        catch (error: SignupLinkException) { assertEquals(reason, error.reason) }
    }
}
