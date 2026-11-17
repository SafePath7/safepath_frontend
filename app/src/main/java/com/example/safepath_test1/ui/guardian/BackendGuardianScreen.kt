package com.example.safepath_test1.ui.guardian

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.safepath_test1.ui.theme.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.safepath_test1.api.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BackendGuardianScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val viewModel: BackendGuardianViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = state.rows
    val myId = state.myId
    val busy = state.busy
    val message = state.error
    val events = state.events
    val guardianId = state.guardianInput
    var editing by remember { mutableStateOf<GuardianRegistration?>(null) }
    var reportDescription by remember { mutableStateOf<String?>(null) }
    var confirmResolve by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pending = pendingDownload
        pendingDownload = null
        if (uri != null && pending != null) viewModel.downloadMedia(pending.first, pending.second, uri)
    }
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().imePadding().padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TextMain) }
            Text("보호자 관리", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            IconButton(enabled = !busy, onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "새로고침", tint = TextMain) }
        }
        Text("내 회원 ID  ${myId ?: "확인 중"}", color = TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                guardianId, viewModel::changeGuardianInput,
                placeholder = { Text("보호자 회원 ID", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.PersonAdd, null, tint = TextMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f), singleLine = true, enabled = !busy, shape = RoundedCornerShape(14.dp),
            )
            Button(enabled = !busy && (guardianId.toLongOrNull() ?: 0L) > 0, onClick = { val id = guardianId.toLongOrNull() ?: return@Button; viewModel.requestGuardian(id) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(56.dp)) { Text("요청") }
        }
        HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 12.dp), color = AppBorder)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !busy, onClick = { viewModel.loadEvents() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("내 위험 기록") }
                    if (state.sessionId != null) TextButton(enabled = !busy, onClick = { reportDescription = "" }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("위험 상황 기록") }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 12.dp), color = AppBorder)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("보호 관계 ${rows.size}", color = TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text("회원 ID로 요청", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
                message?.let { Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                state.notice?.let { Text(it, color = SafeBlue, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = SkyBlue, trackColor = FieldBg)
            }
            if (rows.isEmpty() && !busy) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PeopleOutline, null, tint = AppBorder, modifier = Modifier.size(56.dp))
                    Text("등록된 보호 관계가 없어요", color = TextMuted, modifier = Modifier.padding(top = 12.dp))
                }
            }
            items(rows, key = { it.id }) { row ->
                val owner = row.userId == myId
                val name = if (owner) row.guardianName else row.userName
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(18.dp), color = SkyBlue.copy(alpha = .12f), modifier = Modifier.size(52.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(name.take(1), color = SafeBlue, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(name, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(if (owner) "나의 보호자" else "내가 보호하는 사용자", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(when (row.status) { "PENDING" -> "요청 대기"; "ACCEPTED" -> "연결됨"; "REJECTED" -> "거절됨"; "REVOKED" -> "해제됨"; "SUSPENDED" -> "중지됨"; else -> row.status }, color = if (row.status == "ACCEPTED") SafeBlue else TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    if (row.status == "PENDING" || row.status == "ACCEPTED") {
                        Row(Modifier.padding(start = 58.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (row.status == "PENDING" && !owner) {
                                TextButton(enabled = !busy, onClick = { viewModel.respond(row.id, "accept") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("수락") }
                                TextButton(enabled = !busy, onClick = { viewModel.respond(row.id, "reject") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("거절") }
                            }
                            if (owner && row.status == "ACCEPTED") TextButton(enabled = !busy, onClick = { editing = row }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("열람 권한") }
                            if (!owner && row.status == "ACCEPTED" && row.canViewStatus) TextButton(enabled = !busy, onClick = { viewModel.loadEvents(row.userId, row.id) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("위험 기록") }
                            TextButton(enabled = !busy, onClick = { viewModel.respond(row.id, "revoke") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (row.status == "PENDING") "요청 취소" else "관계 해제", color = TextMuted) }
                        }
                    }
                }
            }
        }
    }
    reportDescription?.let { description ->
        AlertDialog(onDismissRequest = { reportDescription = null }, title = { Text("위험 상황 직접 기록") }, text = {
            Column {
                Text("저장하면 등록된 보호자에게 알림이 발송될 수 있습니다.")
                OutlinedTextField(description, { reportDescription = it.take(500) }, label = { Text("상황 설명") })
            }
        }, confirmButton = {
            TextButton(enabled = !busy && description.isNotBlank(), onClick = { viewModel.createEvent(description); reportDescription = null }) { Text("저장 및 알림") }
        }, dismissButton = { TextButton(onClick = { reportDescription = null }) { Text("취소") } })
    }
    events?.let { list ->
        AlertDialog(onDismissRequest = viewModel::dismissEvents, title = { Text("위험 기록") }, text = {
            LazyColumn { if (list.isEmpty()) item { Text("기록이 없습니다.") }; items(list, key = { it.id }) { event ->
                Column(Modifier.padding(vertical = 8.dp)) { Text("${event.eventType} · ${event.status}"); Text(event.description); Text(event.detectedAt); TextButton(enabled = !busy, onClick = { viewModel.loadEventDetail(event.id) }) { Text("상세 및 첨부 목록") } }
            } }
        }, confirmButton = { TextButton(onClick = viewModel::dismissEvents) { Text("닫기") } })
    }
    state.eventDetail?.let { event ->
        AlertDialog(onDismissRequest = viewModel::dismissEventDetail, title = { Text("위험 기록 상세") }, text = {
            Column {
                Text("${event.eventType} · ${event.status}")
                Text(event.description)
                Text(event.detectedAt)
                Text("첨부 ${state.eventMedia.size}개")
                state.eventMedia.forEach { media ->
                    TextButton(enabled = !busy, onClick = {
                        pendingDownload = event.id to media.id
                        downloadLauncher.launch(media.fileName?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() } ?: "attachment")
                    }) { Text("${media.mediaType}: ${media.fileName ?: "파일"} 저장") }
                }
                if (event.userId == myId && event.status != "RESOLVED") {
                    TextButton(enabled = !busy, onClick = { confirmResolve = true }) { Text("해결 완료로 표시") }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.notice?.let { Text(it) }
            }
        }, confirmButton = { TextButton(onClick = viewModel::dismissEventDetail) { Text("닫기") } })
    }
    if (confirmResolve) AlertDialog(
        onDismissRequest = { confirmResolve = false }, title = { Text("해결 완료로 표시할까요?") },
        text = { Text("상태 변경 시 보호자에게 알림이 발송될 수 있습니다.") },
        confirmButton = { TextButton(onClick = { state.eventDetail?.let { viewModel.resolveEvent(it.id) }; confirmResolve = false }) { Text("변경") } },
        dismissButton = { TextButton(onClick = { confirmResolve = false }) { Text("취소") } },
    )
    editing?.let { row ->
        var location by remember(row.id) { mutableStateOf(row.canViewLocation) }
        var status by remember(row.id) { mutableStateOf(row.canViewStatus) }
        var personal by remember(row.id) { mutableStateOf(row.canViewPersonalInfo) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("보호자 열람 권한") }, text = {
            Column {
                Row { Checkbox(location, { location = it }); Text("위치") }
                Row { Checkbox(status, { status = it }); Text("상태") }
                Row { Checkbox(personal, { personal = it }); Text("개인정보") }
            }
        }, confirmButton = { TextButton(onClick = { editing = null; viewModel.updatePermissions(row.id, GuardianPermissions(location, status, personal)) }) { Text("저장") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("취소") } })
    }
}
