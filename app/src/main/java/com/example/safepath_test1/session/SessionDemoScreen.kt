package com.example.safepath_test1.session

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionDemoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val collectorRunning by JourneyLocationService.running.collectAsStateWithLifecycle()
    val collectedLocation by JourneyLocationService.latest.collectAsStateWithLifecycle()
    val collectorMessage by JourneyLocationService.message.collectAsStateWithLifecycle()
    var collectorError by remember { mutableStateOf<String?>(null) }
    val startCollector = {
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, android.content.Intent(context, JourneyLocationService::class.java))
            collectorError = null
        } catch (_: Exception) { collectorError = "위치 수집을 시작하지 못했습니다. 위치 권한과 앱 실행 상태를 확인해 주세요." }
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true || results[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true) startCollector()
        else collectorError = "위치 권한이 거부됐습니다. 샘플 좌표 시연은 계속 사용할 수 있어요."
    }
    DisposableEffect(Unit) { onDispose { context.stopService(android.content.Intent(context, JourneyLocationService::class.java)) } }
    val vm: SessionDemoViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("추천") }
    var guardian by rememberSaveable { mutableStateOf(true) }
    var pin by remember { mutableStateOf("") }
    var authPin by remember { mutableStateOf("") }
    var monitor by rememberSaveable { mutableStateOf(false) }
    val settings = DetectionSettingsStore(LocalContext.current).read()
    fun time(value: Long) = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(value))
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("앱으로 돌아가기") }
        Text("안전귀가 로컬 시연", style = MaterialTheme.typography.headlineSmall)
        Text("모의 데이터 · 실제 위치 전송/보호자 알림/긴급 신고 없음", color = MaterialTheme.colorScheme.error)
        Text("샘플 계정과 좌표를 사용합니다. 앱 프로세스 종료 시 모의 세션은 폐기됩니다.")
        OutlinedButton(onClick = {
            if (collectorRunning) context.stopService(android.content.Intent(context, JourneyLocationService::class.java))
            else permissionLauncher.launch(buildList {
                add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray())
        }) { Text(if (collectorRunning) "실제 GPS 수집 시험 종료" else "실제 GPS 백그라운드 수집 시험") }
        Text(collectorError ?: collectorMessage)
        collectedLocation?.let { Text("기기 GPS: ${it.point.latitude}, ${it.point.longitude} · ${time(it.measuredAt)} · 서버 전송 없음") }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.session == null) {
            Text("샘플 사용자 · 계명대 주변 출발 → 샘플 목적지")
            Text("경로 비교 (고정 샘플, 실제 탐색 결과 아님)")
            listOf("가장 안전" to "1.4km · 18분 · 92점", "추천" to "1.2km · 16분 · 85점", "최단" to "1.0km · 13분 · 54점").forEach { (name, detail) ->
                OutlinedButton(onClick = { route = name }, modifier = Modifier.fillMaxWidth()) { Text("${if (route == name) "✓ " else ""}$name · $detail") }
            }
            Row { Checkbox(checked = guardian, onCheckedChange = { guardian = it }); Text("샘플 보호자 (수락 상태 · 모의)") }
            OutlinedTextField(pin, { pin = it.filter { c -> c in '0'..'9' }.take(4) }, label = { Text("종료 비밀번호 4자리") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
            Text("생체인증은 서버 challenge 규격 확정 후 연결됩니다. 비밀번호를 사용해 주세요.")
            Button(onClick = { vm.start(route, if (guardian) listOf("demo-guardian") else emptyList(), pin, settings); pin = "" }, enabled = guardian && pin.length == 4) { Text("모의 귀가 시작") }
        } else {
            val session = requireNotNull(state.session)
            Text("상태: ${when(session.status) { SessionStatus.MOVING -> "이동중"; SessionStatus.SUSPECTED -> "위험 의심단계"; SessionStatus.DANGER -> "위험상황"; else -> "종료" }}", style = MaterialTheme.typography.titleLarge)
            Text("목적지에 도착해도 비밀번호 인증 전에는 종료되지 않습니다.")
            session.location?.let { Text("마지막 위치 ${it.point.latitude}, ${it.point.longitude}\n갱신 ${time(it.measuredAt)}") } ?: Text("위치 미수신 · 아래 샘플 이동으로 갱신")
            Row { Switch(monitor, { monitor = it }); Text("보호자 화면으로 보기 (모의 역할 전환)") }
            if (monitor) {
                Text("샘플 귀가자 · 샘플 목적지")
                if (session.status == SessionStatus.DANGER) Text("위험 배너: ${session.eventDescription}", color = MaterialTheme.colorScheme.error)
                Text("상태 기록")
                state.history.forEach { Text(it) }
            }
            Button(onClick = vm::move) { Text("샘플 위치 한 단계 이동") }
            OutlinedButton(onClick = vm::toggleOffline) { Text(if (state.offline) "모의 연결 복구" else "모의 네트워크 끊기") }
            Text("임시 위치 ${state.pending.size}개 · 메모리 보관만 검증")
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = vm::sos)) {
                Text("길게 눌러 SOS (모의)", Modifier.padding(24.dp))
            }
            OutlinedButton(onClick = vm::suspect, enabled = session.status == SessionStatus.MOVING) { Text("위험 확인 질문 주입 (시험용 30초)") }
            if (session.status == SessionStatus.SUSPECTED) {
                RiskConfirmationDialog(requireNotNull(session.responseDeadlineMillis), vm::answer, isSimulation = true)
            }
            OutlinedTextField(authPin, { authPin = it.filter { c -> c in '0'..'9' }.take(4) }, label = { Text("종료/해제 비밀번호") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
            if (session.status == SessionStatus.DANGER) OutlinedButton(onClick = { vm.authenticate(authPin, true); authPin = "" }, enabled = authPin.length == 4) { Text("모의 위험 해제") }
            Button(onClick = { vm.authenticate(authPin, false); authPin = "" }, enabled = authPin.length == 4) { Text("인증 후 모의 귀가 종료") }
        }
    }
}
