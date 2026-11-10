package com.example.safepath_test1.wear.presentation

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 경로를 얼마나 지나왔는지.
 * [traveledIndex]는 현재 위치가 놓인 구간의 시작 점 번호(구간 i는 점 i와 i+1 사이),
 * [traveledMeters]는 경로 시작부터 현재 위치까지 따라 걸은 거리다.
 */
internal data class RouteProgress(
    val progress: Float,
    val remainingMeters: Double,
    val traveledIndex: Int,
    val traveledMeters: Double,
)

/** 두 좌표 사이의 거리(m). 지구를 구로 보는 하버사인 공식. */
internal fun haversineMeters(a: LatLng, b: LatLng): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.first - a.first)
    val dLng = Math.toRadians(b.second - a.second)
    val h =
        sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(dLng / 2).pow(2)
    return 2 * earthRadius * asin(sqrt(h))
}

/** 경로 시작부터 각 점까지의 누적 거리(m). 첫 값은 0. */
internal fun cumulativeMeters(points: List<LatLng>): DoubleArray {
    val cumulative = DoubleArray(points.size)
    for (i in 1 until points.size) {
        cumulative[i] = cumulative[i - 1] + haversineMeters(points[i - 1], points[i])
    }
    return cumulative
}

/** 위도 1도의 길이(m). 지구 반지름 6,371km 기준이라 [haversineMeters]와 맞는다. */
private const val METERS_PER_DEGREE = 111_194.93

/**
 * 폰이 보내 준 경로 점들과 현재 위치로 진행률과 남은 거리를 계산한다.
 * 현재 위치를 경로의 가장 가까운 선분 위에 내린 점으로 보고 거리를 잰다(점과 점 사이에 있어도 정확하다).
 * 현재 위치가 없으면 아직 출발 전으로 보고 진행률 0, 남은 거리는 경로 전체 길이로 한다.
 */
internal fun computeRouteProgress(points: List<LatLng>, current: LatLng?): RouteProgress {
    if (points.size < 2) return RouteProgress(0f, 0.0, 0, 0.0)
    val cumulative = cumulativeMeters(points)
    val total = cumulative.last()
    if (current == null) return RouteProgress(0f, total, 0, 0.0)

    // 현재 위치를 원점으로 하는 평면 좌표(m). 수백 m 범위라 이 근사로 충분하다.
    val cosLat = cos(Math.toRadians(current.first))

    fun x(p: LatLng) = (p.second - current.second) * cosLat * METERS_PER_DEGREE

    fun y(p: LatLng) = (p.first - current.first) * METERS_PER_DEGREE

    var bestSegment = 0
    var bestT = 0.0
    var bestDistance = Double.MAX_VALUE
    for (i in 0 until points.lastIndex) {
        val ax = x(points[i])
        val ay = y(points[i])
        val dx = x(points[i + 1]) - ax
        val dy = y(points[i + 1]) - ay
        val lengthSquared = dx * dx + dy * dy
        val t =
            if (lengthSquared == 0.0) 0.0
            else ((-ax * dx - ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val distance = hypot(ax + t * dx, ay + t * dy)
        if (distance < bestDistance) {
            bestDistance = distance
            bestSegment = i
            bestT = t
        }
    }
    val segmentLength = cumulative[bestSegment + 1] - cumulative[bestSegment]
    val traveled = (cumulative[bestSegment] + bestT * segmentLength).coerceIn(0.0, total)
    val progress = if (total > 0.0) (traveled / total).toFloat().coerceIn(0f, 1f) else 0f
    return RouteProgress(progress, (total - traveled).coerceAtLeast(0.0), bestSegment, traveled)
}

/** 1km 미만은 10m 단위 "430m", 이상은 소수 첫째 자리 "1.2km". */
internal fun formatDistance(meters: Double): String =
    if (meters < 1000.0) {
        "${(meters / 10).roundToInt() * 10}m"
    } else {
        String.format(Locale.US, "%.1fkm", meters / 1000.0)
    }
