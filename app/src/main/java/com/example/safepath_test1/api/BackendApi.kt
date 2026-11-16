package com.example.safepath_test1.api

import android.content.Context
import retrofit2.http.*

data class GuardianRegistration(val id: Long, val userId: Long, val userName: String, val guardianId: Long, val guardianName: String, val status: String, val canViewLocation: Boolean, val canViewStatus: Boolean, val canViewPersonalInfo: Boolean)
data class GuardianRequest(val guardianId: Long)
data class GuardianPermissions(val canViewLocation: Boolean, val canViewStatus: Boolean, val canViewPersonalInfo: Boolean)
data class SosResult(val successCount: Int, val failureCount: Int)
data class FcmTokenBody(val fcmToken: String)
data class BackendSession(val id: String, val startedAt: String)
data class RiskEventDetail(val id: Long, val userId: Long, val sessionId: String, val eventType: String, val description: String, val status: String, val detectedAt: String)

interface BackendApi {
    @GET("api/guardian-registrations") suspend fun guardians(@Header("Authorization") auth: String): List<GuardianRegistration>
    @POST("api/guardian-registrations") suspend fun requestGuardian(@Header("Authorization") auth: String, @Body body: GuardianRequest): GuardianRegistration
    @PATCH("api/guardian-registrations/{id}/{action}") suspend fun respond(@Header("Authorization") auth: String, @Path("id") id: Long, @Path("action") action: String): GuardianRegistration
    @PATCH("api/guardian-registrations/{id}/permissions") suspend fun permissions(@Header("Authorization") auth: String, @Path("id") id: Long, @Body body: GuardianPermissions): GuardianRegistration
    @PUT("api/notifications/fcm-token") suspend fun registerToken(@Header("Authorization") auth: String, @Body body: FcmTokenBody)
    @POST("api/notifications/sos") suspend fun sos(@Header("Authorization") auth: String): SosResult
    @POST("api/safe-return/sessions") suspend fun startSession(@Header("Authorization") auth: String): BackendSession
    @GET("api/safe-return/sessions/{id}/status") suspend fun sessionStatus(@Header("Authorization") auth: String, @Path("id") id: String): BackendSession
    @PATCH("api/safe-return/sessions/{id}/end") suspend fun endSession(@Header("Authorization") auth: String, @Path("id") id: String)
    @GET("api/risk-events") suspend fun riskEvents(@Header("Authorization") auth: String, @Query("userId") userId: Long? = null): List<RiskEventDetail>
}

class BackendRepository(context: Context) {
    private val backend = AuthenticatedBackend(context, readTimeoutSeconds = 15)
    private val api: BackendApi by lazy { backend.createApi(BackendApi::class.java) }
    suspend fun guardians() = backend.request { api.guardians(it) }
    suspend fun requestGuardian(id: Long) = backend.request { api.requestGuardian(it, GuardianRequest(id)) }
    suspend fun respond(id: Long, action: String) = backend.request { api.respond(it, id, action) }
    suspend fun permissions(id: Long, value: GuardianPermissions) = backend.request { api.permissions(it, id, value) }
    suspend fun registerToken(token: String) = backend.request { api.registerToken(it, FcmTokenBody(token)) }
    suspend fun sos() = backend.request { api.sos(it) }
    suspend fun startSession() = backend.request { api.startSession(it) }
    suspend fun sessionStatus(id: String) = backend.request { api.sessionStatus(it, id) }
    suspend fun endSession(id: String) = backend.request { api.endSession(it, id) }
    suspend fun riskEvents(userId: Long? = null) = backend.request { api.riskEvents(it, userId) }
}
