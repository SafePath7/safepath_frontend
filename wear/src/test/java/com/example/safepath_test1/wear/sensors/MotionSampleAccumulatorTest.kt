package com.example.safepath_test1.wear.sensors

import org.junit.Assert.*
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
