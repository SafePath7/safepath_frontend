package com.example.safepath_test1.ui.guardian

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.safepath_test1.api.AccountRepository
import com.example.safepath_test1.api.BackendRepository
import com.example.safepath_test1.api.GuardianPermissions
import com.example.safepath_test1.api.GuardianRegistration
import com.example.safepath_test1.api.RiskEventDetail
import com.example.safepath_test1.api.RiskMedia
import com.example.safepath_test1.api.accountErrorMessage
import com.example.safepath_test1.friends.cacheServerFriends
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class BackendGuardianState(
    val rows: List<GuardianRegistration> = emptyList(),
    val myId: Long? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val events: List<RiskEventDetail>? = null,
    val eventDetail: RiskEventDetail? = null,
    val eventMedia: List<RiskMedia> = emptyList(),
    val guardianInput: String = "",
    val sessionId: String? = null,
)

class BackendGuardianViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val accounts = AccountRepository(application)
    private val backend = BackendRepository(application)
    private val mutableState = MutableStateFlow(BackendGuardianState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var accountUid: String? = null
    private val sessions = application.getSharedPreferences("backend_sessions", Application.MODE_PRIVATE)
    private val listener = FirebaseAuth.AuthStateListener { firebase ->
        val uid = firebase.currentUser?.uid
        if (uid != accountUid) {
            job?.cancel()
            accountUid = uid
            mutableState.value = BackendGuardianState()
            if (uid != null) refresh()
        }
    }

    init { auth.addAuthStateListener(listener) }

    private suspend fun load(uid: String) {
        val account = accounts.profile()
        val registrations = backend.guardians()
        check(auth.currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
        cacheServerFriends(getApplication(), uid, registrations.filter {
            it.userId == account.userId && it.status == "ACCEPTED"
        }.map { GuardianItem(it.id.toString(), it.guardianName, "보호자", "") })
        var sessionId = sessions.getString(uid, null)
        if (sessionId != null) {
            try { backend.sessionStatus(sessionId) }
            catch (error: HttpException) {
                if (error.code() != 404) throw error
                sessions.edit().remove(uid).apply()
                sessionId = null
            }
        }
        mutableState.update { it.copy(myId = account.userId, rows = registrations, sessionId = sessionId) }
    }

    private fun perform(action: suspend (String) -> Unit) {
        val uid = accountUid ?: return
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        job = viewModelScope.launch {
            try {
                action(uid)
                check(auth.currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (accountUid == uid) mutableState.update { it.copy(error = accountErrorMessage(error)) }
            } finally {
                if (accountUid == uid) mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun refresh() = perform { load(it) }
    fun changeGuardianInput(value: String) { mutableState.update { it.copy(guardianInput = value.filter(Char::isDigit)) } }
    fun requestGuardian(id: Long) = perform { uid ->
        backend.requestGuardian(id)
        load(uid)
        mutableState.update { it.copy(guardianInput = "") }
    }
    fun respond(id: Long, action: String) = perform { uid -> backend.respond(id, action); load(uid) }
    fun updatePermissions(id: Long, value: GuardianPermissions) = perform { uid -> backend.permissions(id, value); load(uid) }
    fun loadEvents(userId: Long? = null) = perform { uid ->
        val events = backend.riskEvents(userId)
        check(auth.currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
        mutableState.update { it.copy(events = events) }
    }
    fun dismissEvents() { mutableState.update { it.copy(events = null) } }
    fun loadEventDetail(id: Long) = perform { uid ->
        val event = backend.riskEvent(id)
        val media = backend.riskMedia(id)
        check(auth.currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
        mutableState.update { it.copy(eventDetail = event, eventMedia = media) }
    }
    fun dismissEventDetail() { mutableState.update { it.copy(eventDetail = null, eventMedia = emptyList()) } }
    fun startSession() = perform { uid ->
        val session = backend.startSession()
        check(auth.currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
        if (!sessions.edit().putString(uid, session.id).commit()) {
            runCatching { backend.endSession(session.id) }
            error("세션을 기기에 저장하지 못했습니다. 서버 상태를 확인해 주세요.")
        }
        mutableState.update { it.copy(sessionId = session.id) }
    }
    fun endSession() = perform { uid ->
        val id = sessions.getString(uid, null) ?: return@perform
        try { backend.endSession(id) }
        catch (error: HttpException) { if (error.code() != 404) throw error }
        check(auth.currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
        sessions.edit().remove(uid).apply()
        mutableState.update { it.copy(sessionId = null) }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(listener)
        job?.cancel()
        super.onCleared()
    }
}
