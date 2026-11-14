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
    val prefs = remember { context.getSharedPreferences("safe_path_profile", Context.MODE_PRIVATE) }
    val user = FirebaseAuth.getInstance().currentUser
    val repository = remember(context) { AccountRepository(context) }
    val scope = rememberCoroutineScope()
    var serverProfile by remember(user?.uid) { mutableStateOf<AccountProfile?>(null) }
    var accountError by remember(user?.uid) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var editedName by remember { mutableStateOf("") }
    suspend fun refreshProfile() {
        val result = repository.profile()
        serverProfile = result
        prefs.edit().putString("user_name", result.name).putString("user_phone", result.phoneNumber).apply()
    }
    LaunchedEffect(user?.uid) {
        try { refreshProfile() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { accountError = accountErrorMessage(error) }
    }
    val name = serverProfile?.name ?: prefs.getString("user_name", null) ?: user?.displayName ?: "사용자"
    val phone = serverProfile?.phoneNumber ?: prefs.getString("user_phone", null) ?: "전화번호 미등록"
    if (dialog != null) AlertDialog(
        onDismissRequest = { if (!busy) dialog = null },
        title = { Text(when (dialog) { "name" -> "회원정보 수정"; "phone" -> "전화번호 변경"; else -> "회원 탈퇴" }) },
        text = {
            when (dialog) {
                "name" -> OutlinedTextField(editedName, { editedName = it }, label = { Text("이름") }, singleLine = true, enabled = !busy)
                "phone" -> Text("전화번호 변경은 새 번호의 SMS 인증을 서버에서 확인하는 기능이 준비된 후 사용할 수 있습니다.")
                else -> Text("서버의 회원정보를 삭제하고 로그아웃합니다. Google/Firebase 계정과 Firestore 친구 정보는 삭제되지 않습니다. 계속하시겠습니까?")
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (dialog != "name" || (editedName.isNotBlank() && editedName.trim().codePointCount(0, editedName.trim().length) <= 100)), onClick = {
                when (dialog) {
                    "phone" -> dialog = null
                    "name" -> {
                        busy = true
                        scope.launch {
                            try {
                                repository.update(editedName)
                                refreshProfile()
                                accountError = null
                                dialog = null
                            } catch (error: CancellationException) { throw error }
                            catch (error: Exception) { accountError = accountErrorMessage(error); dialog = null }
                            finally { busy = false }
                        }
                    }
                    else -> {
                        busy = true
                        scope.launch {
                            try {
                                repository.withdraw()
                                if (FirebaseAuth.getInstance().currentUser?.uid == user?.uid) {
                                    prefs.edit().remove("user_name").remove("user_phone").remove("onboarded_uid").apply()
                                    onLogout()
                                }
                            } catch (error: CancellationException) { throw error }
                            catch (error: Exception) { accountError = accountErrorMessage(error); dialog = null }
                            finally { busy = false }
                        }
                    }
                }
            }) { Text(if (dialog == "phone") "확인" else if (dialog == "name") "저장" else "탈퇴") }
        },
        dismissButton = { if (dialog != "phone") TextButton(enabled = !busy, onClick = { dialog = null }) { Text("취소") } },
    )
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { editedName = name; dialog = "name" }) { Text("정보 수정") }
                    TextButton(onClick = { dialog = "phone" }) { Text("전화번호 변경") }
                    TextButton(onClick = { dialog = "withdraw" }) { Text("회원 탈퇴") }
                }
                accountError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
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
