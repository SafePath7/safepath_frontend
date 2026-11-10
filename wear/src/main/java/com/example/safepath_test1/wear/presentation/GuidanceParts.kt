package com.example.safepath_test1.wear.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Text

/** 처음 보여 줄 지도 지름(m) = 반경 150m. 두 번 탭해서 되돌아올 때도 이 값이다. */
internal const val DEFAULT_VIEW_METERS = 300.0

/** 핀치로 줄일 수 있는 최소 지름(m). 이보다 작게 하면 이미지가 너무 흐려진다. */
internal const val MIN_VIEW_METERS = 200.0

/** 핀치로 넓힐 수 있는 최대 지름(m). 폰이 받은 이미지(약 1km 폭)보다 충분히 작아야 한다. */
internal const val MAX_VIEW_METERS = 800.0

/** 끌어서 둘러볼 수 있는 최대 거리(m). 받아 둔 이미지의 가장자리 근처까지. */
internal const val MAX_PAN_METERS = 250.0

/** 손을 뗀 뒤 이 시간(ms)이 지나면 지도가 내 위치로 돌아온다. */
internal const val PAN_RETURN_DELAY_MS = 8_000L

/** 회전 화살표. 직진은 위쪽 화살표, 우회전/좌회전은 꺾인 화살표(좌회전은 좌우 반전). */
@Composable
internal fun TurnIcon(
    direction: TurnDirection,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mirror = direction == TurnDirection.Left

        fun point(x: Float, y: Float) = Offset((if (mirror) 1f - x else x) * w, y * h)

        val stroke = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (direction == TurnDirection.Straight) {
            drawLine(
                color = color,
                start = Offset(w * 0.5f, h * 0.92f),
                end = Offset(w * 0.5f, h * 0.30f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            drawPath(
                Path().apply {
                    moveTo(w * 0.5f, h * 0.06f)
                    lineTo(w * 0.26f, h * 0.38f)
                    lineTo(w * 0.74f, h * 0.38f)
                    close()
                },
                color,
            )
        } else {
            drawPath(
                Path().apply {
                    val start = point(0.32f, 0.92f)
                    val corner = point(0.32f, 0.45f)
                    val end = point(0.62f, 0.45f)
                    moveTo(start.x, start.y)
                    lineTo(corner.x, corner.y)
                    lineTo(end.x, end.y)
                },
                color,
                style = stroke,
            )
            drawPath(
                Path().apply {
                    val tip = point(0.94f, 0.45f)
                    val top = point(0.62f, 0.20f)
                    val bottom = point(0.62f, 0.70f)
                    moveTo(tip.x, tip.y)
                    lineTo(top.x, top.y)
                    lineTo(bottom.x, bottom.y)
                    close()
                },
                color,
            )
        }
    }
}

/** 위쪽 반투명 안내 상자. 명세 기준 폭 250, 높이 55 (384 기준). */
@Composable
internal fun TurnBanner(direction: TurnDirection, text: String, modifier: Modifier = Modifier) {
    val s = rememberWatchScale()
    Row(
        modifier =
            modifier
                .width(s.dp(250))
                .height(s.dp(55))
                .clip(RoundedCornerShape(s.dp(18)))
                .background(WatchColors.Surface.copy(alpha = 0.88f))
                .padding(horizontal = s.dp(14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TurnIcon(direction, Modifier.size(s.dp(34)))
        Spacer(Modifier.width(s.dp(10)))
        Text(
            text = text,
            color = Color.White,
            fontSize = s.sp(17),
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
