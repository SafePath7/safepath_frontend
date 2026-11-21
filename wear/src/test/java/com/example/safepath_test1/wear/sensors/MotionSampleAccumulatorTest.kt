package com.example.safepath_test1.wear.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MotionSampleAccumulatorTest {
    @Test
    fun sample_waitsUntilBothSensorsAreReady() {
        val samples = MotionSampleAccumulator()
        assertNull(samples.updateAcceleration(1f, 2f, 3f, 0L, 100L))
        val sample = samples.updateRotation(4f, 5f, 6f, 1L, 101L)!!
        assertEquals(1f, sample.accelX, 0f)
        assertEquals(6f, sample.gyroZ, 0f)
        assertEquals(101L, sample.timestamp)
    }

    @Test
    fun sample_acceptsEitherSensorFirst() {
        val samples = MotionSampleAccumulator()
        assertNull(samples.updateRotation(4f, 5f, 6f, 0L, 100L))
        assertNotNull(samples.updateAcceleration(1f, 2f, 3f, 1L, 101L))
    }

    @Test
    fun alternatingEvents_doNotDoubleTheRate() {
        val samples = MotionSampleAccumulator()
        samples.updateAcceleration(1f, 2f, 3f, 0L, 100L)
        assertNotNull(samples.updateRotation(4f, 5f, 6f, 0L, 100L))
        assertNull(samples.updateAcceleration(7f, 8f, 9f, 25_000_000L, 125L))
        val sample = samples.updateRotation(10f, 11f, 12f, 50_000_000L, 150L)!!
        assertEquals(7f, sample.accelX, 0f)
        assertEquals(12f, sample.gyroZ, 0f)
    }

    @Test
    fun jitteredEvents_stillGiveTwentySamplesPerSecond() {
        // 실제 센서처럼 50ms 간격이 조금씩 흔들리며 온다. 예전 방식(마지막 시각 + 50ms)은 여기서 2초에 27개였다.
        val samples = MotionSampleAccumulator()
        val accelJitter = longArrayOf(0L, -300_000L, 400_000L)
        val gyroJitter = longArrayOf(200_000L, -400_000L, 0L)
        val events =
            (0 until 40).flatMap { k ->
                val beat = 1_000_000_000L + k * 50_000_000L
                listOf(
                    true to beat + accelJitter[k % 3],
                    false to beat + 25_000_000L + gyroJitter[k % 3],
                )
            }.sortedBy { it.second }
        var kept = 0
        for ((isAccel, nanos) in events) {
            val sample =
                if (isAccel) {
                    samples.updateAcceleration(1f, 2f, 3f, nanos, nanos / 1_000_000L)
                } else {
                    samples.updateRotation(4f, 5f, 6f, nanos, nanos / 1_000_000L)
                }
            if (sample != null) kept++
        }
        assertEquals(40, kept)
    }

    @Test
    fun longGap_restartsTheBeatWithoutCatchingUp() {
        val samples = MotionSampleAccumulator()
        samples.updateAcceleration(1f, 2f, 3f, 0L, 0L)
        assertNotNull(samples.updateRotation(4f, 5f, 6f, 0L, 0L))
        // 1초 동안 센서가 끊겼다가 다시 오면, 밀린 박자 20개를 몰아서 남기지 않고 하나만 남긴다.
        assertNotNull(samples.updateAcceleration(1f, 2f, 3f, 1_000_000_000L, 1_000L))
        assertNull(samples.updateRotation(4f, 5f, 6f, 1_010_000_000L, 1_010L))
        assertNotNull(samples.updateRotation(4f, 5f, 6f, 1_050_000_000L, 1_050L))
    }

    @Test
    fun reset_requiresFreshReadingsFromBothSensors() {
        val samples = MotionSampleAccumulator()
        samples.updateAcceleration(1f, 2f, 3f, 0L, 100L)
        samples.updateRotation(4f, 5f, 6f, 1L, 101L)
        samples.reset()
        assertNull(samples.updateAcceleration(7f, 8f, 9f, 100_000_000L, 200L))
        assertNotNull(samples.updateRotation(10f, 11f, 12f, 100_000_001L, 201L))
    }

    @Test
    fun invalidValues_areNotSent() {
        val samples = MotionSampleAccumulator()
        assertNull(samples.updateAcceleration(Float.NaN, 2f, 3f, 0L, 100L))
        assertNull(samples.updateRotation(4f, 5f, 6f, 1L, 101L))
        assertNull(samples.updateAcceleration(Float.POSITIVE_INFINITY, 2f, 3f, 2L, 102L))
        assertNotNull(samples.updateAcceleration(1f, 2f, 3f, 3L, 103L))
    }
}
