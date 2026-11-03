package com.example.safepath_test1.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WearDataRelayService"

/** 모듈 간 코드를 공유하지 않으므로 워치의 WatchMotionSender.PATH_MOTION과 일치해야 한다. */
private const val PATH_MOTION = "/safepath/motion"

/**
 * 워치에서 Wearable Data Layer로 전달한 가속도계·자이로스코프 측정값을 수신한다.
 * 현재는 수신 값을 로그에 기록하며, AI 서버의 analyzeMotion 요청·응답 형식이 정해지면
 * 이곳에서 서버를 호출한다.
 */
class WearDataRelayService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != PATH_MOTION) continue

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val accelX = dataMap.getFloat("accelX")
            val accelY = dataMap.getFloat("accelY")
            val accelZ = dataMap.getFloat("accelZ")
            val gyroX = dataMap.getFloat("gyroX")
            val gyroY = dataMap.getFloat("gyroY")
            val gyroZ = dataMap.getFloat("gyroZ")
            val timestamp = dataMap.getLong("timestamp")

            Log.d(
                TAG,
                "accel=(%.2f, %.2f, %.2f) gyro=(%.2f, %.2f, %.2f) timestamp=$timestamp"
                    .format(accelX, accelY, accelZ, gyroX, gyroY, gyroZ),
            )
        }
    }
}
