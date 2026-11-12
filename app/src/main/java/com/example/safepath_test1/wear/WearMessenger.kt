package com.example.safepath_test1.wear

import android.content.Context
import android.util.Log
import com.example.safepath_test1.model.GeoPoint
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * 워치로 보내는 회전 안내 한 단계. Mapbox Directions 응답(steps=true)의 각 step에서
 * maneuver.location을 [point]로, maneuver.type을 [type]으로, maneuver.modifier를 [modifier]로 그대로 옮긴다.
 */
data class WearRouteStep(val point: GeoPoint, val type: String, val modifier: String?)

/**
 * 동일한 applicationId를 사용하는 휴대폰과 워치 사이에 DataClient로 경로 상태를 전송한다.
 * MessageClient는 실기기에서 전송 오류가 발생해, 실기기에서 확인된 DataClient를 사용한다.
 * 동일한 데이터는 중복 전송되지 않으므로 매 호출마다 updatedAt을 포함한다.
 */
object WearMessenger {
    private const val TAG = "WearMessenger"
    const val PATH_STATUS = "/safepath/status"

    /**
     * Route polyline is downsampled to this many points before being sent to the watch - plenty
     * for a small watch screen, and keeps the DataItem payload tiny regardless of how long the
     * actual walking route is.
     */
    private const val MAX_ROUTE_POINTS = 150

    /** 직선 구간의 점은 지우되 모퉁이는 남기는 허용 오차(m). 워치가 모퉁이로 회전 안내를 계산한다. */
    private const val SIMPLIFY_TOLERANCE_M = 3.0

