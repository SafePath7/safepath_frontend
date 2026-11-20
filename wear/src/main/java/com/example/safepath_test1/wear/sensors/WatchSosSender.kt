package com.example.safepath_test1.wear.sensors

import android.content.Context
import android.util.Log
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.SosKeys
import com.google.android.gms.wearable.Wearable

private const val TAG = "WatchSosSender"

/**
 * 워치의 SOS를 Wearable Data Layer로 짝지어진 폰에 보낸다. WatchVoiceSender, WearMessenger와 같은
 * DataClient 방식이다(MessageClient는 실기기 테스트에서 실패했다. 이유는 폰 쪽 WearMessenger 주석 참고).
 *
 * `triggeredAt`이 누를 때마다 달라서, 짧은 간격으로 두 번 눌러도 DataClient가 같은 내용으로 보고
 * 두 번째를 버리지 않는다.
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
            .addOnSuccessListener { nodes ->
                Log.i(TAG, "Connected phone check: ${nodes.size} node(s) ${nodes.map { it.displayName }}")
                onResult(nodes.isNotEmpty())
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check connected phone", e)
                onResult(false)
            }
    }
}
