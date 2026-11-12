package com.example.safepath_test1.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.Executors

private const val TAG = "WearVoiceRelayService"

/** Must match WatchVoiceSender.PATH_VOICE (wear module) - the two modules can't share code. */
private const val PATH_VOICE = "/safepath/voice"

/** WatchVoiceSender의 "format" 값과 같아야 한다. */
private const val FORMAT_AAC = "aac"
private const val FORMAT_PCM16 = "pcm16"

// 서비스 객체는 이벤트마다 새로 만들어질 수 있어서, 조각을 이어 붙이는 버퍼·디코더·작업 순서는 앱 프로세스 단위로 둔다.
// 디코더와 버퍼는 voiceExecutor 스레드에서만 만진다.
private val voiceWindow = VoiceWindowBuffer()
private val voiceGate = VoiceChunkGate()
private val voiceDecoder = VoiceAacDecoder()
private val voiceExecutor = Executors.newSingleThreadExecutor()
private var lastArrivalAt = 0L

/**
 * Receives each audio chunk relayed from the watch (WatchVoiceRecorder + WatchVoiceSender,
 * wear module) over the Wearable Data Layer.
 *
 * 워치는 16kHz 모노 소리를 1초 단위로 AAC 압축해서 보낸다. 여기서 원본 소리(PCM)로 풀고, 조각을 이어 붙여
 * "최근 3초"를 만들고 WAV로 바꾼다. 밀려서 늦게 온 조각은 버린다(VoiceChunkGate).
 * AI 서버로 보내는 부분은 아직 없다(백엔드 주소가 정해지면 [handleChunk] 끝에 붙인다). 그때까지는
 * 도착 간격과 빠진 조각을 로그로 남기고, 디버그 빌드에서만 테스트용 WAV 파일을 저장한다(VoiceDebugRecorder).
 */
class WearVoiceRelayService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != PATH_VOICE) continue

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            // 예전 워치 앱(첨부 파일 방식)이 보낸 조각은 여기서 null이 되어 건너뛴다.
            val bytes = dataMap.getByteArray("audio") ?: continue
            val format = dataMap.getString("format", FORMAT_PCM16)
            val seq = dataMap.getLong("seq", -1L)
            val capturedAt = dataMap.getLong("capturedAt")
            val receivedAt = System.currentTimeMillis()

            // 압축 풀기·WAV 변환·파일 저장이 화면을 멈추지 않도록 별도 스레드 하나에서 순서대로 처리한다.
            voiceExecutor.execute { handleChunk(bytes, format, seq, capturedAt, receivedAt) }
        }
    }

    private fun handleChunk(bytes: ByteArray, format: String, seq: Long, capturedAt: Long, receivedAt: Long) {
        if (seq >= 0) {
            val decision = voiceGate.check(seq, capturedAt)
            if (!decision.accept) {
                Log.d(TAG, "Dropped voice chunk #$seq (${decision.reason})")
                return
            }
            if (decision.missing > 0) Log.w(TAG, "Missing ${decision.missing} voice chunk(s) before #$seq")
        }

        val pcm =
            try {
                if (format == FORMAT_AAC) voiceDecoder.decode(bytes) else bytes
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode voice chunk #$seq ($format)", e)
                voiceDecoder.release()
                return
            }

        val arrivalGap = if (lastArrivalAt == 0L) 0L else receivedAt - lastArrivalAt
        lastArrivalAt = receivedAt
        val window = voiceWindow.add(capturedAt, pcm)
        val wav = pcm16ToWav(window)
        // 도착 간격이 계속 1000ms 안팎이면 실시간으로 따라가고 있는 것이다.
        // delay는 폰과 워치 시계 차이가 섞여 있어 참고용이고, 늘어나는지만 보면 된다.
        Log.d(
            TAG,
            "Voice #$seq: $format ${bytes.size}B -> ${pcm.size * 1000L / VOICE_BYTES_PER_SECOND}ms, " +
                "arrival gap=${arrivalGap}ms, window=${window.size * 1000L / VOICE_BYTES_PER_SECOND}ms, " +
                "watch->phone delay=${receivedAt - capturedAt}ms",
        )
        VoiceDebugRecorder.save(this, wav, capturedAt)
        // TODO(서버 연동): 여기서 wav를 AI 서버로 보낸다.
    }
}
