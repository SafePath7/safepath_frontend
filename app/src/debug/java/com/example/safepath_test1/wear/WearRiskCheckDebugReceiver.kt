package com.example.safepath_test1.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "WearRiskCheckDebugReceiver"

/**
 * 디버그 빌드 전용. adb 브로드캐스트로 폰이 워치에 위험 확인 질문(RISK_CHECK)을 보내게 한다.
 * 위험 이벤트를 만드는 서버 쪽이 아직 없어서, 워치 확인 질문 흐름을 실기기에서 시험하려고 둔다.
 * src/debug에만 있어서 릴리스 빌드에는 들어가지 않는다.
 *
 * ```
 * adb shell am broadcast -n com.example.safepath_test1/.wear.WearRiskCheckDebugReceiver \
 *     --es eventId test-1 --es riskType FALL
 * ```
 * eventId를 빼면 매번 새 ID(debug-현재시각)를 쓰고, riskType을 빼면 DEBUG를 쓴다.
 * 워치는 이미 답한 eventId를 다시 띄우지 않으므로, 같은 ID로 다시 시험하려면 ID를 바꾼다.
 */
class WearRiskCheckDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId =
            intent.getStringExtra(EXTRA_EVENT_ID)?.takeIf { it.isNotBlank() }
                ?: "debug-${System.currentTimeMillis()}"
        val riskType = intent.getStringExtra(EXTRA_RISK_TYPE)?.takeIf { it.isNotBlank() } ?: "DEBUG"
        Log.i(TAG, "Debug risk check requested eventId=$eventId riskType=$riskType")
        WearMessenger.sendRiskCheck(context, eventId, riskType)
    }

    private companion object {
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_RISK_TYPE = "riskType"
    }
}
