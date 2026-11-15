package com.example.safepath_test1.wear.sensors

import android.content.Context
import android.util.Log
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.MotionKeys
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

private const val TAG = "WatchMotionSender"

/**
 * Synchronizes the latest WatchSensorManager reading to the phone over the Wearable Data Layer's
 * DataClient, mirroring the phone->watch pattern already used by WearMessenger (app module,
 * com.example.safepath_test1.wear.WearMessenger): DataClient + matching applicationId is the
 * combination confirmed working real-device + real-device for this project, so MessageClient is not
 * used. StateFlow and a single DataItem may coalesce updates; this is not a lossless sensor stream.
 *
 * Does not log on success - at 20Hz that would double the log volume WatchSensorManager already
 * produces. Failures are still logged.
 */
object WatchMotionSender {

    fun send(context: Context, motion: MotionData) {
        val putDataMapRequest =
            PutDataMapRequest.create(DataLayerPaths.MOTION).apply {
                dataMap.putFloat(MotionKeys.ACCEL_X, motion.accelX)
                dataMap.putFloat(MotionKeys.ACCEL_Y, motion.accelY)
                dataMap.putFloat(MotionKeys.ACCEL_Z, motion.accelZ)
                dataMap.putFloat(MotionKeys.GYRO_X, motion.gyroX)
                dataMap.putFloat(MotionKeys.GYRO_Y, motion.gyroY)
                dataMap.putFloat(MotionKeys.GYRO_Z, motion.gyroZ)
                dataMap.putLong(MotionKeys.TIMESTAMP, motion.timestamp)
            }
        val request = putDataMapRequest.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request).addOnFailureListener { e ->
            Log.e(TAG, "Failed to relay motion data to phone", e)
        }
    }
}
