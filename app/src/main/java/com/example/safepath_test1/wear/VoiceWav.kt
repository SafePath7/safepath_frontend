package com.example.safepath_test1.wear

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 워치 WatchVoiceRecorder가 녹음하는 형식: 16kHz, 모노, 16비트 PCM. 워치 쪽 값과 같아야 한다. */
internal const val VOICE_SAMPLE_RATE_HZ = 16_000

/** 1초 분량의 바이트 수(16kHz x 2바이트). */
internal const val VOICE_BYTES_PER_SECOND = VOICE_SAMPLE_RATE_HZ * 2

/**
 * 16비트 PCM 원본 소리 앞에 44바이트 WAV 머리말을 붙인다. 소리 자체는 바꾸지 않아서 음질 손실이 없다.
 * AI 서버로 보낼 때와 테스트용 파일로 저장할 때 이 형식을 쓴다.
 */
internal fun pcm16ToWav(
    pcm: ByteArray,
    sampleRateHz: Int = VOICE_SAMPLE_RATE_HZ,
    channels: Int = 1,
): ByteArray {
    val bitsPerSample = 16
    val blockAlign = channels * bitsPerSample / 8
    val header =
        ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size) // 이 뒤로 남은 파일 크기
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // fmt 블록 크기
            putShort(1) // 1 = 압축 안 한 PCM
            putShort(channels.toShort())
            putInt(sampleRateHz)
            putInt(sampleRateHz * blockAlign) // 초당 바이트 수
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }
    return header.array() + pcm
}

/**
 * 워치가 보내는 소리 조각을 이어 붙여 "최근 [maxBytes]만큼(기본 3초)"을 만든다.
 * AI는 한 번에 3초 분량을 보되, 조각이 올 때마다(약 1초마다) 새 판단을 낼 수 있다.
 * 압축을 풀면 조각 길이가 0.5~1.5초로 들쭉날쭉해서, 조각 개수가 아니라 소리 길이로 자른다.
 * 녹음이 [maxGapMs] 넘게 끊겼다가 다시 시작되면 이전 소리는 버린다. 순서 정리는 VoiceChunkGate가 먼저 한다.
 */
internal class VoiceWindowBuffer(
    private val maxBytes: Int = VOICE_BYTES_PER_SECOND * 3,
    private val maxGapMs: Long = 2_500L,
) {
    private var window = ByteArray(0)
    private var lastCapturedAt = Long.MIN_VALUE

    /** 조각을 넣고, 지금까지 모인 최근 소리(PCM)를 돌려준다. */
    @Synchronized
    fun add(capturedAt: Long, pcm: ByteArray): ByteArray {
        if (lastCapturedAt != Long.MIN_VALUE && capturedAt - lastCapturedAt > maxGapMs) {
            window = ByteArray(0)
        }
        lastCapturedAt = maxOf(lastCapturedAt, capturedAt)
        val combined = window + pcm
        window =
            if (combined.size > maxBytes) combined.copyOfRange(combined.size - maxBytes, combined.size)
            else combined
        return window
    }
}

/**
 * 워치가 보낸 조각의 순번(seq)과 녹음 시각으로, 받아도 되는 조각인지 판단한다.
 * - 가장 최근 조각보다 [staleMs] 넘게 오래된 조각은 버린다(실시간 감지에 쓸모없는 밀린 소리).
 * - 이미 받은 순번 이하로 늦게 도착한 조각도 버린다. 다만 녹음 시각이 더 새로우면
 *   워치 앱이 다시 시작돼 순번이 0부터 다시 시작한 것이므로 받는다.
 * - 순번이 건너뛰면 빠진 개수를 알려 준다(로그용).
 */
internal class VoiceChunkGate(private val staleMs: Long = 5_000L) {
    data class Decision(val accept: Boolean, val missing: Long, val reason: String)

    private var lastSeq = -1L
    private var newestCapturedAt = Long.MIN_VALUE

    @Synchronized
    fun check(seq: Long, capturedAt: Long): Decision {
        if (newestCapturedAt == Long.MIN_VALUE) {
            remember(seq, capturedAt)
            return Decision(accept = true, missing = 0, reason = "first")
        }
        if (capturedAt < newestCapturedAt - staleMs) {
            return Decision(accept = false, missing = 0, reason = "stale")
        }
        if (seq <= lastSeq) {
            if (capturedAt > newestCapturedAt) {
                remember(seq, capturedAt)
                return Decision(accept = true, missing = 0, reason = "restart")
            }
            return Decision(accept = false, missing = 0, reason = "late")
        }
        val missing = seq - lastSeq - 1
        remember(seq, capturedAt)
        return Decision(accept = true, missing = missing, reason = "ok")
    }

    private fun remember(seq: Long, capturedAt: Long) {
        lastSeq = seq
        newestCapturedAt = maxOf(newestCapturedAt, capturedAt)
    }
}
