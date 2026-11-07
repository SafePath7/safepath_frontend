package com.example.safepath_test1.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WearVoiceRelayService"

/** Must match WatchVoiceSender.PATH_VOICE (wear module) - the two modules can't share code. */
private const val PATH_VOICE = "/safepath/voice"

/**
 * Receives each raw PCM audio chunk relayed from the watch (WatchVoiceRecorder +
 * WatchVoiceSender, wear module) over the Wearable Data Layer.
 *
 * Watch -> phone half only, same as WearDataRelayService for motion data. What should actually
 * happen with this audio (store it, stream it to a guardian, run it through something) hasn't
 * been decided yet, so for now this just fetches the Asset and logs its size.
 */
class WearVoiceRelayService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != PATH_VOICE) continue

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val asset = dataMap.getAsset("audio") ?: continue
            val capturedAt = dataMap.getLong("capturedAt")

            Wearable.getDataClient(this)
                .getFdForAsset(asset)
                .addOnSuccessListener { assetFd ->
                    val bytes = assetFd.inputStream.use { it.readBytes() }
                    Log.d(TAG, "Received voice chunk: ${bytes.size} bytes, capturedAt=$capturedAt")
                }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to fetch voice chunk asset", e) }
        }
    }
}
