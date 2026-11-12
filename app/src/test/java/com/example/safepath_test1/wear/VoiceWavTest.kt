package com.example.safepath_test1.wear

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceWavTest {
    @Test
    fun wav_hasA44ByteHeaderFollowedByTheSamePcm() {
        val pcm = ByteArray(VOICE_BYTES_PER_SECOND) { (it % 100).toByte() }
        val wav = pcm16ToWav(pcm)
        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }

    @Test
    fun wav_headerDescribes16kMono16Bit() {
        val wav = pcm16ToWav(ByteArray(1000))
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 1000, header.getInt(4))
        assertEquals(1, header.getShort(20).toInt()) // PCM
        assertEquals(1, header.getShort(22).toInt()) // 모노
        assertEquals(16_000, header.getInt(24))
        assertEquals(32_000, header.getInt(28)) // 초당 바이트
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(1000, header.getInt(40))
    }

    @Test
    fun window_keepsOnlyTheMostRecentAudio() {
        val buffer = VoiceWindowBuffer(maxBytes = 4)
        buffer.add(1_000, byteArrayOf(1, 2))
        buffer.add(2_000, byteArrayOf(3, 4))
        assertArrayEquals(byteArrayOf(3, 4, 5, 6), buffer.add(3_000, byteArrayOf(5, 6)))
    }

    @Test
    fun window_handlesEmptyAndLongChunksByLength() {
        // 압축을 풀면 조각 길이가 들쭉날쭉하다(빈 조각, 1.5초짜리 조각).
        val buffer = VoiceWindowBuffer(maxBytes = 4)
        buffer.add(1_000, byteArrayOf(1))
        assertArrayEquals(byteArrayOf(1), buffer.add(2_000, byteArrayOf()))
        assertArrayEquals(byteArrayOf(3, 4, 5, 6), buffer.add(3_000, byteArrayOf(2, 3, 4, 5, 6)))
    }

    @Test
    fun window_startsOverAfterARecordingGap() {
        val buffer = VoiceWindowBuffer(maxBytes = 6, maxGapMs = 2_500)
        buffer.add(1_000, byteArrayOf(1))
        buffer.add(2_000, byteArrayOf(2))
        assertArrayEquals(byteArrayOf(9), buffer.add(60_000, byteArrayOf(9)))
    }
}
