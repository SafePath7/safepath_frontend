package com.example.safepath_test1.wear.sensors

import android.content.Context
import android.util.Log
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.SosKeys
import com.google.android.gms.wearable.Wearable

private const val TAG = "WatchSosSender"

/**
 * Sends an SOS trigger from the watch to the paired phone over the Wearable Data Layer, using the
 * same DataClient pattern as WatchVoiceSender/WearMessenger (MessageClient failed real-device
 * testing - see WearMessenger's doc comment on the phone side for why).
 *
 * `triggeredAt` makes every press its own distinct DataItem so DataClient's identical-payload
 * dedup never swallows a second press sent in quick succession.
 *
 * "SOS sent to phone" 로그는 워치의 Data Layer에 넣었다는 뜻이지 폰에 도착했다는 뜻이 아니다.
 * 도착은 폰의 답장(SOS_ACK)으로 안다. [triggeredAt]은 위험상황 화면의 요청 시각과 같은 값을 넘겨야
 * 답장이 어느 SOS의 것인지 맞출 수 있다.
 */
object WatchSosSender {

    fun send(context: Context, triggeredAt: Long) {
        DataLayerSender.putUrgent(
            context = context,
            path = DataLayerPaths.SOS,
            tag = TAG,
            failureMessage = "Failed to send SOS to phone",
            successMessage = "SOS sent to phone",
        ) {
            putLong(SosKeys.TRIGGERED_AT, triggeredAt)
        }
    }

    /**
     * 지금 연결된 폰이 있는지 [onResult]로 알린다(메인 스레드). 조회에 실패하면 연결 안 됨(false)으로 본다.
     * 연결이 없어도 SOS는 워치에 남아 있다가 연결되면 넘어간다. 이 결과는 화면에 그 사실을 알리는 데만 쓴다.
     */
    fun checkPhoneConnected(context: Context, onResult: (Boolean) -> Unit) {
        Wearable.getNodeClient(context)
            .connectedNodes
            .addOnSuccessListener { nodes -> onResult(nodes.isNotEmpty()) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check connected phone", e)
                onResult(false)
            }
    }
}
