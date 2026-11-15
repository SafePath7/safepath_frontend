package com.example.safepath_test1.api

import android.content.Context
import com.example.safepath_test1.BuildConfig
import com.example.safepath_test1.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

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
    private val firebase = FirebaseAuth.getInstance()
    private val api = Retrofit.Builder().baseUrl(checkedAccountBaseUrl(context.applicationContext.getString(R.string.safepath_api_base_url), BuildConfig.DEBUG))
        .client(OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(BackendApi::class.java)
    private suspend fun <T> request(block: suspend (String) -> T): T {
        val user = firebase.currentUser ?: error("로그인이 필요합니다.")
        val token = user.getIdToken(false).await().token ?: error("인증 토큰이 없습니다.")
        check(firebase.currentUser?.uid == user.uid) { "로그인 계정이 변경되었습니다." }
        val result = try { block("Bearer $token") } catch (e: HttpException) {
            if (e.code() == 401 && firebase.currentUser?.uid == user.uid) firebase.signOut()
            throw e
        }
        check(firebase.currentUser?.uid == user.uid) { "로그인 계정이 변경되었습니다." }
        return result
    }
    suspend fun guardians() = request { api.guardians(it) }
    suspend fun requestGuardian(id: Long) = request { api.requestGuardian(it, GuardianRequest(id)) }
    suspend fun respond(id: Long, action: String) = request { api.respond(it, id, action) }
    suspend fun permissions(id: Long, value: GuardianPermissions) = request { api.permissions(it, id, value) }
    suspend fun registerToken(token: String) = request { api.registerToken(it, FcmTokenBody(token)) }
    suspend fun sos() = request { api.sos(it) }
    suspend fun startSession() = request { api.startSession(it) }
    suspend fun sessionStatus(id: String) = request { api.sessionStatus(it, id) }
    suspend fun endSession(id: String) = request { api.endSession(it, id) }
    suspend fun riskEvents(userId: Long? = null) = request { api.riskEvents(it, userId) }
}
