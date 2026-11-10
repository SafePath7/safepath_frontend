package com.example.safepath_test1.wear.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/** 팀 UI 명세서(Safe Path UI 개발용 디자인 명세서)의 워치 색 토큰. */
internal object WatchColors {
    val Bg = Color(0xFF0B1F2C) // watchBg: 워치 배경
    val Surface = Color(0xFF173547) // watchSurface: 칩·링 바탕
    val Map = Color(0xFF132C3B) // watchMap: 미니맵 바탕
    val TextSub = Color(0xFF9FB6C6) // watchTextSub: 보조 글자
    val Time = Color(0xFFC9DCE8) // 시각 글자
    val Route = Color(0xFF5FA8D3) // 경로·진행 링
    val Success = Color(0xFF2E9E6B) // 이동중 점, 초록 링, 괜찮아요 버튼
    val Warning = Color(0xFFF2A93B) // 위험 의심 주황
    val Danger = Color(0xFFD23C3C) // SOS, 도움 요청
    val DangerBg = Color(0xFFB42F2F) // 위험상황 배경
}

/** 명세가 기준으로 삼은 원형 화면 크기(dp). 실제 워치는 이보다 작아서 배율로 줄여 쓴다. */
internal const val WATCH_DESIGN_SIZE = 384f

/**
 * 명세의 숫자(좌표, 크기, 글자)를 실제 화면에 맞게 바꿔 주는 배율.
 * 길이는 화면 폭에 정비례로 줄이고, 글자는 그대로 비례하면 너무 작아지므로 제곱근 배율을 쓴다.
 * 실기기에서 글자가 크거나 작으면 [sp]의 배율만 조정하면 된다.
 */
@Immutable
internal class WatchScale(val factor: Float) {
    fun dp(designPx: Float): Dp = (designPx * factor).dp

    fun dp(designPx: Int): Dp = dp(designPx.toFloat())

    fun sp(designPx: Float): TextUnit = (designPx * sqrt(factor)).sp

    fun sp(designPx: Int): TextUnit = sp(designPx.toFloat())

    /** 화면 아래에서 SOS 버튼이 차지하는 높이(바닥 여백 22 + 버튼 44). 그 위로 글자를 올릴 때 쓴다. */
    fun sosReservedHeight(): Dp = dp(22) + maxOf(dp(44), 28.dp)
}

@Composable
internal fun rememberWatchScale(): WatchScale {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) { WatchScale(widthDp / WATCH_DESIGN_SIZE) }
}

/**
 * 화면 테두리 링. 12시 방향에서 시계방향으로 [progress](0..1)만큼 채워진다.
 * 명세: 반지름 186, 두께 7 (384 기준). 바탕 링은 watchSurface.
 */
@Composable
internal fun WatchRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Canvas(modifier) {
        val k = size.minDimension / WATCH_DESIGN_SIZE
        val strokeWidth = 7f * k
        val inset = 6f * k // 중심 192에서 반지름 186이면 가장자리에서 6만큼 안쪽
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = WatchColors.Surface,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth),
        )
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

/** 명세의 상단 시각(15/600, #C9DCE8). 분이 바뀔 때마다 갱신된다. */
@Composable
internal fun WatchClock(modifier: Modifier = Modifier) {
    val s = rememberWatchScale()
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm") }
    val time by
        produceState(initialValue = LocalTime.now().format(formatter)) {
            while (true) {
                value = LocalTime.now().format(formatter)
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    Text(
        text = time,
        color = WatchColors.Time,
        fontSize = s.sp(15),
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

/**
 * 워치 SOS 버튼. 명세: 높이 44, 반지름 22, #D23C3C, 16/800 흰색, 폭 92 (384 기준).
 * 비상 버튼이라 누르기 어려우면 안 되므로, 줄어든 크기가 너무 작아지지 않게 최소 크기를 둔다.
 */
@Composable
internal fun WatchSosButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = rememberWatchScale()
    val height = maxOf(s.dp(44), 28.dp)
    Box(
        modifier =
            modifier
                .size(width = maxOf(s.dp(92), 56.dp), height = height)
                .clip(RoundedCornerShape(height / 2))
                .background(WatchColors.Danger)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "SOS",
            color = Color.White,
            fontSize = s.sp(16),
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
