package com.example.safepath_test1.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import com.example.safepath_test1.wear.state.RouteMapState
import com.example.safepath_test1.wear.state.RouteStatus

/** 평소 화면: 대기/탐색/연결 끊김 상태, 또는 길안내 중이면 UI-W1(길안내)과 UI-W2(이동 상태)를 옆으로 넘겨 본다. */
@Composable
internal fun MainScreen(
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

        // 위 Column 안이 아니라 화면 아래 가운데에 고정한다. RouteMiniMap과 글자 최대 3줄만으로도 40mm 워치의
        // 보이는 높이에 거의 차고 스크롤도 없어서, Column 안에 두면 화면 밖으로 밀려날 수 있다.
        // 한 번 누르면 바로 보낸다. 설정 버튼이 아니라 긴급 버튼이라, 길게 눌러 확인하게 하면 가장 빨라야 할
        // 순간만 느려진다. 지금은 짝지어진 폰까지만 전달된다(WatchSosSender → WearSosRelayService).
        if (isNavigating) {
            val s = rememberWatchScale()
            // 명세: 워치 하단 가운데, 화면 아래에서 22(384 기준) 위.
            WatchSosButton(
                onClick = onSos,
                modifier =
                    Modifier.align(Alignment.BottomCenter).padding(bottom = s.sosBottomMargin()),
            )
        }
    }
}
