package com.example.safepath_test1.wear

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

private const val TAG = "VoiceAacDecoder"

/** ADTS 머리말의 표본 주파수 번호 → Hz (MPEG-4 표준 표). */
private val ADTS_SAMPLE_RATES =
    intArrayOf(96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025, 8_000, 7_350)

/** ADTS로 감싼 AAC 프레임 하나. [payload]는 머리말을 뗀 순수 AAC 데이터다. */
internal class AdtsFrame(
    val profile: Int,
    val sampleRateIndex: Int,
    val channels: Int,
    val payload: ByteArray,
) {
    val sampleRateHz: Int
        get() = ADTS_SAMPLE_RATES.getOrElse(sampleRateIndex) { 0 }

    /** 디코더 설정에 넣는 AudioSpecificConfig(2바이트). */
    fun audioSpecificConfig(): ByteArray {
        val config = (profile shl 11) or (sampleRateIndex shl 7) or (channels shl 3)
        return byteArrayOf((config shr 8).toByte(), config.toByte())
    }
}

/**
 * 워치가 보낸 ADTS 데이터를 프레임 단위로 나눈다. 동기 신호(0xFFF)가 아닌 바이트는 건너뛰고,
 * 끝이 잘린 프레임은 버린다.
 */
internal fun splitAdtsFrames(data: ByteArray): List<AdtsFrame> {
    val frames = ArrayList<AdtsFrame>()
    var i = 0
    while (i + 7 <= data.size) {
        val b0 = data[i].toInt() and 0xFF
        val b1 = data[i + 1].toInt() and 0xFF
        if (b0 != 0xFF || (b1 and 0xF0) != 0xF0) {
            i++
            continue
        }
        val b2 = data[i + 2].toInt() and 0xFF
        val b3 = data[i + 3].toInt() and 0xFF
        val b4 = data[i + 4].toInt() and 0xFF
        val b5 = data[i + 5].toInt() and 0xFF
        val headerLength = if ((b1 and 1) == 1) 7 else 9 // CRC가 있으면 2바이트 더
        val frameLength = ((b3 and 0x03) shl 11) or (b4 shl 3) or ((b5 and 0xE0) shr 5)
        if (frameLength < headerLength || i + frameLength > data.size) break
        frames.add(
            AdtsFrame(
                profile = ((b2 shr 6) and 3) + 1,
                sampleRateIndex = (b2 shr 2) and 0x0F,
                channels = ((b2 and 1) shl 2) or ((b3 shr 6) and 3),
                payload = data.copyOfRange(i + headerLength, i + frameLength),
            )
        )
        i += frameLength
    }
    return frames
}

/** 16비트 스테레오를 모노로(좌우 평균). 디코더가 스테레오로 내보내는 기기를 대비한다. */
internal fun downmixToMono(stereo: ByteArray): ByteArray {
    val mono = ByteArray(stereo.size / 2)
    var i = 0
    var o = 0
    while (i + 3 < stereo.size) {
        val left = ((stereo[i + 1].toInt() shl 8) or (stereo[i].toInt() and 0xFF)).toShort().toInt()
        val right = ((stereo[i + 3].toInt() shl 8) or (stereo[i + 2].toInt() and 0xFF)).toShort().toInt()
        val mixed = (left + right) / 2
        mono[o] = mixed.toByte()
        mono[o + 1] = (mixed shr 8).toByte()
        i += 4
        o += 2
    }
    return mono
}

/**
 * 워치가 보낸 AAC(ADTS)를 16비트 PCM으로 푼다. 1초 조각들이 이어지는 소리라 디코더 하나를 계속 쓴다.
 * 한 스레드에서만 불러야 한다(WearVoiceRelayService의 작업 스레드).
 */
internal class VoiceAacDecoder {
    private var codec: MediaCodec? = null
    private var configKey: String? = null
    private var outputChannels = 1
    private var framesFed = 0L
    private val info = MediaCodec.BufferInfo()

    fun decode(adts: ByteArray): ByteArray {
        val frames = splitAdtsFrames(adts)
        if (frames.isEmpty()) return ByteArray(0)
        val decoder = ensureCodec(frames.first())
        val out = ByteArrayOutputStream(frames.size * 2_048)
        for (frame in frames) {
            var waitRounds = 0
            while (true) {
                val index = decoder.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val buffer = decoder.getInputBuffer(index) ?: error("AAC decoder returned no input buffer")
                    buffer.clear()
                    buffer.put(frame.payload)
                    val presentationUs = framesFed * 1_024L * 1_000_000L / frame.sampleRateHz
                    decoder.queueInputBuffer(index, 0, frame.payload.size, presentationUs, 0)
                    framesFed++
                    drain(decoder, out, timeoutUs = 0)
                    break
                }
                check(++waitRounds <= 100) { "AAC decoder did not accept input" }
                drain(decoder, out, timeoutUs = 0)
            }
        }
        drain(decoder, out, timeoutUs = 5_000)
        val pcm = out.toByteArray()
        return if (outputChannels == 2) downmixToMono(pcm) else pcm
    }

    private fun ensureCodec(first: AdtsFrame): MediaCodec {
        val key = "${first.profile}/${first.sampleRateIndex}/${first.channels}"
        codec?.let { if (key == configKey) return it }
        release()
        val format =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, first.sampleRateHz, first.channels).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(first.audioSpecificConfig()))
            }
        val created = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            created.configure(format, null, null, 0)
            created.start()
        } catch (e: Exception) {
            created.release()
            throw e
        }
        codec = created
        configKey = key
        outputChannels = first.channels
        framesFed = 0
        return created
    }

    private fun drain(decoder: MediaCodec, out: ByteArrayOutputStream, timeoutUs: Long) {
        while (true) {
            val index = decoder.dequeueOutputBuffer(info, timeoutUs)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = decoder.outputFormat
                    outputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (rate != VOICE_SAMPLE_RATE_HZ) {
                        Log.w(TAG, "Decoder outputs $rate Hz, expected $VOICE_SAMPLE_RATE_HZ Hz")
                    }
                }
                index < 0 -> continue
                else -> {
                    if (info.size > 0) {
                        val buffer = decoder.getOutputBuffer(index)
                        if (buffer != null) {
                            val pcm = ByteArray(info.size)
                            buffer.position(info.offset)
                            buffer.get(pcm, 0, info.size)
                            out.write(pcm)
                        }
                    }
                    decoder.releaseOutputBuffer(index, false)
                }
            }
        }
    }

    fun release() {
        codec?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        codec = null
        configKey = null
    }
}
