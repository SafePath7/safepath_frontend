package com.example.safepath_test1.wear

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAacTest {
    /** 워치 adtsHeader와 같은 규칙(AAC-LC, 16kHz, 모노, CRC 없음)으로 만든 프레임. */
    private fun adtsFrame(payload: ByteArray): ByteArray {
        val length = payload.size + 7
        val header =
            byteArrayOf(
                0xFF.toByte(),
                0xF1.toByte(),
                0x60,
                (0x40 or (length shr 11)).toByte(),
                ((length shr 3) and 0xFF).toByte(),
                (((length and 7) shl 5) or 0x1F).toByte(),
                0xFC.toByte(),
            )
        return header + payload
    }

    @Test
    fun split_readsFormatAndPayloadOfEachFrame() {
        val first = ByteArray(100) { 1 }
        val second = ByteArray(80) { 2 }
        val frames = splitAdtsFrames(adtsFrame(first) + adtsFrame(second))
        assertEquals(2, frames.size)
        assertEquals(2, frames[0].profile)
        assertEquals(16_000, frames[0].sampleRateHz)
        assertEquals(1, frames[0].channels)
        assertArrayEquals(first, frames[0].payload)
        assertArrayEquals(second, frames[1].payload)
    }

    @Test
    fun audioSpecificConfig_for16kMonoLc() {
        val frame = splitAdtsFrames(adtsFrame(ByteArray(10))).single()
        assertArrayEquals(byteArrayOf(0x14, 0x08), frame.audioSpecificConfig())
    }

    @Test
    fun split_skipsGarbageAndDropsATruncatedFrame() {
        val good = adtsFrame(ByteArray(50) { 3 })
        val truncated = adtsFrame(ByteArray(50)).copyOf(20)
        val frames = splitAdtsFrames(byteArrayOf(0x00, 0x12) + good + truncated)
        assertEquals(1, frames.size)
        assertEquals(50, frames[0].payload.size)
    }

    @Test
    fun downmix_averagesLeftAndRight() {
        // 왼쪽 1000, 오른쪽 -2000 → (1000 - 2000) / 2 = -500
        val stereo = byteArrayOf(0xE8.toByte(), 0x03, 0x30, 0xF8.toByte())
        val mono = downmixToMono(stereo)
        assertEquals(2, mono.size)
        assertEquals(-500, ((mono[1].toInt() shl 8) or (mono[0].toInt() and 0xFF)).toShort().toInt())
    }

    @Test
    fun gate_acceptsInOrderAndReportsMissingChunks() {
        val gate = VoiceChunkGate(staleMs = 5_000)
        assertTrue(gate.check(seq = 0, capturedAt = 10_000).accept)
        assertEquals(0, gate.check(seq = 1, capturedAt = 11_000).missing)
        val skipped = gate.check(seq = 4, capturedAt = 14_000)
        assertTrue(skipped.accept)
        assertEquals(2, skipped.missing)
    }

    @Test
    fun gate_dropsLateAndStaleChunks() {
        val gate = VoiceChunkGate(staleMs = 5_000)
        gate.check(seq = 5, capturedAt = 20_000)
        assertFalse(gate.check(seq = 4, capturedAt = 19_000).accept) // 순서가 뒤바뀌어 늦게 옴
        assertFalse(gate.check(seq = 6, capturedAt = 14_000).accept) // 5초 넘게 오래됨
    }

    @Test
    fun gate_acceptsAWatchRestartWhoseSequenceStartsOver() {
        val gate = VoiceChunkGate(staleMs = 5_000)
        gate.check(seq = 50, capturedAt = 20_000)
        val restarted = gate.check(seq = 0, capturedAt = 30_000)
        assertTrue(restarted.accept)
        assertEquals("restart", restarted.reason)
    }
}
