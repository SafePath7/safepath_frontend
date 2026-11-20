package com.example.safepath_test1.wear.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "WatchVoiceRecorder"
private const val SAMPLE_RATE_HZ = 16_000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

/**
 * 조각 하나에 담는 녹음 길이. WatchVoiceSender로 넘기기 전에 이만큼 모은다.
 * 1초: 위험 감지가 늦어지지 않게 짧게 자른다. 폰이 조각을 이어 붙여 최근 3초를 만든다(WearVoiceRelayService).
 */
private const val CHUNK_DURATION_MS = 1_000L

/** 녹음기가 오류로 멈췄을 때 다시 열어 보는 최대 횟수(조각을 하나라도 받으면 0부터 다시 센다)와 재시도 간격. */
private const val MAX_CONSECUTIVE_FAILURES = 3
private const val RETRY_DELAY_MS = 500L

/**
 * 길안내 중에 워치 마이크로 원본 PCM 소리를 짧은 조각으로 녹음한다(시작과 정지는 WearEffects.kt의 VoiceCaptureEffect가 한다).
 * 음성 인식은 여기서 하지 않는다. 원본 바이트를 그대로 내보내면 WatchVoiceSender가 AAC로 압축해서
 * WatchMotionSender가 센서 값을 보내는 것과 같은 방식으로 폰에 전달한다.
 *
 * 녹음 도중 AudioRecord가 오류를 내면(마이크를 다른 앱이 가져갔을 때 등) 잠깐 뒤에 다시 열어 이어서 녹음한다.
 * 계속 실패하면 포기하고 로그만 남긴다.
 */
class WatchVoiceRecorder(private val context: Context) {

    private val _audioChunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 4)
    val audioChunks: SharedFlow<ByteArray> = _audioChunks.asSharedFlow()

    @Volatile private var scope: CoroutineScope? = null
    @Volatile private var audioRecord: AudioRecord? = null

    val hasPermission: Boolean
        get() =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    /** 여러 번 불러도 안전하다. 이미 녹음 중이거나 RECORD_AUDIO 권한이 없으면 아무것도 하지 않는다. */
    fun start() {
        if (scope != null) return
        if (!hasPermission) {
            Log.w(TAG, "RECORD_AUDIO not granted - skipping voice capture")
            return
        }

        val minBufferSize =
            AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "AudioRecord.getMinBufferSize failed: $minBufferSize")
            return
        }

        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        Log.i(TAG, "Started voice capture")
        newScope.launch {
            try {
                captureWithRetry(minBufferSize)
            } finally {
                // 스스로 포기하고 끝난 경우에만 비운다. stop()이 이미 비웠거나 새로 start()됐다면 그대로 둔다.
                if (scope === newScope) scope = null
            }
        }
    }

    /** 길안내가 끝나거나 화면이 사라질 때 불러서 마이크가 배터리를 계속 쓰지 않게 한다. */
    fun stop() {
        scope?.cancel()
        scope = null
        // 읽는 중인 AudioRecord를 멈춰 대기를 풀어 준다. 해제는 녹음 코루틴이 마무리하며 한다.
        audioRecord?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioRecord.stop() called while not recording", e)
            }
        }
        Log.i(TAG, "Stopped voice capture")
    }

    private suspend fun captureWithRetry(minBufferSize: Int) {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val record = createAudioRecord(minBufferSize)
            val gotAudio =
                if (record == null) {
                    false
                } else {
                    audioRecord = record
                    try {
                        record.startRecording()
                        readChunks(record, minBufferSize)
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "AudioRecord.startRecording() failed", e)
                        false
                    } finally {
                        release(record)
                        if (audioRecord === record) audioRecord = null
                    }
                }
            if (!currentCoroutineContext().isActive) return
            failures = if (gotAudio) 0 else failures + 1
            if (failures >= MAX_CONSECUTIVE_FAILURES) {
                Log.e(TAG, "Giving up voice capture after $failures failures in a row")
                return
            }
            Log.w(TAG, "Voice capture interrupted - reopening the microphone")
            delay(RETRY_DELAY_MS)
        }
    }

    private fun createAudioRecord(minBufferSize: Int): AudioRecord? {
        val record =
            try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_HZ,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBufferSize * 2,
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "AudioRecord init failed", e)
                return null
            }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return null
        }
        return record
    }

    /**
     * 읽기가 실패하거나 취소될 때까지 1초 조각을 만들어 내보낸다. 조각을 하나라도 내보냈으면 true.
     * 실패한 시점까지 모인 소리가 있으면 짧은 조각으로라도 내보낸다.
     */
    private suspend fun readChunks(record: AudioRecord, minBufferSize: Int): Boolean {
        // 16비트 모노 PCM: 초당 SAMPLE_RATE_HZ 샘플 * 샘플당 2바이트.
        val bytesPerChunk = (SAMPLE_RATE_HZ * 2 * CHUNK_DURATION_MS / 1000).toInt()
        val chunk = ByteArray(bytesPerChunk)
        var emittedAny = false
        while (currentCoroutineContext().isActive) {
            var offset = 0
            var failed = false
            while (offset < chunk.size) {
                // 남은 양을 한 번에 요청하지 않고 minBufferSize씩 요청한다. AudioRecord.read()는 요청한 만큼
                // 모일 때까지 막히므로, 크게 요청하면 정지 요청에도 오래 반응하지 못한다.
                val toRead = minOf(minBufferSize, chunk.size - offset)
                val read = record.read(chunk, offset, toRead)
                if (read <= 0) {
                    failed = true
                    break
                }
                offset += read
            }
            if (offset > 0) {
                val emitted = if (offset == chunk.size) chunk.copyOf() else chunk.copyOf(offset)
                _audioChunks.emit(emitted)
                emittedAny = true
            }
            if (failed) {
                Log.w(TAG, "AudioRecord.read() failed - the recorder will be reopened")
                return emittedAny
            }
        }
        return emittedAny
    }

    private fun release(record: AudioRecord) {
        try {
            record.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioRecord.stop() called while not recording", e)
        }
        record.release()
    }
}
