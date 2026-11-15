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
import com.example.safepath_test1.friends.CreatedFriendInvite
import com.example.safepath_test1.location.shareLocation
import com.example.safepath_test1.location.TestJourneyClient
import com.example.safepath_test1.model.GeoPoint
import com.example.safepath_test1.model.PlaceSelection
import com.example.safepath_test1.ui.SafePathTab
import com.example.safepath_test1.ui.components.SafePathBottomBar
import com.example.safepath_test1.ui.guardian.FriendsScreen
import com.example.safepath_test1.ui.guardian.GuardianScreen
import com.example.safepath_test1.ui.home.HomeScreen
import com.example.safepath_test1.ui.login.LoginPage
import com.example.safepath_test1.ui.login.SignupScreen
import com.example.safepath_test1.ui.profile.ProfileScreen
import com.example.safepath_test1.ui.theme.AppBackground
import com.example.safepath_test1.ui.theme.SafePathTheme
import com.google.firebase.auth.FirebaseAuth
import com.example.safepath_test1.api.AccountRepository
import com.example.safepath_test1.api.accountErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SafePathApp() {
    val permissionContext = LocalContext.current
    var permissionRevision by remember { mutableIntStateOf(0) }
    val startupPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionRevision++ }
    LaunchedEffect(Unit) {
        val requested = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= 33) requested.add(Manifest.permission.POST_NOTIFICATIONS)
        if (android.os.Build.VERSION.SDK_INT >= 37 && BuildConfig.DEBUG) requested.add("android.permission.ACCESS_LOCAL_NETWORK")
        val missing = requested.filter { ContextCompat.checkSelfPermission(permissionContext, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) startupPermissions.launch(missing.toTypedArray())
    }
    val auth = remember { FirebaseAuth.getInstance() }
    val appScope = rememberCoroutineScope()
    var firebaseUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val context = LocalContext.current
    var profileVersion by remember { mutableIntStateOf(0) }
    val profile =
        context.getSharedPreferences("safe_path_profile", android.content.Context.MODE_PRIVATE)
    val repository = remember(context) { AccountRepository(context) }
    var retry by remember { mutableIntStateOf(0) }
    var membership by remember(firebaseUser?.uid) { mutableStateOf("checking") }
    var membershipError by remember(firebaseUser?.uid) { mutableStateOf<String?>(null) }
    LaunchedEffect(firebaseUser?.uid, membership) {
        if (firebaseUser != null && membership == "registered") {
            try {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                com.example.safepath_test1.api.BackendRepository(context).registerToken(token)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w("SafePathFCM", "Token registration failed", e) }
        }
    }
    LaunchedEffect(firebaseUser?.uid, profileVersion, retry) {
        val uid = firebaseUser?.uid ?: return@LaunchedEffect
        membership = "checking"
        try {
            membership = when (repository.login().status) {
                "SIGNUP_REQUIRED" -> "signup"
                "REGISTERED" -> {
                    val serverProfile = repository.profile()
                    if (auth.currentUser?.uid == uid) {
                        profile.edit().putString("onboarded_uid", uid)
                            .putString("user_name", serverProfile.name)
                            .putString("user_phone", serverProfile.phoneNumber).apply()
                    }
                    "registered"
                }
                else -> error("서버의 회원 상태를 확인할 수 없습니다.")
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            membershipError = accountErrorMessage(error)
            membership = "error"
        }
    }
    SafePathTheme {
        if (firebaseUser == null) {
            LoginPage(onLoginSuccess = { firebaseUser = auth.currentUser })
        } else if (membership == "signup") {
            SignupScreen(onComplete = { profileVersion++ }, onBack = { auth.signOut() })
        } else if (membership == "registered") {
            SafePathMain(onLogout = {
                appScope.launch {
                    try { com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken().await() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Log.w("SafePathFCM", "Could not invalidate token on logout", e) }
                    auth.signOut()
                }
            }, permissionRevision = permissionRevision)
        } else {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (membership == "checking") androidx.compose.material3.CircularProgressIndicator()
                else {
                    Text(membershipError ?: "서버에 연결할 수 없습니다.")
                    Button(onClick = { retry++ }) { Text("다시 시도") }
                    TextButton(onClick = { auth.signOut() }) { Text("로그아웃") }
                }
            }
        }
    }
}

