package com.example.safepath_test1.wear.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// UI-W2 ~ W4 화면 (이동 상태 / 확인 질문 / 보호자 알림). 색상은 Figma 목업 기준.
private val SafeGreen = Color(0xFF4ADE80)
private val Amber = Color(0xFFF5A623)
private val AlertRed = Color(0xFFC62828)
private val Navy = Color(0xFF14233B)
private val ChipBackground = Color(0xFF26405C)
private val MutedText = Color(0xFF9AA5B1)

/** 둥근 화면 가장자리를 따라 그리는 테두리. [progress]가 1보다 작으면 12시 방향부터 그 비율만큼만 그린다. */
@Composable
private fun ScreenRing(color: Color, progress: Float = 1f) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 4.dp.toPx()
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun ShieldIcon() {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        val shield =
            Path().apply {
                moveTo(w * 0.5f, 0f)
                lineTo(w, h * 0.18f)
                lineTo(w, h * 0.5f)
                cubicTo(w, h * 0.78f, w * 0.75f, h * 0.92f, w * 0.5f, h)
                cubicTo(w * 0.25f, h * 0.92f, 0f, h * 0.78f, 0f, h * 0.5f)
                lineTo(0f, h * 0.18f)
                close()
            }
        drawPath(shield, SafeGreen, style = Stroke(width = strokeWidth, join = StrokeJoin.Round))
        val check =
            Path().apply {
                moveTo(w * 0.3f, h * 0.52f)
                lineTo(w * 0.45f, h * 0.66f)
                lineTo(w * 0.72f, h * 0.38f)
            }
        drawPath(
            check,
            SafeGreen,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun WarningIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        val triangle =
            Path().apply {
                moveTo(w * 0.5f, h * 0.06f)
                lineTo(w * 0.96f, h * 0.9f)
                lineTo(w * 0.04f, h * 0.9f)
                close()
            }
        drawPath(triangle, color, style = Stroke(width = strokeWidth, join = StrokeJoin.Round))
        drawLine(
            color,
            Offset(w * 0.5f, h * 0.38f),
            Offset(w * 0.5f, h * 0.62f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(color, radius = strokeWidth * 0.7f, center = Offset(w * 0.5f, h * 0.76f))
    }
}

/** 음성·소리·움직임·경로처럼 지금 감시 중인 항목을 보여주는 작은 배지. */
@Composable
private fun DetectionBadge(label: String) {
    Row(
        modifier =
            Modifier.clip(RoundedCornerShape(50))
                .background(ChipBackground)
                .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(SafeGreen))
        Text(text = " $label", color = Color.White, fontSize = 9.sp)
    }
}

@Composable
private fun PillButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.height(34.dp)
                .widthIn(min = 60.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(color)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, style = MaterialTheme.typography.caption1)
    }
}

/**
 * UI-W2 이동 상태. 길안내 화면 옆 페이지로, 특별한 안내가 없을 때 "안전하게 이동 중"을 보여준다.
 *
 * AI 서버가 위험 신호를 보내기 전까지는 방패 아이콘을 길게 누르는 것이 확인 질문 화면의 테스트용 트리거다.
 */
@Composable
internal fun MovingStatusPage(onShieldLongPress: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        ScreenRing(SafeGreen)
        Column(
            modifier =
                Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F3A2E))
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onShieldLongPress() })
                        },
                contentAlignment = Alignment.Center,
            ) {
                ShieldIcon()
            }
            Text(
                text = "안전하게 이동 중",
                style = MaterialTheme.typography.title3,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "보호자가 함께 보고 있어요",
                style = MaterialTheme.typography.caption2,
                color = MutedText,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("음성", "소리", "움직임", "경로").forEach { DetectionBadge(it) }
            }
        }
    }
}

/** UI-W3 확인 질문. 남은 시간만큼 주황 테두리가 줄어들고, 0이 되면 [onHelp]과 같은 처리를 한다(호출 측). */
@Composable
internal fun CheckInScreen(remainingMs: Long, totalMs: Long, onOkay: () -> Unit, onHelp: () -> Unit) {
    val seconds = ((remainingMs + 999) / 1000).coerceAtLeast(0)
    Box(Modifier.fillMaxSize().background(Navy)) {
        ScreenRing(Amber, progress = remainingMs.toFloat() / totalMs)
        Column(
            modifier =
                Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A2E14)),
                contentAlignment = Alignment.Center,
            ) {
                WarningIcon(Amber)
            }
            Text(
                text = "괜찮으신가요?",
                style = MaterialTheme.typography.title3,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "위험 상황이 감지됐어요",
                style = MaterialTheme.typography.caption2,
                color = MutedText,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${seconds}초 후 보호자에게 알려요",
                style = MaterialTheme.typography.caption2,
                color = Amber,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PillButton("괜찮아요", Color(0xFF2E9E6A), onOkay)
                PillButton("도움 요청", Color(0xFFE53935), onHelp)
            }
        }
    }
}

/**
 * UI-W4 보호자 알림. 도움 요청 버튼을 누르거나 확인 질문 시간이 끝나면 표시된다.
 *
 * 지금은 폰 알림까지만 전달되므로(WatchSosSender) 문구도 그 범위로 적었다. 목업의 "휴대폰에서 해제"는 폰 쪽 기능이
 * 없어서 워치의 "해제" 버튼으로 대신한다.
 */
@Composable
internal fun GuardianAlertedScreen(requestedAt: Long, onDismiss: () -> Unit) {
    val time =
        remember(requestedAt) { SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(requestedAt)) }
    Box(Modifier.fillMaxSize().background(AlertRed)) {
        ScreenRing(Color.White.copy(alpha = 0.35f))
        Column(
            modifier =
                Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                WarningIcon(Color.White)
            }
            Text(
                text = "보호자에게 알렸어요",
                style = MaterialTheme.typography.title3,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "휴대폰으로 긴급 알림을 보냈어요",
                style = MaterialTheme.typography.caption2,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "요청 $time",
                style = MaterialTheme.typography.caption2,
                color = Color.White.copy(alpha = 0.7f),
            )
            Box(
                modifier =
                    Modifier.padding(top = 4.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White, RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "해제", color = Color.White, style = MaterialTheme.typography.caption1)
            }
        }
    }
}
