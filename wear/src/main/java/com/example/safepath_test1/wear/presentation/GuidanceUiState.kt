package com.example.safepath_test1.wear.presentation

import com.example.safepath_test1.wear.geo.LatLng
import com.example.safepath_test1.wear.geo.RoutePath

/** 남은 거리가 이 값(m) 아래면 도착한 것으로 본다. */
internal const val ARRIVED_METERS = 20.0

/** 길안내 화면(W-01)이 그릴 값. 화면은 이 값만 읽고, 어떻게 계산했는지는 [buildGuidanceUiState]가 맡는다. */
internal data class GuidanceUiState(
    /** 화면 테두리 링이 채워질 비율(0..1). 현재 위치를 아직 모르면 0. */
    val ringProgress: Float,
    /** 아래쪽 한 줄: "남은 430m" 또는 위치를 모를 때 "총 1.2km". */
    val summaryText: String,
    /** 위쪽 안내 상자의 화살표. */
    val bannerDirection: TurnDirection,
    /** 위쪽 안내 상자의 글자: "120m 후 우회전", "목적지 도착", "위치 확인 중". */
    val bannerText: String,
    val arrived: Boolean,
)

/**
 * 경로와 현재 위치로 길안내 화면에 보여 줄 값을 만든다.
 * 현재 위치가 와야 남은 거리, 다음 회전, 도착 여부를 알 수 있다. 아직이면 경로 전체 거리만 보여 준다.
 * 다음 회전은 폰이 회전 안내 목록([steps])을 보냈으면 그걸 쓰고, 없으면 경로 선이 꺾인 모양으로 추측한다.
 *
 * @param path 폰이 보낸 경로. 아직 없으면 null.
 * @param totalDistanceKm 폰이 알려 준 경로 전체 거리(km, 소수 첫째 자리 문자열).
 */
internal fun buildGuidanceUiState(
    path: RoutePath?,
    current: LatLng?,
    steps: List<RouteStep>,
    totalDistanceKm: String,
): GuidanceUiState {
    val live = if (path != null && current != null) path.progressAt(current) else null
    val turn =
        if (path != null && live != null) {
            if (steps.isNotEmpty()) {
                computeNextTurnFromSteps(path, steps, live.traveledMeters)
            } else {
                computeNextTurn(path, live.traveledMeters)
            }
        } else {
            null
        }
    val arrived = live != null && live.remainingMeters < ARRIVED_METERS

    return GuidanceUiState(
        ringProgress = live?.progress ?: 0f,
        summaryText =
            if (live != null) "남은 ${formatDistance(live.remainingMeters)}" else "총 ${totalDistanceKm}km",
        bannerDirection = if (arrived || turn == null) TurnDirection.Straight else turn.direction,
        bannerText =
            when {
                arrived -> "목적지 도착"
                turn != null -> turnBannerText(turn)
                else -> "위치 확인 중"
            },
        arrived = arrived,
    )
}
