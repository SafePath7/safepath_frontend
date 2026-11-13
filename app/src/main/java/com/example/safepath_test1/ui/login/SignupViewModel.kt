package com.example.safepath_test1.ui.login

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safepath_test1.auth.PhoneAuthError
import com.example.safepath_test1.auth.PhoneAuthState
import com.example.safepath_test1.auth.PhoneCredentialSource
import com.example.safepath_test1.auth.normalizeKoreanPhoneNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job

enum class SignupPhase { CHECKING, IDLE, SENDING, CODE_SENT, LINKING, VERIFIED, FAILED }

/** Memory only: do not put SMS codes/credentials in SavedStateHandle or saved instance state. */
data class SignupUiState(
    val name: String = "",
    val phone: String = "",
    val code: String = "",
    val phase: SignupPhase = SignupPhase.IDLE,
    val canEnterCode: Boolean = false,
    val canResend: Boolean = false,
    val resendSeconds: Int = 0,
    val autoRetrievalTimedOut: Boolean = false,
    val automaticVerification: Boolean = false,
    val error: String? = null,
) {
    val busy get() = phase == SignupPhase.CHECKING || phase == SignupPhase.SENDING || phase == SignupPhase.LINKING
    val canComplete get() = phase == SignupPhase.VERIFIED && name.isNotBlank()
    override fun toString() = "SignupUiState(phase=$phase, [REDACTED])"
}

