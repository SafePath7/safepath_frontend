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
    val displayedGuardians = (guardians + friendState.friends).distinctBy { it.id }
    val displayedReceived =
        if (friendsViewModel.isConfigured) friendState.received else receivedRequests
    val displayedSent = if (friendsViewModel.isConfigured) friendState.sent else sentRequests
    val acceptAction: ((String) -> Unit)? =
        if (friendsViewModel.isConfigured) ({ friendsViewModel.respond(it, "accept") })
        else onAccept
    val rejectAction: ((String) -> Unit)? =
        if (friendsViewModel.isConfigured) ({ friendsViewModel.respond(it, "reject") })
        else onReject
    val cancelAction: ((String) -> Unit)? =
        if (friendsViewModel.isConfigured) ({ friendsViewModel.respond(it, "cancel") })
        else onCancel
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, showAddFriend) {
        if (showAddFriend) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                friendsViewModel.refresh()
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
                            .combinedClickable(onClick = {}, onLongClick = { disconnect = friend })
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
                    }
                }
            }
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
    disconnect?.let { target ->
        AlertDialog(
            onDismissRequest = { disconnect = null },
            title = { Text("연결을 해제할까요?") },
            text = { Text("${target.name} 연락처를 기기 목록에서 제거합니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
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
    onTestJourneyStart: (List<String>) -> Unit,
    onTestJourneyEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val guardians = remember { readServerFriends(context) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var pin by remember { mutableStateOf("") }
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
                        "시간 · 안전점수: 데이터 미제공",
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
            Text("종료 비밀번호", fontWeight = FontWeight.Bold)
            Text(
                "운영 세션 종료에 사용할 숫자 4자리입니다. 현재 LAN 간이 테스트에는 비밀번호 인증이 연결되지 않았어요.",
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
            Text(
                "간이 테스트는 현재 위치를 개인 Wi-Fi의 테스트 서버로 전송합니다. 보호자 알림이나 긴급 신고는 전송되지 않습니다.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            shape = MaterialTheme.shapes.small,
            onClick = { if (testJourneyActive) onTestJourneyEnd() else onTestJourneyStart(guardians.filter { it.id in selected }.map { it.name }) },
            enabled = !testJourneyPending && (testJourneyActive || (selected.isNotEmpty() && pin.length == 4)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(if (testJourneyActive) "간이 테스트 종료" else "안전귀가 시작 (간이 테스트)")
        }
    }
}
