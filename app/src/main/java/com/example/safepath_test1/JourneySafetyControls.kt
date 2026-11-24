package com.example.safepath_test1

import android.os.SystemClock
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.example.safepath_test1.model.GeoPoint
import com.example.safepath_test1.session.DetectionSettingsStore
import kotlinx.coroutines.delay

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun JourneySafetyControls(modifier: Modifier, sessionKey: String, location: GeoPoint?, onSos: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var deadline by rememberSaveable(sessionKey) { mutableLongStateOf(0L) }
    var result by rememberSaveable(sessionKey) { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(0) }
    Column(modifier) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
            Text("길게 눌러 SOS", Modifier.combinedClickable(onClick = {
                android.widget.Toast.makeText(context, "길게 누르면 SOS 전송을 확인합니다.", android.widget.Toast.LENGTH_SHORT).show()
            }, onLongClick = onSos).padding(14.dp))
        }
        if (BuildConfig.DEBUG) TextButton(onClick = {
            val timeout = DetectionSettingsStore(context).read().responseTimeoutSeconds ?: 30
            seconds = timeout
            deadline = SystemClock.elapsedRealtime() + timeout * 1000L
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }) { Text("위험 확인 시연") }
    }
    if (deadline > 0) {
        LaunchedEffect(deadline) {
            while (true) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                seconds = ((remaining + 999) / 1000).toInt()
                delay(minOf(remaining, 500L))
            }
            result = "무응답 · 위험 확정"
            deadline = 0
        }
        AlertDialog(onDismissRequest = {}, title = { Text("괜찮으신가요?") },
            text = { Text("모의 위험 감지 · 응답까지 ${seconds}초") },
            confirmButton = { TextButton(onClick = { deadline = 0; result = "안전 확인" }) { Text("괜찮아요") } },
            dismissButton = { TextButton(onClick = { deadline = 0; result = "도움 요청 · 위험 확정" }) { Text("도와주세요") } })
    }
    if (result.isNotEmpty()) AlertDialog(onDismissRequest = { result = "" },
        title = { Text(result) }, text = { Text("시연 결과 · 실제 알림 발송 없음\n" + (location?.let { "현재 위치 ${it.latitude}, ${it.longitude}" } ?: "현재 위치 확인 중")) },
        confirmButton = { TextButton(onClick = { result = "" }) { Text("확인") } })
}
