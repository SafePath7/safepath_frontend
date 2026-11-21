package com.example.safepath_test1.wear.sensors

import android.content.Context
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.RiskAnswerKeys
import com.example.safepath_test1.wear.state.RiskAnswer

private const val TAG = "WatchRiskAnswerSender"

/**
 * 위험 확인 질문의 답(SAFE / HELP / NO_RESPONSE)을 폰의 WearRiskRelayService로 보낸다. WatchSosSender와 같은 방식이다.
 *
 * `answeredAt`이 답할 때마다 달라서 DataClient가 중복으로 보고 버리지 않는다.
 * 워치가 쓴 이 값은 워치의 Data Layer에도 남아서, 앱을 다시 켤 때 이미 답한 질문을 다시 띄우지 않는 데 쓴다(WatchDataReceiver).
 * "sent" 로그는 워치의 Data Layer에 넣었다는 뜻이지 폰에 도착했다는 뜻이 아니다.
 */
internal object WatchRiskAnswerSender {

    fun send(context: Context, answer: RiskAnswer) {
        DataLayerSender.putUrgent(
            context = context,
            path = DataLayerPaths.RISK_ANSWER,
            tag = TAG,
            failureMessage = "Failed to send risk answer to phone",
            successMessage =
                "Risk answer sent to phone eventId=${answer.eventId} answer=${answer.answer}",
        ) {
            putString(RiskAnswerKeys.EVENT_ID, answer.eventId)
            putString(RiskAnswerKeys.ANSWER, answer.answer)
            putLong(RiskAnswerKeys.ANSWERED_AT, answer.answeredAt)
        }
    }
}
