package com.example.safepath_test1.ui.guardian

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safepath_test1.friends.CreatedFriendInvite
import com.example.safepath_test1.friends.FriendsViewModel
import com.example.safepath_test1.friends.readServerFriends
import com.example.safepath_test1.friends.readServerGuardians
import com.example.safepath_test1.ui.components.PageHeader
import com.example.safepath_test1.ui.theme.*
import kotlinx.coroutines.delay
import org.json.JSONArray

// 기존 로컬 연락처는 유지하며, 서버에서 수락된 친구 관계와 구분한다.
data class FriendshipRequest(
    val id: String,
    val name: String,
    val deliveryStatus: String = "submitted",
)

data class GuardianItem(
    val id: String,
    val name: String,
    val relationship: String,
    val phone: String,
)

private fun readLocalGuardians(context: Context): List<GuardianItem> =
    runCatching {
            val data =
                JSONArray(
                    context
                        .getSharedPreferences("safe_path_guardians", Context.MODE_PRIVATE)
                        .getString("guardian_list", "[]")
                )
            (0 until data.length()).map { i ->
                data.getJSONObject(i).let {
                    GuardianItem(
                        it.optString("id"),
                        it.optString("name"),
                        it.optString("relationship"),
                        it.optString("phone"),
                    )
                }
            }
        }
        .getOrDefault(emptyList())

fun readGuardians(context: Context): List<GuardianItem> =
    (readLocalGuardians(context) + readServerFriends(context)).distinctBy { it.id }

