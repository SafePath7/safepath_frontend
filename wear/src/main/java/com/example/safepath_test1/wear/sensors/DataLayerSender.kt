package com.example.safepath_test1.wear.sensors

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * 워치 → 폰 DataItem 전송의 공통 부분. 모션·SOS·음성 Sender가 같은 방식으로 보낸다.
 *
 * 항상 긴급(setUrgent)으로 보낸다. DataClient는 내용이 같은 DataItem을 중복으로 보고 무시하므로,
 * 호출하는 쪽이 [fill]에서 `triggeredAt`, `capturedAt`처럼 매번 달라지는 값을 반드시 넣어야 한다.
 * 실패는 항상 로그로 남기고, 성공 로그는 [successMessage]가 있을 때만 남긴다(20Hz 모션처럼 잦은 전송은 생략).
 */
internal object DataLayerSender {

    fun putUrgent(
        context: Context,
        path: String,
        tag: String,
        failureMessage: String,
        successMessage: String? = null,
        fill: DataMap.() -> Unit,
    ) {
        val request =
            PutDataMapRequest.create(path).apply { dataMap.fill() }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context)
            .putDataItem(request)
            .addOnSuccessListener { successMessage?.let { Log.i(tag, it) } }
            .addOnFailureListener { e -> Log.e(tag, failureMessage, e) }
    }
}
