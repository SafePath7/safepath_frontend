package com.example.safepath_test1.ui.login

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safepath_test1.auth.GoogleAuthManager
import com.example.safepath_test1.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginPage(onLoginSuccess: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { GoogleAuthManager(context) }
    var busy by remember { mutableStateOf(false) }
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
                    val result = auth.signInWithGoogle()
                    busy = false
                    if (result.isSuccess) onLoginSuccess()
                    else
                        Toast.makeText(
                                context,
                                "Google 로그인에 실패했습니다. 다시 시도해 주세요.",
                                Toast.LENGTH_LONG,
                            )
                            .show()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text("G", color = SkyBlue, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(if (busy) "로그인 중…" else "Google로 계속하기", color = TextMain)
        }
        Text(
            "계속하면 Safe Path의 이용약관 · 개인정보처리방침에\n동의하는 것으로 간주돼요.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
        )
    }
}

@Composable
fun SignupScreen(onComplete: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    val prefs = remember {
        context.getSharedPreferences("safe_path_profile", android.content.Context.MODE_PRIVATE)
    }
    var name by rememberSaveable { mutableStateOf(user?.displayName.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val phoneValid = phone.filter(Char::isDigit).length in 10..11
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
            Text("처음 오셨네요", style = MaterialTheme.typography.headlineLarge)
            Text("보호자가 알아볼 수 있도록 이름과 전화번호를 알려 주세요.", color = TextMuted)
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("이름") },
                singleLine = true,
                isError = submitted && name.isBlank(),
                supportingText = { if (submitted && name.isBlank()) Text("이름을 입력해 주세요.") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                phone,
                { phone = it },
                label = { Text("전화번호") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = submitted && !phoneValid,
                supportingText = { if (submitted && !phoneValid) Text("올바른 전화번호를 입력해 주세요.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("이런 권한이 필요해요", fontWeight = FontWeight.Bold)
            listOf(
                    "위치" to "귀가 중 보호자와 실시간 위치를 공유해요",
                    "마이크" to "위험 음성과 소리를 감지할 때 사용해요",
                    "알림" to "내가 지키는 사람의 위험 알림을 받아요",
                )
                .forEach { (title, body) ->
                    Surface(color = FieldBg, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(title, fontWeight = FontWeight.Bold)
                            Text(
                                body,
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
        }
        Button(
            shape = MaterialTheme.shapes.small,
            onClick = {
                submitted = true
                if (name.isNotBlank() && phoneValid) {
                    prefs
                        .edit()
                        .putString("user_name", name.trim())
                        .putString("user_phone", phone.trim())
                        .putString("onboarded_uid", user?.uid)
                        .apply()
                    onComplete()
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text("시작하기")
        }
    }
}
