package com.example.safepath_test1.wear.launch

import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.toRiskCheck
import com.example.safepath_test1.wear.state.isRiskCheckFresh
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WatchRiskCheckListener"

/**
 * 폰의 위험 확인 질문(RISK_CHECK)이 왔을 때 워치 앱이 화면에 없으면 앱을 연다.
 *
 * 질문을 받아 확인 질문 화면을 띄우는 것은 앱 화면의 WatchDataReceiver가 한다. 이 서비스는 앱이 꺼져 있거나
 * 뒤로 가 있어서 질문을 놓치는 경우만 메운다. 앱이 열리면 WatchDataReceiver가 이미 와 있는 질문을 읽어 띄운다.
 *
 * - 앱이 보이면 아무것도 하지 않는다. 다시 열면 화면이 새로 만들어져 지금 상태(위험상황 등)를 잃을 수 있다.
 * - 오래된 질문(연결이 끊겼다가 늦게 온 것)이면 열지 않는다. 앱도 그 질문을 띄우지 않는다(AlertState).
 * - 화면이 뒤에 살아 있으면 새로 만들지 않고 앞으로만 가져온다(SINGLE_TOP). 그 화면은 이미 질문을 받아 두었다.
 *   (폰의 "워치 앱 열기"와 달리 CLEAR_TOP만 쓰면 화면이 새로 만들어진다.)
 */
class WatchRiskCheckListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != DataLayerPaths.RISK_CHECK) continue

            val check = DataMapItem.fromDataItem(event.dataItem).dataMap.toRiskCheck()
            val visible = WatchAppVisibility.isVisible
            val fresh = isRiskCheckFresh(check.requestedAt, System.currentTimeMillis())
            Log.i(
                TAG,
                "Risk check received eventId=${check.eventId} visible=$visible fresh=$fresh",
            )
            if (visible || !fresh) continue

            WatchAppOpener.open(
                context = this,
                intentFlags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                notice = RISK_CHECK_NOTICE,
            )
        }
    }
}

private val RISK_CHECK_NOTICE =
    OpenAppNotice(
        channelId = "safepath_risk_check",
        channelName = "SafePath 위험 확인",
        importance = NotificationManager.IMPORTANCE_HIGH,
        notificationId = 1002,
        title = "괜찮으신가요?",
        text = "탭하여 응답하기",
    )
