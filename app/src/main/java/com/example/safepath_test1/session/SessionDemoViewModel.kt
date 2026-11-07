package com.example.safepath_test1.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safepath_test1.model.GeoPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionDemoState(
    val session: SessionSnapshot? = null,
    val message: String? = null,
    val offline: Boolean = false,
    val pending: List<SessionLocation> = emptyList(),
    val history: List<String> = emptyList(),
    val now: Long = System.currentTimeMillis(),
)

class SessionDemoViewModel : ViewModel() {
    private val repository = FakeSessionRepository()
    private val mutable = MutableStateFlow(SessionDemoState())
    val state = mutable.asStateFlow()
    private var step = 0

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val old = mutable.value
                val session = old.session?.let { repository.latest(it.id) }
                mutable.value = old.copy(session = session, now = System.currentTimeMillis(),
                    history = if (old.session?.status != session?.status && session != null) old.history + "무응답 → 위험상황 (모의)" else old.history)
            }
        }
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { mutable.value = mutable.value.copy(message = null); block() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(message = e.message ?: "처리하지 못했습니다.") }
        }
    }
    fun start(route: String, guardians: List<String>, pin: String, settings: DetectionSettings) = action {
        val session = repository.start(SessionStartRequest(route, guardians, pin, settings))
        step = 0
        mutable.value = SessionDemoState(session = session, history = listOf("귀가 시작 (모의)"))
    }
    fun move() = action {
        val session = mutable.value.session ?: return@action
        step++
        val location = SessionLocation(GeoPoint(35.8530 + step.coerceAtMost(20) * .0001, 128.4800 + step.coerceAtMost(20) * .0001), System.currentTimeMillis(), 5f)
        if (mutable.value.offline) mutable.value = mutable.value.copy(pending = (mutable.value.pending + location).takeLast(100), message = "모의 오프라인: 위치를 메모리에 임시 보관합니다.")
        else { repository.uploadLocation(session.id, location); mutable.value = mutable.value.copy(session = repository.latest(session.id)) }
    }
    fun toggleOffline() = action {
        val value = mutable.value
        if (value.offline && value.session != null) {
            value.pending.sortedBy { it.measuredAt }.forEach { repository.uploadLocation(value.session.id, it) }
            mutable.value = value.copy(offline = false, pending = emptyList(), session = repository.latest(value.session.id), message = "모의 연결 복구: 임시 위치 반영 완료")
        } else mutable.value = value.copy(offline = !value.offline)
    }
    fun sos() = action {
        check(!mutable.value.offline) { "모의 네트워크 오류: SOS가 전달되지 않았습니다. 연결 복구 후 재시도하세요." }
        val id = mutable.value.session?.id ?: error("먼저 모의 귀가를 시작해 주세요.")
        mutable.value = mutable.value.copy(session = repository.requestSos(id), history = mutable.value.history + "SOS → 위험상황 (모의)")
    }
    fun suspect() = action {
        val id = mutable.value.session?.id ?: return@action
        repository.injectRisk(id, System.currentTimeMillis() + 30_000)
        mutable.value = mutable.value.copy(session = repository.latest(id), history = mutable.value.history + "위험 확인 질문 (모의 30초)")
    }
    fun answer(help: Boolean) = action {
        val id = mutable.value.session?.id ?: return@action
        mutable.value = mutable.value.copy(session = repository.answerRisk(id, help), history = mutable.value.history + if (help) "도움 요청 (모의)" else "괜찮아요 → 이동중")
    }
    fun authenticate(pin: String, dismiss: Boolean) = action {
        val id = mutable.value.session?.id ?: return@action
        if (dismiss) mutable.value = mutable.value.copy(session = repository.dismissSos(id, pin), history = listOf("모의 위험 해제 · 위험 기록 폐기"))
        else { repository.end(id, pin, true); mutable.value = SessionDemoState(message = "모의 귀가 종료 · 위치와 일반 세션 기록 폐기") }
    }
    override fun onCleared() { repository.clear() }
}
