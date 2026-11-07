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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private const val TAG = "WatchVoiceRecorder"
private const val SAMPLE_RATE_HZ = 16_000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

/** Roughly how much raw audio each chunk holds before being handed off to WatchVoiceSender. */
private const val CHUNK_DURATION_MS = 1_500L

/**
 * RMS value (on the raw 16-bit PCM scale) that maps to a normalized level of 1.0. Normal speaking
 * voice tends to land well under Short.MAX_VALUE (32767), so this is a practical ceiling rather
 * than the theoretical max - tune it after testing on a real watch mic if it feels too
 * dull/sensitive.
 */
private const val LEVEL_NORMALIZATION_CEILING = 4000.0

/**
 * Captures raw PCM audio from the watch's microphone in short chunks while route guidance is
 * active (started/stopped from WearApp.kt based on RouteStatus). No speech-to-text here - this
 * just hands off raw audio bytes; WatchVoiceSender relays each chunk to the phone the same way
 * WatchMotionSender relays sensor readings, just over a DataClient Asset instead of a DataMap
 * field since a chunk is far bigger than a few floats.
 */
class WatchVoiceRecorder(private val context: Context) {

    private val _audioChunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 4)
    val audioChunks: SharedFlow<ByteArray> = _audioChunks.asSharedFlow()

    /** Live mic input level, normalized to 0f (silence) .. 1f (loud), for a UI indicator. */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private var scope: CoroutineScope? = null
    private var audioRecord: AudioRecord? = null

    val hasPermission: Boolean
        get() =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    /** Safe to call repeatedly - a no-op if already recording or if RECORD_AUDIO isn't granted. */
    fun start() {
        if (scope != null) return
        if (!hasPermission) {
            Log.w(TAG, "RECORD_AUDIO not granted - skipping voice capture")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "AudioRecord.getMinBufferSize failed: $minBufferSize")
            return
        }

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
                return
            }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return
        }

        audioRecord = record
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        record.startRecording()
        Log.i(TAG, "Started voice capture")

        // 16-bit mono PCM: SAMPLE_RATE_HZ samples/sec * 2 bytes/sample.
        val bytesPerChunk = (SAMPLE_RATE_HZ * 2 * CHUNK_DURATION_MS / 1000).toInt()

        newScope.launch {
            val chunk = ByteArray(bytesPerChunk)
            while (true) {
                var offset = 0
                while (offset < chunk.size) {
                    // Request at most minBufferSize at a time instead of "whatever's left in the
                    // 1.5s chunk" - AudioRecord.read() blocks until the requested amount is
                    // available, so asking for up to ~48000 bytes in one call made it block for
                    // nearly the full 1.5s, and updateAudioLevel() below only ran that rarely too
                    // (the screen-tint lag the user noticed). Small requests return in tens of ms.
                    val toRead = minOf(minBufferSize, chunk.size - offset)
                    val read = record.read(chunk, offset, toRead)
                    if (read <= 0) break
                    updateAudioLevel(chunk, offset, read)
                    offset += read
                }
                if (offset <= 0) break
                _audioChunks.emit(if (offset == chunk.size) chunk.copyOf() else chunk.copyOf(offset))
            }
        }
    }

    /** Computes RMS over [length] bytes of little-endian 16-bit PCM starting at [offset] and
     *  publishes it (normalized 0f..1f) to [_audioLevel]. */
    private fun updateAudioLevel(buffer: ByteArray, offset: Int, length: Int) {
        if (length < 2) return
        var sumSquares = 0.0
        var sampleCount = 0
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low
            sumSquares += (sample * sample).toDouble()
            sampleCount++
            i += 2
        }
        if (sampleCount == 0) return
        val rms = sqrt(sumSquares / sampleCount)
        _audioLevel.value = (rms / LEVEL_NORMALIZATION_CEILING).toFloat().coerceIn(0f, 1f)
    }

    /** Call when leaving the Found state (or on dispose) so the mic doesn't keep draining the battery. */
    fun stop() {
        scope?.cancel()
        scope = null
        audioRecord?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioRecord.stop() called while not recording", e)
            }
            it.release()
        }
        audioRecord = null
        _audioLevel.value = 0f
        Log.i(TAG, "Stopped voice capture")
    }
}
