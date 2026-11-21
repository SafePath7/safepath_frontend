package com.example.safepath_test1.ui.guardian

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.safepath_test1.api.AccountRepository
import com.example.safepath_test1.api.BackendRepository
import com.example.safepath_test1.api.accountErrorMessage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException

private data class ActiveGuardianSession(val id: String, val name: String, val startedAt: String)

@Composable
fun ActiveGuardianScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var revision by remember { mutableIntStateOf(0) }
    var rows by remember(uid) { mutableStateOf<List<ActiveGuardianSession>>(emptyList()) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var loading by remember(uid) { mutableStateOf(true) }
    LaunchedEffect(uid, revision) {
        rows = emptyList(); error = null; loading = true
        if (uid == null) { loading = false; return@LaunchedEffect }
        try {
            val api = BackendRepository(context)
            val me = AccountRepository(context).profile()
            val registrations = api.guardians().filter {
                it.guardianId == me.userId && it.status == "ACCEPTED" && it.canViewLocation
            }
            val active = registrations.mapNotNull { registration ->
                val session = api.activeSession(registration.userId)
                session?.let { ActiveGuardianSession(it.id, registration.userName, it.startedAt) }
            }
            check(FirebaseAuth.getInstance().currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
            rows = active
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = accountErrorMessage(e) }
        finally { loading = false }
    }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("보호 중인 귀가", style = MaterialTheme.typography.headlineSmall)
        Text("선택된 보호자에게 허용된 진행 세션을 표시합니다.")
        OutlinedButton(onClick = { revision++ }, enabled = !loading) { Text("새로고침") }
        when {
            loading -> Text("세션을 확인하고 있습니다.")
            error != null -> Text(error!!)
            rows.isEmpty() -> Text("현재 확인할 수 있는 귀가 세션이 없습니다.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.id }) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(row.name, style = MaterialTheme.typography.titleMedium)
                            Text("세션 시작: ${row.startedAt}")
                            Text("실시간 위치 수신은 아직 연결되지 않았습니다.")
                        }
                    }
                }
            }
        }
    }
}
