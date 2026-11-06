package com.example.safepath_test1.wear.sensors

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

private const val TAG = "WatchVoiceSender"

/** Phone-side WearVoiceRelayService (app module) listens on this same path - keep both in sync. */
const val PATH_VOICE = "/safepath/voice"

/**
 * Relays each raw PCM audio chunk from WatchVoiceRecorder to the phone over the Wearable Data
 * Layer, mirroring WatchMotionSender's phone-relay pattern but using a DataClient Asset instead
 * of plain DataMap fields - an audio chunk (tens of KB) is far bigger than the handful of floats
 * a motion reading needs.
 *
 * `capturedAt` makes every chunk's DataItem distinct so DataClient's identical-payload dedup
 * never swallows one (two consecutive audio chunks are never byte-identical anyway, but the
 * timestamp keeps this robust regardless).
 */
object WatchVoiceSender {

    fun send(context: Context, audioBytes: ByteArray) {
        val putDataMapRequest =
            PutDataMapRequest.create(PATH_VOICE).apply {
                dataMap.putAsset("audio", Asset.createFromBytes(audioBytes))
                dataMap.putLong("capturedAt", System.currentTimeMillis())
            }
        val request = putDataMapRequest.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context)
            .putDataItem(request)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to relay audio chunk to phone", e) }
    }
}
