package com.example.safepath_test1.ui.login

import android.app.Activity
import com.example.safepath_test1.auth.*
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

internal class SignupAccount(val uid: String, val phone: String?, val name: String = "") {
    override fun toString() = "SignupAccount([REDACTED])"
}

internal enum class SignupLinkError {
    ACCOUNT_CHANGED, INVALID_CODE, EXPIRED_CODE, PHONE_IN_USE, NETWORK, TOO_MANY_REQUESTS,
    RECENT_LOGIN_REQUIRED, ALREADY_LINKED, UNKNOWN,
}

internal class SignupLinkException(val reason: SignupLinkError) : Exception(reason.name)

/** Isolates Firebase and Android from the signup state machine for offline unit tests. */
internal interface SignupPhoneBackend {
    val state: StateFlow<PhoneAuthState>
    val canResend: Boolean
    val resendSeconds: Int
    fun tick()
    suspend fun restoreAccount(): SignupAccount?
    fun currentAccount(): SignupAccount?
    fun observeAccount(listener: () -> Unit): () -> Unit
    fun send(activity: Activity, phone: String)
    fun resend(activity: Activity)
    fun resume(activity: Activity)
    fun createCredential(code: String)
    suspend fun linkCredential(expectedUid: String, expectedPhone: String): SignupAccount
    fun retryCode(): Boolean
    fun reset()
}

internal class FirebaseSignupPhoneBackend(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : SignupPhoneBackend {
    private val manager = PhoneAuthManager(FirebasePhoneAuthGateway(auth))
    override val state = manager.state
    override val canResend get() = manager.canResend
    override val resendSeconds get() = manager.resendSeconds
    override fun tick() = manager.tick()
    override fun currentAccount(): SignupAccount? = auth.currentUser?.asSignupAccount()

    override fun observeAccount(listener: () -> Unit): () -> Unit {
        val observer = FirebaseAuth.AuthStateListener { listener() }
        auth.addAuthStateListener(observer)
        return { auth.removeAuthStateListener(observer) }
    }

    override fun send(activity: Activity, phone: String) = manager.sendVerificationCode(activity, phone)
    override fun resend(activity: Activity) = manager.resendVerificationCode(activity)
    override fun resume(activity: Activity) = manager.resumeVerification(activity)
    override fun createCredential(code: String) = manager.createCredential(code)
    override fun retryCode() = manager.retryAfterLinkFailure()
    override fun reset() = manager.reset()

    override suspend fun restoreAccount(): SignupAccount? = sanitized {
        val user = auth.currentUser ?: return@sanitized null
        val uid = currentAccount()?.uid ?: throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
        user.reload().await()
        if (currentAccount()?.uid != uid) throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
        // Firebase's persisted user is a cache; restore verification only after server confirmation.
        user.getIdToken(true).await()
        currentAccount()?.also {
            if (it.uid != uid) throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
        } ?: throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
    }

    override suspend fun linkCredential(expectedUid: String, expectedPhone: String): SignupAccount = sanitized {
        val pending = manager.consumePendingCredential()
            ?: throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
        val user = auth.currentUser
        if (user == null || currentAccount()?.uid != expectedUid || pending.userUid != expectedUid ||
            pending.phone != expectedPhone) throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
        PhoneAccountLinker(object : PhoneAccountOperations<PhoneAuthCredential> {
            override fun currentAccount() = this@FirebaseSignupPhoneBackend.currentAccount()
            override suspend fun link(credential: PhoneAuthCredential): SignupAccount = sanitized {
                user.linkWithCredential(credential).await().user?.asSignupAccount()
                    ?: throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
            }
            override suspend fun reload(): SignupAccount {
                user.reload().await()
                return user.asSignupAccount() ?: throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
            }
            override suspend fun reauthenticate(credential: PhoneAuthCredential) {
                user.reauthenticate(credential).await()
            }
            override suspend fun updatePhone(credential: PhoneAuthCredential) {
                user.updatePhoneNumber(credential).await()
            }
            override suspend fun refreshToken() { user.getIdToken(true).await() }
        }).link(expectedUid, expectedPhone, pending.credential)
    }
}

private fun FirebaseUser.asSignupAccount(): SignupAccount? = takeIf { user ->
    user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
}?.let { SignupAccount(it.uid, it.phoneNumber, it.displayName.orEmpty()) }

private suspend fun <T> sanitized(block: suspend () -> T): T = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: SignupLinkException) {
    throw error
} catch (error: Exception) {
    // Discard raw exceptions, which may contain identifiers or credential material.
    throw SignupLinkException(classifySignupLinkError(error))
}

