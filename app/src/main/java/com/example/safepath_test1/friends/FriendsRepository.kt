package com.example.safepath_test1.friends

import android.content.Context
import com.example.safepath_test1.R
import com.example.safepath_test1.ui.guardian.FriendshipRequest
import com.example.safepath_test1.ui.guardian.GuardianItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class FriendsSnapshot(
    val friends: List<GuardianItem>,
    val received: List<FriendshipRequest>,
    val sent: List<FriendshipRequest>,
)

data class CreatedFriendInvite(
    val id: String,
    val url: String,
    val deliveryStatus: String,
    val senderName: String,
)

interface FriendsDataSource {
    val isConfigured: Boolean
    suspend fun load(): FriendsSnapshot
    suspend fun create(submissionId: String): CreatedFriendInvite
    suspend fun respond(id: String, action: String)
}

class FriendsRepository(private val context: Context) : FriendsDataSource {
    private val baseUrl = context.getString(R.string.friends_api_base_url).trimEnd('/')
    override val isConfigured: Boolean
        get() = baseUrl.isNotBlank()

    override suspend fun load(): FriendsSnapshot {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("로그인이 필요합니다.")
        val json = request("GET", "/friends")
        val friends =
            json.getJSONArray("friends").objects().map {
                GuardianItem(it.getString("id"), it.getString("name"), "친구", it.optString("phone"))
            }
        fun requests(key: String) =
            json.getJSONArray(key).objects().map {
                FriendshipRequest(
                    it.getString("id"),
                    it.getString("name"),
                    it.optString("deliveryStatus", "submitted"),
                )
            }
        check(FirebaseAuth.getInstance().currentUser?.uid == uid) { "계정이 변경되었습니다." }
        cacheServerFriends(context, uid, friends)
        return FriendsSnapshot(friends, requests("received"), requests("sent"))
    }

    override suspend fun create(submissionId: String): CreatedFriendInvite {
        val user = FirebaseAuth.getInstance().currentUser ?: error("로그인이 필요합니다.")
        val profile = context.getSharedPreferences("safe_path_profile", Context.MODE_PRIVATE)
        check(profile.getString("onboarded_uid", null) == user.uid) {
            "내 정보에서 이름과 전화번호를 먼저 등록해 주세요."
        }
        val senderName = profile.getString("user_name", "").orEmpty().trim()
        val senderPhone = normalizeFriendPhone(profile.getString("user_phone", "").orEmpty())
        check(senderName.isNotEmpty() && senderPhone != null) { "내 정보의 이름과 전화번호를 확인해 주세요." }
        val json =
            request(
                "POST",
                "/requests/share",
                JSONObject()
                    .put("senderName", senderName)
                    .put("senderPhone", senderPhone)
                    .put("submissionId", submissionId),
            )
        val link = json.getString("url")
        check(URI(link).scheme == "https" && URI(link).host != null) { "올바른 요청 링크를 만들지 못했습니다." }
        check(json.getString("deliveryChannel") == "kakao_share") { "카카오톡 공유 요청을 확인할 수 없습니다." }
        val deliveryStatus = json.getString("deliveryStatus")
        return CreatedFriendInvite(
            json.getString("id"),
            link,
            deliveryStatus,
            json.getString("senderName"),
        )
    }

    override suspend fun respond(id: String, action: String) {
        require(action in listOf("accept", "reject", "cancel", "disconnect"))
        require(Regex("[a-zA-Z0-9_-]{1,128}").matches(id))
        request("POST", "/requests/$id/$action", JSONObject())
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
    ): JSONObject {
        check(isConfigured) { "친구 요청 서버가 아직 연결되지 않았습니다." }
        check(URI(baseUrl).scheme == "https") { "친구 요청 서버 주소는 HTTPS여야 합니다." }
        val user = FirebaseAuth.getInstance().currentUser ?: error("로그인이 필요합니다.")
        val token = user.idToken()
        return withContext(Dispatchers.IO) {
            val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                if (status == 401) {
                    // FD-002: an invalid session returns to login. Do not sign out a newer account.
                    if (FirebaseAuth.getInstance().currentUser?.uid == user.uid) FirebaseAuth.getInstance().signOut()
                    error("인증이 만료됐습니다. 다시 로그인해 주세요.")
                }
                val stream =
                    if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val result = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                if (status !in 200..299)
                    error(result.optString("error", "친구 요청 처리에 실패했습니다. ($status)"))
                check(FirebaseAuth.getInstance().currentUser?.uid == user.uid) {
                    "계정이 변경되었습니다. 다시 시도해 주세요."
                }
                result
            } finally {
                connection.disconnect()
            }
        }
    }
}

private suspend fun FirebaseUser.idToken(): String = suspendCancellableCoroutine { continuation ->
    getIdToken(false)
        .addOnSuccessListener { result ->
            if (continuation.isActive) {
                val token = result.token
                if (token != null) continuation.resume(token)
                else continuation.resumeWithException(IllegalStateException("로그인 인증을 확인할 수 없습니다."))
            }
        }
        .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