private fun maskedPhone(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    return if (digits.length >= 8) "${digits.take(3)}-****-${digits.takeLast(4)}" else "연락처 미등록"
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    receivedRequests: List<FriendshipRequest> = emptyList(),
    sentRequests: List<FriendshipRequest> = emptyList(),
    onAccept: ((String) -> Unit)? = null,
    onReject: ((String) -> Unit)? = null,
    onCancel: ((String) -> Unit)? = null,
    onInviteCreated: (CreatedFriendInvite) -> Unit = {},
    onManageGuardians: () -> Unit = {},
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var guardians by remember { mutableStateOf(readLocalGuardians(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var appliedQuery by rememberSaveable { mutableStateOf("") }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var showAddFriend by rememberSaveable { mutableStateOf(false) }
    val friendsViewModel: FriendsViewModel = viewModel()
    val friendState by friendsViewModel.uiState.collectAsStateWithLifecycle()
    val backendViewModel: BackendGuardianViewModel = viewModel()
    val backendState by backendViewModel.state.collectAsStateWithLifecycle()
    val backendRows = backendState.rows
    val backendIds = backendRows.map { it.id.toString() }.toSet()
    val backendFriends = backendRows.filter { it.userId == backendState.myId && it.status == "ACCEPTED" }
        .map { GuardianItem("backend:${it.id}", it.guardianName, "보호자", "") }
    val displayedGuardians = (guardians + friendState.friends.filterNot { it.id in backendIds } + backendFriends).distinctBy { it.id }
    val displayedReceived =
        (if (friendsViewModel.isConfigured) friendState.received else receivedRequests) +
            backendRows.filter { it.guardianId == backendState.myId && it.status == "PENDING" }
                .map { FriendshipRequest("backend:${it.id}", it.userName) }
    val displayedSent = (if (friendsViewModel.isConfigured) friendState.sent else sentRequests) +
        backendRows.filter { it.userId == backendState.myId && it.status == "PENDING" }
            .map { FriendshipRequest("backend:${it.id}", it.guardianName) }
    val acceptAction: ((String) -> Unit)? =
        { id -> if (id.startsWith("backend:")) id.removePrefix("backend:").toLongOrNull()?.let { backendViewModel.respond(it, "accept") }
            else if (friendsViewModel.isConfigured) friendsViewModel.respond(id, "accept") else onAccept?.invoke(id) }
    val rejectAction: ((String) -> Unit)? =
        { id -> if (id.startsWith("backend:")) id.removePrefix("backend:").toLongOrNull()?.let { backendViewModel.respond(it, "reject") }
            else if (friendsViewModel.isConfigured) friendsViewModel.respond(id, "reject") else onReject?.invoke(id) }
    val cancelAction: ((String) -> Unit)? =
        { id -> if (id.startsWith("backend:")) id.removePrefix("backend:").toLongOrNull()?.let { backendViewModel.respond(it, "revoke") }
            else if (friendsViewModel.isConfigured) friendsViewModel.respond(id, "cancel") else onCancel?.invoke(id) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, showAddFriend) {
        if (showAddFriend) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                friendsViewModel.refresh()
                backendViewModel.refresh()
                delay(15_000)
            }
        }
    }
    LaunchedEffect(friendState.invite) {
        friendState.invite?.let {
            showAddFriend = false
            onInviteCreated(it)
            friendsViewModel.consumeInvite()
        }
    }
    var disconnect by remember { mutableStateOf<GuardianItem?>(null) }
    var selectedFriend by remember { mutableStateOf<GuardianItem?>(null) }
    var guardianCandidate by remember { mutableStateOf<GuardianItem?>(null) }
    var guardianCandidateId by rememberSaveable { mutableStateOf("") }
    val matches =
        remember(displayedGuardians, appliedQuery) {
            val text = appliedQuery.trim()
            val digits = text.filter(Char::isDigit)
            displayedGuardians
                .filter { friend ->
                    text.isBlank() ||
                        friend.name.contains(text, ignoreCase = true) ||
                        friend.relationship.contains(text, ignoreCase = true) ||
                        friend.phone.contains(text, ignoreCase = true) ||
                        (digits.isNotEmpty() &&
                            text.all { it.isDigit() || it in " -+()" } &&
                            friend.phone.filter(Char::isDigit).contains(digits))
                }
                .sortedBy { it.name }
        }
    val search: () -> Unit = {
        appliedQuery = query.trim()
        keyboard?.hide()
    }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "친구",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onManageGuardians) { Text("보호자 관리") }
            IconButton(
                onClick = {
                    keyboard?.hide()
                    friendsViewModel.clearError()
                    showAddFriend = true
                }
            ) {
                Icon(Icons.Default.PersonAdd, "친구 추가", tint = TextMain)
            }
            IconButton(
                onClick = {
                    keyboard?.hide()
                    showNotifications = true
                    friendsViewModel.refresh()
                }
            ) {
                BadgedBox(
                    badge = {
                        val count = displayedReceived.size + displayedSent.size
                        if (count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) }
                    }
                ) {
                    Icon(Icons.Default.NotificationsNone, "친구 요청 알림", tint = TextMain)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank()) appliedQuery = ""
                },
                placeholder = { Text("이름 또는 전화번호", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                trailingIcon =
                    if (query.isNotEmpty())
                        ({
                            IconButton(
                                onClick = {
                                    query = ""
                                    appliedQuery = ""
                                }
                            ) {
                                Icon(Icons.Default.Close, "검색 지우기")
                            }
                        })
                    else null,
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = search,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(56.dp),
            ) {
                Text("검색")
            }
        }
        HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 12.dp), color = AppBorder)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (appliedQuery.isBlank()) "친구 ${displayedGuardians.size}"
                else "검색 결과 ${matches.size}",
                color = TextMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text("길게 눌러 연결 해제", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        if (!showAddFriend && friendState.error != null) {
            Text(
                friendState.error.orEmpty(),
                color = DangerRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        if (!showAddFriend && backendState.error != null) Text(backendState.error.orEmpty(), color = DangerRed, style = MaterialTheme.typography.bodySmall)
        if (matches.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.PeopleOutline,
                    null,
                    tint = AppBorder,
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    if (appliedQuery.isBlank()) "등록된 친구가 없어요" else "검색 결과가 없어요",
                    color = TextMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
            ) {
                items(matches, key = { it.id }) { friend ->
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { selectedFriend = friend }, onLongClick = { disconnect = friend })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = SkyBlue.copy(alpha = .12f),
                            modifier = Modifier.size(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    friend.name.take(1),
                                    color = SafeBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                )
                            }
                        }
                        Column(
                            Modifier.weight(1f).padding(start = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                friend.name,
                                color = TextMain,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf(friend.relationship, maskedPhone(friend.phone))
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (friendState.friends.any { it.id == friend.id }) {
                            IconButton(onClick = { guardianCandidateId = ""; guardianCandidate = friend }, enabled = !backendState.busy) {
                                Icon(Icons.Default.Shield, "${friend.name} 보호자 요청", tint = SafeBlue)
                            }
                        }
                    }
                }
            }
    }
    selectedFriend?.let { friend ->
        AlertDialog(
            onDismissRequest = { selectedFriend = null },
            title = { Text("친구 정보") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("이름: ${friend.name}")
                    Text("관계: ${friend.relationship}")
                    Text("전화번호: ${friend.phone.ifBlank { "정보 없음" }}")
                }
            },
            confirmButton = { TextButton(onClick = { selectedFriend = null }) { Text("확인") } },
        )
    }
    if (showAddFriend)
        AddFriendDialog(
            friendState.isSubmitting,
            friendState.error,
            onDismiss = {
                showAddFriend = false
                friendsViewModel.clearError()
            },
            onSubmit = friendsViewModel::create,
        )
    if (showNotifications)
        Dialog(
            onDismissRequest = { showNotifications = false },
            properties =
                DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showNotifications = false }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "친구 목록으로 돌아가기",
                                tint = TextMain,
                            )
                        }
                        Text(
                            "친구 알림",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    HorizontalDivider(color = AppBorder)
                    Column(
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (friendState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        friendState.error?.let { Text(it, color = DangerRed) }
                        if (
                            displayedReceived.isEmpty() &&
                                displayedSent.isEmpty() &&
                                !friendState.isLoading
                        ) {
                            Text(
                                "받거나 보낸 친구 요청이 없어요",
                                color = TextMuted,
                                modifier = Modifier.padding(vertical = 28.dp),
                            )
                        }
                        displayedReceived.forEach { request ->
                            FriendNotificationRow(request.name, "받은 요청", "보호자 등록을 요청했어요") {
                                OutlinedButton(
                                    onClick = { rejectAction?.invoke(request.id) },
                                    enabled = rejectAction != null && !friendState.isSubmitting,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("거절")
                                }
                                Button(
                                    onClick = { acceptAction?.invoke(request.id) },
                                    enabled = acceptAction != null && !friendState.isSubmitting,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("수락")
                                }
                            }
                        }
                        displayedSent.forEach { request ->
                            FriendNotificationRow(
                                request.name,
                                "보낸 요청",
                                when (request.deliveryStatus) {
                                    "ready" -> "카카오톡 공유용 요청 · 수락 대기 중이에요"
                                    "submitted" -> "친구 요청 · 수락 대기 중이에요"
                                    else -> "친구 요청 상태를 확인해 주세요"
                                },
                            ) {
                                TextButton(
                                    onClick = { cancelAction?.invoke(request.id) },
                                    enabled = cancelAction != null && !friendState.isSubmitting,
                                ) {
                                    Text("요청 취소")
                                }
                            }
                        }
                    }
                }
            }
        }
    guardianCandidate?.let { target ->
        AlertDialog(
            onDismissRequest = { guardianCandidate = null },
            title = { Text("보호자 요청") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${target.name}님에게 확인한 SafePath 회원 ID를 입력해 주세요. 상대방이 수락하면 보호자로 지정됩니다.")
                    Text("카카오톡 친구 정보만으로 회원 ID를 확인할 수 없어요. 입력한 ID의 상대에게 요청이 전송됩니다.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = guardianCandidateId, onValueChange = { guardianCandidateId = it.filter(Char::isDigit) }, label = { Text("보호자 회원 ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            },
            confirmButton = { TextButton(enabled = !backendState.busy && (guardianCandidateId.toLongOrNull() ?: 0L) > 0, onClick = {
                val id = guardianCandidateId.toLongOrNull() ?: return@TextButton
                guardianCandidate = null
                backendViewModel.requestGuardian(id)
            }) { Text("요청") } },
            dismissButton = { TextButton(onClick = { guardianCandidate = null }) { Text("취소") } },
        )
    }
    disconnect?.let { target ->
        AlertDialog(
            onDismissRequest = { disconnect = null },
            title = { Text("연결을 해제할까요?") },
            text = { Text("${target.name} 연락처를 기기 목록에서 제거합니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (target.id.startsWith("backend:")) {
                            target.id.removePrefix("backend:").toLongOrNull()?.let { backendViewModel.respond(it, "revoke") }
                            disconnect = null
                            return@TextButton
                        }
                        if (friendState.friends.any { it.id == target.id }) {
                            friendsViewModel.respond(target.id, "disconnect")
                            disconnect = null
                            return@TextButton
                        }
                        guardians = guardians.filterNot { it.id == target.id }
                        val array = JSONArray()
                        guardians.forEach { g ->
                            array.put(
                                org.json
                                    .JSONObject()
                                    .put("id", g.id)
                                    .put("name", g.name)
                                    .put("relationship", g.relationship)
                                    .put("phone", g.phone)
                            )
                        }
                        context
                            .getSharedPreferences("safe_path_guardians", Context.MODE_PRIVATE)
                            .edit()
                            .putString("guardian_list", array.toString())
                            .apply()
                        disconnect = null
                    }
                ) {
                    Text("해제")
                }
            },
            dismissButton = { TextButton(onClick = { disconnect = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun FriendNotificationRow(
    name: String,
    type: String,
    message: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FieldBg,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PersonAdd, null, tint = SafeBlue)
                }
            }
            Column(
                Modifier.weight(1f).padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(type, color = SafeBlue, style = MaterialTheme.typography.labelSmall)
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(message, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
        HorizontalDivider(color = AppBorder)
    }
}

@Composable
private fun EmptyPanel(message: String) {
    Surface(
        color = FieldBg,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            color = TextMuted,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun GuardianScreen(modifier: Modifier = Modifier, journeys: List<ProtectedJourney> = emptyList()) {
    val orderedJourneys = journeys.sortedBy {
        when (it.status) { "위험상황" -> 0; "위험 의심단계" -> 1; "경로이탈" -> 2; else -> 3 }
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedJourney = journeys.firstOrNull { it.sessionId.isNotBlank() && it.sessionId == selectedId }
    if (selectedJourney != null) {
        BackHandler { selectedId = null }
        ProtectedJourneyScreen(
            selectedJourney,
            onBack = { selectedId = null },
            modifier = modifier,
        )
        return
    }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PageHeader("보호", "나를 보호자로 선택한 사람의 귀가를 확인해요")
        orderedJourneys.forEach { journey ->
            OutlinedButton(
                shape = MaterialTheme.shapes.small,
                onClick = { selectedId = journey.sessionId },
                enabled = journey.sessionId.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Text("${journey.userName} · ${journey.status}\n위치 갱신 ${journey.lastUpdated}")
            }
        }
        if (journeys.isEmpty())
            Surface(
                color = FieldBg,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Shield,
                        null,
                        tint = SkyBlue,
                        modifier = Modifier.size(48.dp),
                    )
                    Text("현재 보호 중인 귀가가 없어요", fontWeight = FontWeight.Bold)
                    Text("보호 세션이 연결되면 사용자 위치와 상태를 여기에 표시합니다.", color = TextMuted)
                }
            }
    }
}

@Composable
fun DepartureScreen(
    destination: String,
    routeTitle: String,
    distance: String,
    onBack: () -> Unit,
    testJourneyActive: Boolean,
    testJourneyPending: Boolean,
    onTestJourneyStart: (List<String>, String) -> Unit,
    onTestJourneyEnd: () -> Unit,
    route: com.example.safepath_test1.location.RouteResult? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val guardians = remember { readServerGuardians(context) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var pin by remember { mutableStateOf("") }
    var useBiometric by remember { mutableStateOf(com.example.safepath_test1.session.BiometricSessionSigner.isAvailable(context)) }
    var authenticating by remember { mutableStateOf(false) }
    var authenticationCancellation by remember { mutableStateOf<android.os.CancellationSignal?>(null) }
    val authenticationScopeActive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) {
        onDispose {
            authenticationScopeActive.set(false)
            authenticationCancellation?.cancel()
        }
    }
    BackHandler(onBack = onBack)
    Column(
        modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                Text("출발 준비", style = MaterialTheme.typography.titleLarge)
            }
            Surface(color = FieldBg, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(destination, fontWeight = FontWeight.Bold)
                    Text("$routeTitle · $distance", color = TextMuted)
                    Text(
                        route?.let { "약 ${kotlin.math.ceil(com.example.safepath_test1.location.NavigationRepository.travelSeconds(it) / 60).toInt()}분 · 시설지표 ${String.format(java.util.Locale.KOREA, "%.1f", it.safetyFacilityScore)}" } ?: "경로 정보 없음",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("함께 지켜줄 보호자", fontWeight = FontWeight.Bold)
                Text("${selected.size}명 선택됨", color = SafeBlue)
            }
            if (guardians.isEmpty()) EmptyPanel("친구 탭에서 보호자 연결을 먼저 확인해 주세요.")
            guardians.forEach { friend ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                        selected =
                            if (friend.id in selected) selected - friend.id
                            else selected + friend.id
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        tint = SkyBlue,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(friend.name, fontWeight = FontWeight.Bold)
                        Text(friend.relationship, color = TextMuted)
                    }
                    Checkbox(
                        friend.id in selected,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + friend.id else selected - friend.id
                        },
                    )
                }
            }
            if (com.example.safepath_test1.session.BiometricSessionSigner.isAvailable(context)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("생체인증 사용", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(checked = useBiometric, onCheckedChange = { useBiometric = it })
                }
            }
            if (useBiometric) {
                Text("시작과 종료 시 생체인증을 사용합니다.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            } else {
            Text("종료 비밀번호", fontWeight = FontWeight.Bold)
            Text(
                "종료에 사용할 숫자 4자리입니다. 비밀번호는 화면에 저장하지 않습니다.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            // 실제 입력 필드는 숨기고 네 칸의 점만 그려 비밀번호 노출을 막는다.
            BasicTextField(
                value = pin,
                onValueChange = { pin = it.filter { char -> char in '0'..'9' }.take(4) },
                singleLine = true,
                textStyle = TextStyle(color = Color.Transparent),
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(4) { index ->
                                Surface(
                                    Modifier.weight(1f).height(56.dp),
                                    color = FieldBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border =
                                        if (pin.length == index) BorderStroke(1.dp, SafeBlue)
                                        else BorderStroke(1.dp, AppBorder),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            if (index < pin.length) "●" else "",
                                            color = TextMain,
                                            fontSize = 20.sp,
                                        )
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(56.dp).alpha(0f)) { inner() }
                    }
                },
            )
            }
            Text(
                "귀가 세션을 시작하지만 선택한 보호자에게 위치나 경로가 아직 공유되지 않습니다. 종료 인증은 이 기기에서만 확인합니다.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            shape = MaterialTheme.shapes.small,
            onClick = {
                val request = com.example.safepath_test1.session.SessionStartRequest(
                    routeId = route?.geoJsonLineString?.let { geometry ->
                        java.security.MessageDigest.getInstance("SHA-256").digest(geometry.toByteArray()).joinToString("") { "%02x".format(it) }
                    }.orEmpty(),
                    guardianIds = selected.toList(), endPassword = pin,
                    useBiometric = useBiometric,
                    selectedRouteGeoJson = route?.geoJsonLineString,
                    detectionSettings = com.example.safepath_test1.session.DetectionSettingsStore(context).read(),
                )
                val message = request.validationError()
                if (message != null) android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                else {
                    val client = com.example.safepath_test1.session.JourneyEndAuth
                    client.biometricKeyAlias?.let { runCatching { com.example.safepath_test1.session.BiometricSessionSigner.deleteKey(it) } }
                    client.biometricKeyAlias = null
                    val names = selected.toList()
                    val activity = context as? android.app.Activity
                    if (useBiometric && activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val alias = runCatching { com.example.safepath_test1.session.BiometricSessionSigner.createKey(context).keyAlias }.getOrNull()
                        if (alias == null) {
                            useBiometric = false
                            android.widget.Toast.makeText(context, "생체인증을 사용할 수 없어 종료 비밀번호로 진행해 주세요.", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            authenticating = true
                            val challenge = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                            authenticationCancellation = com.example.safepath_test1.session.BiometricSessionSigner.signChallenge(activity, alias, challenge,
                                onSigned = {
                                    if (authenticationScopeActive.get()) {
                                        authenticating = false; client.biometricKeyAlias = alias; onTestJourneyStart(names, pin)
                                    } else runCatching { com.example.safepath_test1.session.BiometricSessionSigner.deleteKey(alias) }
                                },
                                onFallback = {
                                    runCatching { com.example.safepath_test1.session.BiometricSessionSigner.deleteKey(alias) }
                                    if (authenticationScopeActive.get()) {
                                        authenticating = false
                                        useBiometric = false
                                        android.widget.Toast.makeText(context, "종료 비밀번호로 진행할 수 있어요.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                })
                        }
                    } else onTestJourneyStart(names, pin)
                }
            },
            enabled = !testJourneyActive && !testJourneyPending && !authenticating && route != null && selected.isNotEmpty() && (useBiometric || pin.length == 4),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(if (testJourneyPending) "연결 중..." else "안전귀가 시작")
        }
    }
}