private class FirebasePhoneAuthGateway(private val auth: FirebaseAuth) :
    PhoneAuthGateway<PhoneAuthCredential, PhoneAuthProvider.ForceResendingToken, Activity> {
    override fun currentGoogleUid() = auth.currentUser?.asSignupAccount()?.uid
    override fun verify(
        host: Activity,
        phone: String,
        token: PhoneAuthProvider.ForceResendingToken?,
        callbacks: PhoneVerificationCallbacks<PhoneAuthCredential, PhoneAuthProvider.ForceResendingToken>,
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setActivity(host)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) =
                    callbacks.codeSent(id, token)
                override fun onVerificationCompleted(credential: PhoneAuthCredential) = callbacks.completed(credential)
                override fun onVerificationFailed(error: FirebaseException) = callbacks.failed(when (error) {
                    is FirebaseNetworkException -> PhoneAuthError.NETWORK
                    is FirebaseTooManyRequestsException -> PhoneAuthError.TOO_MANY_REQUESTS
                    is FirebaseAuthException -> PhoneAuthError.INVALID_REQUEST
                    else -> PhoneAuthError.SDK_FAILURE
                })
                override fun onCodeAutoRetrievalTimeOut(id: String) = callbacks.timedOut(id)
            })
        if (token != null) options.setForceResendingToken(token)
        PhoneAuthProvider.verifyPhoneNumber(options.build())
    }
    override fun createCredential(verificationId: String, code: String) =
        PhoneAuthProvider.getCredential(verificationId, code)
}

internal fun classifySignupLinkError(error: Exception): SignupLinkError = when (error) {
    is FirebaseNetworkException -> SignupLinkError.NETWORK
    is FirebaseTooManyRequestsException -> SignupLinkError.TOO_MANY_REQUESTS
    is FirebaseAuthException -> signupLinkErrorCode(error.errorCode)
    else -> SignupLinkError.UNKNOWN
}

internal fun signupLinkErrorCode(code: String): SignupLinkError = when (code) {
    "ERROR_PROVIDER_ALREADY_LINKED" -> SignupLinkError.ALREADY_LINKED
    "ERROR_INVALID_VERIFICATION_CODE", "ERROR_INVALID_CREDENTIAL" -> SignupLinkError.INVALID_CODE
    "ERROR_SESSION_EXPIRED", "ERROR_CODE_EXPIRED", "ERROR_INVALID_VERIFICATION_ID" -> SignupLinkError.EXPIRED_CODE
    "ERROR_CREDENTIAL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> SignupLinkError.PHONE_IN_USE
    "ERROR_REQUIRES_RECENT_LOGIN" -> SignupLinkError.RECENT_LOGIN_REQUIRED
    "ERROR_USER_MISMATCH", "ERROR_USER_TOKEN_EXPIRED", "ERROR_USER_DISABLED" -> SignupLinkError.ACCOUNT_CHANGED
    "ERROR_TOO_MANY_REQUESTS", "ERROR_QUOTA_EXCEEDED" -> SignupLinkError.TOO_MANY_REQUESTS
    "ERROR_NETWORK_REQUEST_FAILED" -> SignupLinkError.NETWORK
    else -> SignupLinkError.UNKNOWN
}
