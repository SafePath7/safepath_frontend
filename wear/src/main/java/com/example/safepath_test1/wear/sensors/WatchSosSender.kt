package com.example.safepath_test1.wear.sensors

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

private const val TAG = "WatchSosSender"

/** Phone-side WearSosRelayService (app module) listens on this same path - keep both in sync. */
const val PATH_SOS = "/safepath/sos"

/**
 * Sends an SOS trigger from the watch to the paired phone over the Wearable Data Layer, using the
 * same DataClient pattern as WatchVoiceSender/WearMessenger (MessageClient failed real-device
 * testing - see WearMessenger's doc comment on the phone side for why).
 *
 * `triggeredAt` makes every press its own distinct DataItem so DataClient's identical-payload
 * dedup never swallows a second press sent in quick succession.
 */
object WatchSosSender {

    fun send(context: Context) {
        val putDataMapRequest =
            PutDataMapRequest.create(PATH_SOS).apply {
                dataMap.putLong("triggeredAt", System.currentTimeMillis())
            }
        val request = putDataMapRequest.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context)
            .putDataItem(request)
            .addOnSuccessListener { Log.i(TAG, "SOS sent to phone") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to send SOS to phone", e) }
    }
}
