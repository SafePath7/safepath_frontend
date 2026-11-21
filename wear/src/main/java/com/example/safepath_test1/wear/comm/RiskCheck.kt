package com.example.safepath_test1.wear.comm

import com.google.android.gms.wearable.DataMap

/**
 * 폰이 보낸 위험 확인 질문([DataLayerPaths.RISK_CHECK]).
 * [riskType]은 값 목록이 아직 정해지지 않아 기록만 하고 화면에는 쓰지 않는다.
 */
internal data class RiskCheck(
    val eventId: String,
    val riskType: String,
    val requestedAt: Long,
)

/** RISK_CHECK DataMap을 읽는다. 값이 빠져 있으면 빈 문자열과 0이 되고, 그런 질문은 AlertState가 무시한다. */
internal fun DataMap.toRiskCheck(): RiskCheck =
    RiskCheck(
        eventId = getString(RiskCheckKeys.EVENT_ID, ""),
        riskType = getString(RiskCheckKeys.RISK_TYPE, ""),
        requestedAt = getLong(RiskCheckKeys.REQUESTED_AT),
    )
