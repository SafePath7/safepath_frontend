package com.example.safepath_test1.ui.profile

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safepath_test1.ui.guardian.readGuardians
import com.example.safepath_test1.ui.settings.SettingsScreen
import com.example.safepath_test1.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.example.safepath_test1.api.AccountRepository
import com.example.safepath_test1.api.AccountProfile
import com.example.safepath_test1.api.accountErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ProfileDetectionSettings(hasLocationPermission: Boolean, onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val detectionStore = remember { com.example.safepath_test1.session.DetectionSettingsStore(context) }
    var detection by remember { mutableStateOf(detectionStore.read()) }
    var showTimeout by rememberSaveable { mutableStateOf(false) }
    var timeoutText by rememberSaveable { mutableStateOf("") }
    var timeoutError by remember { mutableStateOf(false) }
    if (showTimeout) AlertDialog(
        onDismissRequest = { showTimeout = false },
        title = { Text("응답 대기 시간") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("다음 귀가에 사용할 시간을 초 단위로 입력해 주세요. 기본값은 30초이며 이 기기에 저장합니다.")
            OutlinedTextField(timeoutText, { timeoutText = it.filter { c -> c in '0'..'9' }; timeoutError = false },
                label = { Text("초") }, singleLine = true, isError = timeoutError,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                supportingText = { if (timeoutError) Text("0보다 큰 정수를 입력해 주세요.") })
        } },
        confirmButton = { TextButton(onClick = {
            val seconds = timeoutText.toIntOrNull()
            if (seconds == null || seconds <= 0) timeoutError = true
            else { detection = detection.copy(responseTimeoutSeconds = seconds); detectionStore.save(detection); showTimeout = false }
        }) { Text("저장") } },
        dismissButton = { TextButton(onClick = { showTimeout = false }) { Text("취소") } },
    )
        Text("AI 위험 감지", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                listOf("위험 음성 감지", "위험 음향 감지", "격한 움직임 감지", "경로 이탈 알림").forEachIndexed {
                    index,
                    title ->
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .padding(start = 16.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Switch(
                            checked = listOf(detection.voice, detection.sound, detection.motion, detection.routeDeviation)[index],
                            onCheckedChange = { enabled ->
                                detection = when (index) {
                                    0 -> detection.copy(voice = enabled)
                                    1 -> detection.copy(sound = enabled)
                                    2 -> detection.copy(motion = enabled)
                                    else -> detection.copy(routeDeviation = enabled)
                                }
                                detectionStore.save(detection)
                            },
                        )
                    }
                    if (index < 3)
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = AppBorder)
                }
            }
        }
        Text("다음 귀가에 사용할 설정을 이 기기에 저장합니다. 실제 AI 감지는 서버 연결 후 사용할 수 있어요.", color = TextMuted, fontSize = 12.sp)
        Text("귀가 설정", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                ProfileSetting("응답 대기 시간", detection.responseTimeoutSeconds?.let { "${it}초 (기기 저장)" } ?: "미설정") {
                    timeoutText = detection.responseTimeoutSeconds?.toString().orEmpty()
                    timeoutError = false
                    showTimeout = true
                }
                HorizontalDivider(Modifier.padding(start = 16.dp), color = AppBorder)
                ProfileSetting("즐겨찾는 장소", "홈에서 관리")
                HorizontalDivider(Modifier.padding(start = 16.dp), color = AppBorder)
                ProfileSetting(
                    "앱 권한",
                    if (hasLocationPermission) "위치 ›" else "허용된 권한 없음 ›",
                    valueColor = if (hasLocationPermission) SafeGreen else TextMuted,
                ) {
                    onSettingsClick()
                }
            }
        }
}
