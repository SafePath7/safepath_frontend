package com.example.safepath_test1.auth

import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PhoneCredentialSource { MANUAL_CODE, AUTOMATIC_VERIFICATION }

enum class PhoneAuthError {
    INVALID_PHONE, INVALID_CODE, GOOGLE_LOGIN_REQUIRED, ACCOUNT_CHANGED,
    NO_ACTIVE_REQUEST, NETWORK, TOO_MANY_REQUESTS, INVALID_REQUEST, SDK_FAILURE,
}

/** No credentials, verification IDs, tokens or raw exceptions in observable state. */
sealed interface PhoneAuthState {
    data object Idle : PhoneAuthState
    data object Sending : PhoneAuthState
    data class CodeSent(val autoRetrievalTimedOut: Boolean = false) : PhoneAuthState
    data class CredentialReady(val source: PhoneCredentialSource) : PhoneAuthState
    data class Failed(val error: PhoneAuthError) : PhoneAuthState
}

internal class PendingPhoneCredential<C>(val userUid: String, val phone: String, val credential: C) {
    override fun toString() = "PendingPhoneCredential([REDACTED])"
}

/** Type parameters keep the state machine independently testable without Android or live SMS. */
internal interface PhoneAuthGateway<C, T, H> {
    fun currentGoogleUid(): String?
    fun verify(host: H, phone: String, token: T?, callbacks: PhoneVerificationCallbacks<C, T>)
    fun createCredential(verificationId: String, code: String): C
}

internal interface PhoneVerificationCallbacks<C, T> {
    fun codeSent(id: String, token: T)
    fun completed(credential: C)
    fun failed(error: PhoneAuthError)
    fun timedOut(id: String)
}

/**
 * Main-thread, memory-only session. Never persist this class in SavedStateHandle/preferences.
 * A generation invalidates stale callbacks on edits, account changes, resend and abandonment.
 * Activity is supplied per invocation and never stored. Cooldown survives phone edits/reset.
 */
