package com.example.safepath_test1.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safepath_test1.auth.GoogleAuthManager
import com.example.safepath_test1.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginPage(onLoginSuccess: () -> Unit, modifier: Modifier = Modifier, repository: com.example.safepath_test1.auth.AuthRepository? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultAuth = remember(context) { GoogleAuthManager(context) }
    val auth = repository ?: defaultAuth
    var busy by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf<String?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFEAF4F7), Color.White, Color.White)))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, AppBorder),
            modifier = Modifier.size(112.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Shield, null, tint = SafeBlue, modifier = Modifier.size(64.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "SAFE PATH",
            style = MaterialTheme.typography.headlineLarge,
            color = SafeBlue,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("안심하고 걷는 모든 순간", color = TextMuted)
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, AppBorder),
            onClick = {
                scope.launch {
                    busy = true
                    loginMessage = null
                    try {
                        val result = auth.signInWithGoogle()
                        if (result.isSuccess) onLoginSuccess()
                        else loginMessage = when (val error = result.exceptionOrNull()) {
                            is com.example.safepath_test1.auth.LoginCancelledException -> "로그인을 취소했어요. 다시 시작할 수 있습니다."
                            is com.google.firebase.FirebaseNetworkException -> "네트워크 연결을 확인한 뒤 다시 시도해 주세요."
                            else -> error?.message?.takeIf { it == "사용 가능한 Google 계정을 찾지 못했습니다." }
                                ?: "Google 로그인에 실패했습니다. 다시 시도해 주세요."
                        }
                    } finally { busy = false }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text("G", color = SkyBlue, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(if (busy) "로그인 중…" else "Google로 계속하기", color = TextMain)
        }
        loginMessage?.let { Text(it, modifier = Modifier.padding(top = 12.dp), color = TextMuted) }
        Text(
            "계속하면 Safe Path의 이용약관 · 개인정보처리방침에\n동의하는 것으로 간주돼요.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
        )
    }
}
