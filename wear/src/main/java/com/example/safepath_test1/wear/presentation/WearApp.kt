package com.example.safepath_test1.wear.presentation

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.safepath_test1.wear.sensors.WatchMotionSender
import com.example.safepath_test1.wear.sensors.WatchSensorManager
import com.example.safepath_test1.wear.sensors.WatchSosSender
import com.example.safepath_test1.wear.sensors.WatchVoiceRecorder
import com.example.safepath_test1.wear.sensors.WatchVoiceSender
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "WearApp"
private const val PATH_STATUS = "/safepath/status"

/** If no update has arrived from the phone in this long, treat it as stale/disconnected. */
internal const val STALE_AFTER_MS = 10 * 60 * 1000L

internal fun isStatusStale(updatedAt: Long, now: Long = System.currentTimeMillis()): Boolean =
    updatedAt <= 0L || now - updatedAt > STALE_AFTER_MS

/** 확인 질문(UI-W3)에 응답이 없으면 보호자 알림으로 넘어가기까지의 시간. */
private const val CHECK_IN_COUNTDOWN_MS = 15_000L

/** 위험 감지 흐름: 평소(길안내) → 확인 질문(UI-W3) → 보호자 알림(UI-W4). */
private enum class AlertStage {
    None,
    CheckIn,
    Alerted,
}

private sealed class RouteStatus {
    data object Idle : RouteStatus()

    data object Searching : RouteStatus()

    data class Found(val routeType: String, val distanceKm: String, val destinationName: String?) :
        RouteStatus()

    data object Stale : RouteStatus()
}

/** Route shape + live position for RouteMiniMap, decoded from WearMessenger's DataMap fields. */
private data class RouteMapState(
    val points: List<LatLng>,
    val destination: LatLng?,
    val current: LatLng?,
)

/** Reverses WearMessenger's "lat,lng;lat,lng;..." encoding. */
private fun decodeLatLngList(encoded: String?): List<LatLng> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.split(";").mapNotNull { pair ->
        val parts = pair.split(",")
        if (parts.size != 2) return@mapNotNull null
        val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val lng = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        lat to lng
    }
}

/**
 * Experimental counterpart to WearApp.kt: syncs status over the Wearable Data Layer's DataClient
 * instead of Firebase. Uses DataClient (not MessageClient) — see WearMessenger.kt on the phone side
 * for why. No pairing code screen here - Bluetooth pairing between this watch and a phone already
 * scopes who can push data to it.
 */
