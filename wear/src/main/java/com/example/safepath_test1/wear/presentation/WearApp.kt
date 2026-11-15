package com.example.safepath_test1.wear.presentation

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.safepath_test1.wear.comm.WatchDataReceiver
import com.example.safepath_test1.wear.state.AlertStage
import com.example.safepath_test1.wear.state.AlertState
import com.example.safepath_test1.wear.state.CHECK_IN_COUNTDOWN_MS
import com.example.safepath_test1.wear.state.RouteMapState
import com.example.safepath_test1.wear.state.RouteStatus
import com.example.safepath_test1.wear.state.isStatusStale
import com.example.safepath_test1.wear.state.statusOrStale
import com.example.safepath_test1.wear.sensors.WatchMotionSender
import com.example.safepath_test1.wear.sensors.WatchSensorManager
import com.example.safepath_test1.wear.sensors.WatchSosSender
import com.example.safepath_test1.wear.sensors.WatchVoiceRecorder
import com.example.safepath_test1.wear.sensors.WatchVoiceSender
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val TAG = "WearApp"

/**
 * 워치 앱의 최상위 화면. 폰이 Wearable Data Layer(DataClient)로 보내는 길안내 상태·경로·지도 이미지·해제 신호를
 * 받아 보여 주고, 워치의 센서와 마이크 값을 폰으로 보낸다. MessageClient가 아니라 DataClient를 쓰는 이유는
 * 폰 쪽 WearMessenger.kt를 본다. 블루투스로 이미 짝지어진 폰만 값을 보낼 수 있어서 별도 페어링 화면은 없다.
 */
@Composable
fun WearApp() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<RouteStatus>(RouteStatus.Idle) }
    var lastReceivedAt by remember { mutableStateOf(0L) }
    var routeMap by remember { mutableStateOf<RouteMapState?>(null) }
    var mapBackground by remember { mutableStateOf<MapBackground?>(null) }
    var releaseSignalAt by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    // 최신 센서 값을 폰의 WearDataRelayService로 전달한다. AI 서버 연동은 아직 구현되지 않았다.
    val sensorManager = remember { WatchSensorManager(context) }
    DisposableEffect(Unit) {
        sensorManager.startAccelCollection()
        sensorManager.startGyroCollection()
        onDispose { sensorManager.stopCollection() }
    }
    LaunchedEffect(sensorManager) {
        sensorManager.motionData.collect { motion ->
            motion?.let { WatchMotionSender.send(context, it) }
        }
    }

    // Mic capture while route guidance is active (FOUND) - stopped the instant we leave that
    // state so it doesn't keep listening (or draining the battery) once navigation ends.
    val voiceRecorder = remember { WatchVoiceRecorder(context) }
    val isNavigating = status is RouteStatus.Found
    LaunchedEffect(isNavigating) {
        if (isNavigating) voiceRecorder.start() else voiceRecorder.stop()
    }
    LaunchedEffect(voiceRecorder) {
        voiceRecorder.audioChunks.collect { chunk -> WatchVoiceSender.send(context, chunk) }
    }
    DisposableEffect(Unit) { onDispose { voiceRecorder.stop() } }

    // 폰이 보내는 값(상태, 지도 이미지, 해제 신호)을 받는다. 받는 방법과 순서 규칙은 WatchDataReceiver에 있다.
    DisposableEffect(Unit) {
        val receiver =
            WatchDataReceiver(
                context = context,
                scope = scope,
                onStatus = { update ->
                    lastReceivedAt = update.updatedAt
                    status = statusOrStale(update.status, update.updatedAt)
                    routeMap = update.routeMap
                    Log.i(TAG, "New status=$status")
                },
                onMapImage = { image -> mapBackground = image.toBackground() },
                onRelease = { updatedAt -> releaseSignalAt = updatedAt },
            )
        receiver.start()
        onDispose { receiver.stop() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            if (lastReceivedAt > 0 && isStatusStale(lastReceivedAt) && status !is RouteStatus.Stale) {
                status = RouteStatus.Stale
            }
        }
    }

    // 위험 감지 흐름의 상태. 전이 규칙은 AlertState에 있고, 여기서는 호출만 한다.
    var alert by remember { mutableStateOf(AlertState()) }
    var checkInRemainingMs by remember { mutableStateOf(CHECK_IN_COUNTDOWN_MS) }

    // 지금은 방패 아이콘 길게 누르기(테스트용)로 호출된다. AI 서버가 연결되면 실제 위험 신호에서 호출한다.
    fun startCheckIn() {
        checkInRemainingMs = CHECK_IN_COUNTDOWN_MS
        alert = alert.startCheckIn(System.currentTimeMillis())
    }

    // 보호자 연동 전이라 SOS 버튼과 같은 경로(워치 → 폰 알림)로 보낸다.
    fun requestHelp() {
        WatchSosSender.send(context)
        alert = alert.requestHelp(System.currentTimeMillis())
    }

    // 폰에서 해제 신호가 오면 위험상황 화면을 닫고 평소 화면(길안내)으로 돌아간다.
    LaunchedEffect(releaseSignalAt) { alert = alert.releasedBy(releaseSignalAt) }

    LaunchedEffect(alert.stage, alert.checkInStartedAt) {
        if (alert.stage != AlertStage.CheckIn) return@LaunchedEffect
        while (true) {
            val remaining = alert.checkInRemainingMs(System.currentTimeMillis())
            if (remaining <= 0) {
                requestHelp()
                break
            }
            checkInRemainingMs = remaining
            delay(100)
        }
    }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when (alert.stage) {
                AlertStage.CheckIn ->
                    CheckInScreen(
                        remainingMs = checkInRemainingMs,
                        totalMs = CHECK_IN_COUNTDOWN_MS,
                        onOkay = { alert = alert.dismiss() },
                        onHelp = { requestHelp() },
                    )
                AlertStage.Alerted ->
                    GuardianAlertedScreen(
                        requestedAt = alert.helpRequestedAt,
                        onTestRelease = { alert = alert.dismiss() },
                    )
                AlertStage.None -> MainScreen(
                    status = status,
                    routeMap = routeMap,
                    mapBackground = mapBackground,
                    // 명세: SOS는 어느 상태에서든 바로 위험상황으로 보낸다.
                    onSos = { requestHelp() },
                    onShieldLongPress = { startCheckIn() },
                )
            }
        }
    }
}

