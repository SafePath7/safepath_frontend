package com.example.safepath_test1.wear.comm

import com.example.safepath_test1.wear.geo.LatLng
import com.example.safepath_test1.wear.geo.RouteStep
import com.example.safepath_test1.wear.state.RouteMapState
import com.example.safepath_test1.wear.state.RouteStatus
import com.google.android.gms.wearable.DataMap
import java.util.Locale

/**
 * 폰이 보낸 상태 DataMap에서 꺼낸 값 그대로. Android의 [DataMap] 없이도 [decodeStatus]를 시험할 수 있게
 * DataMap에서 꺼내는 일([toStatusPayload])과 해석하는 일을 나눴다.
 */
internal data class StatusPayload(
    val updatedAt: Long,
    val state: String?,
    val routeType: String?,
    val distanceMeters: Double,
    val destinationName: String?,
    val routePoints: String?,
    val routeSteps: String?,
    val destination: LatLng?,
    val current: LatLng?,
)

/** [decodeStatus]의 결과: 폰이 보낸 시각, 길안내 상태, 지도에 그릴 경로(점이 2개 미만이면 null). */
internal data class StatusUpdate(
    val updatedAt: Long,
    val status: RouteStatus,
    val routeMap: RouteMapState?,
)

/** 상태 DataMap(경로 [DataLayerPaths.STATUS])에서 값을 꺼낸다. 없는 키는 0이나 null이 된다. */
internal fun DataMap.toStatusPayload(): StatusPayload =
    StatusPayload(
        updatedAt = getLong(StatusKeys.UPDATED_AT),
        state = getString(StatusKeys.STATE),
        routeType = getString(StatusKeys.ROUTE_TYPE),
        distanceMeters = getDouble(StatusKeys.DISTANCE_METERS),
        destinationName = getString(StatusKeys.DESTINATION_NAME),
        routePoints = getString(StatusKeys.ROUTE_POINTS),
        routeSteps = getString(StatusKeys.ROUTE_STEPS),
        destination = latLngOrNull(StatusKeys.DEST_LAT, StatusKeys.DEST_LNG),
        current = latLngOrNull(StatusKeys.CUR_LAT, StatusKeys.CUR_LNG),
    )

private fun DataMap.latLngOrNull(latKey: String, lngKey: String): LatLng? =
    if (containsKey(latKey) && containsKey(lngKey)) getDouble(latKey) to getDouble(lngKey) else null

/** 폰이 보낸 값을 워치 화면의 길안내 상태와 경로로 바꾼다. 알 수 없는 상태 문자열은 대기(Idle)로 본다. */
internal fun decodeStatus(payload: StatusPayload): StatusUpdate {
    val status =
        when (payload.state) {
            StatusStates.SEARCHING -> RouteStatus.Searching
            StatusStates.FOUND ->
                RouteStatus.Found(
                    routeType = payload.routeType ?: "경로",
                    // 기기 언어 설정과 상관없이 "1.2"처럼 점으로 쓴다(남은 거리 표기와 같은 방식).
                    distanceKm = String.format(Locale.US, "%.1f", payload.distanceMeters / 1000.0),
                    destinationName = payload.destinationName,
                )
            else -> RouteStatus.Idle
        }
    val points = decodeLatLngList(payload.routePoints)
    val routeMap =
        if (points.size >= 2) {
            RouteMapState(
                points = points,
                destination = payload.destination,
                current = payload.current,
                steps = decodeRouteSteps(payload.routeSteps),
            )
        } else {
            null
        }
    return StatusUpdate(payload.updatedAt, status, routeMap)
}

/** Reverses WearMessenger's "lat,lng;lat,lng;..." encoding. 형식이 틀린 항목은 버린다. */
internal fun decodeLatLngList(encoded: String?): List<LatLng> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.split(";").mapNotNull { pair ->
        val parts = pair.split(",")
        if (parts.size != 2) return@mapNotNull null
        val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val lng = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        lat to lng
    }
}

/** 폰 WearMessenger의 "lat,lng,type,modifier;..." 형식을 되돌린다. 형식이 틀린 항목은 버린다. */
internal fun decodeRouteSteps(encoded: String?): List<RouteStep> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.split(";").mapNotNull { item ->
        val parts = item.split(",", limit = 4)
        if (parts.size < 3) return@mapNotNull null
        val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val lng = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        RouteStep(lat to lng, parts[2].trim(), parts.getOrElse(3) { "" }.trim())
    }
}
