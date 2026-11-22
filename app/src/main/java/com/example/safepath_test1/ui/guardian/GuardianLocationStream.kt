package com.example.safepath_test1.ui.guardian

import android.content.Context
import com.example.safepath_test1.BuildConfig
import com.example.safepath_test1.R
import com.example.safepath_test1.api.checkedAccountBaseUrl
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class GuardianLocation(
    val latitude: Double,
    val longitude: Double,
    val capturedAt: Long,
    val status: String,
)

internal fun guardianLocations(context: Context, uid: String, channel: String) = callbackFlow {
    require(channel.matches(Regex("[A-Za-z0-9_-]{1,200}")))
    val user = FirebaseAuth.getInstance().currentUser
    check(user?.uid == uid)
    val token = user.getIdToken(false).await().token ?: error("로그인 인증이 필요합니다.")
    val base = checkedAccountBaseUrl(context.getString(R.string.safepath_api_base_url), BuildConfig.DEBUG).trimEnd('/')
    val endpoint = when {
        base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
        else -> "ws://" + base.removePrefix("http://")
    }
    val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    val socket = client.newWebSocket(Request.Builder().url("$endpoint/ws").build(), object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer $token\n\n\u0000")
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            for (frame in text.split('\u0000')) {
                when {
                    frame.trimStart().startsWith("CONNECTED\n") ->
                        webSocket.send("SUBSCRIBE\nid:guardian\ndestination:/topic/connections/$channel/locations\n\n\u0000")
                    frame.trimStart().startsWith("ERROR\n") -> close(IllegalStateException("위치 구독이 거부되었습니다."))
                    frame.trimStart().startsWith("MESSAGE\n") -> {
                        try {
                            val data = JSONObject(frame.substringAfter("\n\n"))
                            val latitude = data.getDouble("latitude")
                            val longitude = data.getDouble("longitude")
                            val timestamp = data.getString("capturedAt")
                            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }.parse(timestamp.take(19))?.time ?: return
                            val fraction = timestamp.substringAfter('.', "").takeWhile(Char::isDigit).take(3).padEnd(3, '0').toLongOrNull() ?: 0
                            if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                                trySend(GuardianLocation(latitude, longitude, parsed + fraction, data.optString("safetyStatus", "UNKNOWN")))
                            }
                        } catch (_: Exception) {
                            // Invalid frames are ignored.
                        }
                    }
                }
            }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close(t) }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
    })
    awaitClose {
        socket.close(1000, "leaving")
        client.dispatcher().executorService().shutdown()
        client.connectionPool().evictAll()
    }
}
