package com.example.safepath_test1.api

import android.content.Context
import okhttp3.HttpUrl
import retrofit2.HttpException
import retrofit2.http.*

data class AccountLogin(val userId: Long?, val status: String)
data class AccountRegistration(val userId: Long)
data class AccountProfile(val userId: Long, val name: String, val email: String?, val phoneNumber: String?)
data class RegistrationBody(val name: String)
data class ProfileBody(val name: String)
data class PhoneBody(val phoneNumber: String)

interface AccountApi {
    @POST("api/auth/login") suspend fun login(@Header("Authorization") bearer: String): AccountLogin
    @POST("api/users/me/registration") suspend fun register(@Header("Authorization") bearer: String, @Body body: RegistrationBody): AccountRegistration
    @GET("api/users/me") suspend fun profile(@Header("Authorization") bearer: String): AccountProfile
    @PATCH("api/users/me") suspend fun update(@Header("Authorization") bearer: String, @Body body: ProfileBody)
    @PATCH("api/users/me/phone") suspend fun changePhone(@Header("Authorization") bearer: String, @Body body: PhoneBody)
    @DELETE("api/users/me") suspend fun withdraw(@Header("Authorization") bearer: String)
}

internal fun checkedAccountBaseUrl(value: String, debug: Boolean): String {
    val url = HttpUrl.parse(value.trim().trimEnd('/') + "/") ?: throw IllegalStateException("서버 주소를 확인해 주세요.")
    require(url.username().isEmpty() && url.password().isEmpty() && url.query() == null && url.fragment() == null && url.encodedPath() == "/") { "서버 주소는 기본 주소만 입력해 주세요." }
    val parts = url.host().split('.').map { it.toIntOrNull() }
    val privateIpv4 = parts.size == 4 && parts.all { it != null && it in 0..255 } &&
        (parts[0] == 10 || parts[0] == 127 || parts[0] == 192 && parts[1] == 168 || parts[0] == 172 && parts[1]!! in 16..31)
    require(url.scheme() == "https" || debug && url.scheme() == "http" && (privateIpv4 || url.host() == "localhost")) { "운영 서버는 HTTPS가 필요합니다." }
    return url.toString()
}

class AccountRepository(context: Context) {
    private val backend = AuthenticatedBackend(context, readTimeoutSeconds = 10)
    private val api: AccountApi by lazy { backend.createApi(AccountApi::class.java) }
    suspend fun login(): AccountLogin = backend.request { api.login(it) }
    suspend fun register(name: String): AccountRegistration = backend.request(true) { api.register(it, RegistrationBody(name.trim())) }
    suspend fun profile(): AccountProfile = backend.request { api.profile(it) }
    suspend fun update(name: String) = backend.request { api.update(it, ProfileBody(name.trim())) }
    suspend fun changePhone(verifiedPhone: String) = backend.request(true) { api.changePhone(it, PhoneBody(verifiedPhone)) }
    suspend fun withdraw() = backend.request { api.withdraw(it) }
}

fun accountErrorMessage(error: Throwable): String = when ((error as? HttpException)?.code()) {
    400 -> "입력한 정보를 확인해 주세요."
    401 -> "로그인이 만료되었습니다. 다시 로그인해 주세요."
    404 -> "등록된 회원정보가 없습니다."
    409 -> "이미 사용 중인 정보이거나 연관 정보가 있어 처리할 수 없습니다."
    422 -> "Google 계정의 이메일과 인증된 전화번호 연결 상태를 확인해 주세요."
    503 -> "서버에서 이 기능을 아직 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."
    else -> when (error) {
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "서버 설정을 확인해 주세요."
        else -> "서버에 연결하지 못했습니다. 네트워크와 서버 주소를 확인해 주세요."
    }
}
