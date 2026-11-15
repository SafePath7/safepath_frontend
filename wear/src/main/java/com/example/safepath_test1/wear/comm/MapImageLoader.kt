package com.example.safepath_test1.wear.comm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MapImageLoader"

/**
 * 폰이 보낸 지도 이미지와 그 투영 정보(이미지 중심 위경도, 줌, 한 변 픽셀 수).
 * [updatedAt]은 폰이 이 이미지를 보낸 시각(ms)이다.
 */
internal class MapImage(
    val bitmap: Bitmap,
    val centerLat: Double,
    val centerLng: Double,
    val zoom: Double,
    val imagePx: Int,
    val updatedAt: Long,
)

/** 폰이 보낸 지도 DataMap에서 이미지를 꺼내 디코딩한다. 실패하면 null이라 기존 지도(또는 스케치 지도)를 그대로 쓴다. */
internal suspend fun loadMapImage(context: Context, dataMap: DataMap): MapImage? =
    withContext(Dispatchers.IO) {
        try {
            val asset = dataMap.getAsset(MapKeys.IMAGE) ?: return@withContext null
            val response = Tasks.await(Wearable.getDataClient(context).getFdForAsset(asset))
            val bitmap =
                response.inputStream.use { BitmapFactory.decodeStream(it) }
                    ?: return@withContext null
            MapImage(
                bitmap = bitmap,
                centerLat = dataMap.getDouble(MapKeys.CENTER_LAT),
                centerLng = dataMap.getDouble(MapKeys.CENTER_LNG),
                zoom = dataMap.getDouble(MapKeys.ZOOM),
                imagePx = dataMap.getInt(MapKeys.IMAGE_PX),
                updatedAt = dataMap.getLong(MapKeys.UPDATED_AT),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load map image", e)
            null
        }
    }
