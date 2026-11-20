package com.example.safepath_test1.wear.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.safepath_test1.wear.comm.MapImage
import com.example.safepath_test1.wear.comm.StatusUpdate
import com.example.safepath_test1.wear.state.AlertState
import com.example.safepath_test1.wear.state.CHECK_IN_COUNTDOWN_MS
import com.example.safepath_test1.wear.state.RouteMapState
import com.example.safepath_test1.wear.state.RouteStatus
import com.example.safepath_test1.wear.state.isStatusStale
import com.example.safepath_test1.wear.state.statusOrStale

/**
 * 워치 화면이 읽는 상태를 한 곳에 모은 것. 폰에서 받은 길안내 상태·경로·지도, 위험 감지 흐름(AlertState), 해제 신호가 들어 있고,
 * 값이 바뀌는 길은 아래 함수들뿐이다. 센서 전송, 마이크, 통신처럼 부작용이 있는 일은 여기서 하지 않고
 * WearEffects.kt의 효과 함수와 WearApp이 한다. 그래서 화면 없이 단위 테스트할 수 있다.
 */
@Stable
internal class WearAppState {
    var status: RouteStatus by mutableStateOf<RouteStatus>(RouteStatus.Idle)
        private set

    var routeMap: RouteMapState? by mutableStateOf<RouteMapState?>(null)
        private set

    var mapBackground: MapBackground? by mutableStateOf<MapBackground?>(null)
        private set

    /** 폰이 마지막으로 보낸 위험상황 해제 신호의 시각(ms). 0이면 아직 없음. */
    var releaseSignalAt: Long by mutableStateOf(0L)
        private set

    var alert: AlertState by mutableStateOf(AlertState())
        private set

    /** 확인 질문 화면이 보여 줄 남은 시간(ms). */
    var checkInRemainingMs: Long by mutableStateOf(CHECK_IN_COUNTDOWN_MS)
        private set

    /** 폰이 마지막으로 보낸 상태의 시각(ms). 화면에 쓰지 않아서 상태 값이 아니다. */
    private var lastReceivedAt = 0L

    /** 길안내 중이면 마이크를 켜고 지도를 보여 준다. */
    val isNavigating: Boolean
        get() = status is RouteStatus.Found

    // ---- 폰에서 받은 값 ----

    fun onStatus(update: StatusUpdate, now: Long = System.currentTimeMillis()) {
        lastReceivedAt = update.updatedAt
        status = statusOrStale(update.status, update.updatedAt, now)
        routeMap = update.routeMap
    }

    fun onMapImage(image: MapImage) {
        mapBackground = image.toBackground()
    }

    fun onRelease(signalAt: Long) {
        releaseSignalAt = signalAt
    }

    /** 폰에서 마지막으로 받은 값이 너무 오래됐으면 연결 끊김으로 바꾼다. 아직 아무것도 못 받았으면 그대로 둔다. */
    fun markStaleIfNeeded(now: Long = System.currentTimeMillis()) {
        if (lastReceivedAt > 0 && isStatusStale(lastReceivedAt, now) && status !is RouteStatus.Stale) {
            status = RouteStatus.Stale
        }
    }

    // ---- 위험 감지 흐름 ----

    fun startCheckIn(now: Long) {
        checkInRemainingMs = CHECK_IN_COUNTDOWN_MS
        alert = alert.startCheckIn(now)
    }

    fun showCheckInRemaining(remainingMs: Long) {
        checkInRemainingMs = remainingMs
    }

    fun requestHelp(now: Long) {
        alert = alert.requestHelp(now)
    }

    fun dismissAlert() {
        alert = alert.dismiss()
    }

    /** 폰이 SOS를 받았다는 답장. 지금 위험상황의 것이면 전달됨으로 바꾼다. */
    fun onSosAck(triggeredAt: Long) {
        alert = alert.acknowledgedBy(triggeredAt)
    }

    /** 요청 시각이 [requestedAt]인 SOS를 보낼 때 연결된 폰이 없었다. */
    fun markSosNotConnected(requestedAt: Long) {
        alert = alert.markNotConnected(requestedAt)
    }

    /** 폰의 해제 신호가 이 위험상황의 것이면 평소 화면으로 돌아간다. */
    fun releaseIfSignalled() {
        alert = alert.releasedBy(releaseSignalAt)
    }
}

@Composable
internal fun rememberWearAppState(): WearAppState = remember { WearAppState() }
