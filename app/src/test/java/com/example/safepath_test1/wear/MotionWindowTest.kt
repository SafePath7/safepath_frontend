package com.example.safepath_test1.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionWindowTest {
    private fun sample(t: Long, ax: Float = 0f) = MotionSample(t, ax, 0f, 9.8f, 0f, 0f, 0f)

    private fun batch(from: Long, count: Int = 20) = List(count) { sample(from + it * 50L) }

    @Test
    fun motionSamplesOf_pairsTheArraysBySample() {
        val samples =
            motionSamplesOf(
                longArrayOf(1_000L, 1_050L),
                floatArrayOf(1f, 2f),
                floatArrayOf(3f, 4f),
                floatArrayOf(5f, 6f),
                floatArrayOf(7f, 8f),
                floatArrayOf(9f, 10f),
                floatArrayOf(11f, 12f),
            )
        assertEquals(
            listOf(
                MotionSample(1_000L, 1f, 3f, 5f, 7f, 9f, 11f),
                MotionSample(1_050L, 2f, 4f, 6f, 8f, 10f, 12f),
            ),
            samples,
        )
    }

    @Test
    fun motionSamplesOf_returnsEmptyForMismatchedOrMissingArrays() {
        val one = floatArrayOf(1f)
        assertTrue(motionSamplesOf(longArrayOf(1L, 2L), one, one, one, one, one, one).isEmpty())
        assertTrue(motionSamplesOf(null, one, one, one, one, one, one).isEmpty())
        assertTrue(motionSamplesOf(longArrayOf(1L), one, one, one, one, one, null).isEmpty())
    }

    @Test
    fun window_returnsTheLastThreeSecondsInTimeOrder() {
        val buffer = MotionWindowBuffer()
        // 두 번째 묶음이 먼저 도착해도 시각 순으로 꺼낸다.
        buffer.add(batch(2_000L))
        buffer.add(batch(1_000L))
        buffer.add(batch(3_000L))
        buffer.add(batch(4_000L))
        val window = buffer.window(endAt = 4_950L)
        assertEquals(60, window.size)
        assertEquals(2_000L, window.first().tMs)
        assertEquals(4_950L, window.last().tMs)
        assertEquals(window.map { it.tMs }.sorted(), window.map { it.tMs })
    }

    @Test
    fun add_keepsOneSamplePerTimestamp() {
        val buffer = MotionWindowBuffer()
        buffer.add(listOf(sample(1_000L, ax = 1f)))
        buffer.add(listOf(sample(1_000L, ax = 2f)))
        assertEquals(1, buffer.size())
        assertEquals(2f, buffer.window(endAt = 1_000L).single().ax, 0f)
    }

    @Test
    fun add_dropsSamplesOlderThanTheKeepTime() {
        val buffer = MotionWindowBuffer(keepMs = 2_000L)
        buffer.add(batch(1_000L))
        buffer.add(batch(4_000L))
        assertTrue(buffer.window(endAt = 2_000L, lengthMs = 1_500L).isEmpty())
        assertEquals(20, buffer.size())
    }
}
