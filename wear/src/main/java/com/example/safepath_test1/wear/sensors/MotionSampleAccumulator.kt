package com.example.safepath_test1.wear.sensors

/** 두 센서의 최신 측정값. timestamp는 폰에서 쓰는 실제 시각(밀리초)이다. */
internal data class MotionData(
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val timestamp: Long,
)

/** 샘플 사이 간격(나노초). [MOTION_RATE_HZ]에서 나온다. */
private const val SAMPLE_PERIOD_NANOS = 1_000_000_000L / MOTION_RATE_HZ

/**
 * 가속도·자이로의 최신 값을 합쳐 [MOTION_RATE_HZ](20Hz) 샘플을 만든다.
 *
 * 센서 값이 올 때마다 최신 값을 바꿔 두고, 50ms 간격의 고정 박자 시각이 지났을 때만 샘플을 하나 남긴다.
 * 박자는 "마지막으로 남긴 시각 + 50ms"가 아니라 원래 자리(처음 시각에서 50ms씩)에 고정한다.
 * 센서 값은 49.8ms, 50.3ms처럼 조금씩 흔들리며 오는데, 마지막 시각을 기준으로 하면 한 번 늦을 때마다
 * 박자가 밀려 실제로는 초당 13개 정도만 남았다. 고정 박자는 조금 늦게 남겨도 다음 박자가 밀리지 않는다.
 * 시각은 센서 이벤트의 단조 증가 시각(나노초)으로 판단한다.
 */
internal class MotionSampleAccumulator {
    private var acceleration: Triple<Float, Float, Float>? = null
    private var rotation: Triple<Float, Float, Float>? = null

    /** 다음 샘플을 남길 박자 시각(나노초). null이면 아직 박자가 없어 다음 값을 바로 남긴다. */
    private var nextDueNanos: Long? = null

    fun updateAcceleration(x: Float, y: Float, z: Float, nanos: Long, millis: Long): MotionData? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
        acceleration = Triple(x, y, z)
        return sample(nanos, millis)
    }

    fun updateRotation(x: Float, y: Float, z: Float, nanos: Long, millis: Long): MotionData? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
        rotation = Triple(x, y, z)
        return sample(nanos, millis)
    }

    fun reset() {
        acceleration = null
        rotation = null
        nextDueNanos = null
    }

    private fun sample(nanos: Long, millis: Long): MotionData? {
        val accel = acceleration ?: return null
        val gyro = rotation ?: return null
        val due = nextDueNanos
        if (due != null && nanos < due) return null
        // 박자를 원래 자리에 고정한다. 한 박자 넘게 밀렸으면(센서가 잠깐 끊긴 경우) 밀린 박자를
        // 몰아서 채우지 않고 지금부터 다시 박자를 시작한다.
        nextDueNanos =
            if (due == null || nanos - due >= SAMPLE_PERIOD_NANOS) {
                nanos + SAMPLE_PERIOD_NANOS
            } else {
                due + SAMPLE_PERIOD_NANOS
            }
        return MotionData(
            accel.first,
            accel.second,
            accel.third,
            gyro.first,
            gyro.second,
            gyro.third,
            millis,
        )
    }
}
