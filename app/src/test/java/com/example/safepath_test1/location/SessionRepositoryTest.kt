package com.example.safepath_test1.location

import com.example.safepath_test1.session.*
import com.example.safepath_test1.model.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SessionRepositoryTest {
    private fun request(pin: String = "1234") = SessionStartRequest("route", listOf("accepted"), pin, DetectionSettings())

    @Test fun invalidStartAndDuplicateAreRejected() = runBlocking {
        val repo = FakeSessionRepository()
        assertTrue(runCatching { repo.start(request().copy(guardianIds = emptyList())) }.isFailure)
        assertTrue(runCatching { repo.start(request("１２３４")) }.isFailure)
        repo.start(request())
        assertTrue(runCatching { repo.start(request()) }.isFailure)
    }
    @Test fun fiveFailuresKeepSessionAndCorrectPinEndsIt() = runBlocking {
        val repo = FakeSessionRepository()
        val session = repo.start(request())
        repeat(5) { assertTrue(runCatching { repo.end(session.id, "9999", true) }.isFailure) }
        assertEquals(5, repo.failedAttempts)
        assertEquals(SessionStatus.MOVING, repo.latest(session.id).status)
        assertEquals(SessionStatus.ARRIVED, repo.end(session.id, "1234", true).status)
        assertTrue(runCatching { repo.latest(session.id) }.isFailure)
    }
    @Test fun deadlineCannotBeExtendedAndLateOkayCannotClearDanger() = runBlocking {
        var clock = 1000L
        val repo = FakeSessionRepository { clock }
        val session = repo.start(request())
        repo.injectRisk(session.id, 2000)
        repo.injectRisk(session.id, 9000)
        assertEquals(2000L, repo.latest(session.id).responseDeadlineMillis)
        clock = 2000
        assertTrue(runCatching { repo.answerRisk(session.id, false) }.isFailure)
        assertEquals(SessionStatus.DANGER, repo.latest(session.id).status)
    }
    @Test fun earlyOkayAndHelpFollowDifferentTransitions() = runBlocking {
        val repo = FakeSessionRepository { 1000 }
        val id = repo.start(request()).id
        repo.injectRisk(id, 2000)
        assertEquals(SessionStatus.MOVING, repo.answerRisk(id, false).status)
        repo.injectRisk(id, 2000)
        assertEquals(SessionStatus.DANGER, repo.answerRisk(id, true).status)
        assertTrue(runCatching { repo.dismissSos(id, "0000") }.isFailure)
        assertEquals(SessionStatus.MOVING, repo.dismissSos(id, "1234").status)
    }
    @Test fun oldCoordinatesCannotOverwriteNewLocationAndArrivalDoesNotEndSession() = runBlocking {
        val repo = FakeSessionRepository()
        val id = repo.start(request()).id
        val point = SessionLocation(GeoPoint(35.0, 128.0), 2000, 5f)
        repo.uploadLocation(id, point)
        repo.uploadLocation(id, point.copy(measuredAt = 1000))
        assertEquals(point, repo.latest(id).location)
        assertEquals(SessionStatus.MOVING, repo.latest(id).status)
        assertTrue(runCatching { repo.uploadLocation(id, point.copy(point = GeoPoint(Double.NaN, 128.0))) }.isFailure)
    }
    @Test fun sosIsImmediateAndPasswordIsNotLogged() = runBlocking {
        val repo = FakeSessionRepository()
        val id = repo.start(request()).id
        assertEquals(SessionStatus.DANGER, repo.requestSos(id).status)
        assertFalse(request().toString().contains("1234"))
        repo.clear()
        assertTrue(runCatching { repo.latest(id) }.isFailure)
    }
}
