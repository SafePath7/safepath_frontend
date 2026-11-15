package com.example.safepath_test1.wear.sensors

import android.content.Context
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.SosKeys

private const val TAG = "WatchSosSender"

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
        DataLayerSender.putUrgent(
            context = context,
            path = DataLayerPaths.SOS,
            tag = TAG,
            failureMessage = "Failed to send SOS to phone",
            successMessage = "SOS sent to phone",
        ) {
            putLong(SosKeys.TRIGGERED_AT, System.currentTimeMillis())
        }
    }
}