@Composable
fun WearApp() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<RouteStatus>(RouteStatus.Idle) }
    var lastReceivedAt by remember { mutableStateOf(0L) }
    var routeMap by remember { mutableStateOf<RouteMapState?>(null) }
    var mapBackground by remember { mutableStateOf<MapBackground?>(null) }
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

    // Live mic level while navigating, used to tint the whole screen below so the "is it
    // actually picking up sound" indicator is easy to notice at a glance (a small dot wasn't).
    val audioLevel by voiceRecorder.audioLevel.collectAsState()
    val screenTint =
        if (isNavigating) lerp(WatchColors.Bg, Color(0xFFA5D6A7), audioLevel) else Color.Black

    fun applyDataMap(dataMap: com.google.android.gms.wearable.DataMap) {
        val updatedAt = dataMap.getLong("updatedAt")
        lastReceivedAt = updatedAt
        val receivedStatus =
            when (dataMap.getString("state")) {
                "SEARCHING" -> RouteStatus.Searching
                "FOUND" ->
                    RouteStatus.Found(
                        routeType = dataMap.getString("routeType") ?: "경로",
                        distanceKm = "%.1f".format(dataMap.getDouble("distanceMeters") / 1000.0),
                        destinationName = dataMap.getString("destinationName"),
                    )
                else -> RouteStatus.Idle
            }
        status = if (isStatusStale(updatedAt)) RouteStatus.Stale else receivedStatus

        val points = decodeLatLngList(dataMap.getString("routePoints"))
        val destination =
            if (dataMap.containsKey("destLat") && dataMap.containsKey("destLng")) {
                dataMap.getDouble("destLat") to dataMap.getDouble("destLng")
            } else {
                null
            }
        val current =
            if (dataMap.containsKey("curLat") && dataMap.containsKey("curLng")) {
                dataMap.getDouble("curLat") to dataMap.getDouble("curLng")
            } else {
                null
            }
        routeMap = if (points.size >= 2) RouteMapState(points, destination, current) else null

        Log.i(TAG, "New status=$status")
    }

    // 폰이 보낸 지도 이미지를 디코딩해서 미니맵 배경으로 쓴다. 실패하면 기존 값(또는 스케치 지도)을 그대로 둔다.
    fun applyMapDataMap(dataMap: com.google.android.gms.wearable.DataMap) {
        scope.launch { loadMapBackground(context, dataMap)?.let { mapBackground = it } }
    }

    // Pick up whatever state is already there (registering the listener below
    // only delivers *future* changes, not the item as it stood before we
    // started listening).
    DisposableEffect(Unit) {
        Wearable.getDataClient(context)
            .dataItems
            .addOnSuccessListener { dataItems ->
                for (i in 0 until dataItems.count) {
                    val item = dataItems[i]
                    when (item.uri.path) {
                        PATH_STATUS -> applyDataMap(DataMapItem.fromDataItem(item).dataMap)
                        PATH_MAP -> applyMapDataMap(DataMapItem.fromDataItem(item).dataMap)
                    }
                }
                dataItems.release()
            }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to read existing DataItems", e) }
        onDispose {}
    }

    DisposableEffect(Unit) {
        val dataClient = Wearable.getDataClient(context)
        val listener = DataClient.OnDataChangedListener { dataEvents ->
            for (event in dataEvents) {
                Log.i(TAG, "DataEvent type=${event.type} path=${event.dataItem.uri.path}")
                if (event.type == DataEvent.TYPE_CHANGED) {
                    when (event.dataItem.uri.path) {
                        PATH_STATUS -> applyDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap)
                        PATH_MAP -> applyMapDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap)
                    }
                }
            }
            dataEvents.release()
        }
        dataClient.addListener(listener)
        Log.i(TAG, "Registered DataClient listener")
        onDispose {
            dataClient.removeListener(listener)
            Log.i(TAG, "Removed DataClient listener")
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            if (
                lastReceivedAt > 0 &&
                    System.currentTimeMillis() - lastReceivedAt > STALE_AFTER_MS &&
                    status !is RouteStatus.Stale
            ) {
                status = RouteStatus.Stale
            }
        }
    }

    var alertStage by remember { mutableStateOf(AlertStage.None) }
    var checkInStartedAt by remember { mutableStateOf(0L) }
    var checkInRemainingMs by remember { mutableStateOf(CHECK_IN_COUNTDOWN_MS) }
    var helpRequestedAt by remember { mutableStateOf(0L) }

    // 지금은 방패 아이콘 길게 누르기(테스트용)로 호출된다. AI 서버가 연결되면 실제 위험 신호에서 호출한다.
    fun startCheckIn() {
        checkInStartedAt = System.currentTimeMillis()
        checkInRemainingMs = CHECK_IN_COUNTDOWN_MS
        alertStage = AlertStage.CheckIn
    }

    // 보호자 연동 전이라 SOS 버튼과 같은 경로(워치 → 폰 알림)로 보낸다.
    fun requestHelp() {
        WatchSosSender.send(context)
        helpRequestedAt = System.currentTimeMillis()
        alertStage = AlertStage.Alerted
    }

    LaunchedEffect(alertStage, checkInStartedAt) {
        if (alertStage != AlertStage.CheckIn) return@LaunchedEffect
        while (true) {
            val remaining = CHECK_IN_COUNTDOWN_MS - (System.currentTimeMillis() - checkInStartedAt)
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
            when (alertStage) {
                AlertStage.CheckIn ->
                    CheckInScreen(
                        remainingMs = checkInRemainingMs,
                        totalMs = CHECK_IN_COUNTDOWN_MS,
                        onOkay = { alertStage = AlertStage.None },
                        onHelp = { requestHelp() },
                    )
                AlertStage.Alerted ->
                    GuardianAlertedScreen(
                        requestedAt = helpRequestedAt,
                        onDismiss = { alertStage = AlertStage.None },
                    )
                AlertStage.None -> MainScreen(
                    status = status,
                    routeMap = routeMap,
                    mapBackground = mapBackground,
                    screenTint = screenTint,
                    onSos = { WatchSosSender.send(context) },
                    onShieldLongPress = { startCheckIn() },
                )
            }
            if (alertStage != AlertStage.None) TimeText()
        }
    }
}

