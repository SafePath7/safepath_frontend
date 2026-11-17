package com.example.safepath_test1.ui.guardian

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.safepath_test1.api.AccountRepository
import com.example.safepath_test1.api.BackendRepository
import com.example.safepath_test1.api.GuardianPermissions
import com.example.safepath_test1.api.GuardianRegistration
import com.example.safepath_test1.api.RiskEventDetail
import com.example.safepath_test1.api.RiskMedia
import com.example.safepath_test1.api.RiskEventRequest
import com.example.safepath_test1.api.accountErrorMessage
import com.example.safepath_test1.friends.cacheServerFriends
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    val notice: String? = null,
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
        mutableState.update { it.copy(busy = true, error = null, notice = null) }
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
    fun loadEvents(userId: Long? = null, registrationId: Long? = null) = perform { uid ->
        if (registrationId != null) {
            check(backend.checkPermission(registrationId, "STATUS").allowed) { "위험 기록을 볼 권한이 없습니다." }
        }
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
    fun createEvent(description: String) = perform { uid ->
        val sessionId = mutableState.value.sessionId ?: error("진행 중인 서버 세션이 없습니다.")
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        backend.createRiskEvent(RiskEventRequest(sessionId, "MANUAL", description.trim(), timestamp))
        check(auth.currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
        mutableState.update { it.copy(notice = "위험 기록을 저장했습니다.") }
    }
    fun resolveEvent(id: Long) = perform { uid ->
        val result = backend.updateRiskEventStatus(id, "RESOLVED")
        check(auth.currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
        mutableState.update { current ->
            current.copy(
                eventDetail = current.eventDetail?.let { if (it.id == id) it.copy(status = result.status) else it },
                events = current.events?.map { if (it.id == id) it.copy(status = result.status) else it },
                notice = "위험 기록을 해결 완료로 변경했습니다.",
            )
        }
    }
    fun downloadMedia(eventId: Long, mediaId: Long, destination: Uri) = perform { uid ->
        val body = backend.downloadRiskMedia(eventId, mediaId)
        body.use { response ->
            withContext(Dispatchers.IO) {
                check(auth.currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
                val output = getApplication<Application>().contentResolver.openOutputStream(destination)
                    ?: error("저장할 파일을 열 수 없습니다.")
                output.use { response.byteStream().copyTo(it) }
            }
        }
        mutableState.update { it.copy(notice = "첨부파일을 저장했습니다.") }
    }
    override fun onCleared() {
        auth.removeAuthStateListener(listener)
        job?.cancel()
        super.onCleared()
    }
}
