package com.example.safepath_test1.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import com.example.safepath_test1.wear.state.RouteMapState
import com.example.safepath_test1.wear.state.RouteStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * UI-W1(W-01) 경로 안내 페이지(전체 화면 지도 초안).
 * 화면 전체를 지도가 채우고(한 손가락으로 끌어서 둘러보기, 두 손가락 핀치로 확대/축소,
 * 두 번 탭하면 내 위치로) 그 위에 링, 시각, 이동중 배지, 위쪽 회전 안내 상자(y80),
 * 아래쪽에 남은 거리 한 줄을 올린다. 위치는 명세의 384dp 원형 기준을 화면 크기에 맞게 줄여 쓴다.
 * 다음 회전은 폰이 회전 안내 목록(routeSteps)을 보내면 그걸 쓰고, 없으면 경로 모양에서 워치가 추측한다.
 * SOS 버튼은 MainScreen이 하단에 따로 붙인다.
 */
@Composable
internal fun GuidancePage(
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
                    Brush.verticalGradient(
                        listOf(WatchColors.Bg.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, WatchColors.Bg.copy(alpha = 0.92f))
                    )
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
