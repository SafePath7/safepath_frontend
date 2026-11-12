package com.example.safepath_test1.wear.sensors

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AdtsHeaderTest {
    @Test
    fun header_for16kMonoLc_matchesTheStandardLayout() {
        val header = adtsHeader(frameLength = 107, sampleRateHz = 16_000, channels = 1)
        val expected = intArrayOf(0xFF, 0xF1, 0x60, 0x40, 0x0D, 0x7F, 0xFC).map { it.toByte() }.toByteArray()
        assertArrayEquals(expected, header)
    }

    @Test
    fun header_storesTheFrameLength() {
        val header = adtsHeader(frameLength = 3000, sampleRateHz = 16_000, channels = 1)
        val b3 = header[3].toInt() and 0xFF
        val b4 = header[4].toInt() and 0xFF
        val b5 = header[5].toInt() and 0xFF
        assertEquals(3000, ((b3 and 0x03) shl 11) or (b4 shl 3) or ((b5 and 0xE0) shr 5))
    }
}