@Composable
private fun SafePathMain(onLogout: () -> Unit, permissionRevision: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sosPending by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by rememberSaveable { mutableStateOf(SafePathTab.Home.name) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var testJourneyActive by remember { mutableStateOf(TestJourneyClient.active) }
    var testJourneyPending by remember { mutableStateOf(false) }
    var testRoute by remember { mutableStateOf<String?>(null) }
    var testMetadata by remember { mutableStateOf("{}") }
    val beginTestJourney = { route: String?, metadata: String ->
        testJourneyPending = true
        TestJourneyClient.start(context, currentLocation, route, metadata) { success ->
            testJourneyPending = false
            testJourneyActive = success
            if (success) currentLocation?.let { TestJourneyClient.update(context, it) }
            android.widget.Toast.makeText(context, if (success) "간이 테스트 시작: 웹에서 위치를 확인하세요." else TestJourneyClient.lastError, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val localNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"
    val localNetworkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginTestJourney(testRoute, testMetadata)
        else android.widget.Toast.makeText(context, "간이 테스트를 위해 근처 기기 접근 권한이 필요합니다.", android.widget.Toast.LENGTH_LONG).show()
    }
    var origin by
        rememberSaveable(stateSaver = placeSelectionSaver) { mutableStateOf(PlaceSelection()) }
    var destination by
        rememberSaveable(stateSaver = placeSelectionSaver) { mutableStateOf(PlaceSelection()) }
    var showSosConfirmation by rememberSaveable { mutableStateOf(false) }
    var preparedInvite by remember { mutableStateOf<CreatedFriendInvite?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    var hasLocationPermission by remember { mutableStateOf(isLocationPermissionGranted(context)) }
    LaunchedEffect(permissionRevision) {
        hasLocationPermission = isLocationPermissionGranted(context)
    }
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            onDispose {}
        } else {
            val locationManager = context.getSystemService(LocationManager::class.java)
            val listener = LocationListener { location ->
                currentLocation = GeoPoint(location.latitude, location.longitude)
                TestJourneyClient.update(context, GeoPoint(location.latitude, location.longitude))
            }
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

            // 대략적인 위치만 허용된 경우 GPS 요청이 실패해도 네트워크 위치를 요청하도록
            // 각 위치 제공자를 독립적으로 처리한다.
            val lastKnownLocations =
                providers.mapNotNull { provider ->
                    try {
                        locationManager.getLastKnownLocation(provider)
                    } catch (exception: SecurityException) {
                        Log.w(
                            "SafePathApp",
                            "No permission to read last known location from $provider",
                            exception,
                        )
                        null
                    }
                }
            lastKnownLocations
                .maxByOrNull { location -> location.time }
                ?.let { location ->
                    currentLocation = GeoPoint(location.latitude, location.longitude)
                }

            providers.forEach { provider ->
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(provider, 5_000L, 5f, listener)
                    } else {
                        Log.w("SafePathApp", "$provider is disabled on this device")
                    }
                } catch (exception: SecurityException) {
                    Log.w(
                        "SafePathApp",
                        "No permission to request updates from $provider",
                        exception,
                    )
                }
            }

            onDispose { locationManager.removeUpdates(listener) }
        }
    }

    val tab = SafePathTab.entries.find { it.name == selectedTab } ?: SafePathTab.Home
    BackHandler(enabled = tab != SafePathTab.Home) { selectedTab = SafePathTab.Home.name }

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
                    onTestJourneyStart = { route, originName, destinationName, names, originLat, originLng, destinationLat, destinationLng, routeType ->
                        testRoute = route
                        val metadata = org.json.JSONObject().put("origin", originName).put("destination", destinationName).put("recipients", org.json.JSONArray(names)).put("originLat", originLat).put("originLng", originLng).put("destinationLat", destinationLat).put("destinationLng", destinationLng).put("routeType", routeType).toString()
                        testMetadata = metadata
                        if (android.os.Build.VERSION.SDK_INT >= 37 && context.getString(com.example.safepath_test1.R.string.safepath_test_url).isNotBlank() && ContextCompat.checkSelfPermission(context, localNetworkPermission) != PackageManager.PERMISSION_GRANTED)
                            localNetworkLauncher.launch(localNetworkPermission)
                        else beginTestJourney(route, metadata)
                    },
                    onTestJourneyEnd = {
                        testJourneyPending = true
                        TestJourneyClient.end(context) { success ->
                            testJourneyPending = false
                            if (success) testJourneyActive = false
                            android.widget.Toast.makeText(context, if (success) "간이 테스트가 종료되었습니다." else "종료 요청에 실패했습니다. 서버 연결을 확인하세요.", android.widget.Toast.LENGTH_LONG).show()
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
                com.example.safepath_test1.ui.guardian.BackendGuardianScreen(
                    modifier = Modifier.padding(bottom = 100.dp),
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

        preparedInvite?.let { invite ->
            AlertDialog(
                onDismissRequest = { preparedInvite = null },
                title = { Text("카카오톡 친구 요청") },
                text = {
                    Text(
                        shareError ?: "카카오톡에서 받을 친구를 선택하고 메시지를 보내 주세요. 상대방이 링크에서 수락하면 친구 목록에 표시됩니다."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            shareError =
                                com.example.safepath_test1.friends.shareFriendInvite(
                                    context,
                                    invite,
                                )
                        }
                    ) {
                        Text("카카오톡 다시 열기")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            preparedInvite = null
                            shareError = null
                        }
                    ) {
                        Text("닫기")
                    }
                },
            )
        }

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
                                    val result = com.example.safepath_test1.api.BackendRepository(context).sos()
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
