package com.example.safepath_test1.wear.sensors

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.ByteArrayOutputStream

/** ADTS 머리말에 넣는 표본 주파수 번호(MPEG-4 표준 표). 16kHz는 8. */
private val ADTS_SAMPLE_RATE_INDEX =
    mapOf(
        96_000 to 0,
        88_200 to 1,
        64_000 to 2,
        48_000 to 3,
        44_100 to 4,
        32_000 to 5,
        24_000 to 6,
        22_050 to 7,
        16_000 to 8,
        12_000 to 9,
        11_025 to 10,
        8_000 to 11,
        7_350 to 12,
    )

/**
 * AAC 프레임 하나 앞에 붙이는 7바이트 ADTS 머리말. 프레임마다 형식 정보가 들어 있어서,
 * 폰이 1초 조각을 하나씩 받아도(중간 조각이 빠져도) 각각 풀 수 있다.
 * [frameLength]는 머리말 7바이트를 포함한 길이다.
 */
internal fun adtsHeader(frameLength: Int, sampleRateHz: Int, channels: Int): ByteArray {
    val profile = 2 // AAC-LC
    val frequencyIndex =
        ADTS_SAMPLE_RATE_INDEX[sampleRateHz] ?: error("Unsupported sample rate: $sampleRateHz")
    return byteArrayOf(
        0xFF.toByte(),
        0xF1.toByte(), // 동기 신호 나머지 + MPEG-4 + CRC 없음
        (((profile - 1) shl 6) or (frequencyIndex shl 2) or (channels shr 2)).toByte(),
        (((channels and 3) shl 6) or (frameLength shr 11)).toByte(),
        ((frameLength shr 3) and 0xFF).toByte(),
        (((frameLength and 7) shl 5) or 0x1F).toByte(),
        0xFC.toByte(),
    )
}

/**
 * 녹음한 16비트 PCM을 AAC-LC(ADTS)로 압축한다. 16kHz 모노 32kbps면 초당 약 4KB로,
 * 원본(초당 32KB)의 약 1/8이라 워치→폰 통로(실측 초당 16~18KB)에서 밀리지 않는다.
 * 소리가 이어지도록 인코더 하나를 계속 쓰며, 한 스레드에서만 불러야 한다(WatchVoiceSender).
 */
internal class VoiceAacEncoder(
    private val sampleRateHz: Int = 16_000,
    private val channels: Int = 1,
    bitRate: Int = 32_000,
) {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val info = MediaCodec.BufferInfo()
    private var samplesFed = 0L

    init {
        val format =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRateHz, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
            }
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (e: Exception) {
            codec.release()
            throw e
        }
    }

    /** [pcm](16비트 리틀엔디언)을 넣고, 지금까지 나온 AAC 프레임들을 ADTS로 이어 붙여 돌려준다. */
    fun encode(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(pcm.size / 6)
        var offset = 0
        var waitRounds = 0
        while (offset < pcm.size) {
            val index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) {
                val buffer = codec.getInputBuffer(index) ?: error("AAC encoder returned no input buffer")
                buffer.clear()
                // 16비트 표본이 반으로 잘리지 않게 짝수 바이트만 넣는다.
                val length = minOf(buffer.remaining(), pcm.size - offset) and 1.inv()
                check(length > 0) { "AAC encoder input buffer is too small" }
                buffer.put(pcm, offset, length)
                val presentationUs = samplesFed * 1_000_000L / sampleRateHz
                codec.queueInputBuffer(index, 0, length, presentationUs, 0)
                samplesFed += length / (2 * channels)
                offset += length
                waitRounds = 0
            } else {
                check(++waitRounds <= 100) { "AAC encoder did not accept input" }
            }
            drain(out, timeoutUs = 0)
        }
        drain(out, timeoutUs = 5_000)
        return out.toByteArray()
    }

    private fun drain(out: ByteArrayOutputStream, timeoutUs: Long) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, timeoutUs)
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (index < 0) continue // 출력 형식 변경 같은 알림
            val isCodecConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
            if (!isCodecConfig && info.size > 0) {
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null) {
                    val frame = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(frame, 0, info.size)
                    out.write(adtsHeader(info.size + 7, sampleRateHz, channels))
                    out.write(frame)
                }
            }
            codec.releaseOutputBuffer(index, false)
        }
    }

    fun release() {
        try {
            codec.stop()
        } catch (_: Exception) {
        }
        codec.release()
    }
}
