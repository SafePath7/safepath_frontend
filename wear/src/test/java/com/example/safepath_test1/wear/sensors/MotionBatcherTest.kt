package com.example.safepath_test1.wear.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MotionBatcherTest {
    private fun sample(t: Long) = MotionData(0f, 0f, 9.8f, 0f, 0f, 0f, timestamp = t)

    @Test
    fun add_returnsNothingUntilOneSecondHasPassed() {
        val batcher = MotionBatcher()
        for (i in 0 until 20) assertNull(batcher.add(sample(1_000L + i * 50L)))
    }

    @Test
    fun add_returnsTwentySamplesAtTwentyHertz() {
        val batcher = MotionBatcher()
        for (i in 0 until 20) batcher.add(sample(1_000L + i * 50L))
        val batch = batcher.add(sample(2_000L))
        assertNotNull(batch)
        assertEquals(MOTION_RATE_HZ, batch!!.rateHz)
        assertEquals(20, batch.samples.size)
        assertEquals(1_000L, batch.samples.first().timestamp)
        assertEquals(1_950L, batch.samples.last().timestamp)
    }

    @Test
    fun add_startsTheNextBatchWithTheSampleThatClosedThePreviousOne() {
        val batcher = MotionBatcher()
        for (i in 0..20) batcher.add(sample(1_000L + i * 50L))
        for (i in 21 until 40) assertNull(batcher.add(sample(1_000L + i * 50L)))
        val second = batcher.add(sample(3_000L))!!
        assertEquals(2_000L, second.samples.first().timestamp)
        assertEquals(20, second.samples.size)
    }

    @Test
    fun clockGoingBack_flushesTheCurrentBatch() {
        val batcher = MotionBatcher()
        batcher.add(sample(5_000L))
        batcher.add(sample(5_050L))
        val batch = batcher.add(sample(4_000L))!!
        assertEquals(listOf(5_000L, 5_050L), batch.samples.map { it.timestamp })
    }

    @Test
    fun reset_dropsPendingSamples() {
        val batcher = MotionBatcher()
        for (i in 0 until 10) batcher.add(sample(1_000L + i * 50L))
        batcher.reset()
        assertNull(batcher.add(sample(1_600L)))
        assertNull(batcher.add(sample(2_500L)))
    }
}
