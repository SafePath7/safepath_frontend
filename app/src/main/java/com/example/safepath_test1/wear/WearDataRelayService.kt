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
 * 워치에서 Wearable Data Layer로 전달한 가속도계·자이로스코프 측정값을 수신한다.
 * 현재는 수신 값을 로그에 기록하며, AI 서버의 analyzeMotion 요청·응답 형식이 정해지면
 * 이곳에서 서버를 호출한다.
 */
class WearDataRelayService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != DataLayerPaths.MOTION) continue

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val accelX = dataMap.getFloat(MotionKeys.ACCEL_X)
            val accelY = dataMap.getFloat(MotionKeys.ACCEL_Y)
            val accelZ = dataMap.getFloat(MotionKeys.ACCEL_Z)
            val gyroX = dataMap.getFloat(MotionKeys.GYRO_X)
            val gyroY = dataMap.getFloat(MotionKeys.GYRO_Y)
            val gyroZ = dataMap.getFloat(MotionKeys.GYRO_Z)
            val timestamp = dataMap.getLong(MotionKeys.TIMESTAMP)

            Log.d(
                TAG,
                "accel=(%.2f, %.2f, %.2f) gyro=(%.2f, %.2f, %.2f) timestamp=$timestamp"
                    .format(accelX, accelY, accelZ, gyroX, gyroY, gyroZ),
            )
        }
    }
}
