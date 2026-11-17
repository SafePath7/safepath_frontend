package com.example.safepath_test1.api

import android.content.Context
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.*

data class GuardianRegistration(val id: Long, val userId: Long, val userName: String, val guardianId: Long, val guardianName: String, val status: String, val canViewLocation: Boolean, val canViewStatus: Boolean, val canViewPersonalInfo: Boolean)
data class GuardianRequest(val guardianId: Long)
data class GuardianPermissions(val canViewLocation: Boolean, val canViewStatus: Boolean, val canViewPersonalInfo: Boolean)
data class SosResult(val successCount: Int, val failureCount: Int)
data class FcmTokenBody(val fcmToken: String)
data class BackendSession(val id: String, val startedAt: String)
data class RiskEventDetail(val id: Long, val userId: Long, val sessionId: String, val eventType: String, val description: String, val status: String, val detectedAt: String)
data class GuardianPermissionResult(val allowed: Boolean)
data class RiskEventRequest(val sessionId: String, val eventType: String, val description: String, val detectedAt: String)
data class RiskEventStatusRequest(val status: String)
data class RiskEventResult(val id: Long, val sessionId: String, val eventType: String, val description: String, val status: String, val detectedAt: String)
data class RiskMedia(val id: Long, val eventId: Long, val mediaType: String, val fileName: String?, val uploadedAt: String)

interface BackendApi {
    @GET("api/guardian-registrations") suspend fun guardians(@Header("Authorization") auth: String): List<GuardianRegistration>
    @POST("api/guardian-registrations") suspend fun requestGuardian(@Header("Authorization") auth: String, @Body body: GuardianRequest): GuardianRegistration
    @PATCH("api/guardian-registrations/{id}/{action}") suspend fun respond(@Header("Authorization") auth: String, @Path("id") id: Long, @Path("action") action: String): GuardianRegistration
    @PATCH("api/guardian-registrations/{id}/permissions") suspend fun permissions(@Header("Authorization") auth: String, @Path("id") id: Long, @Body body: GuardianPermissions): GuardianRegistration
    @GET("api/guardian-registrations/{id}/permissions/check") suspend fun checkPermission(@Header("Authorization") auth: String, @Path("id") id: Long, @Query("permissionType") permissionType: String): GuardianPermissionResult
    @PUT("api/notifications/fcm-token") suspend fun registerToken(@Header("Authorization") auth: String, @Body body: FcmTokenBody)
    @POST("api/notifications/sos") suspend fun sos(@Header("Authorization") auth: String): SosResult
    @POST("api/safe-return/sessions") suspend fun startSession(@Header("Authorization") auth: String): BackendSession
    @GET("api/safe-return/sessions/{id}/status") suspend fun sessionStatus(@Header("Authorization") auth: String, @Path("id") id: String): BackendSession
    @PATCH("api/safe-return/sessions/{id}/end") suspend fun endSession(@Header("Authorization") auth: String, @Path("id") id: String)
    @GET("api/risk-events") suspend fun riskEvents(@Header("Authorization") auth: String, @Query("userId") userId: Long? = null): List<RiskEventDetail>
    @GET("api/risk-events/{id}") suspend fun riskEvent(@Header("Authorization") auth: String, @Path("id") id: Long): RiskEventDetail
    @POST("api/risk-events") suspend fun createRiskEvent(@Header("Authorization") auth: String, @Body body: RiskEventRequest): RiskEventResult
    @PATCH("api/risk-events/{id}/status") suspend fun updateRiskEventStatus(@Header("Authorization") auth: String, @Path("id") id: Long, @Body body: RiskEventStatusRequest): RiskEventResult
    @GET("api/risk-events/{id}/medias") suspend fun riskMedia(@Header("Authorization") auth: String, @Path("id") id: Long): List<RiskMedia>
    @Multipart @POST("api/risk-events/{id}/medias") suspend fun uploadRiskMedia(@Header("Authorization") auth: String, @Path("id") id: Long, @Part file: MultipartBody.Part, @Part("mediaType") mediaType: RequestBody): RiskMedia
    @Streaming @GET("api/risk-events/{id}/medias") suspend fun downloadRiskMedia(@Header("Authorization") auth: String, @Path("id") id: Long, @Query("mediaId") mediaId: Long): ResponseBody
    @DELETE("api/risk-events/{id}/medias/{mediaId}") suspend fun deleteRiskMedia(@Header("Authorization") auth: String, @Path("id") id: Long, @Path("mediaId") mediaId: Long)
}

class BackendRepository(context: Context) {
    private val backend = AuthenticatedBackend(context, readTimeoutSeconds = 15)
    private val api: BackendApi by lazy { backend.createApi(BackendApi::class.java) }
    suspend fun guardians() = backend.request { api.guardians(it) }
    suspend fun requestGuardian(id: Long) = backend.request { api.requestGuardian(it, GuardianRequest(id)) }
    suspend fun respond(id: Long, action: String) = backend.request { api.respond(it, id, action) }
    suspend fun permissions(id: Long, value: GuardianPermissions) = backend.request { api.permissions(it, id, value) }
    suspend fun checkPermission(id: Long, type: String) = backend.request { api.checkPermission(it, id, type) }
    suspend fun registerToken(token: String) = backend.request { api.registerToken(it, FcmTokenBody(token)) }
    suspend fun sos() = backend.request { api.sos(it) }
    suspend fun startSession() = backend.request { api.startSession(it) }
    suspend fun sessionStatus(id: String) = backend.request { api.sessionStatus(it, id) }
    suspend fun endSession(id: String) = backend.request { api.endSession(it, id) }
    suspend fun riskEvents(userId: Long? = null) = backend.request { api.riskEvents(it, userId) }
    suspend fun riskEvent(id: Long) = backend.request { api.riskEvent(it, id) }
    suspend fun createRiskEvent(value: RiskEventRequest) = backend.request { api.createRiskEvent(it, value) }
    suspend fun updateRiskEventStatus(id: Long, status: String) = backend.request { api.updateRiskEventStatus(it, id, RiskEventStatusRequest(status)) }
    suspend fun riskMedia(id: Long) = backend.request { api.riskMedia(it, id) }
    suspend fun uploadRiskMedia(id: Long, file: MultipartBody.Part, mediaType: RequestBody) = backend.request { api.uploadRiskMedia(it, id, file, mediaType) }
    suspend fun downloadRiskMedia(id: Long, mediaId: Long) = backend.request { api.downloadRiskMedia(it, id, mediaId) }
    suspend fun deleteRiskMedia(id: Long, mediaId: Long) = backend.request { api.deleteRiskMedia(it, id, mediaId) }
}
