package com.example.safepath_test1.location

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.safepath_test1.R
import com.example.safepath_test1.model.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.Proxy
import java.util.concurrent.Executors
import org.json.JSONObject

/** 디버그 빌드의 명시적 LAN 시험용. 운영 세션이나 긴급 신고 기능이 아니다. */
object TestJourneyClient {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var active = false
        private set
    @Volatile var lastError = ""
        private set

    private fun roadAddress(context: Context, latitude: Double, longitude: Double): String? {
        val key = context.getString(R.string.kakao_rest_api_key)
        if (key.isBlank() || !latitude.isFinite() || !longitude.isFinite()) return null
        return try {
            val connection = URL("https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$longitude&y=$latitude").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.setRequestProperty("Authorization", "KakaoAK $key")
                if (connection.responseCode != 200) null
                else JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                    .optJSONArray("documents")?.optJSONObject(0)?.optJSONObject("road_address")
                    ?.optString("address_name")?.trim()?.takeIf { it.isNotEmpty() }
            } finally { connection.disconnect() }
        } catch (_: Exception) { null }
    }

    private fun resolveSession(context: Context, metadata: String): JSONObject {
        val session = JSONObject(metadata)
        for (field in listOf("origin", "destination")) {
            val lat = session.optDouble("${field}Lat", Double.NaN)
            val lng = session.optDouble("${field}Lng", Double.NaN)
            session.put(field, roadAddress(context, lat, lng) ?: "\uB3C4\uB85C\uBA85 \uC8FC\uC18C \uD655\uC778 \uBD88\uAC00")
            session.remove("${field}Lat")
            session.remove("${field}Lng")
        }
        return session
    }

    private fun send(context: Context, action: String, location: GeoPoint?, route: String? = null, metadata: String? = null): Boolean {
        val base = context.getString(R.string.safepath_test_url).trimEnd('/')
        val token = context.getString(R.string.safepath_test_token)
        if (base.isBlank() || token.isBlank()) {
            lastError = "테스트 서버 주소 또는 토큰이 비어 있습니다."
            return false
        }
        var phase = "연결 준비"
        return try {
            // 로컬 Wi-Fi 테스트 서버는 기기의 HTTP 프록시를 경유하지 않는다.
            val connection = URL("$base/api/test/$action").openConnection(Proxy.NO_PROXY) as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                val body = JSONObject()
                if (location != null) {
                    body.put("latitude", location.latitude)
                    body.put("longitude", location.longitude)
                }
                phase = "서버 연결 및 요청 전송"
                if (route != null) body.put("route", JSONObject(route))
                if (metadata != null) body.put("session", resolveSession(context, metadata))
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                phase = "서버 응답 대기"
                val status = connection.responseCode
                lastError = when (status) {
                    403 -> "테스트 토큰이 서버와 다릅니다. 앱을 다시 빌드해 주세요."
                    409 -> "서버에 이미 시작된 테스트가 있습니다."
                    410 -> "테스트가 종료되었습니다. 서버를 다시 시작해 주세요."
                    in 200..299 -> ""
                    else -> "서버가 오류를 반환했습니다 (HTTP $status)."
                }
                Log.i("TestJourney", "action=$action HTTP=$status")
                status in 200..299
            } finally { connection.disconnect() }
        } catch (error: Exception) {
            lastError = "네트워크 오류 ($phase): ${error.javaClass.simpleName}"
            Log.w("TestJourney", "action=$action phase=$phase error=${error.javaClass.simpleName}")
            false
        }
    }

    fun start(context: Context, location: GeoPoint?, route: String?, metadata: String, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val success = send(app, "start", location, route, metadata)
            active = success
            main.post { done(success) }
        }
    }

    fun update(context: Context, location: GeoPoint) {
        if (!active) return
        val app = context.applicationContext
        worker.execute { if (active) send(app, "location", location) }
    }

    fun end(context: Context, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val success = send(app, "end", null)
            if (success) active = false
            main.post { done(success) }
        }
    }
}
