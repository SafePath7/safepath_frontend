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

@Composable
fun ProfileScreen(
    hasLocationPermission: Boolean,
    onLogout: () -> Unit,
    onSosClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }
    if (showSettings) {
        SettingsScreen(
            hasLocationPermission = hasLocationPermission,
            onBack = { showSettings = false },
            modifier = modifier,
        )
        return
    }
    val context = LocalContext.current
    val detectionStore = remember { com.example.safepath_test1.session.DetectionSettingsStore(context) }
    var detection by remember { mutableStateOf(detectionStore.read()) }
    val prefs = remember { context.getSharedPreferences("safe_path_profile", Context.MODE_PRIVATE) }
    val user = FirebaseAuth.getInstance().currentUser
    val name = prefs.getString("user_name", null) ?: user?.displayName ?: "사용자"
    val phone = prefs.getString("user_phone", null) ?: "전화번호 미등록"
    val friends = remember { readGuardians(context).size }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "내 정보",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineLarge,
                color = TextMain,
            )
            TextButton(onClick = onLogout) { Text("로그아웃", color = TextMuted) }
        }
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = SafeBlue,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                name.take(1),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(phone, color = TextMuted, fontSize = 12.sp)
                        Text(
                            if (user != null) "Google 계정 연결됨" else "계정 미연결",
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                    OutlinedButton(
                        onClick = { showSettings = true },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Text("설정")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileStat("$friends", "연결된 친구", Modifier.weight(1f))
                    ProfileStat("—", "보호 중", Modifier.weight(1f))
                }
            }
        }
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
                ProfileSetting("응답 대기 시간", "서버 정책 확정 대기")
                HorizontalDivider(Modifier.padding(start = 16.dp), color = AppBorder)
                ProfileSetting("즐겨찾는 장소", "홈에서 관리")
                HorizontalDivider(Modifier.padding(start = 16.dp), color = AppBorder)
                ProfileSetting(
                    "앱 권한",
                    if (hasLocationPermission) "위치 ›" else "허용된 권한 없음 ›",
                    valueColor = if (hasLocationPermission) SafeGreen else TextMuted,
                ) {
                    showSettings = true
                }
            }
        }
        Button(
            onClick = onSosClick,
            modifier = Modifier.fillMaxWidth(0.5f).align(Alignment.CenterHorizontally),
            colors =
                ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text("SOS", maxLines = 1, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = FieldBg, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = SafeBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProfileSetting(
    title: String,
    value: String,
    valueColor: Color = TextMuted,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 12.sp)
    }
}
