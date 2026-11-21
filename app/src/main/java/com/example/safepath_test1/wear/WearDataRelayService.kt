package com.example.safepath_test1.wear

import android.util.Log
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.MotionKeys
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WearDataRelayService"

/**
 * 워치가 Wearable Data Layer로 보낸 가속도·자이로 1초 묶음(20Hz, 보통 샘플 20개)을 받는다.
 * 받은 샘플은 최근 움직임 버퍼([recentMotion])에 쌓고, AI 분석 요청 때 음성 3초 창과 같은 구간을 꺼내
 * motion 필드로 보낸다. 받을 때마다 로그 한 줄을 남긴다(초당 한 번).
 */
class WearDataRelayService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != DataLayerPaths.MOTION) continue

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val samples =
                motionSamplesOf(
                    dataMap.getLongArray(MotionKeys.TIMESTAMPS),
                    dataMap.getFloatArray(MotionKeys.ACCEL_X),
                    dataMap.getFloatArray(MotionKeys.ACCEL_Y),
                    dataMap.getFloatArray(MotionKeys.ACCEL_Z),
                    dataMap.getFloatArray(MotionKeys.GYRO_X),
                    dataMap.getFloatArray(MotionKeys.GYRO_Y),
                    dataMap.getFloatArray(MotionKeys.GYRO_Z),
                )
            if (samples.isEmpty()) {
                Log.w(TAG, "Ignored an empty or broken motion batch")
                continue
            }
            recentMotion.add(samples)
            val rateHz = dataMap.getInt(MotionKeys.RATE_HZ)
            val spanMs = samples.last().tMs - samples.first().tMs
            Log.d(
                TAG,
                "Motion batch: ${samples.size} samples @${rateHz}Hz, span=${spanMs}ms, " +
                    "buffered=${recentMotion.size()}",
            )
        }
    }
}
