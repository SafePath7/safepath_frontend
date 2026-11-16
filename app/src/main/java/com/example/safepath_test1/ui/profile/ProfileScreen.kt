package com.example.safepath_test1.ui.profile

import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val profileViewModel: ProfileViewModel = viewModel()
    val state by profileViewModel.state.collectAsStateWithLifecycle()
    val busy = state.busy
    val accountError = state.error
    val editedName = state.editedName
    val name = state.name
    val phone = state.phone
    val dialog = when (state.dialog) {
        ProfileDialog.NAME -> "name"
        ProfileDialog.PHONE -> "phone"
        ProfileDialog.WITHDRAW -> "withdraw"
        null -> null
    }
    LaunchedEffect(state.withdrawn) {
        if (state.withdrawn) {
            profileViewModel.consumeWithdrawal()
            onLogout()
        }
    }
    if (dialog != null) AlertDialog(
        onDismissRequest = profileViewModel::dismissDialog,
        title = { Text(when (dialog) { "name" -> "회원정보 수정"; "phone" -> "전화번호 변경"; else -> "회원 탈퇴" }) },
        text = {
            when (dialog) {
                "name" -> OutlinedTextField(editedName, profileViewModel::editName, label = { Text("이름") }, singleLine = true, enabled = !busy)
                "phone" -> Text("전화번호 변경은 새 번호의 SMS 인증을 서버에서 확인하는 기능이 준비된 후 사용할 수 있습니다.")
                else -> Text("서버의 회원정보를 삭제하고 로그아웃합니다. Google/Firebase 계정과 Firestore 친구 정보는 삭제되지 않습니다. 계속하시겠습니까?")
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (dialog != "name" || (editedName.isNotBlank() && editedName.trim().codePointCount(0, editedName.trim().length) <= 100)), onClick = {
                when (state.dialog) {
                    ProfileDialog.PHONE -> profileViewModel.dismissDialog()
                    ProfileDialog.NAME -> profileViewModel.saveName()
                    ProfileDialog.WITHDRAW -> profileViewModel.withdraw()
                    null -> Unit
                }
            }) { Text(if (dialog == "phone") "확인" else if (dialog == "name") "저장" else "탈퇴") }
        },
        dismissButton = { if (dialog != "phone") TextButton(enabled = !busy, onClick = profileViewModel::dismissDialog) { Text("취소") } },
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
                            if (state.signedIn) "Google 계정 연결됨" else "계정 미연결",
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
                    TextButton(onClick = { profileViewModel.openDialog(ProfileDialog.NAME) }) { Text("정보 수정") }
                    TextButton(onClick = { profileViewModel.openDialog(ProfileDialog.PHONE) }) { Text("전화번호 변경") }
                    TextButton(onClick = { profileViewModel.openDialog(ProfileDialog.WITHDRAW) }) { Text("회원 탈퇴") }
                }
                accountError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }
        ProfileDetectionSettings(hasLocationPermission, onSettingsClick = { showSettings = true })
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
internal fun ProfileSetting(
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
