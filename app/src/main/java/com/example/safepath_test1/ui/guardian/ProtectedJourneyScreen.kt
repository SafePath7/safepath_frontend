package com.example.safepath_test1.ui.guardian

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.safepath_test1.model.GeoPoint
import com.example.safepath_test1.ui.map.SafePathKakaoMapView
import com.example.safepath_test1.ui.theme.*

// 로그인한 보호자에게 허용된 세션만 전달하며 실제 운영 화면에 임의의 세션을 넣지 않는다.
data class ProtectedJourney(
    val userName: String,
    val lastUpdated: String,
    val status: String,
    val location: GeoPoint,
    val destination: GeoPoint,
    val destinationName: String,
    val remainingDistance: String,
    val arrivalTime: String,
    val travelledGeoJson: String? = null,
    val remainingGeoJson: String? = null,
    val dangerType: String? = null,
    val detectedAt: String? = null,
    val confirmationUnanswered: Boolean = false,
    val history: List<Pair<String, String>> = emptyList(),
    val sessionId: String = "",
)

@Composable
fun ProtectedJourneyScreen(
    journey: ProtectedJourney,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val danger = journey.status == "위험상황"
    val statusColor =
        when (journey.status) {
            "위험상황" -> DangerRed
            "경로이탈",
            "위험 의심단계" -> Color(0xFFD89024)
            "취소" -> TextMuted
            else -> SafeGreen
        }
    Box(modifier.fillMaxSize()) {
        SafePathKakaoMapView(
            currentLocation = journey.location,
            hasLocationPermission = true,
            recenterToken = 0,
            destinationPoint = journey.destination,
            routeLineGeoJson = journey.travelledGeoJson,
            routeLineColor = "#1B4965",
            remainingRouteGeoJson = journey.remainingGeoJson,
            currentMarkerColor = if (danger) "#DC2626" else "#16A36A",
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = Color.White,
                shape = MaterialTheme.shapes.medium,
                border = if (danger) BorderStroke(1.dp, DangerRed) else null,
                shadowElevation = 4.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                    Column(Modifier.weight(1f)) {
                        Text(journey.userName, fontWeight = FontWeight.Bold)
                        Text(
                            "위치 갱신 ${journey.lastUpdated}",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Surface(
                        color = statusColor.copy(alpha = .12f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            "● ${journey.status}",
                            color = statusColor,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (danger)
                Surface(color = DangerRed, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("⚠ 위험상황이 감지됐어요", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "${journey.dangerType ?: "감지 유형 확인 중"} · ${journey.detectedAt ?: "시각 확인 중"}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (journey.confirmationUnanswered)
                            Text(
                                "확인 요청에 응답이 없어요",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                    }
                }
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape =
                androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                ),
            color = Color.White,
            shadowElevation = 4.dp,
        ) {
            Column(
                Modifier.heightIn(max = 390.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .background(FieldBg)
                )
                Text("⚑ ${journey.destinationName}", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("남은 거리" to journey.remainingDistance, "도착 예정" to journey.arrivalTime)
                        .forEach { (title, value) ->
                            Surface(
                                Modifier.weight(1f),
                                color = FieldBg,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        title,
                                        color = TextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(value, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                }
                if (journey.history.isNotEmpty()) {
                    HorizontalDivider(color = AppBorder)
                    Text("상태 기록", fontWeight = FontWeight.Bold)
                    journey.history.forEach { (time, event) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("●", color = if (event.contains("위험상황")) DangerRed else SkyBlue)
                            Text(
                                time,
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                event,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
