package com.example.safepath_test1.wear

import com.example.safepath_test1.model.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Mapbox 지도 이미지 한 장이 어디를 비추는지: 중심 좌표와 줌. 워치가 같은 값으로 현재 위치와 경로를 겹쳐 그린다. */
internal data class MapFrame(val centerLat: Double, val centerLng: Double, val zoom: Double)

/** Mapbox 지도는 줌 0에서 세계 전체 폭이 512px이고, 줌이 1 오를 때마다 2배가 된다. */
internal const val MAP_TILE_PX = 512.0

/** 적도 기준 지구 둘레(m). 위도 φ에서 1픽셀의 길이는 둘레 × cos φ / (512 × 2^줌)이다. */
internal const val EARTH_CIRCUMFERENCE_M = 40_075_016.686

/**
 * [center]를 중심으로, 지도 1픽셀이 약 [metersPerPixel]미터가 되는 줌을 구한다.
 * 줌은 Mapbox가 소수점 둘째 자리까지만 받으므로 내림해서, 워치가 같은 값으로 정확히 계산할 수 있게 한다.
 * (내림이라 실제 1픽셀은 요청한 값보다 아주 조금 크다.)
 */
internal fun mapFrameAround(
    center: GeoPoint,
    metersPerPixel: Double,
    minZoom: Double = 10.0,
    maxZoom: Double = 18.0,
): MapFrame {
    val idealZoom =
        log2(
            EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(center.latitude)) /
                (MAP_TILE_PX * metersPerPixel)
        )
    val zoom = floor(idealZoom.coerceIn(minZoom, maxZoom) * 100) / 100
    return MapFrame(center.latitude, center.longitude, zoom)
}

/** 두 좌표 사이의 거리(m). 지구를 구로 보는 하버사인 공식. */
internal fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val h =
        sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).pow(2)
    return 2 * earthRadius * asin(sqrt(h))
}
