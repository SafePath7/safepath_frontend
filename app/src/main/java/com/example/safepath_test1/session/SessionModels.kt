package com.example.safepath_test1.session

import com.example.safepath_test1.model.GeoPoint

data class DetectionSettings(
    val voice: Boolean = false,
    val sound: Boolean = false,
    val motion: Boolean = false,
    val routeDeviation: Boolean = false,
    val responseTimeoutSeconds: Int? = null,
)

enum class SessionStatus { MOVING, SUSPECTED, DANGER, ARRIVED, CANCELLED }

/** Android domain model. Wire field names and TBD policies still require API agreement. */
data class SessionStartRequest(
    val routeId: String,
    val guardianIds: List<String>,
    val endPassword: String,
    val detectionSettings: DetectionSettings,
) {
    fun validationError(): String? = when {
        routeId.isBlank() -> "경로를 선택해 주세요."
        guardianIds.isEmpty() || guardianIds.any(String::isBlank) -> "수락된 보호자를 한 명 이상 선택해 주세요."
        guardianIds.distinct().size != guardianIds.size -> "보호자가 중복 선택됐어요."
        !endPassword.matches(Regex("[0-9]{4}")) -> "종료 비밀번호는 숫자 4자리입니다."
        else -> null
    }
    override fun toString() = "SessionStartRequest(routeId=$routeId, guardianCount=${guardianIds.size}, endPassword=[REDACTED])"
}

data class SessionLocation(val point: GeoPoint, val measuredAt: Long, val accuracyMeters: Float)
data class SessionSnapshot(
    val id: String,
    val status: SessionStatus,
    val location: SessionLocation? = null,
    val responseDeadlineMillis: Long? = null,
    val eventDescription: String? = null,
)

interface SessionRepository {
    suspend fun start(request: SessionStartRequest): SessionSnapshot
    suspend fun latest(sessionId: String): SessionSnapshot
    suspend fun uploadLocation(sessionId: String, location: SessionLocation)
    suspend fun end(sessionId: String, password: String, arrived: Boolean): SessionSnapshot
}

interface EmergencyRepository {
    suspend fun requestSos(sessionId: String): SessionSnapshot
    suspend fun dismissSos(sessionId: String, password: String): SessionSnapshot
    suspend fun answerRisk(sessionId: String, needsHelp: Boolean): SessionSnapshot
}
