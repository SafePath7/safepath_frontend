package com.example.safepath_test1.wear

import android.content.Context
import android.util.Log
import com.example.safepath_test1.R
import com.example.safepath_test1.model.GeoPoint
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 워치 미니맵의 배경이 될 Mapbox 지도 이미지를 폰이 대신 받아서 Data Layer로 보낸다.
 * 워치는 인터넷을 쓰지 않고, 이 이미지 위에 경로와 현재 위치만 직접 그린다.
 *
 * 이미지는 사용자가 있는 곳을 중심으로 한 약 1km 폭이다. 워치는 그중 핀치 확대 정도(지름 250m~800m)에 맞는
 * 부분만 잘라서 현재 위치가 항상 가운데 오도록 보여 준다. 그래서 걷는 동안에는 이미지를 새로 받지 않고 워치가
 * 창만 옮기고, 처음 이미지의 중심에서 [REFETCH_DISTANCE_M] 이상 멀어졌을 때만 지금 위치 기준으로 다시 받는다.
 * 요청이 실패하면 1분 동안은 다시 요청하지 않아, 토큰 오류 같은 문제로 요청이 쏟아지지 않게 한다.
 * 워치의 WearApp/MapBackground가 같은 경로(PATH_MAP)와 값(imagePx 등)을 읽는다 - 같이 바꿔야 한다.
 */
object WearMapImageSender {
    private const val TAG = "WearMapImageSender"
    const val PATH_MAP = "/safepath/map"

    /** 어두운 배경 지도. 라벨이 거슬리면 Mapbox Studio에서 만든 스타일 ID로 바꾸면 된다. */
    private const val MAP_STYLE = "mapbox/dark-v11"

    /** 이미지 한 장의 크기(px). Mapbox 정적 지도의 최대 크기는 1280이다. */
    private const val IMAGE_PX = 1024

    /** 지도 1픽셀의 길이(m). 1024px이면 약 1km 폭을 덮는다. 작을수록 확대해도 선명하지만 이미지를 더 자주 받는다. */
    private const val METERS_PER_PIXEL = 1.0

    /** 워치가 가장 넓게(지름 800m) 볼 때도 이미지 안에 남도록(여유 약 110m), 중심에서 이만큼 멀어지면 다시 받는다. */
    private const val REFETCH_DISTANCE_M = 100.0
    private const val MAX_IMAGE_BYTES = 1500 * 1024
    private const val RETRY_AFTER_FAILURE_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var sentCenter: GeoPoint? = null
    private var inFlight = false
    private var failedAt = 0L

    /** 걷는 동안 5초마다 불려도 괜찮다. 이미지가 없거나 중심에서 충분히 멀어졌을 때만 새로 받는다. */
    fun sendIfNeeded(context: Context, center: GeoPoint) {
        synchronized(lock) {
            if (inFlight) return
            val sent = sentCenter
            if (sent != null && haversineMeters(sent, center) < REFETCH_DISTANCE_M) return
            if (System.currentTimeMillis() - failedAt < RETRY_AFTER_FAILURE_MS) return
            inFlight = true
        }

        val appContext = context.applicationContext
        scope.launch {
            val succeeded =
                try {
                    fetchAndSend(appContext, center)
                } catch (e: Exception) {
                    Log.e(TAG, "Map image fetch/send failed", e)
                    false
                }
            synchronized(lock) {
                inFlight = false
                if (succeeded) {
                    sentCenter = center
                    failedAt = 0L
                } else {
                    failedAt = System.currentTimeMillis()
                }
            }
        }
    }

    private fun fetchAndSend(context: Context, center: GeoPoint): Boolean {
        val token = context.getString(R.string.mapbox_access_token)
        if (token.isBlank()) {
            Log.e(TAG, "Mapbox access token is blank")
            return false
        }
        val frame = mapFrameAround(center, metersPerPixel = METERS_PER_PIXEL)

        // 주소에 토큰이 들어 있어서 로그에는 남기지 않는다.
        val url =
            String.format(
                Locale.US,
                "https://api.mapbox.com/styles/v1/%s/static/%.6f,%.6f,%.2f/%dx%d?access_token=%s",
                MAP_STYLE,
                frame.centerLng,
                frame.centerLat,
                frame.zoom,
                IMAGE_PX,
                IMAGE_PX,
                token,
            )
        val imageBytes = download(url) ?: return false

        val request =
            PutDataMapRequest.create(PATH_MAP)
                .apply {
                    dataMap.putAsset("image", Asset.createFromBytes(imageBytes))
                    dataMap.putDouble("centerLat", frame.centerLat)
                    dataMap.putDouble("centerLng", frame.centerLng)
                    dataMap.putDouble("zoom", frame.zoom)
                    dataMap.putInt("imagePx", IMAGE_PX)
                    dataMap.putLong("updatedAt", System.currentTimeMillis())
                }
                .asPutDataRequest()
                .setUrgent()
        Wearable.getDataClient(context)
            .putDataItem(request)
            .addOnSuccessListener { Log.i(TAG, "Map image sent to watch (${imageBytes.size} bytes)") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to send map image to watch", e) }
        return true
    }

    private fun download(url: String): ByteArray? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Mapbox static map request failed: HTTP ${connection.responseCode}")
                null
            } else {
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.size > MAX_IMAGE_BYTES) {
                    Log.e(TAG, "Map image too large: ${bytes.size} bytes")
                    null
                } else {
                    bytes
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
