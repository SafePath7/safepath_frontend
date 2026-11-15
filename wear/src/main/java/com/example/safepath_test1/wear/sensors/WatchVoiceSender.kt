package com.example.safepath_test1.wear.sensors

import android.content.Context
import android.util.Log
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.VoiceKeys
import java.util.concurrent.Executors

private const val TAG = "WatchVoiceSender"

/**
 * WatchVoiceRecorder가 녹음한 1초 조각을 AAC로 압축해서 폰으로 보낸다.
 *
 * 압축 없이(초당 32KB) 보내면 워치→폰 통로가 초당 16~18KB밖에 못 실어 날라 지연이 계속 쌓였다(실측).
 * AAC(초당 약 4KB)로 줄여서 실시간으로 따라가게 했다. 압축에 실패하는 기기에서는 원본 PCM으로 보낸다.
 *
 * 조각마다 순번(`seq`)과 녹음 시각(`capturedAt`)을 붙인다. 폰은 순번으로 빠지거나 늦게 온 조각을 알아내고,
 * `capturedAt` 덕분에 DataItem 내용이 매번 달라져 같은 내용으로 무시되지 않는다.
 * 압축은 화면이 멈추지 않도록 별도 스레드 하나에서 순서대로 한다.
 */
object WatchVoiceSender {
    private val executor = Executors.newSingleThreadExecutor()
    private var encoder: VoiceAacEncoder? = null
    private var encoderUnavailable = false
    private var nextSeq = 0L

    /**
     * 새 녹음을 시작할 때 부른다. 순번을 0부터 다시 세고, 지난번에 압축이 안 됐던 기기도 다시 시도하도록 압축기를 새로 만든다.
     * 순번이 0으로 돌아가도 폰은 `capturedAt`이 더 새로우면 새 녹음으로 받아들인다(VoiceChunkGate).
     * 압축기를 만들고 없애는 일은 조각을 보내는 스레드에서 해서, 보내던 조각과 겹치지 않는다.
     */
    fun startSession() {
        executor.execute {
            releaseEncoder()
            encoderUnavailable = false
            nextSeq = 0L
        }
    }

    /** 녹음이 끝났을 때 부른다. 압축기가 잡고 있는 코덱 자원을 놓는다. */
    fun stopSession() {
        executor.execute { releaseEncoder() }
    }

    fun send(context: Context, audioBytes: ByteArray) {
        val appContext = context.applicationContext
        val capturedAt = System.currentTimeMillis()
        executor.execute { encodeAndSend(appContext, audioBytes, capturedAt) }
    }

    private fun encodeAndSend(context: Context, pcm: ByteArray, capturedAt: Long) {
        val aac = encodeOrNull(pcm)
        // 인코더가 이번 조각의 소리를 다음 조각과 함께 내보내면 이번 결과는 비어 있다. 보낼 게 없으면 건너뛴다.
        if (aac != null && aac.isEmpty()) return
        DataLayerSender.putUrgent(
            context = context,
            path = DataLayerPaths.VOICE,
            tag = TAG,
            failureMessage = "Failed to relay audio chunk to phone",
        ) {
            if (aac != null) {
                putString(VoiceKeys.FORMAT, VoiceKeys.FORMAT_AAC)
                putByteArray(VoiceKeys.AUDIO, aac)
            } else {
                putString(VoiceKeys.FORMAT, VoiceKeys.FORMAT_PCM16)
                putByteArray(VoiceKeys.AUDIO, pcm)
            }
            putLong(VoiceKeys.SEQ, nextSeq++)
            putLong(VoiceKeys.CAPTURED_AT, capturedAt)
        }
    }

    private fun encodeOrNull(pcm: ByteArray): ByteArray? {
        if (encoderUnavailable) return null
        return try {
            val current = encoder ?: VoiceAacEncoder().also { encoder = it }
            current.encode(pcm)
        } catch (e: Exception) {
            Log.e(TAG, "AAC encoding failed - sending raw PCM instead", e)
            releaseEncoder()
            encoderUnavailable = true
            null
        }
    }

    private fun releaseEncoder() {
        encoder?.release()
        encoder = null
    }
}
