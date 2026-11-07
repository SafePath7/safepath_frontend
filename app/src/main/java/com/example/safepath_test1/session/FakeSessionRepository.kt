package com.example.safepath_test1.session

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/** Explicit local simulation only. No network, push, AI inference, or production authentication. */
class FakeSessionRepository(private val now: () -> Long = System::currentTimeMillis) : SessionRepository, EmergencyRepository {
    private var current: SessionSnapshot? = null
    private var salt = ByteArray(16)
    private var passwordHash = ByteArray(0)
    var failedAttempts: Int = 0
        private set

    override suspend fun start(request: SessionStartRequest): SessionSnapshot {
        require(current == null) { "진행 중인 세션이 있습니다." }
        require(request.validationError() == null) { request.validationError().orEmpty() }
        SecureRandom().nextBytes(salt)
        passwordHash = hash(request.endPassword)
        failedAttempts = 0
        return SessionSnapshot("demo-${UUID.randomUUID()}", SessionStatus.MOVING).also { current = it }
    }

    override suspend fun latest(sessionId: String): SessionSnapshot {
        val value = requireSession(sessionId)
        if (value.status == SessionStatus.SUSPECTED && now() >= requireNotNull(value.responseDeadlineMillis)) {
            current = value.copy(status = SessionStatus.DANGER, responseDeadlineMillis = null, eventDescription = "확인 질문 무응답 (모의)")
        }
        return requireNotNull(current)
    }

    override suspend fun uploadLocation(sessionId: String, location: SessionLocation) {
        val value = requireSession(sessionId)
        require(location.point.latitude.isFinite() && location.point.latitude in -90.0..90.0)
        require(location.point.longitude.isFinite() && location.point.longitude in -180.0..180.0)
        require(location.measuredAt > 0 && location.accuracyMeters.isFinite() && location.accuracyMeters >= 0)
        if (location.measuredAt >= (value.location?.measuredAt ?: 0)) current = value.copy(location = location)
    }

    override suspend fun end(sessionId: String, password: String, arrived: Boolean): SessionSnapshot {
        val value = requireSession(sessionId)
        authenticate(password)
        val ended = value.copy(status = if (arrived) SessionStatus.ARRIVED else SessionStatus.CANCELLED, responseDeadlineMillis = null)
        clear()
        return ended
    }

    override suspend fun requestSos(sessionId: String): SessionSnapshot = requireSession(sessionId)
        .copy(status = SessionStatus.DANGER, responseDeadlineMillis = null, eventDescription = "SOS (모의)")
        .also { current = it }

    override suspend fun dismissSos(sessionId: String, password: String): SessionSnapshot {
        val value = requireSession(sessionId)
        require(value.status == SessionStatus.DANGER)
        authenticate(password)
        return value.copy(status = SessionStatus.MOVING, eventDescription = null).also { current = it }
    }

    fun injectRisk(sessionId: String, deadlineMillis: Long) {
        val value = requireSession(sessionId)
        if (value.status != SessionStatus.MOVING) return
        require(deadlineMillis > now())
        current = value.copy(status = SessionStatus.SUSPECTED, responseDeadlineMillis = deadlineMillis, eventDescription = "모의 위험 감지")
    }

    override suspend fun answerRisk(sessionId: String, needsHelp: Boolean): SessionSnapshot {
        val value = latest(sessionId)
        require(value.status == SessionStatus.SUSPECTED) { "응답 시간이 끝났거나 처리된 질문입니다." }
        return value.copy(status = if (needsHelp) SessionStatus.DANGER else SessionStatus.MOVING,
            responseDeadlineMillis = null, eventDescription = if (needsHelp) "도움 요청 (모의)" else null).also { current = it }
    }

    private fun requireSession(id: String): SessionSnapshot = requireNotNull(current?.takeIf { it.id == id }) { "활성 세션이 없습니다." }
    private fun hash(pin: String) = MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray())
    private fun authenticate(pin: String) {
        if (!pin.matches(Regex("[0-9]{4}")) || !MessageDigest.isEqual(hash(pin), passwordHash)) {
            failedAttempts++
            error(if (failedAttempts >= 5) "5회 이상 불일치했습니다. 세션은 유지됩니다. 보호자 경고는 모의 상태이며 실제 전송되지 않습니다." else "비밀번호가 다릅니다. ($failedAttempts/5)")
        }
        failedAttempts = 0
    }
    fun clear() {
        current = null
        passwordHash.fill(0)
        passwordHash = ByteArray(0)
        salt.fill(0)
        failedAttempts = 0
    }
}