@MainThread
internal class PhoneAuthManager<C, T, H>(
    private val gateway: PhoneAuthGateway<C, T, H>,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutableState = MutableStateFlow<PhoneAuthState>(PhoneAuthState.Idle)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var ownerUid: String? = null
    private var phoneNumber: String? = null
    private var verificationId: String? = null
    private var resendToken: T? = null
    private var pending: PendingPhoneCredential<C>? = null
    private var terminal = false
    private var retrievalTimedOut = false
    private var nextSendAt = 0L
    private var retrievalUntil = 0L

    val resendSeconds: Int get() = ((nextSendAt - nowMillis()).coerceAtLeast(0) + 999).div(1000).toInt()
    val canResend: Boolean get() = ownerUid != null && !terminal && resendSeconds == 0 &&
        state.value !is PhoneAuthState.Sending

    fun sendVerificationCode(host: H, phone: String) {
        if (state.value is PhoneAuthState.Sending || pending != null || resendSeconds > 0) return
        val normalized = normalizeKoreanPhoneNumber(phone)
        val uid = gateway.currentGoogleUid()
        if (normalized == null || uid == null) {
            reset()
            fail(if (normalized == null) PhoneAuthError.INVALID_PHONE else PhoneAuthError.GOOGLE_LOGIN_REQUIRED)
            return
        }
        reset()
        ownerUid = uid
        phoneNumber = normalized
        start(host, forceResend = false, reattach = false)
    }

    fun resendVerificationCode(host: H) {
        if (!checkOwner() || !canResend) return
        start(host, forceResend = true, reattach = false)
    }

    /** Reattach only inside the original SDK auto-retrieval window; never send SMS on resume. */
    fun resumeVerification(host: H) {
        if (ownerUid == null || terminal || !checkOwner()) return
        tick()
        if (!retrievalTimedOut) start(host, forceResend = false, reattach = true)
    }

    /** A watchdog also recovers a Sending state when Activity-bound callbacks were detached. */
    fun tick() {
        if (ownerUid == null || terminal || retrievalTimedOut || nowMillis() < retrievalUntil) return
        retrievalTimedOut = true
        generation++
        if (verificationId != null) mutableState.value = PhoneAuthState.CodeSent(true)
        else {
            terminal = true
            fail(PhoneAuthError.NO_ACTIVE_REQUEST)
        }
    }

    fun createCredential(code: String) {
        if (!checkOwner() || terminal || state.value is PhoneAuthState.Sending) return
        val id = verificationId
        if (id == null) { fail(PhoneAuthError.NO_ACTIVE_REQUEST); return }
        if (!Regex("[0-9]{6}").matches(code)) { fail(PhoneAuthError.INVALID_CODE); return }
        try {
            ready(gateway.createCredential(id, code), PhoneCredentialSource.MANUAL_CODE)
        } catch (_: Exception) { fail(PhoneAuthError.INVALID_CODE) }
    }

    fun consumePendingCredential(): PendingPhoneCredential<C>? {
        if (!checkOwner()) return null
        val result = pending ?: return null
        pending = null
        generation++
        mutableState.value = PhoneAuthState.Idle
        return result
    }

    fun retryAfterLinkFailure(): Boolean {
        if (!checkOwner()) return false
        terminal = false
        if (verificationId == null) return false
        mutableState.value = PhoneAuthState.CodeSent(retrievalTimedOut)
        return true
    }

    fun reset() {
        generation++
        ownerUid = null
        phoneNumber = null
        verificationId = null
        resendToken = null
        pending = null
        terminal = false
        retrievalTimedOut = false
        mutableState.value = PhoneAuthState.Idle
    }

    private fun start(host: H, forceResend: Boolean, reattach: Boolean) {
        val phone = phoneNumber ?: return
        val requestGeneration = ++generation
        if (!reattach) {
            verificationId = null
            pending = null
            retrievalTimedOut = false
            nextSendAt = nowMillis() + 60_000
            retrievalUntil = nextSendAt
            mutableState.value = PhoneAuthState.Sending
        }
        terminal = false
        val callbacks = object : PhoneVerificationCallbacks<C, T> {
            override fun codeSent(id: String, token: T) {
                if (!accept(requestGeneration)) return
                verificationId = id
                resendToken = token
                mutableState.value = PhoneAuthState.CodeSent(retrievalTimedOut)
            }
            override fun completed(credential: C) {
                if (accept(requestGeneration)) ready(credential, PhoneCredentialSource.AUTOMATIC_VERIFICATION)
            }
            override fun failed(error: PhoneAuthError) {
                if (!accept(requestGeneration)) return
                terminal = true
                verificationId = null
                resendToken = null
                fail(error)
            }
            override fun timedOut(id: String) {
                if (!accept(requestGeneration)) return
                verificationId = id
                retrievalTimedOut = true
                mutableState.value = PhoneAuthState.CodeSent(true)
            }
        }
        try {
            gateway.verify(host, phone, if (forceResend) resendToken else null, callbacks)
        } catch (_: Exception) { callbacks.failed(PhoneAuthError.SDK_FAILURE) }
    }

    private fun accept(requestGeneration: Long) = requestGeneration == generation && !terminal && checkOwner()

    private fun checkOwner(): Boolean {
        if (ownerUid == null) { fail(PhoneAuthError.NO_ACTIVE_REQUEST); return false }
        if (gateway.currentGoogleUid() == ownerUid) return true
        reset()
        fail(PhoneAuthError.ACCOUNT_CHANGED)
        return false
    }

    private fun ready(credential: C, source: PhoneCredentialSource) {
        pending = PendingPhoneCredential(checkNotNull(ownerUid), checkNotNull(phoneNumber), credential)
        terminal = true
        mutableState.value = PhoneAuthState.CredentialReady(source)
    }

    private fun fail(error: PhoneAuthError) { mutableState.value = PhoneAuthState.Failed(error) }
}