class SignupViewModel internal constructor(
    private val backend: SignupPhoneBackend,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    constructor() : this(FirebaseSignupPhoneBackend(), Dispatchers.Main.immediate)

    private val ownerUid = backend.currentAccount()?.uid
    private val mutableState = MutableStateFlow(SignupUiState(name = backend.currentAccount()?.name.orEmpty()))
    val state = mutableState.asStateFlow()
    private var revision = 0L
    private var verifiedPhone: String? = null
    private var sessionStarted = false
    private var backgrounded = false
    private var timerJob: Job? = null
    private val stopObserving = backend.observeAccount {
        if (backend.currentAccount()?.uid != ownerUid) {
            clearVerification()
            mutableState.value = SignupUiState(error = "로그인 계정이 변경되었습니다. 다시 로그인해 주세요.")
        }
    }

    init {
        viewModelScope.launch(dispatcher) {
            backend.state.collect { event ->
                when (event) {
                    PhoneAuthState.Idle -> Unit
                    PhoneAuthState.Sending -> mutableState.update {
                        it.copy(phase = SignupPhase.SENDING, canEnterCode = false, canResend = false, error = null)
                    }
                    is PhoneAuthState.CodeSent -> mutableState.update {
                        it.copy(phase = SignupPhase.CODE_SENT, canEnterCode = true,
                            canResend = backend.canResend, resendSeconds = backend.resendSeconds, autoRetrievalTimedOut = event.autoRetrievalTimedOut)
                    }
                    is PhoneAuthState.Failed -> {
                        sessionStarted = false
                        mutableState.update {
                            it.copy(phase = SignupPhase.FAILED, canEnterCode = false,
                                canResend = false, error = event.error.userMessage())
                        }
                    }
                    is PhoneAuthState.CredentialReady -> link(event.source)
                }
            }
        }
    }

    /** Existing Firebase linkage is restored from the server, never from a local verified flag. */
    fun onEnter() {
        if (timerJob?.isActive != true) timerJob = viewModelScope.launch(dispatcher) {
            while (isActive) {
                delay(1_000)
                backend.tick()
                updateTimer()
            }
        }
        if (sessionStarted || state.value.busy || state.value.phase == SignupPhase.VERIFIED || !checkAccount()) return
        val attempt = revision
        mutableState.update { it.copy(phase = SignupPhase.CHECKING, error = null) }
        viewModelScope.launch(dispatcher) {
            try {
                val account = backend.restoreAccount()
                if (attempt != revision || !checkAccount()) return@launch
                if (account == null || account.uid != ownerUid) throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
                val phone = account.phone?.let(::normalizeKoreanPhoneNumber)
                if (phone != null) {
                    verifiedPhone = phone
                    mutableState.update { it.copy(phone = phone, phase = SignupPhase.VERIFIED) }
                } else mutableState.update { it.copy(phase = SignupPhase.IDLE) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (attempt == revision) mutableState.update {
                    it.copy(phase = SignupPhase.FAILED, error = "계정의 전화번호 연결 상태를 확인하지 못했습니다. 네트워크를 확인하고 다시 시도해 주세요.")
                }
            }
        }
    }

    private fun updateTimer() {
        mutableState.update { it.copy(resendSeconds = backend.resendSeconds,
            canResend = it.phase == SignupPhase.CODE_SENT && backend.canResend) }
    }

    fun setName(value: String) { mutableState.update { it.copy(name = value) } }

    fun setPhone(value: String) {
        if (state.value.busy || value == state.value.phone) return
        clearVerification()
        mutableState.update { SignupUiState(name = it.name, phone = value) }
    }

    fun setCode(value: String) {
        if (state.value.busy || state.value.phase == SignupPhase.VERIFIED) return
        mutableState.update { it.copy(code = value.filter { digit -> digit in '0'..'9' }.take(6), error = null) }
    }

    fun sendCode(activity: Activity) {
        if (state.value.busy || state.value.phase == SignupPhase.VERIFIED || state.value.canEnterCode || !checkAccount()) return
        if (normalizeKoreanPhoneNumber(state.value.phone) == null) {
            mutableState.update { it.copy(error = "010으로 시작하는 올바른 휴대전화 번호를 입력해 주세요.") }
            return
        }
        if (backend.resendSeconds > 0) { updateTimer(); return }
        verifiedPhone = null
        sessionStarted = true
        mutableState.update { it.copy(code = "", phase = SignupPhase.SENDING, canEnterCode = false, canResend = false, error = null, automaticVerification = false) }
        backend.send(activity, state.value.phone)
    }

    fun resendCode(activity: Activity) {
        if (state.value.busy || !state.value.canResend || !backend.canResend || !checkAccount()) return
        mutableState.update { it.copy(code = "", phase = SignupPhase.SENDING, canResend = false, canEnterCode = false, error = null, autoRetrievalTimedOut = false) }
        backend.resend(activity)
    }

    fun verifyCode() {
        if (state.value.busy || !state.value.canEnterCode || !checkAccount()) return
        if (!Regex("[0-9]{6}").matches(state.value.code)) {
            mutableState.update { it.copy(error = "6자리 인증번호를 입력해 주세요.") }
            return
        }
        backend.createCredential(state.value.code)
    }

    fun onBackground() { backgrounded = true }

    fun onForeground(activity: Activity, reattach: Boolean = false) {
        if ((backgrounded || reattach) && sessionStarted && !state.value.busyLinkingOrVerified() && checkAccount()) {
            backend.resume(activity)
        }
        backgrounded = false
    }

    /** Final synchronous guard immediately before the existing local onboarding write. */
    fun completeSignup(saveProfile: (uid: String, name: String, phone: String) -> Unit): Boolean {
        if (!checkAccount() || !state.value.canComplete) return false
        val phone = verifiedPhone
        val account = backend.currentAccount()
        if (phone == null || account?.phone != phone || normalizeKoreanPhoneNumber(state.value.phone) != phone) {
            clearVerification()
            mutableState.update { it.copy(phase = SignupPhase.FAILED, error = "전화번호를 다시 인증해 주세요.") }
            return false
        }
        saveProfile(checkNotNull(ownerUid), state.value.name.trim(), phone)
        abandon()
        return true
    }

    fun abandon() {
        timerJob?.cancel()
        timerJob = null
        clearVerification()
        mutableState.value = SignupUiState(name = backend.currentAccount()?.name.orEmpty())
    }

    private suspend fun link(source: PhoneCredentialSource) {
        if (!checkAccount()) return
        if (state.value.phase == SignupPhase.LINKING || state.value.phase == SignupPhase.VERIFIED) return
        val phone = normalizeKoreanPhoneNumber(state.value.phone) ?: return
        val attempt = revision
        mutableState.update { it.copy(phase = SignupPhase.LINKING, code = "", error = null,
            canEnterCode = false, canResend = false, automaticVerification = source == PhoneCredentialSource.AUTOMATIC_VERIFICATION) }
        try {
            val result = backend.linkCredential(checkNotNull(ownerUid), phone)
            if (attempt != revision || !checkAccount()) return
            if (result.uid != ownerUid || result.phone != phone || backend.currentAccount()?.phone != phone) {
                throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
            }
            verifiedPhone = phone
            sessionStarted = false
            backend.reset()
            mutableState.update { it.copy(phase = SignupPhase.VERIFIED, error = null) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (attempt != revision) return
            verifiedPhone = null
            val reason = (error as? SignupLinkException)?.reason ?: SignupLinkError.UNKNOWN
            val retry = reason in listOf(SignupLinkError.INVALID_CODE, SignupLinkError.NETWORK)
            val canRetry = retry && backend.retryCode()
            if (!canRetry) { backend.reset(); sessionStarted = false }
            mutableState.update { it.copy(phase = if (canRetry) SignupPhase.CODE_SENT else SignupPhase.FAILED,
                canEnterCode = canRetry, canResend = canRetry && backend.canResend, error = reason.userMessage()) }
        }
    }

    private fun checkAccount(): Boolean {
        if (ownerUid != null && backend.currentAccount()?.uid == ownerUid) return true
        clearVerification()
        mutableState.update { it.copy(phase = SignupPhase.FAILED, canEnterCode = false, canResend = false,
            error = "Google 로그인 계정이 변경되었습니다. 다시 로그인해 주세요.") }
        return false
    }

    private fun clearVerification() {
        revision++
        verifiedPhone = null
        sessionStarted = false
        backend.reset()
    }

    override fun onCleared() {
        stopObserving()
        backend.reset()
    }
}

private fun SignupUiState.busyLinkingOrVerified() = phase == SignupPhase.LINKING || phase == SignupPhase.VERIFIED

private fun PhoneAuthError.userMessage(): String = when (this) {
    PhoneAuthError.INVALID_PHONE -> "올바른 휴대전화 번호를 입력해 주세요."
    PhoneAuthError.INVALID_CODE -> "6자리 인증번호를 입력해 주세요."
    PhoneAuthError.GOOGLE_LOGIN_REQUIRED, PhoneAuthError.ACCOUNT_CHANGED -> "Google 계정으로 다시 로그인해 주세요."
    PhoneAuthError.NO_ACTIVE_REQUEST -> "인증번호를 다시 발송해 주세요."
    PhoneAuthError.NETWORK -> "네트워크 연결을 확인한 뒤 다시 발송해 주세요."
    PhoneAuthError.TOO_MANY_REQUESTS -> "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
    PhoneAuthError.INVALID_REQUEST -> "SMS 인증 요청을 처리할 수 없습니다. 번호와 인증 설정을 확인해 주세요."
    PhoneAuthError.SDK_FAILURE -> "인증번호 발송에 실패했습니다. 다시 시도해 주세요."
}

private fun SignupLinkError.userMessage(): String = when (this) {
    SignupLinkError.INVALID_CODE -> "인증번호가 올바르지 않습니다. 다시 입력해 주세요."
    SignupLinkError.EXPIRED_CODE -> "인증번호가 만료되었습니다. 다시 발송해 주세요."
    SignupLinkError.PHONE_IN_USE -> "이미 다른 계정에 연결된 전화번호입니다. 다른 번호를 사용해 주세요."
    SignupLinkError.NETWORK -> "네트워크 연결을 확인한 뒤 인증을 다시 시도해 주세요."
    SignupLinkError.TOO_MANY_REQUESTS -> "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
    SignupLinkError.RECENT_LOGIN_REQUIRED -> "보안을 위해 Google 계정으로 다시 로그인해 주세요."
    SignupLinkError.ACCOUNT_CHANGED -> "로그인 계정 또는 연결된 번호가 변경되었습니다. 다시 인증해 주세요."
    SignupLinkError.ALREADY_LINKED, SignupLinkError.UNKNOWN -> "전화번호 연결에 실패했습니다. 인증번호를 다시 발송해 주세요."
}
