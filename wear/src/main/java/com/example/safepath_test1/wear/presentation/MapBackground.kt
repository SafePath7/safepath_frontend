package com.example.safepath_test1.wear.presentation

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MapBackground"

/** 폰의 WearMapImageSender가 지도 이미지를 보내는 경로 - 같은 값으로 맞춰야 한다. */
internal const val PATH_MAP = "/safepath/map"

/** Mapbox 지도는 줌 0에서 세계 전체 폭이 512px이고, 줌이 1 오를 때마다 2배가 된다. */
private const val MAP_TILE_PX = 512.0

/** 적도 기준 지구 둘레(m). */
private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686

internal fun mercatorX(lng: Double): Double = (lng + 180.0) / 360.0

internal fun mercatorY(lat: Double): Double {
    val radians = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    return 0.5 - ln(tan(PI / 4 + radians / 2)) / (2 * PI)
}

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

/** 폰이 보낸 DataMap에서 지도 이미지를 꺼내 디코딩한다. 실패하면 null이라 기존 스케치 지도를 그대로 쓴다. */
internal suspend fun loadMapBackground(context: Context, dataMap: DataMap): MapBackground? =
    withContext(Dispatchers.IO) {
        try {
            val asset = dataMap.getAsset("image") ?: return@withContext null
            val response = Tasks.await(Wearable.getDataClient(context).getFdForAsset(asset))
            val bitmap =
                response.inputStream.use { BitmapFactory.decodeStream(it) }
                    ?: return@withContext null
            MapBackground(
                image = bitmap.asImageBitmap(),
                projection =
                    MapProjection(
                        centerLat = dataMap.getDouble("centerLat"),
                        centerLng = dataMap.getDouble("centerLng"),
                        zoom = dataMap.getDouble("zoom"),
                        imagePx = dataMap.getInt("imagePx"),
                    ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load map image", e)
            null
        }
    }
