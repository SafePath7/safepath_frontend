package com.example.safepath_test1.ui.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safepath_test1.auth.normalizeKoreanPhoneNumber
import com.example.safepath_test1.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

@Composable
fun SignupScreen(onComplete: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val model: SignupViewModel = viewModel(key = "signup:$uid")
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(context) { context.getSharedPreferences("safe_path_profile", Context.MODE_PRIVATE) }
    val back = { model.abandon(); onBack() }
    BackHandler(onBack = back)

    LaunchedEffect(model) {
        model.onEnter()
        activity?.let { model.onForeground(it, reattach = true) }
    }
    DisposableEffect(model, lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> activity?.let { model.onForeground(it, reattach = true) }
                Lifecycle.Event.ON_STOP -> model.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Retain the ViewModel session on rotation, clear it when leaving onboarding.
            if (activity?.isChangingConfigurations != true) model.abandon()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color.White).systemBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
            Text("처음 오셨네요", style = MaterialTheme.typography.headlineLarge)
            Text("보호자가 알아볼 수 있도록 이름과 전화번호를 알려 주세요.", color = TextMuted)
            OutlinedTextField(
                state.name, model::setName, label = { Text("이름") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state.phone, model::setPhone, label = { Text("전화번호") }, singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                supportingText = { Text("010-1234-5678 또는 +82 10-1234-5678") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.phase != SignupPhase.VERIFIED) {
                OutlinedButton(
                    onClick = { activity?.let(model::sendCode) },
                    enabled = activity != null && !state.busy && !state.canEnterCode &&
                        state.resendSeconds == 0 && normalizeKoreanPhoneNumber(state.phone) != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.phase == SignupPhase.SENDING) "인증번호 발송 중…" else "인증번호 발송") }
                Text("인증을 요청하면 SMS가 발송됩니다. 통신사 요금이 발생할 수 있어요.",
                    color = TextMuted, style = MaterialTheme.typography.bodySmall)
                if (state.canEnterCode || state.phase == SignupPhase.LINKING) {
                    OutlinedTextField(
                        state.code, model::setCode, label = { Text("6자리 인증번호") },
                        singleLine = true, enabled = state.canEnterCode && !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = model::verifyCode,
                            enabled = state.canEnterCode && !state.busy && state.code.length == 6,
                            modifier = Modifier.weight(1f),
                        ) { Text("인증 확인") }
                        OutlinedButton(
                            onClick = { activity?.let(model::resendCode) },
                            enabled = activity != null && !state.busy && state.canResend,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (state.resendSeconds > 0) "재발송 (${state.resendSeconds}초)" else "인증번호 재발송") }
                    }
                }
                if (state.resendSeconds > 0 && !state.canEnterCode) {
                    Text("다시 발송하려면 ${state.resendSeconds}초 기다려 주세요.", color = TextMuted)
                }
            }
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(when (state.phase) {
                    SignupPhase.CHECKING -> "계정의 전화번호 연결 상태를 확인하고 있어요."
                    SignupPhase.LINKING -> "Google 계정에 전화번호를 연결하고 있어요."
                    else -> "인증번호를 요청하고 있어요."
                }, color = TextMuted)
            }
            if (state.phase == SignupPhase.VERIFIED) Text("전화번호 인증이 완료되었습니다.", color = SafeBlue)
            else if (state.autoRetrievalTimedOut && state.canEnterCode) {
                Text("자동 인증 대기가 끝났습니다. SMS의 6자리 번호를 직접 입력해 주세요.", color = TextMuted)
            } else if (state.canEnterCode) Text("인증번호를 발송했습니다. SMS를 확인해 주세요.", color = TextMuted)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.phase == SignupPhase.FAILED) {
                TextButton(onClick = model::onEnter) { Text("계정 연결 상태 다시 확인") }
            }
            Text("이런 권한이 필요해요", fontWeight = FontWeight.Bold)
            listOf(
                "위치" to "귀가 중 보호자와 실시간 위치를 공유해요",
                "마이크" to "위험 음성과 소리를 감지할 때 사용해요",
                "알림" to "내가 지키는 사람의 위험 알림을 받아요",
            ).forEach { (title, body) ->
                Surface(color = FieldBg, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(body, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(
            shape = MaterialTheme.shapes.small,
            enabled = state.canComplete,
            onClick = {
                if (model.completeSignup { userUid, name, phone ->
                    // Existing device onboarding contract, not a server membership record.
                    prefs.edit().putString("user_name", name).putString("user_phone", phone)
                        .putString("onboarded_uid", userUid).apply()
                }) onComplete()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("시작하기") }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
