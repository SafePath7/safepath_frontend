package com.example.safepath_test1.session

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** FD-019: deadline is supplied by the event source, never restarted on recomposition. */
@Composable
fun RiskConfirmationDialog(deadlineMillis: Long, onAnswer: (Boolean) -> Unit, isSimulation: Boolean) {
    val context = LocalContext.current
    var now by remember(deadlineMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadlineMillis) {
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            else { @Suppress("DEPRECATION") vibrator.vibrate(400) }
        }
        while (now < deadlineMillis) { delay(200); now = System.currentTimeMillis() }
    }
    val seconds = ((deadlineMillis - now).coerceAtLeast(0) + 999) / 1000
    AlertDialog(
        onDismissRequest = {},
        title = { Text("괜찮으신가요?") },
        text = { Text("응답까지 ${seconds}초\n" + if (isSimulation) "모의 위험 확인입니다. 시간이 끝나면 모의 위험상황으로 전환합니다. 실제 알림은 전송되지 않습니다." else "도움이 필요하면 도와주세요를 눌러 주세요.") },
        confirmButton = { Button(onClick = { onAnswer(true) }, enabled = seconds > 0) { Text("도와주세요") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }, enabled = seconds > 0) { Text("괜찮아요") } },
    )
}
