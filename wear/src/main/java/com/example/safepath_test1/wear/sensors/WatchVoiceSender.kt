package com.example.safepath_test1.wear.sensors

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.Executors

private const val TAG = "WatchVoiceSender"

/** Phone-side WearVoiceRelayService (app module) listens on this same path - keep both in sync. */
const val PATH_VOICE = "/safepath/voice"

/** "format" 값. 폰 WearVoiceRelayService도 같은 값을 쓴다. */
private const val FORMAT_AAC = "aac"
private const val FORMAT_PCM16 = "pcm16"

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

    fun send(context: Context, audioBytes: ByteArray) {
        val appContext = context.applicationContext
        val capturedAt = System.currentTimeMillis()
        executor.execute { encodeAndSend(appContext, audioBytes, capturedAt) }
    }

    private fun encodeAndSend(context: Context, pcm: ByteArray, capturedAt: Long) {
        val aac = encodeOrNull(pcm)
        // 인코더가 이번 조각의 소리를 다음 조각과 함께 내보내면 이번 결과는 비어 있다. 보낼 게 없으면 건너뛴다.
        if (aac != null && aac.isEmpty()) return
        val request =
            PutDataMapRequest.create(PATH_VOICE)
                .apply {
                    if (aac != null) {
                        dataMap.putString("format", FORMAT_AAC)
                        dataMap.putByteArray("audio", aac)
                    } else {
                        dataMap.putString("format", FORMAT_PCM16)
                        dataMap.putByteArray("audio", pcm)
                    }
                    dataMap.putLong("seq", nextSeq++)
                    dataMap.putLong("capturedAt", capturedAt)
                }
                .asPutDataRequest()
                .setUrgent()

        Wearable.getDataClient(context)
            .putDataItem(request)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to relay audio chunk to phone", e) }
    }

    private fun encodeOrNull(pcm: ByteArray): ByteArray? {
        if (encoderUnavailable) return null
        return try {
            val current = encoder ?: VoiceAacEncoder().also { encoder = it }
            current.encode(pcm)
        } catch (e: Exception) {
            Log.e(TAG, "AAC encoding failed - sending raw PCM instead", e)
            encoder?.release()
            encoder = null
            encoderUnavailable = true
            null
        }
    }
}
