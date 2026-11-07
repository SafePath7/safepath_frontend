package com.example.safepath_test1.wear

import android.content.Context
import android.util.Log
import com.example.safepath_test1.model.GeoPoint
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONObject

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
    private const val MAX_ROUTE_POINTS = 40

    private fun send(
        context: Context,
        state: String,
        routeType: String? = null,
        distanceMeters: Double? = null,
        destinationName: String? = null,
        routeLineGeoJson: String? = null,
        destinationPoint: GeoPoint? = null,
        currentPoint: GeoPoint? = null,
    ) {
        val putDataMapRequest =
            PutDataMapRequest.create(PATH_STATUS).apply {
                dataMap.putString("state", state)
                if (routeType != null) dataMap.putString("routeType", routeType)
                if (distanceMeters != null) dataMap.putDouble("distanceMeters", distanceMeters)
                if (destinationName != null) dataMap.putString("destinationName", destinationName)
                routeLineGeoJson?.let { geoJson ->
                    val points = downsample(parseGeoJsonPoints(geoJson), MAX_ROUTE_POINTS)
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
    ) {
        send(
            context,
            "FOUND",
            routeType = routeType,
            distanceMeters = distanceMeters,
            destinationName = destinationName,
            routeLineGeoJson = routeLineGeoJson,
            destinationPoint = destinationPoint,
            currentPoint = currentPoint,
        )
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

    private fun parseGeoJsonPoints(geoJson: String): List<GeoPoint> =
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
