package com.example.safepath_test1

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.safepath_test1.session.JourneyEndAuth
import com.example.safepath_test1.model.GeoPoint
import com.example.safepath_test1.model.PlaceSelection
import com.example.safepath_test1.ui.SafePathTab
import com.example.safepath_test1.ui.components.SafePathBottomBar
import com.example.safepath_test1.ui.guardian.GuardianScreen
import com.example.safepath_test1.ui.home.HomeScreen
import com.example.safepath_test1.ui.login.LoginPage
import com.example.safepath_test1.ui.login.SignupScreen
import com.example.safepath_test1.ui.profile.ProfileScreen
import com.example.safepath_test1.ui.theme.AppBackground
import com.example.safepath_test1.ui.theme.SafePathTheme
import com.example.safepath_test1.wear.WatchAlertState
import com.example.safepath_test1.wear.WearMessenger
import com.example.safepath_test1.wear.authenticateRelease
import com.example.safepath_test1.wear.cancelSosNotification
import com.google.firebase.auth.FirebaseAuth
import com.example.safepath_test1.api.AccountRepository
import com.example.safepath_test1.api.accountErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
internal fun SafePathMain(onLogout: () -> Unit, permissionRevision: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backendRepository = remember(context) { com.example.safepath_test1.api.BackendRepository(context) }
    var sosPending by remember { mutableStateOf(false) }
    val watchAlertAt by WatchAlertState.requestedAt.collectAsState()
    var hiddenAlertAt by remember { mutableStateOf<Long?>(null) }
    var releasePending by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by rememberSaveable { mutableStateOf(SafePathTab.Home.name) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val sessions = remember(context) { context.getSharedPreferences("backend_sessions", android.content.Context.MODE_PRIVATE) }
    var sessionId by remember(uid) { mutableStateOf(uid?.let { sessions.getString(it, null) }) }
    var testJourneyPending by remember(uid) { mutableStateOf(sessionId != null) }
    val testJourneyActive = sessionId != null
    var showGuardianManagement by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        try {
            val profile = AccountRepository(context).profile()
            val rows = backendRepository.guardians()
            if (FirebaseAuth.getInstance().currentUser?.uid == uid) {
                com.example.safepath_test1.friends.cacheServerFriends(context, uid, rows.filter {
                    it.userId == profile.userId && it.status == "ACCEPTED"
                }.map { com.example.safepath_test1.ui.guardian.GuardianItem(it.id.toString(), it.guardianName, "보호자", "") })
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.w("SafePath", "Could not refresh guardians", e) }
        val saved = sessionId
        if (saved != null) {
            try { backendRepository.sessionStatus(saved) }
            catch (e: retrofit2.HttpException) {
                if (e.code() == 404) { sessions.edit().remove(uid).apply(); sessionId = null; JourneyEndAuth.clear() }
                else android.widget.Toast.makeText(context, accountErrorMessage(e), android.widget.Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.widget.Toast.makeText(context, accountErrorMessage(e), android.widget.Toast.LENGTH_LONG).show() }
        }
        testJourneyPending = false
    }
    LaunchedEffect(selectedTab, showGuardianManagement) {
        if (selectedTab == SafePathTab.Home.name && !testJourneyPending) {
            val saved = uid?.let { sessions.getString(it, null) }
            if (saved != sessionId) {
                sessionId = saved
                if (saved == null) JourneyEndAuth.clear()
            }
        }
    }
    var origin by
        rememberSaveable(stateSaver = placeSelectionSaver) { mutableStateOf(PlaceSelection()) }
    var destination by
        rememberSaveable(stateSaver = placeSelectionSaver) { mutableStateOf(PlaceSelection()) }
    var showSosConfirmation by rememberSaveable { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(isLocationPermissionGranted(context)) }
    LaunchedEffect(permissionRevision) {
        hasLocationPermission = isLocationPermissionGranted(context)
    }
    AppLocationTracker(context, hasLocationPermission) { location, _ ->
        currentLocation = location
    }

    val tab = SafePathTab.entries.find { it.name == selectedTab } ?: SafePathTab.Home
    BackHandler(enabled = tab != SafePathTab.Home) {
        if (showGuardianManagement && tab == SafePathTab.Friends) showGuardianManagement = false
        else selectedTab = SafePathTab.Home.name
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(if (tab == SafePathTab.Home) Color.White else AppBackground)
    ) {
        when (tab) {
            SafePathTab.Home ->
                HomeScreen(
                    currentLocation = currentLocation,
                    hasLocationPermission = hasLocationPermission,
                    testJourneyActive = testJourneyActive,
                    testJourneyPending = testJourneyPending,
                    onTestJourneyStart = { _, _, _, _, _, _, _, _, _ ->
                        testJourneyPending = true
                        scope.launch {
                            try {
                                val user = uid ?: error("로그인이 필요합니다.")
                                val session = backendRepository.startSession()
                                check(FirebaseAuth.getInstance().currentUser?.uid == user) { "로그인 계정이 변경되었습니다." }
                                if (!sessions.edit().putString(user, session.id).commit()) {
                                    runCatching { backendRepository.endSession(session.id) }
                                    error("세션을 기기에 저장하지 못했습니다.")
                                }
                                sessionId = session.id
                                android.widget.Toast.makeText(context, "귀가 세션이 시작됐습니다. 보호자 위치 공유는 아직 지원되지 않습니다.", android.widget.Toast.LENGTH_LONG).show()
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { JourneyEndAuth.clear(); android.widget.Toast.makeText(context, accountErrorMessage(e), android.widget.Toast.LENGTH_LONG).show() }
                            finally { testJourneyPending = false }
                        }
                    },
                    onTestJourneyEnd = {
                        testJourneyPending = true
                        scope.launch {
                            try {
                                val user = uid ?: error("로그인이 필요합니다.")
                                val id = sessionId ?: error("진행 중인 세션이 없습니다.")
                                try { backendRepository.endSession(id) } catch (e: retrofit2.HttpException) { if (e.code() != 404) throw e }
                                check(FirebaseAuth.getInstance().currentUser?.uid == user) { "로그인 계정이 변경되었습니다." }
                                sessions.edit().remove(user).apply(); sessionId = null; JourneyEndAuth.clear()
                                android.widget.Toast.makeText(context, "귀가 세션이 종료되었습니다.", android.widget.Toast.LENGTH_LONG).show()
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { android.widget.Toast.makeText(context, accountErrorMessage(e), android.widget.Toast.LENGTH_LONG).show() }
                            finally { testJourneyPending = false }
                        }
                    },
                    origin = origin,
                    destination = destination,
                    onOriginChanged = { origin = it },
                    onDestinationChanged = { destination = it },
                    onSwap = {
                        val previousOrigin = origin
                        origin = destination
                        destination = previousOrigin
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            SafePathTab.Friends ->
                if (showGuardianManagement) com.example.safepath_test1.ui.guardian.BackendGuardianScreen(modifier = Modifier.padding(bottom = 100.dp), onBack = { showGuardianManagement = false })
                else com.example.safepath_test1.ui.guardian.FriendsScreen(
                    modifier = Modifier.padding(bottom = 100.dp),
                    onManageGuardians = { showGuardianManagement = true },
                    onInviteCreated = { invite ->
                        com.example.safepath_test1.friends.shareFriendInvite(context, invite)?.let { error ->
                            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                )
            SafePathTab.Guardian -> GuardianScreen(modifier = Modifier.padding(bottom = 100.dp))
            SafePathTab.Profile ->
                ProfileScreen(
                    hasLocationPermission = hasLocationPermission,
                    onLogout = onLogout,
                    onSosClick = {
                        if (
                            context
                                .getSharedPreferences(
                                    "safe_path_settings",
                                    android.content.Context.MODE_PRIVATE,
                                )
                                .getBoolean("sos_enabled", true)
                        )
                            showSosConfirmation = true
                        else
                            android.widget.Toast.makeText(
                                    context,
                                    "설정에서 SOS 버튼을 활성화해 주세요.",
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                                .show()
                    },
                    modifier = Modifier.padding(bottom = 100.dp),
                )
        }

        SafePathBottomBar(
            selectedTab = tab,
            onTabSelected = { selectedTab = it.name },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (showSosConfirmation) {
            AlertDialog(
                onDismissRequest = { showSosConfirmation = false },
                title = { Text("보호자에게 SOS를 보낼까요?") },
                text = {
                    Text("수락된 보호자에게 서버 알림을 요청합니다. 현재 서버 알림에는 위치가 포함되지 않으며 자동 신고는 되지 않습니다.")
                },
                confirmButton = {
                    Button(
                        enabled = !sosPending,
                        onClick = {
                            sosPending = true
                            scope.launch {
                                try {
                                    val result = backendRepository.sos()
                                    android.widget.Toast.makeText(context, "SOS 알림 서버 발송 성공 ${result.successCount}건, 실패 ${result.failureCount}건", android.widget.Toast.LENGTH_LONG).show()
                                    showSosConfirmation = false
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { android.widget.Toast.makeText(context, accountErrorMessage(e), android.widget.Toast.LENGTH_LONG).show() }
                                finally { sosPending = false }
                            }
                        },
                    ) {
                        Text("SOS 전송")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { if (!sosPending) showSosConfirmation = false }) { Text("취소") }
                },
            )
        }

        // 워치에서 SOS가 눌렸을 때: 본인 확인을 거쳐 해제하면 워치가 길안내 화면으로 돌아간다.
        val alertAt = watchAlertAt
        if (alertAt != null && hiddenAlertAt != alertAt) {
            AlertDialog(
                onDismissRequest = { if (!releasePending) hiddenAlertAt = alertAt },
                title = { Text("워치에서 SOS가 눌렸어요") },
                text = { Text("해제하면 워치가 길안내 화면으로 돌아갑니다.") },
                confirmButton = {
                    Button(
                        enabled = !releasePending,
                        onClick = {
                            val activity = context as? android.app.Activity
                            if (activity == null) {
                                android.widget.Toast.makeText(context, "해제할 수 없습니다. 앱을 다시 열어 주세요.", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                releasePending = true
                                authenticateRelease(activity) { confirmed ->
                                    if (confirmed) {
                                        WearMessenger.sendRelease(context) { sent ->
                                            releasePending = false
                                            if (sent) {
                                                WatchAlertState.clear()
                                                cancelSosNotification(context)
                                            } else {
                                                android.widget.Toast.makeText(context, "워치에 해제 신호를 보내지 못했습니다. 다시 시도해 주세요.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        releasePending = false
                                        android.widget.Toast.makeText(context, "본인 확인에 실패해 해제하지 않았습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    ) {
                        Text("해제")
                    }
                },
                dismissButton = {
                    TextButton(enabled = !releasePending, onClick = { hiddenAlertAt = alertAt }) { Text("나중에") }
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = isLocationPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private val placeSelectionSaver =
    mapSaver(
        save = { place ->
            mapOf(
                "name" to place.name,
                "latitude" to place.latitude,
                "longitude" to place.longitude,
            )
        },
        restore = { values ->
            PlaceSelection(
                values["name"] as String,
                values["latitude"] as Double?,
                values["longitude"] as Double?,
            )
        },
    )

private fun isLocationPermissionGranted(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
