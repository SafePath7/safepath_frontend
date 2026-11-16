package com.example.safepath_test1.ui.guardian

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.safepath_test1.api.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BackendGuardianScreen(modifier: Modifier = Modifier) {
    val viewModel: BackendGuardianViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = state.rows
    val myId = state.myId
    val busy = state.busy
    val message = state.error
    val events = state.events
    val guardianId = state.guardianInput
    var editing by remember { mutableStateOf<GuardianRegistration?>(null) }
    Column(modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        Text("보호자 관리", style = MaterialTheme.typography.headlineMedium)
        Text("내 회원 ID: ${myId ?: "확인 중"}", modifier = Modifier.padding(vertical = 8.dp))
        Text("상대방의 회원 ID를 입력해 보호자를 요청하세요. 전화번호 검색은 서버에서 아직 제공하지 않습니다.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(guardianId, viewModel::changeGuardianInput, label = { Text("보호자 회원 ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
            Button(enabled = !busy && (guardianId.toLongOrNull() ?: 0L) > 0, onClick = { val id = guardianId.toLongOrNull() ?: return@Button; viewModel.requestGuardian(id) }) { Text("요청") }
        }
        Row { TextButton(enabled = !busy, onClick = viewModel::refresh) { Text("새로고침") } }
        TextButton(enabled = !busy, onClick = { viewModel.loadEvents() }) { Text("내 위험 기록") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { row ->
                val owner = row.userId == myId
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (owner) "내 보호자: ${row.guardianName}" else "보호할 사용자: ${row.userName}")
                        Text(when (row.status) { "PENDING" -> "요청 대기"; "ACCEPTED" -> "수락됨"; "REJECTED" -> "거절됨"; "REVOKED" -> "해제됨"; "SUSPENDED" -> "중지됨"; else -> row.status })
                        Row {
                            if (row.status == "PENDING" && !owner) {
                                TextButton(enabled = !busy, onClick = { viewModel.respond(row.id, "accept") }) { Text("수락") }
                                TextButton(enabled = !busy, onClick = { viewModel.respond(row.id, "reject") }) { Text("거절") }
                            }
                            if (row.status == "PENDING" || row.status == "ACCEPTED") TextButton(enabled = !busy, onClick = { viewModel.respond(row.id, "revoke") }) { Text(if (row.status == "PENDING") "요청 취소" else "관계 해제") }
                            if (owner && row.status == "ACCEPTED") TextButton(enabled = !busy, onClick = { editing = row }) { Text("열람 권한") }
                            if (!owner && row.status == "ACCEPTED" && row.canViewStatus) TextButton(enabled = !busy, onClick = { viewModel.loadEvents(row.userId) }) { Text("위험 기록") }
                        }
                    }
                }
            }
        }
    }
    events?.let { list ->
        AlertDialog(onDismissRequest = viewModel::dismissEvents, title = { Text("위험 기록") }, text = {
            LazyColumn { if (list.isEmpty()) item { Text("기록이 없습니다.") }; items(list, key = { it.id }) { event ->
                Column(Modifier.padding(vertical = 8.dp)) { Text("${event.eventType} · ${event.status}"); Text(event.description); Text(event.detectedAt) }
            } }
        }, confirmButton = { TextButton(onClick = viewModel::dismissEvents) { Text("닫기") } })
    }
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
