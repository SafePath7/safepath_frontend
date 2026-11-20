package com.example.safepath_test1.wear.sensors

/** 두 센서의 최신 측정값. timestamp는 폰에서 쓰는 실제 시각(밀리초)이다. */
data class MotionData(
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val timestamp: Long,
)

/** 센서 이벤트의 단조 증가 시각으로 합친 값의 갱신을 20Hz로 제한한다. */
internal class MotionSampleAccumulator {
    private var acceleration: Triple<Float, Float, Float>? = null
    private var rotation: Triple<Float, Float, Float>? = null
    private var lastEmissionNanos: Long? = null

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
        lastEmissionNanos = null
    }

    private fun sample(nanos: Long, millis: Long): MotionData? {
        val accel = acceleration ?: return null
        val gyro = rotation ?: return null
        val previous = lastEmissionNanos
        if (previous != null && nanos - previous < 50_000_000L) return null
        lastEmissionNanos = nanos
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
