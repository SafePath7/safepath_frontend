package com.example.safepath_test1.wear.sensors

import android.content.Context
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.MotionKeys

private const val TAG = "WatchMotionSender"

/**
 * WatchSensorManager의 최신 측정값을 Wearable Data Layer의 DataClient로 폰에 보낸다.
 * 폰 → 워치 방향의 WearMessenger(app 모듈)와 같은 방식이다. 이 프로젝트에서는 DataClient + 같은 applicationId
 * 조합만 실기기 두 대에서 동작이 확인돼 MessageClient는 쓰지 않는다.
 * StateFlow와 DataItem 하나가 갱신을 합칠 수 있어서, 빠짐없는 센서 스트림은 아니다.
 *
 * 성공 로그는 남기지 않는다. 20Hz로 남기면 WatchSensorManager가 이미 남기는 로그 양이 두 배가 된다.
 * 실패는 로그로 남긴다.
 */
object WatchMotionSender {

    fun send(context: Context, motion: MotionData) {
        DataLayerSender.putUrgent(
            context = context,
            path = DataLayerPaths.MOTION,
            tag = TAG,
            failureMessage = "Failed to relay motion data to phone",
        ) {
            putFloat(MotionKeys.ACCEL_X, motion.accelX)
            putFloat(MotionKeys.ACCEL_Y, motion.accelY)
            putFloat(MotionKeys.ACCEL_Z, motion.accelZ)
            putFloat(MotionKeys.GYRO_X, motion.gyroX)
            putFloat(MotionKeys.GYRO_Y, motion.gyroY)
            putFloat(MotionKeys.GYRO_Z, motion.gyroZ)
            putLong(MotionKeys.TIMESTAMP, motion.timestamp)
        }
    }
}
