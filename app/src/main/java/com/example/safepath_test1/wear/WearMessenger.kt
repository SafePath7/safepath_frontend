package com.example.safepath_test1.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * 동일한 applicationId를 사용하는 휴대폰과 워치 사이에 DataClient로 경로 상태를 전송한다.
 * MessageClient는 실기기에서 전송 오류가 발생해, 실기기에서 확인된 DataClient를 사용한다.
 * 동일한 데이터는 중복 전송되지 않으므로 매 호출마다 updatedAt을 포함한다.
 */
object WearMessenger {
    private const val TAG = "WearMessenger"
    const val PATH_STATUS = "/safepath/status"

    private fun send(
        context: Context,
        state: String,
        routeType: String? = null,
        distanceMeters: Double? = null,
        destinationName: String? = null,
    ) {
        val putDataMapRequest =
            PutDataMapRequest.create(PATH_STATUS).apply {
                dataMap.putString("state", state)
                if (routeType != null) dataMap.putString("routeType", routeType)
                if (distanceMeters != null) dataMap.putDouble("distanceMeters", distanceMeters)
                if (destinationName != null) dataMap.putString("destinationName", destinationName)
                dataMap.putLong("updatedAt", System.currentTimeMillis())
            }
        val request = putDataMapRequest.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context)
            .putDataItem(request)
            .addOnSuccessListener {
                Log.i(
                    TAG,
                    "DataClient synced state=$state routeType=$routeType destination=$destinationName",
                )
            }
            .addOnFailureListener { e -> Log.e(TAG, "DataClient sync failed for state=$state", e) }
    }

    fun sendIdle(context: Context) {
        send(context, "IDLE")
    }

    fun sendRouteSearching(context: Context) {
        send(context, "SEARCHING")
    }

    fun sendRouteFound(
        context: Context,
        routeType: String,
        destinationName: String?,
        distanceMeters: Double,
    ) {
        send(
            context,
            "FOUND",
            routeType = routeType,
            distanceMeters = distanceMeters,
            destinationName = destinationName,
        )
    }
}