/** 평소 화면: 대기/탐색/연결 끊김 상태, 또는 길안내 중이면 UI-W1(길안내)과 UI-W2(이동 상태)를 옆으로 넘겨 본다. */
@Composable
private fun MainScreen(
    status: RouteStatus,
    routeMap: RouteMapState?,
    mapBackground: MapBackground?,
    onSos: () -> Unit,
    onShieldLongPress: () -> Unit,
) {
    val isNavigating = status is RouteStatus.Found
    Box(
        modifier =
            Modifier.fillMaxSize().background(if (isNavigating) WatchColors.Bg else Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (status is RouteStatus.Found) {
            val pagerState = rememberPagerState(pageCount = { 2 })
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    GuidancePage(status, routeMap, mapBackground)
                } else {
                    MovingStatusPage(onShieldLongPress = onShieldLongPress)
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (status) {
                    RouteStatus.Idle -> {
                        Text(text = "SafePath", textAlign = TextAlign.Center)
                        Text(text = "대기 중", textAlign = TextAlign.Center)
                    }
                    RouteStatus.Searching -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(text = "탐색 중...", textAlign = TextAlign.Center)
                    }
                    is RouteStatus.Found -> Unit
                    RouteStatus.Stale -> {
                        Text(text = "⏱️ 연결 끊김", textAlign = TextAlign.Center)
                        Text(text = "폰과 블루투스 연결을 확인하세요", textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // Pinned to the screen's own bottom-center rather than laid out inside the Column
        // above, so it can't get pushed off-screen by how much the rest of that content
        // needs (RouteMiniMap + up to 3 text lines already run close to a 40mm watch's
        // visible height, with nothing there to scroll it). Single tap sends immediately -
        // this is an emergency control, not a settings toggle, so a hold-to-confirm gesture
        // would only slow down the one moment it needs to be fast. Only reaches the paired
        // phone for now (WatchSosSender -> WearSosRelayService); see that service's doc
        // comment for why it doesn't reach a guardian yet.
        if (isNavigating) {
            val s = rememberWatchScale()
            // 명세: 워치 하단 가운데, 화면 아래에서 22(384 기준) 위.
            WatchSosButton(
                onClick = onSos,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = s.dp(22)),
            )
        }
    }
}

/**
 * UI-W1(W-01) 경로 안내 페이지(전체 화면 지도 초안).
 * 화면 전체를 지도가 채우고(한 손가락으로 끌어서 둘러보기, 두 손가락 핀치로 확대/축소, 두 번 탭하면 내 위치로) 그 위에 링, 시각, 이동중 배지, 위쪽 회전 안내 상자(y80),
 * 아래쪽에 남은 거리 한 줄을 올린다. 위치는 명세의 384dp 원형 기준을 화면 크기에 맞게 줄여 쓴다.
 * 다음 회전은 폰이 회전 안내 목록(routeSteps)을 보내면 그걸 쓰고, 없으면 경로 모양에서 워치가 추측한다.
 * SOS 버튼은 MainScreen이 하단에 따로 붙인다.
 */
@Composable
private fun GuidancePage(
    current: RouteStatus.Found,
    routeMap: RouteMapState?,
    mapBackground: MapBackground?,
) {
    val s = rememberWatchScale()
    // 손가락으로 지도를 둘러보는 상태: 보는 지름(핀치)과 내 위치에서 옮긴 거리(끌기).
    // 값은 지도가 그려질 때만 읽어서, 끌거나 확대해도 이 화면 전체가 아니라 지도 그림만 다시 그려진다.
    val mapView = remember { MapViewState() }
    // 손을 떼고 잠시 지나면 지도가 다시 내 위치를 따라간다(확대 정도는 유지).
    LaunchedEffect(mapView) {
        snapshotFlow { mapView.lastTouchAt }
            .collectLatest { touchedAt ->
                if (touchedAt == 0L) return@collectLatest
                delay(PAN_RETURN_DELAY_MS)
                mapView.returnToCurrent()
            }
    }
    // 경로와 현재 위치에서 화면에 보여 줄 값(진행 링, 다음 회전, 도착 여부, 거리 글자)을 만든다.
    val ui =
        remember(routeMap, current.distanceKm) {
            buildGuidanceUiState(
                path = routeMap?.path,
                current = routeMap?.current,
                steps = routeMap?.steps.orEmpty(),
                totalDistanceKm = current.distanceKm,
            )
        }

    val sosReserved = s.sosReservedHeight()
    val panelHeight = sosReserved + s.dp(86)

    Box(modifier = Modifier.fillMaxSize()) {
        routeMap?.let { map ->
            RouteMiniMap(
                routePath = map.path,
                destination = map.destination,
                current = map.current,
                view = mapView,
                modifier = Modifier.fillMaxSize(),
                background = mapBackground,
            )
            // 끌기/핀치를 받는 투명 영역. 위쪽 시각·배지와 아래쪽 글자·SOS 영역은 빼서,
            // 그 위에서 옆으로 쓸면 기존처럼 다음 페이지로 넘어간다.
            Box(
                Modifier.fillMaxSize()
                    .padding(top = s.dp(70), bottom = s.sosReservedHeight() + s.dp(40))
                    // 두 번 탭하면 바로 내 위치, 기본 확대로 돌아온다.
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { mapView.reset() })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            mapView.applyGesture(
                                panXPx = pan.x,
                                panYPx = pan.y,
                                zoom = zoom,
                                widthPx = size.width.toFloat(),
                                nowMs = System.currentTimeMillis(),
                            )
                        }
                    }
            )
        }
        // 지도 위 글자가 잘 읽히도록 위·아래를 배경색으로 덮는 그라데이션.
        Box(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(s.dp(110))
                .background(
                    Brush.verticalGradient(listOf(WatchColors.Bg.copy(alpha = 0.85f), Color.Transparent))
                )
        )
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelHeight)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, WatchColors.Bg.copy(alpha = 0.92f)))
                )
        )
        WatchRing(progress = ui.ringProgress, color = WatchColors.Route)
        WatchClock(Modifier.align(Alignment.TopCenter).offset(y = s.dp(24)))
        MovingBadge(Modifier.align(Alignment.TopCenter).offset(y = s.dp(48)))
        TurnBanner(
            direction = ui.bannerDirection,
            text = ui.bannerText,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = s.dp(80)),
        )
        Column(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = sosReserved + 2.dp)
                    .widthIn(max = s.dp(250)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = ui.summaryText,
                color = Color.White,
                fontSize = s.sp(17),
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "● 이동중" 알약 배지. 명세: 높이 22, 앞에 초록 점. */
@Composable
private fun MovingBadge(modifier: Modifier = Modifier) {
    val s = rememberWatchScale()
    Row(
        modifier =
            modifier
                .height(s.dp(22))
                .clip(RoundedCornerShape(50))
                .background(WatchColors.Surface)
                .padding(horizontal = s.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(s.dp(7)).clip(CircleShape).background(WatchColors.Success))
        Text(
            text = "  이동중",
            color = Color.White,
            fontSize = s.sp(12),
            fontWeight = FontWeight.Bold,
        )
    }
}