/** 평소 화면: 대기/탐색/연결 끊김 상태, 또는 길안내 중이면 UI-W1(길안내)과 UI-W2(이동 상태)를 옆으로 넘겨 본다. */
@Composable
private fun MainScreen(
    status: RouteStatus,
    routeMap: RouteMapState?,
    mapBackground: MapBackground?,
    screenTint: Color,
    onSos: () -> Unit,
    onShieldLongPress: () -> Unit,
) {
    val isNavigating = status is RouteStatus.Found
    Box(
        modifier = Modifier.fillMaxSize().background(screenTint),
        contentAlignment = Alignment.Center,
    ) {
        if (status is RouteStatus.Found) {
            val pagerState = rememberPagerState(pageCount = { 2 })
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    GuidancePage(status, routeMap, mapBackground)
                } else {
                    Box(Modifier.fillMaxSize()) {
                        MovingStatusPage(onShieldLongPress = onShieldLongPress)
                        TimeText()
                    }
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

/** 남은 거리가 이 값(m) 아래면 도착한 것으로 본다. */
private const val ARRIVED_METERS = 20.0

/**
 * UI-W1(W-01) 경로 안내 페이지(전체 화면 지도 초안).
 * 화면 전체를 지도가 채우고(한 손가락으로 끌어서 둘러보기, 두 손가락 핀치로 확대/축소, 두 번 탭하면 내 위치로) 그 위에 링, 시각, 이동중 배지, 위쪽 회전 안내 상자(y80),
 * 아래쪽에 남은 거리 한 줄을 올린다. 위치는 명세의 384dp 원형 기준을 화면 크기에 맞게 줄여 쓴다.
 * 다음 회전은 폰이 보낸 경로 모양에서 워치가 직접 계산한다(실제 턴바이턴 문구는 13주차 이후).
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
    var viewMeters by remember { mutableStateOf(DEFAULT_VIEW_METERS) }
    var panEastMeters by remember { mutableStateOf(0.0) }
    var panNorthMeters by remember { mutableStateOf(0.0) }
    var lastTouchAt by remember { mutableStateOf(0L) }
    // 손을 떼고 잠시 지나면 지도가 다시 내 위치를 따라간다(확대 정도는 유지).
    LaunchedEffect(lastTouchAt) {
        if (lastTouchAt == 0L) return@LaunchedEffect
        delay(PAN_RETURN_DELAY_MS)
        panEastMeters = 0.0
        panNorthMeters = 0.0
    }
    val progress = remember(routeMap) { routeMap?.let { computeRouteProgress(it.points, it.current) } }
    // 현재 위치가 와야 남은 거리·다음 회전·도착 시각을 알 수 있다. 아직이면 경로 전체 거리만 보여 준다.
    val live = if (routeMap?.current != null) progress else null
    val turn = remember(routeMap) { live?.let { computeNextTurn(routeMap!!.points, it.traveledMeters) } }
    val arrived = live != null && live.remainingMeters < ARRIVED_METERS

    val bannerDirection = if (arrived || turn == null) TurnDirection.Straight else turn.direction
    val bannerText =
        when {
            arrived -> "목적지 도착"
            turn != null -> turnBannerText(turn)
            else -> "위치 확인 중"
        }
    val summary =
        if (live != null) "남은 ${formatDistance(live.remainingMeters)}" else "총 ${current.distanceKm}km"

    val sosReserved = s.sosReservedHeight()
    val panelHeight = sosReserved + s.dp(86)

    Box(modifier = Modifier.fillMaxSize()) {
        routeMap?.let { map ->
            RouteMiniMap(
                routePoints = map.points,
                destination = map.destination,
                current = map.current,
                modifier = Modifier.fillMaxSize(),
                background = mapBackground,
                viewMeters = viewMeters,
                panEastMeters = panEastMeters,
                panNorthMeters = panNorthMeters,
            )
            // 끌기/핀치를 받는 투명 영역. 위쪽 시각·배지와 아래쪽 글자·SOS 영역은 빼서,
            // 그 위에서 옆으로 쓸면 기존처럼 다음 페이지로 넘어간다.
            Box(
                Modifier.fillMaxSize()
                    .padding(top = s.dp(70), bottom = s.sosReservedHeight() + s.dp(40))
                    // 두 번 탭하면 바로 내 위치, 기본 확대로 돌아온다.
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                panEastMeters = 0.0
                                panNorthMeters = 0.0
                                viewMeters = DEFAULT_VIEW_METERS
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val metersPerPx = viewMeters / size.width
                            // 손가락을 오른쪽으로 끌면 지도가 오른쪽으로 가니까 보는 위치는 서쪽으로 간다.
                            panEastMeters =
                                (panEastMeters - pan.x * metersPerPx)
                                    .coerceIn(-MAX_PAN_METERS, MAX_PAN_METERS)
                            panNorthMeters =
                                (panNorthMeters + pan.y * metersPerPx)
                                    .coerceIn(-MAX_PAN_METERS, MAX_PAN_METERS)
                            // 두 손가락을 벌리면(zoom > 1) 더 가까이, 모으면 더 넓게.
                            viewMeters = (viewMeters / zoom).coerceIn(MIN_VIEW_METERS, MAX_VIEW_METERS)
                            lastTouchAt = System.currentTimeMillis()
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
        WatchRing(progress = progress?.progress ?: 0f, color = WatchColors.Route)
        WatchClock(Modifier.align(Alignment.TopCenter).offset(y = s.dp(24)))
        MovingBadge(Modifier.align(Alignment.TopCenter).offset(y = s.dp(48)))
        TurnBanner(
            direction = bannerDirection,
            text = bannerText,
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
                text = summary,
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
