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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// W-02 화면 3종(안전 / 확인 질문 / 위험상황). 색·크기·위치는 팀 UI 명세서의 384dp 원형 기준을
// WatchScale로 화면 크기에 맞게 줄여 쓴다. 링은 W-01과 같은 WatchRing을 쓴다.

/** 방패 + 체크 아이콘. 선 색은 안전 초록. */
@Composable
private fun ShieldIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.09f
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
        drawPath(shield, WatchColors.Success, style = Stroke(width = strokeWidth, join = StrokeJoin.Round))
        val check =
            Path().apply {
                moveTo(w * 0.3f, h * 0.52f)
                lineTo(w * 0.45f, h * 0.66f)
                lineTo(w * 0.72f, h * 0.38f)
            }
        drawPath(
            check,
            WatchColors.Success,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** 삼각형 경고 아이콘. */
@Composable
private fun WarningIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.1f
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

/** 음성·소리·움직임·경로처럼 지금 감시 중인 항목을 보여주는 작은 칩(초록 점). */
@Composable
private fun DetectionChip(label: String) {
    val s = rememberWatchScale()
    Row(
        modifier =
            Modifier.height(maxOf(s.dp(24), 18.dp))
                .clip(RoundedCornerShape(50))
                .background(WatchColors.Surface)
                .padding(horizontal = s.dp(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(s.dp(7)).clip(CircleShape).background(WatchColors.Success))
        Text(
            text = " $label",
            color = Color.White,
            fontSize = s.sp(11),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** 확인 질문의 알약 버튼. 명세: 높이 60. 작은 화면에서도 누를 수 있게 최소 크기를 둔다. */
@Composable
private fun ActionPill(label: String, color: Color, onClick: () -> Unit) {
    val s = rememberWatchScale()
    Box(
        modifier =
            Modifier.size(width = maxOf(s.dp(138), 70.dp), height = maxOf(s.dp(60), 34.dp))
                .clip(RoundedCornerShape(50))
                .background(color)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = s.sp(16),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * W-02 안전 상태. 길안내 화면 옆 페이지로, 특별한 안내가 없을 때 "안전하게 이동 중"을 보여준다.
 * 링은 초록으로 가득. 보호자 인원수는 앱/백엔드 연동 전이라 문구에서 뺐다.
 *
 * AI 서버가 위험 신호를 보내기 전까지는 방패 원을 길게 누르는 것이 확인 질문 화면의 테스트용 트리거다.
 */
@Composable
internal fun MovingStatusPage(onShieldLongPress: () -> Unit) {
    val s = rememberWatchScale()
    Box(Modifier.fillMaxSize()) {
        WatchRing(progress = 1f, color = WatchColors.Success)
        WatchClock(Modifier.align(Alignment.TopCenter).offset(y = s.dp(24)))
        Column(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = s.dp(54)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier.size(s.dp(76))
                        .clip(CircleShape)
                        .background(WatchColors.SafeIconBg)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onShieldLongPress() })
                        },
                contentAlignment = Alignment.Center,
            ) {
                ShieldIcon(Modifier.size(s.dp(38)))
            }
            Spacer(Modifier.height(s.dp(10)))
            Text(
                text = "안전하게 이동 중",
                color = Color.White,
                fontSize = s.sp(22),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = "보호자가 함께 보고 있어요",
                color = WatchColors.TextSub,
                fontSize = s.sp(13),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(s.dp(10)))
            Row(horizontalArrangement = Arrangement.spacedBy(s.dp(5))) {
                listOf("음성", "소리", "움직임", "경로").forEach { DetectionChip(it) }
            }
        }
    }
}

/**
 * W-02 확인 질문. 남은 시간만큼 주황 링이 줄어들고, 0이 되면 [onHelp]과 같은 처리를 한다(호출 측).
 * 감지 내용은 AI 서버 연동 전이라 일반 문구로 둔다.
 */
@Composable
internal fun CheckInScreen(remainingMs: Long, totalMs: Long, onOkay: () -> Unit, onHelp: () -> Unit) {
    val s = rememberWatchScale()
    val seconds = ((remainingMs + 999) / 1000).coerceAtLeast(0)
    Box(Modifier.fillMaxSize().background(WatchColors.Bg)) {
        WatchRing(progress = remainingMs.toFloat() / totalMs, color = WatchColors.Warning)
        WatchClock(Modifier.align(Alignment.TopCenter).offset(y = s.dp(24)))
        Column(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = s.dp(56)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier.size(s.dp(56))
                        .clip(RoundedCornerShape(s.dp(16)))
                        .background(WatchColors.WarningIconBg),
                contentAlignment = Alignment.Center,
            ) {
                WarningIcon(WatchColors.Warning, Modifier.size(s.dp(30)))
            }
            Spacer(Modifier.height(s.dp(6)))
            Text(
                text = "괜찮으신가요?",
                color = Color.White,
                fontSize = s.sp(27),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = "위험 상황이 감지됐어요",
                color = WatchColors.TextSub,
                fontSize = s.sp(13),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = "${seconds}초 후 보호자에게 알려요",
                color = WatchColors.Warning,
                fontSize = s.sp(14),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = s.dp(240)),
            horizontalArrangement = Arrangement.spacedBy(s.dp(10)),
        ) {
            ActionPill("괜찮아요", WatchColors.Success, onOkay)
            ActionPill("도움 요청", WatchColors.Danger, onHelp)
        }
    }
}

/**
 * W-02 위험상황. 도움 요청 버튼, SOS, 확인 질문 시간 초과 중 하나로 표시된다.
 *
 * 지금은 폰 알림까지만 전달되므로(WatchSosSender) 문구도 그 범위로 적었다. 명세대로 해제는 휴대폰에서 하고,
 * 폰이 보낸 해제 신호를 워치가 받으면 이 화면이 닫힌다(WearApp). 폰 쪽 해제 기능이 생기기 전까지는
 * "휴대폰에서 해제" 버튼을 길게 누르면 [onTestRelease]로 해제를 흉내 낼 수 있다(테스트용, 폰 연동 후 제거).
 */
@Composable
internal fun GuardianAlertedScreen(requestedAt: Long, onTestRelease: () -> Unit) {
    val s = rememberWatchScale()
    val time =
        remember(requestedAt) { SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(requestedAt)) }
    val buttonShape = RoundedCornerShape(50)
    Box(Modifier.fillMaxSize().background(WatchColors.DangerBg)) {
        WatchRing(progress = 1f, color = Color.White)
        WatchClock(Modifier.align(Alignment.TopCenter).offset(y = s.dp(24)))
        Column(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = s.dp(56)).widthIn(max = s.dp(330)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier.size(s.dp(64)).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                WarningIcon(Color.White, Modifier.size(s.dp(34)))
            }
            Spacer(Modifier.height(s.dp(8)))
            Text(
                text = "보호자에게 알렸어요",
                color = Color.White,
                fontSize = s.sp(24),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "휴대폰으로 긴급 알림을 보냈어요",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = s.sp(13),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "요청 $time",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = s.sp(12),
                textAlign = TextAlign.Center,
            )
        }
        Box(
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .offset(y = s.dp(250))
                    .size(width = maxOf(s.dp(160), 96.dp), height = maxOf(s.dp(52), 32.dp))
                    .clip(buttonShape)
                    .border(s.dp(2), Color.White, buttonShape)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { onTestRelease() }) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "휴대폰에서 해제",
                color = Color.White,
                fontSize = s.sp(15),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
