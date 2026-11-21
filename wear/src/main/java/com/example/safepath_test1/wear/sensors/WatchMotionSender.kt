package com.example.safepath_test1.wear.sensors

import android.content.Context
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.MotionKeys

private const val TAG = "WatchMotionSender"

/**
 * WatchSensorManager가 모은 1초 분량의 움직임 샘플을 Wearable Data Layer의 DataClient로 폰에 보낸다.
 * 형식은 wear-contract의 MotionKeys(같은 길이의 배열들)를 따른다. 초당 한 번만 보낸다.
 * 폰 → 워치 방향의 WearMessenger(app 모듈)와 같은 방식이다. 이 프로젝트에서는 DataClient + 같은 applicationId
 * 조합만 실기기 두 대에서 동작이 확인돼 MessageClient는 쓰지 않는다.
 *
 * 성공 로그는 남기지 않는다(초당 한 번이라 폰 쪽 수신 로그로 충분하다). 실패는 로그로 남긴다.
 */
internal object WatchMotionSender {

    fun send(context: Context, batch: MotionBatch) {
        val samples = batch.samples
        if (samples.isEmpty()) return
        DataLayerSender.putUrgent(
            context = context,
            path = DataLayerPaths.MOTION,
            tag = TAG,
            failureMessage = "Failed to relay motion data to phone",
        ) {
            putInt(MotionKeys.RATE_HZ, batch.rateHz)
            putLongArray(MotionKeys.TIMESTAMPS, samples.map { it.timestamp }.toLongArray())
            putFloatArray(MotionKeys.ACCEL_X, samples.map { it.accelX }.toFloatArray())
            putFloatArray(MotionKeys.ACCEL_Y, samples.map { it.accelY }.toFloatArray())
            putFloatArray(MotionKeys.ACCEL_Z, samples.map { it.accelZ }.toFloatArray())
            putFloatArray(MotionKeys.GYRO_X, samples.map { it.gyroX }.toFloatArray())
            putFloatArray(MotionKeys.GYRO_Y, samples.map { it.gyroY }.toFloatArray())
            putFloatArray(MotionKeys.GYRO_Z, samples.map { it.gyroZ }.toFloatArray())
        }
    }
}
