package com.example.safepath_test1.wear

import android.util.Log
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.RiskAnswerKeys
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WearRiskRelayService"

/**
 * 워치가 보낸 위험 확인 질문의 답(RISK_ANSWER: SAFE / HELP / NO_RESPONSE)을 받는다.
 *
 * 지금은 로그만 남긴다. 위험 이벤트를 백엔드에 반영(PATCH)하는 것은 서버 주소와 이벤트 상태 값이 정해진 뒤에 붙인다.
 * HELP와 NO_RESPONSE일 때 워치는 SOS도 따로 보내므로, 폰 알림과 워치 답장은 WearSosRelayService가 그대로 맡는다.
 */
class WearRiskRelayService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != DataLayerPaths.RISK_ANSWER) continue

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            Log.i(
                TAG,
                "Risk answer received from watch " +
                    "eventId=${dataMap.getString(RiskAnswerKeys.EVENT_ID)} " +
                    "answer=${dataMap.getString(RiskAnswerKeys.ANSWER)} " +
                    "answeredAt=${dataMap.getLong(RiskAnswerKeys.ANSWERED_AT)}",
            )
        }
    }
}
