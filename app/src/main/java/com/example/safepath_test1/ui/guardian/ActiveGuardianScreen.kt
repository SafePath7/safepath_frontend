package com.example.safepath_test1.ui.guardian

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.safepath_test1.ui.theme.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import com.example.safepath_test1.model.GeoPoint
import com.example.safepath_test1.ui.map.SafePathKakaoMapView
import com.example.safepath_test1.wear.haversineMeters

private data class ActiveGuardianSession(val id: String, val name: String, val startedAt: String)

@Composable
fun ActiveGuardianScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var revision by remember { mutableIntStateOf(0) }
    var rows by remember(uid) { mutableStateOf<List<ActiveGuardianSession>>(emptyList()) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var loading by remember(uid) { mutableStateOf(true) }
    var selectedId by remember(uid) { mutableStateOf<String?>(null) }
    LaunchedEffect(uid, revision) {
        error = null; loading = true
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
            rows = active.distinctBy { it.id }.sortedByDescending { it.startedAt }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = accountErrorMessage(e) }
        finally { loading = false }
    }
    val selected = rows.firstOrNull { it.id == selectedId }
    if (selected != null) {
        BackHandler { selectedId = null }
        ActiveGuardianDetail(selected, onBack = { selectedId = null }, modifier = modifier)
        return
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("보호", style = MaterialTheme.typography.headlineLarge, color = TextMain)
                Text("소중한 사람의 귀가를 함께 확인해요", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            IconButton(onClick = { revision++ }, enabled = !loading) { Icon(Icons.Default.Refresh, "새로고침", tint = SafeBlue) }
        }
        Surface(color = SafeBlue, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = Color.White, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("보호 중인 귀가", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
                    Text(if (loading || error != null) "확인 중" else "${rows.size}명", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("진행 중인 귀가", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextMain)
        when {
            loading -> GuardianStatePanel("귀가 정보를 확인하고 있어요", "잠시만 기다려 주세요.")
            error != null -> {
                GuardianStatePanel("정보를 불러오지 못했어요", error!!)
                TextButton(onClick = { revision++ }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("다시 시도") }
            }
            uid == null -> GuardianStatePanel("로그인이 필요해요", "로그인 후 보호 중인 귀가를 확인할 수 있어요.")
            rows.isEmpty() -> GuardianStatePanel("현재 보호 중인 귀가가 없어요", "보호자로 선택된 귀가가 시작되면 이곳에서 확인할 수 있어요.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.id }) { row ->
                    Surface(onClick = { selectedId = row.id }, color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(row.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextMain)
                                Surface(color = FieldBg, shape = RoundedCornerShape(50)) {
                                    Text("상태 미확인", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = TextMuted, style = MaterialTheme.typography.labelSmall)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                            }
                            Text("귀가 진행 중", color = SafeBlue, style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(color = AppBorder)
                            Text("시작 ${guardianStartTime(row.startedAt)}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            Text("탭하여 귀가 정보를 확인하세요", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveGuardianDetail(session: ActiveGuardianSession, onBack: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var route by remember(session.id, uid) { mutableStateOf<com.example.safepath_test1.api.SelectedRouteBody?>(null) }
    var error by remember(session.id, uid) { mutableStateOf<String?>(null) }
    var loading by remember(session.id, uid) { mutableStateOf(true) }
    var revision by remember(session.id, uid) { mutableIntStateOf(0) }
    var location by remember(session.id, uid) { mutableStateOf<GuardianLocation?>(null) }
    var connection by remember(session.id, uid) { mutableStateOf("연결 중") }
    LaunchedEffect(session.id, uid, revision) {
        error = null
        loading = true
        try {
            val result = BackendRepository(context).monitoringRoute(session.id)
            check(uid != null && FirebaseAuth.getInstance().currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
            route = result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = accountErrorMessage(e)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(session.id, uid) {
        if (uid == null) return@LaunchedEffect
        while (FirebaseAuth.getInstance().currentUser?.uid == uid) {
            try {
                connection = "연결 중"
                val channel = BackendRepository(context).monitoringChannel(session.id).channelId
                guardianLocations(context, uid, channel).collect { update ->
                    if (FirebaseAuth.getInstance().currentUser?.uid != uid) return@collect
                    if (location == null || update.capturedAt > location!!.capturedAt) location = update
                    connection = "위치 수신 중"
                }
                connection = "연결이 끊겼어요. 다시 연결 중"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                connection = "연결할 수 없어요. 다시 연결 중"
            }
            delay(5_000)
        }
    }
    val current = location?.let { GeoPoint(it.latitude, it.longitude) }
    val points = route?.coordinates?.map { GeoPoint(it.latitude, it.longitude) }.orEmpty()
    val nearest = current?.let { position -> points.indices.minByOrNull { haversineMeters(position, points[it]) } }
    fun line(values: List<GeoPoint>): String? {
        if (values.size < 2) return null
        val coordinates = org.json.JSONArray()
        values.forEach { coordinates.put(org.json.JSONArray().put(it.longitude).put(it.latitude)) }
        return org.json.JSONObject().put("type", "LineString").put("coordinates", coordinates).toString()
    }
    val travelled = nearest?.let { line(points.take(it + 1)) }
    val remaining = line(if (nearest == null) points else points.drop(nearest))
    val status = when (location?.status) {
        "SAFE" -> "안전"
        "RISK_SUSPECTED" -> "위험 의심"
        "EMERGENCY" -> "위험상황"
        else -> "상태 미확인"
    }
    Box(modifier.fillMaxSize()) {
        SafePathKakaoMapView(
            currentLocation = current,
            hasLocationPermission = true,
            recenterToken = 0,
            destinationPoint = points.lastOrNull(),
            routeLineGeoJson = travelled,
            remainingRouteGeoJson = remaining,
            showSafetyFacilities = false,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp), color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                Column(Modifier.weight(1f)) {
                    Text(session.name, fontWeight = FontWeight.Bold, color = TextMain)
                    Text(location?.let { "위치 갱신 " + java.text.SimpleDateFormat("M월 d일 a h:mm:ss", java.util.Locale.KOREA).format(java.util.Date(it.capturedAt)) } ?: "위치 수신 대기 중", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text(status, color = if (status == "위험상황") DangerRed else TextMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), shadowElevation = 4.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(route?.destinationName?.takeIf { it.isNotBlank() } ?: "목적지 정보 없음", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(connection, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                if (loading) Text("경로 정보를 확인하고 있어요.", color = TextMuted)
                else if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { revision++ }) { Text("다시 시도") }
                } else if (route == null) Text("공유된 경로가 없어요.", color = TextMuted)
            }
        }
    }
}

private fun guardianStartTime(value: String): String {
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(value.take(19)) ?: return value
        java.text.SimpleDateFormat("M월 d일 a h:mm", java.util.Locale.KOREA).format(date)
    } catch (_: Exception) {
        value
    }
}

@Composable
private fun GuardianStatePanel(title: String, description: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Shield, null, tint = SafeBlue, modifier = Modifier.size(40.dp))
            Text(title, fontWeight = FontWeight.Bold, color = TextMain)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center)
        }
    }
}
