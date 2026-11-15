package com.example.safepath_test1.wear.presentation

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text

// 여러 화면이 함께 쓰는 작은 화면 조각들. 색과 크기 기준은 WatchTheme.kt를 따른다.

/**
 * 안전 / 확인 질문 / 위험상황 화면의 공통 틀. 배경, 테두리 링([ringProgress], [ringColor]), 위쪽 시각을 깔고
 * 그 위에 [content]를 올린다. 위치를 정할 때는 [BoxScope]의 align을 쓴다.
 */
@Composable
internal fun WatchScreenScaffold(
    ringProgress: Float,
    ringColor: Color,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val s = rememberWatchScale()
    Box(modifier.fillMaxSize().background(background)) {
        WatchRing(progress = ringProgress, color = ringColor)
        WatchClock(Modifier.align(Alignment.TopCenter).offset(y = s.dp(24)))
        content()
    }
}

/** 앞에 초록 점이 붙은 알약 모양 상태 표시. [MovingBadge]와 [DetectionChip]이 크기와 글자만 달리해서 쓴다. */
@Composable
internal fun StatusPill(
    text: String,
    height: Dp,
    horizontalPadding: Dp,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val s = rememberWatchScale()
    Row(
        modifier =
            modifier
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(WatchColors.Surface)
                .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(s.dp(7)).clip(CircleShape).background(WatchColors.Success))
        Text(
            text = text,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
        )
    }
}

/** "● 이동중" 알약 배지. 명세: 높이 22, 앞에 초록 점. */
@Composable
internal fun MovingBadge(modifier: Modifier = Modifier) {
    val s = rememberWatchScale()
    StatusPill(
        text = "  이동중",
        height = s.dp(22),
        horizontalPadding = s.dp(10),
        fontSize = s.sp(12),
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

/** 음성·소리·움직임·경로처럼 지금 감시 중인 항목을 보여주는 작은 칩(초록 점). */
@Composable
internal fun DetectionChip(label: String) {
    val s = rememberWatchScale()
    StatusPill(
        text = " $label",
        height = s.dpAtLeast(24, 18.dp),
        horizontalPadding = s.dp(8),
        fontSize = s.sp(11),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/**
 * 지금 설치된 앱이 디버그 빌드(개발 중 빌드)인지. 테스트용 조작을 개발 중에만 열 때 쓴다.
 * 플레이스토어 등으로 배포하는 릴리스 빌드에서는 false다.
 */
@Composable
internal fun rememberIsDebuggable(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
