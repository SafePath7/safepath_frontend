package com.example.safepath_test1.wear.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 지구 반지름(m). 하버사인 거리와 위도 1도 길이가 같은 구를 기준으로 한다. */
internal const val EARTH_RADIUS_M = 6_371_000.0

/** 위도 1도의 길이(m). 지구 반지름 6,371km 기준이라 [haversineMeters]와 맞는다. */
internal const val METERS_PER_DEGREE = 111_194.93

/** 적도 기준 지구 둘레(m). Mapbox 지도의 픽셀당 미터를 구할 때 쓴다. */
internal const val EARTH_CIRCUMFERENCE_M = 40_075_016.686

/** 두 좌표 사이의 거리(m). 지구를 구로 보는 하버사인 공식. */
internal fun haversineMeters(a: LatLng, b: LatLng): Double {
    val dLat = Math.toRadians(b.first - a.first)
    val dLng = Math.toRadians(b.second - a.second)
    val h =
        sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

/** 두 좌표 사이의 방위각(도, 북쪽 0, 시계방향). */
internal fun bearingDegrees(from: LatLng, to: LatLng): Double {
    val lat1 = Math.toRadians(from.first)
    val lat2 = Math.toRadians(to.first)
    val dLng = Math.toRadians(to.second - from.second)
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** [before]→[at]로 오다가 [at]→[after]로 갈 때 꺾이는 각도(도). 양수면 오른쪽, 음수면 왼쪽. */
internal fun turnAngleDegrees(before: LatLng, at: LatLng, after: LatLng): Double {
    var angle = bearingDegrees(at, after) - bearingDegrees(before, at)
    while (angle > 180.0) angle -= 360.0
    while (angle <= -180.0) angle += 360.0
    return angle
}

/** 웹 메르카토르에서 경도를 0..1(서쪽 끝..동쪽 끝)로 바꾼다. */
internal fun mercatorX(lng: Double): Double = (lng + 180.0) / 360.0

/** 웹 메르카토르에서 위도를 0..1(북쪽 끝..남쪽 끝)로 바꾼다. 위도는 ±85.0511도로 제한한다. */
internal fun mercatorY(lat: Double): Double {
    val radians = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    return 0.5 - ln(tan(PI / 4 + radians / 2)) / (2 * PI)
}
