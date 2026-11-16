package com.example.safepath_test1.wear.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.safepath_test1.wear.comm.MapImage
import com.example.safepath_test1.wear.geo.EARTH_CIRCUMFERENCE_M
import com.example.safepath_test1.wear.geo.LatLng
import com.example.safepath_test1.wear.geo.mercatorX
import com.example.safepath_test1.wear.geo.mercatorY
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

/** Mapbox 지도는 줌 0에서 세계 전체 폭이 512px이고, 줌이 1 오를 때마다 2배가 된다. */
private const val MAP_TILE_PX = 512.0

/** 이미지에서 실제로 보여 줄 정사각 영역(이미지 픽셀 기준). */
internal class MapWindow(val left: Int, val top: Int, val size: Int)

/**
 * 폰이 받아 보낸 지도 이미지(정사각 [imagePx])의 중심과 줌으로, 위경도를 이미지 위 픽셀 좌표로 바꾼다.
 * 폰이 같은 Mapbox 규칙으로 이미지를 요청했으므로 경로가 지도의 도로 위에 겹쳐 그려진다.
 * 이미지는 보여 줄 범위보다 넓게 받아 두고, 사용자가 움직이거나 확대 단계가 바뀌면 보는 창만 옮기거나 키운다.
 */
internal class MapProjection(
    centerLat: Double,
    centerLng: Double,
    zoom: Double,
    val imagePx: Int,
) {
    private val worldPx = MAP_TILE_PX * 2.0.pow(zoom)
    private val centerX = mercatorX(centerLng) * worldPx
    private val centerY = mercatorY(centerLat) * worldPx

    /** 지도 1픽셀의 길이(m). 지구 둘레 × cos(위도) / (512 × 2^줌). */
    val metersPerPixel: Double = EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(centerLat)) / worldPx

    fun project(point: LatLng): Offset =
        Offset(
            x = (mercatorX(point.second) * worldPx - centerX + imagePx / 2.0).toFloat(),
            y = (mercatorY(point.first) * worldPx - centerY + imagePx / 2.0).toFloat(),
        )

    /**
     * [focus]를 가운데 두고 지름 [viewMeters]미터만큼 보여 줄 이미지 영역. 이미지가 낡아서(사용자가 이미지 중심에서
     * 너무 멀어져서) 창이 이미지 밖으로 나가게 되면 null이라, 호출한 쪽이 스케치 지도로 대신한다.
     * [panEastMeters]/[panNorthMeters]는 사용자가 손가락으로 지도를 끌어서 [focus]에서 옮긴 만큼(동쪽/북쪽 +)이다.
     * 옮긴 창이 이미지 밖으로 나가면 이미지 가장자리에서 멈춘다(이미지가 낡았는지는 옮기기 전 위치로만 판단한다).
     */
    fun windowAround(
        focus: LatLng,
        viewMeters: Double,
        panEastMeters: Double = 0.0,
        panNorthMeters: Double = 0.0,
    ): MapWindow? {
        val size = (viewMeters / metersPerPixel).roundToInt()
        if (size <= 0 || size > imagePx) return null
        val center = project(focus)
        val half = imagePx / 2f
        val maxOffset = (imagePx - size) / 2f
        if (abs(center.x - half) > maxOffset || abs(center.y - half) > maxOffset) return null
        val shiftX = (panEastMeters / metersPerPixel).toFloat()
        val shiftY = (-panNorthMeters / metersPerPixel).toFloat() // 북쪽은 이미지 위쪽(y 감소)
        return MapWindow(
            left = (center.x - size / 2f + shiftX).roundToInt().coerceIn(0, imagePx - size),
            top = (center.y - size / 2f + shiftY).roundToInt().coerceIn(0, imagePx - size),
            size = size,
        )
    }
}

/** 워치가 받은 지도 이미지와 그 위치 정보. */
internal class MapBackground(val image: ImageBitmap, val projection: MapProjection)

/** 폰이 보낸 지도 이미지를 화면에 쓸 배경(이미지 + 좌표 변환)으로 바꾼다. */
internal fun MapImage.toBackground(): MapBackground =
    MapBackground(
        image = bitmap.asImageBitmap(),
        projection =
            MapProjection(centerLat = centerLat, centerLng = centerLng, zoom = zoom, imagePx = imagePx),
    )
