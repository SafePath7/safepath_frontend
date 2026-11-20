package com.example.safepath_test1.wear.state

import com.example.safepath_test1.wear.geo.LatLng
import com.example.safepath_test1.wear.geo.RoutePath
import com.example.safepath_test1.wear.geo.RouteStep

/** 폰에서 이 시간 동안 아무 값도 오지 않으면 오래됨(연결 끊김)으로 본다. */
internal const val STALE_AFTER_MS = 10 * 60 * 1000L

/** 폰에서 마지막으로 받은 값의 시각([updatedAt])이 너무 오래됐거나 아예 없으면 연결이 끊긴 것으로 본다. */
internal fun isStatusStale(updatedAt: Long, now: Long = System.currentTimeMillis()): Boolean =
    updatedAt <= 0L || now - updatedAt > STALE_AFTER_MS

/** 폰이 보낸 [status]를 그대로 보여 줄지, 너무 오래된 값이라 연결 끊김으로 보여 줄지 정한다. */
internal fun statusOrStale(
    status: RouteStatus,
    updatedAt: Long,
    now: Long = System.currentTimeMillis(),
): RouteStatus = if (isStatusStale(updatedAt, now)) RouteStatus.Stale else status

/** 워치 화면이 보여 주는 길안내 상태. */
internal sealed class RouteStatus {
    data object Idle : RouteStatus()

    data object Searching : RouteStatus()

    data class Found(val routeType: String, val distanceKm: String, val destinationName: String?) :
        RouteStatus()

    data object Stale : RouteStatus()
}

/** RouteMiniMap이 그릴 경로 모양과 실시간 위치. WearMessenger가 보낸 DataMap 값을 풀어 만든다. */
internal data class RouteMapState(
    val points: List<LatLng>,
    val destination: LatLng?,
    val current: LatLng?,
    /** 폰이 길찾기 서비스에서 받아 보낸 회전 안내. 아직 안 보내는 폰이면 비어 있다. */
    val steps: List<RouteStep> = emptyList(),
) {
    /** 누적 거리를 한 번만 계산해 둔 경로. 수신할 때마다 새로 만들고, 프레임마다 다시 계산하지 않는다. */
    val path: RoutePath = RoutePath(points)
}
