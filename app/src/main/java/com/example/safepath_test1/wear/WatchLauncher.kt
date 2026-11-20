package com.example.safepath_test1.wear

import android.content.Context
import android.util.Log
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.LaunchKeys
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

private const val TAG = "WatchLauncher"

/**
 * 연결된 워치가 있으면 워치의 SafePath 앱을 열라고 알린다. 폰 앱이 켜질 때 한 번 부른다.
 *
 * WearMessenger와 같이 MessageClient가 아니라 DataClient를 쓴다. 실기기 테스트에서 MessageClient의
 * sendMessage()는 "Failed to deliver message to AppKey"로 계속 실패했고, DataClient + 같은 applicationId
 * 조합만 실기기 두 대에서 동작이 확인됐다.
 *
 * DataClient는 DataMap 내용이 실제로 바뀔 때만 onDataChanged를 부르므로, 이전 실행 요청과 같은 것으로
 * 걸러지지 않게 매번 요청 시각(`requestedAt`)을 담는다.
 */
internal object WatchLauncher {

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