    private fun send(
        context: Context,
        state: String,
        routeType: String? = null,
        distanceMeters: Double? = null,
        destinationName: String? = null,
        routeLineGeoJson: String? = null,
        destinationPoint: GeoPoint? = null,
        currentPoint: GeoPoint? = null,
        routeSteps: List<WearRouteStep>? = null,
    ) {
        val putDataMapRequest =
            PutDataMapRequest.create(PATH_STATUS).apply {
                dataMap.putString("state", state)
                if (routeType != null) dataMap.putString("routeType", routeType)
                if (distanceMeters != null) dataMap.putDouble("distanceMeters", distanceMeters)
                if (destinationName != null) dataMap.putString("destinationName", destinationName)
                routeLineGeoJson?.let { geoJson ->
                    val points = simplifyForWatch(parseGeoJsonPoints(geoJson))
                    if (points.isNotEmpty()) dataMap.putString("routePoints", encodePoints(points))
                }
                destinationPoint?.let {
                    dataMap.putDouble("destLat", it.latitude)
                    dataMap.putDouble("destLng", it.longitude)
                }
                currentPoint?.let {
                    dataMap.putDouble("curLat", it.latitude)
                    dataMap.putDouble("curLng", it.longitude)
                }
                routeSteps
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { dataMap.putString("routeSteps", encodeSteps(it)) }
                dataMap.putLong("updatedAt", System.currentTimeMillis())
            }
        val request = putDataMapRequest.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context)
            .putDataItem(request)
            .addOnSuccessListener {
                Log.i(
                    TAG,
                    "DataClient synced state=$state routeType=$routeType destination=$destinationName",
                )
            }
            .addOnFailureListener { e -> Log.e(TAG, "DataClient sync failed for state=$state", e) }
    }

    fun sendIdle(context: Context) {
        send(context, "IDLE")
    }

    fun sendRouteSearching(context: Context) {
        send(context, "SEARCHING")
    }

    /**
     * `routeLineGeoJson`/`destinationPoint` describe the route's shape and only need to be resent
     * when the active route changes; `currentPoint` is the traveler's live GPS position and is
     * meant to be resent every time it updates (SafePathApp's location listener fires roughly
     * every 5s/5m) so the watch's mini-map can move a current-location dot as they walk - a
     * one-time snapshot of the route isn't the point, live position is (same reason the
     * guardian-facing journey screen tracks a live position rather than a static route).
     */
    fun sendRouteFound(
        context: Context,
        routeType: String,
        destinationName: String?,
        distanceMeters: Double,
        routeLineGeoJson: String? = null,
        destinationPoint: GeoPoint? = null,
        currentPoint: GeoPoint? = null,
        routeSteps: List<WearRouteStep>? = null,
    ) {
        // 워치 미니맵 배경 지도 이미지: 사용자 위치(없으면 경로 시작점) 기준으로, 이미지 중심에서 충분히
        // 멀어졌을 때만 새로 받아서 보낸다(WearMapImageSender가 걸러 주므로 매번 불러도 된다).
        val mapCenter = currentPoint ?: routeLineGeoJson?.let { parseGeoJsonPoints(it).firstOrNull() }
        mapCenter?.let { WearMapImageSender.sendIfNeeded(context, it) }
        send(
            context,
            "FOUND",
            routeType = routeType,
            distanceMeters = distanceMeters,
            destinationName = destinationName,
            routeLineGeoJson = routeLineGeoJson,
            destinationPoint = destinationPoint,
            currentPoint = currentPoint,
            routeSteps = routeSteps,
        )
    }

    /**
     * 회전 안내를 "lat,lng,type,modifier;..." 한 줄로 만든다. 워치의 decodeRouteSteps가 같은 형식을 읽는다.
     * 구분 글자(쉼표, 세미콜론)가 값 안에 섞여 형식이 깨지지 않게 지운다.
     */
    private fun encodeSteps(steps: List<WearRouteStep>): String =
        steps.joinToString(";") { step ->
            fun clean(value: String?) = value.orEmpty().replace(",", " ").replace(";", " ").trim()
            String.format(
                Locale.US,
                "%.6f,%.6f,%s,%s",
                step.point.latitude,
                step.point.longitude,
                clean(step.type),
                clean(step.modifier),
            )
        }

    /** 모퉁이는 남기고 직선 위의 점만 줄인 뒤, 그래도 많으면 균등하게 줄인다. */
    private fun simplifyForWatch(points: List<GeoPoint>): List<GeoPoint> =
        downsample(douglasPeucker(points, SIMPLIFY_TOLERANCE_M), MAX_ROUTE_POINTS)

    /** Douglas-Peucker: 양 끝을 잇는 직선에서 [toleranceMeters] 넘게 벗어난 점만 남긴다. */
    private fun douglasPeucker(points: List<GeoPoint>, toleranceMeters: Double): List<GeoPoint> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, points.lastIndex))
        while (stack.isNotEmpty()) {
            val range = stack.removeLast()
            val start = range[0]
            val end = range[1]
            var farthest = 0.0
            var farthestIndex = -1
            for (i in start + 1 until end) {
                val distance = distanceToSegmentMeters(points[i], points[start], points[end])
                if (distance > farthest) {
                    farthest = distance
                    farthestIndex = i
                }
            }
            if (farthestIndex != -1 && farthest > toleranceMeters) {
                keep[farthestIndex] = true
                stack.addLast(intArrayOf(start, farthestIndex))
                stack.addLast(intArrayOf(farthestIndex, end))
            }
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }

    /** 점 [p]에서 선분 a-b까지의 거리(m). 짧은 거리라 평면으로 근사한다. */
    private fun distanceToSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val cosLat = cos(Math.toRadians(a.latitude))
        fun x(g: GeoPoint) = (g.longitude - a.longitude) * cosLat * 111_320.0
        fun y(g: GeoPoint) = (g.latitude - a.latitude) * 110_540.0
        val bx = x(b)
        val by = y(b)
        val px = x(p)
        val py = y(p)
        val lengthSquared = bx * bx + by * by
        if (lengthSquared == 0.0) return hypot(px, py)
        val t = ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(px - t * bx, py - t * by)
    }

    private fun downsample(points: List<GeoPoint>, maxPoints: Int): List<GeoPoint> {
        if (points.size <= maxPoints) return points
        val step = (points.size - 1).toDouble() / (maxPoints - 1)
        return (0 until maxPoints).map { i ->
            points[(i * step).roundToInt().coerceIn(0, points.size - 1)]
        }
    }

    private fun encodePoints(points: List<GeoPoint>): String =
        points.joinToString(";") { String.format(Locale.US, "%.6f,%.6f", it.latitude, it.longitude) }

    internal fun parseGeoJsonPoints(geoJson: String): List<GeoPoint> =
        try {
            val coordinates = JSONObject(geoJson).optJSONArray("coordinates") ?: return emptyList()
            buildList {
                for (index in 0 until coordinates.length()) {
                    val coordinate = coordinates.optJSONArray(index) ?: continue
                    val longitude = coordinate.optDouble(0, Double.NaN)
                    val latitude = coordinate.optDouble(1, Double.NaN)
                    if (latitude.isFinite() && longitude.isFinite()) add(GeoPoint(latitude, longitude))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse route GeoJSON for watch map", e)
            emptyList()
        }
}
