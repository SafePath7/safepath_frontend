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
import com.example.safepath_test1.location.TestJourneyClient
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
    var hasLocationPermission by remember { mutableStateOf(isLocationPermissionGranted(context)) }
    LaunchedEffect(permissionRevision) {
        hasLocationPermission = isLocationPermissionGranted(context)
    }
    AppLocationTracker(context, hasLocationPermission) { location, isUpdate ->
        currentLocation = location
        if (isUpdate) TestJourneyClient.update(context, location)
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
