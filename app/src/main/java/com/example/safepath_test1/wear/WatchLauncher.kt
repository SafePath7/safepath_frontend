package com.example.safepath_test1.wear

import android.content.Context
import android.util.Log
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.LaunchKeys
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

private const val TAG = "WatchLauncher"

/**
 * Tells the paired watch to open the SafePath watch app, if a watch is currently connected.
 * Meant to be called once when the phone app starts.
 *
 * Uses DataClient (not MessageClient), same as WearMessenger: MessageClient's sendMessage() kept
 * failing real-device testing with "Failed to deliver message to AppKey", while DataClient +
 * matching applicationId is the combination confirmed working real-device + real-device.
 *
 * `requestedAt` is included so DataClient doesn't dedupe this against a previous launch request
 * (it only fires onDataChanged when something in the DataMap actually changes).
 */
object WatchLauncher {

    fun launchIfWatchConnected(context: Context) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.i(TAG, "No connected watch node - skipping launch request")
                    return@addOnSuccessListener
                }
                send(context)
            }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to query connected nodes", e) }
    }

    private fun send(context: Context) {
        val putDataMapRequest =
            PutDataMapRequest.create(DataLayerPaths.LAUNCH_WATCH).apply {
                dataMap.putLong(LaunchKeys.REQUESTED_AT, System.currentTimeMillis())
            }
        val request = putDataMapRequest.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context)
            .putDataItem(request)
            .addOnSuccessListener { Log.i(TAG, "Sent watch-launch request") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to send watch-launch request", e) }
    }
}
