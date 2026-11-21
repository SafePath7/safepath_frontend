package com.example.safepath_test1.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safepath_test1.location.KakaoPlace
import com.example.safepath_test1.location.RouteResult
import com.example.safepath_test1.model.GeoPoint
import com.example.safepath_test1.model.PlaceSelection
import com.example.safepath_test1.ui.components.FloatingPanelShape
import com.example.safepath_test1.location.NavigationRepository
import com.example.safepath_test1.ui.guardian.DepartureScreen
import com.example.safepath_test1.ui.map.SafePathKakaoMapView
import com.example.safepath_test1.ui.theme.DestRed
import com.example.safepath_test1.ui.theme.FieldBg
import com.example.safepath_test1.ui.theme.SafeBlue
import com.example.safepath_test1.ui.theme.SkyBlue
import com.example.safepath_test1.ui.theme.TextMain
import com.example.safepath_test1.ui.theme.TextMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentLocation: GeoPoint?,
    hasLocationPermission: Boolean,
    testJourneyActive: Boolean,
    testJourneyPending: Boolean,
    onTestJourneyStart: (com.example.safepath_test1.location.RouteResult?, String, String, List<String>, Double?, Double?, Double?, Double?, String) -> Unit,
    onTestJourneyEnd: () -> Unit,
    origin: PlaceSelection,
    destination: PlaceSelection,
    onOriginChanged: (PlaceSelection) -> Unit,
    onDestinationChanged: (PlaceSelection) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val homeViewModel: HomeViewModel = viewModel()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val routeTypes = remember { listOf(RouteType.Recommended, RouteType.Safe, RouteType.Shortest) }
    val routePagerState = rememberPagerState(initialPage = 0, pageCount = { routeTypes.size })
    val configuration = LocalConfiguration.current
    val tallScreen = configuration.screenWidthDp > 0 && configuration.screenHeightDp >= 700 &&
        configuration.screenHeightDp.toFloat() / configuration.screenWidthDp >= 1.7f
    var selectedRouteIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(tallScreen) {
        if (tallScreen) selectedRouteIndex = routePagerState.settledPage
        else routePagerState.scrollToPage(selectedRouteIndex)
    }
    val routeType = routeTypes[if (tallScreen) selectedRouteIndex else routePagerState.settledPage].name
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val maxPanelHeight = (configuration.screenHeightDp - 220).coerceIn(154, if (tallScreen) 560 else 320).toFloat()
    var routePanelHeight by rememberSaveable { mutableStateOf(320f) }
    LaunchedEffect(tallScreen) { routePanelHeight = maxPanelHeight }
    val panelDrag = rememberDraggableState { delta ->
        routePanelHeight = (routePanelHeight.coerceIn(154f, maxPanelHeight) - delta / density).coerceIn(154f, maxPanelHeight)
    }
    var recenterToken by remember { mutableIntStateOf(0) }
    var showSafetyFacilities by rememberSaveable { mutableStateOf(true) }
    var facilityRetry by remember { mutableIntStateOf(0) }
    val facilitySnackbar = remember { SnackbarHostState() }
    val facilityScope = rememberCoroutineScope()
    var activeTab by rememberSaveable { mutableStateOf("destination") }
    var isDestinationSearchOpen by rememberSaveable { mutableStateOf(false) }
    var stage by rememberSaveable { mutableStateOf("search") }
    var showJourneyEnd by remember { mutableStateOf(false) }
    var showSessionRecoveryEnd by remember { mutableStateOf(false) }
    val recoveryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && testJourneyActive && !testJourneyPending) onTestJourneyEnd()
    }
    var endPin by remember { mutableStateOf("") }
    var endPinError by remember { mutableStateOf(false) }
    var endPinFailures by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(testJourneyActive) { if (!testJourneyActive) endPinFailures = 0 }
    var endAuthenticating by remember { mutableStateOf(false) }
    var endAuthenticationCancellation by remember { mutableStateOf<android.os.CancellationSignal?>(null) }
    val endAuthenticationScopeActive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) {
        onDispose {
            endAuthenticationScopeActive.set(false)
            endAuthenticationCancellation?.cancel()
        }
    }
    var selectedTestRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var destinationConfirmation by remember { mutableIntStateOf(0) }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    var favoriteLabel by rememberSaveable { mutableStateOf<String?>(null) }
    val recentPrefs = remember {
        context.getSharedPreferences("safe_path_destinations", android.content.Context.MODE_PRIVATE)
    }
    LaunchedEffect(Unit) {
        com.example.safepath_test1.location.SafetyRepository.warmAssessmentDatasets(context.applicationContext)
    }
    LaunchedEffect(currentLocation) {
        if (origin.name.isBlank() && currentLocation != null)
            onOriginChanged(
                PlaceSelection("현재 위치", currentLocation.latitude, currentLocation.longitude)
            )
    }
    val returnToSearch = {
        if (stage == "routes") onDestinationChanged(PlaceSelection())
        isDestinationSearchOpen = false
        keyboardController?.hide()
        stage = "search"
    }
    BackHandler(enabled = stage != "search") { returnToSearch() }
    val multiRouteResult = uiState.routes
    val isRouteLoading = uiState.isRouteLoading
    val routeError = uiState.routeError
    val routeNotice = if (stage == "routes") multiRouteResult?.duplicateRouteLabel() else null
    val placeSearchResults = uiState.places
    val isPlaceSearchLoading = uiState.isPlaceSearchLoading
    val placeSearchError = uiState.placeSearchError
    val destinationPoint =
        if (destination.hasCoordinates()) GeoPoint(destination.latitude!!, destination.longitude!!)
        else null

    LaunchedEffect(destination.name, isDestinationSearchOpen) {
        homeViewModel.searchPlaces(destination.name, isDestinationSearchOpen, currentLocation)
    }

    // 출발지·목적지 변경 시 경로를 다시 조회하고 워치 상태도 함께 갱신한다.
    LaunchedEffect(origin, destination, destinationConfirmation) {
        if (testJourneyActive) {
            stage = "navigation"
            return@LaunchedEffect
        }
        inputError =
            if (destination.hasCoordinates() && !origin.hasCoordinates())
                "출발지를 선택하거나 현재 위치를 확인해 주세요."
            else null
        if (!origin.hasCoordinates() || !destination.hasCoordinates()) {
            stage = "search"
            homeViewModel.loadRoutes(origin, destination)
            com.example.safepath_test1.wear.WearMessenger.sendIdle(context)
            return@LaunchedEffect
        }
        isDestinationSearchOpen = false
        keyboardController?.hide()
        stage = "routes"
        com.example.safepath_test1.wear.WearMessenger.sendRouteSearching(context)
        homeViewModel.loadRoutes(origin, destination)
    }

    val activeRoute =
        when (routeType) {
            RouteType.Safe.name -> multiRouteResult?.safeRoute
            RouteType.Shortest.name -> multiRouteResult?.shortestRoute
            else -> multiRouteResult?.recommendedRoute
        }

    val activeRouteColor =
        when (routeType) {
            RouteType.Safe.name -> "#1B4965"
            RouteType.Shortest.name -> "#9AA5AF"
            else -> "#5FA8D3"
        }

    // 사용자가 선택한 경로 탭을 워치와 동기화하고, 결과 조회 후 탭을 바꾸거나 현재 위치가 갱신되면 다시 전송한다.
    LaunchedEffect(multiRouteResult, activeRoute, routeType, currentLocation) {
        if (multiRouteResult == null) return@LaunchedEffect
        val selectedType = RouteType.valueOf(routeType)
        val route = activeRoute
        if (route != null) {
            com.example.safepath_test1.wear.WearMessenger.sendRouteFound(
                context = context,
                routeType = selectedType.title,
                destinationName =
                    destination.name.ifBlank { null }?.takeUnless { it.startsWith("선택한 장소") },
                distanceMeters = route.distanceMeters,
                routeLineGeoJson = route.geoJsonLineString,
                destinationPoint = destinationPoint,
                currentPoint = currentLocation,
                // 워치의 회전 안내("120m 후 우회전")용. 응답에 없으면 비어 있고 워치가 경로 모양으로 추측한다.
                routeSteps =
                    route.maneuvers.map {
                        com.example.safepath_test1.wear.WearRouteStep(it.point, it.type, it.modifier)
                    },
            )
        } else {
            com.example.safepath_test1.wear.WearMessenger.sendIdle(context)
        }
    }

    LaunchedEffect(testJourneyActive) {
        if (testJourneyActive) stage = "navigation"
        else if (stage == "navigation") stage = "departure"
    }

    if (stage == "departure") {
        DepartureScreen(
            destination.name,
            RouteType.valueOf(routeType).title,
            activeRoute?.let {
                String.format(java.util.Locale.KOREA, "%.1fkm", it.distanceMeters / 1000)
            } ?: "—",
            route = activeRoute,
            onBack = { stage = "routes" },
            testJourneyActive = testJourneyActive,
            testJourneyPending = testJourneyPending,
            onTestJourneyStart = { names, pin ->
                com.example.safepath_test1.session.JourneyEndAuth.setEndPin(pin)
                onTestJourneyStart(activeRoute, origin.name, destination.name, names, origin.latitude, origin.longitude, destination.latitude, destination.longitude, routeType)
            },
            onTestJourneyEnd = onTestJourneyEnd,
            modifier = modifier.padding(bottom = 88.dp),
        )
        return
    }
    Box(modifier = modifier.fillMaxSize().imePadding()) {
        SafePathKakaoMapView(
            currentLocation = currentLocation,
            hasLocationPermission = hasLocationPermission,
            recenterToken = recenterToken,
            showSafetyFacilities = showSafetyFacilities,
            facilityRetry = facilityRetry,
            onFacilityError = { message ->
                facilityScope.launch {
                    if (facilitySnackbar.showSnackbar(message, actionLabel = "다시 시도", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) facilityRetry++
                }
            },
            destinationPoint = destinationPoint,
            routeLineGeoJson = if (stage == "navigation") selectedTestRoute else activeRoute?.geoJsonLineString,
            routeLineColor = activeRouteColor,
            alternativeRouteLines =
                if (stage == "routes")
                    listOfNotNull(
                            multiRouteResult?.shortestRoute?.geoJsonLineString?.let {
                                it to "#9AA5AF"
                            },
                            multiRouteResult?.recommendedRoute?.geoJsonLineString?.let {
                                it to "#5FA8D3"
                            },
                            multiRouteResult?.safeRoute?.geoJsonLineString?.let { it to "#1B4965" },
                        )
                        .filterNot { it.first == activeRoute?.geoJsonLineString }
                else emptyList(),
            onMapClick = { point ->
                val latStr = String.format(java.util.Locale.US, "%.4f", point.latitude)
                val lngStr = String.format(java.util.Locale.US, "%.4f", point.longitude)
                val selectedPlace =
                    PlaceSelection("선택한 장소 ($latStr, $lngStr)", point.latitude, point.longitude)

                if (activeTab == "origin") {

                    onOriginChanged(selectedPlace)
                    activeTab = "destination"
                    Toast.makeText(context, "출발지가 설정되었습니다. 이제 도착지를 지정해 주세요.", Toast.LENGTH_SHORT)
                        .show()
                } else {

                    onDestinationChanged(selectedPlace)
                    destinationConfirmation++
                    isDestinationSearchOpen = false
                    keyboardController?.hide()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (stage == "search")
            RouteSearchCard(
                originError = inputError != null && !origin.hasCoordinates(),
                destinationError = inputError != null && !destination.hasCoordinates(),
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                origin = origin,
                destination = destination,
                activeTab = activeTab,
                onActiveTabChanged = {
                    activeTab = it
                    isDestinationSearchOpen = it == "destination"
                },
                onOriginChanged = {
                    onOriginChanged(it)
                    if (it.hasCoordinates() && !destination.hasCoordinates()) {
                        activeTab = "destination"
                    }
                },
                onDestinationChanged = {
                    onDestinationChanged(it)
                    isDestinationSearchOpen = true
                },
                onSwap = {
                    onSwap()
                    isDestinationSearchOpen = false
                    keyboardController?.hide()
                },
                onUseCurrentLocation = {
                    currentLocation?.let {
                        onOriginChanged(PlaceSelection("내 위치", it.latitude, it.longitude))
                        activeTab = "destination"
                        Toast.makeText(
                                context,
                                "출발지가 설정되었습니다. 이제 도착지를 지정해 주세요.",
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                },
            )

        if (stage == "routes")
            Surface(
                Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(14.dp)
                    .fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 3.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = returnToSearch) {
                        Icon(Icons.Default.Close, "목적지 입력으로 돌아가기")
                    }
                    Text(
                        "${origin.name} → ${destination.name}",
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

        if (isDestinationSearchOpen) {
            DestinationSearchPanel(
                query = destination.name,
                places = placeSearchResults,
                isLoading = isPlaceSearchLoading,
                errorMessage = placeSearchError,
                onQueryChanged = { query -> onDestinationChanged(PlaceSelection(name = query)) },
                onPlaceSelected = { place ->
                    destinationConfirmation++

                    onDestinationChanged(
                        PlaceSelection(
                            name = place.name,
                            latitude = place.latitude,
                            longitude = place.longitude,
                        )
                    )
                    isDestinationSearchOpen = false
                    keyboardController?.hide()
                },
                onDismiss = {
                    isDestinationSearchOpen = false
                    keyboardController?.hide()
                },
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 138.dp, start = 14.dp, end = 14.dp),
            )
        } else if (isRouteLoading || routeError != null || routeNotice != null) {
            val statusMessage = routeError ?: if (isRouteLoading) "경로 검색 중" else routeNotice.orEmpty()
            Surface(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(
                            top = if (stage == "routes") 78.dp else 138.dp,
                            start = 24.dp,
                            end = 24.dp,
                        ),
                shape = RoundedCornerShape(12.dp),
                color =
                    when {
                        routeError != null -> Color(0xFFFFF1F2)
                        routeNotice != null -> Color(0xFFFFFBEB)
                        else -> Color.White
                    },
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRouteLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = SafeBlue,
                        )
                    }
                    Text(
                        text = statusMessage,
                        color =
                            if (routeError == null) TextMain
                            else com.example.safepath_test1.ui.theme.DangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (!isDestinationSearchOpen && stage != "navigation") {
            Surface(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = if (stage == "routes") 92.dp else 80.dp,
                        ),
                shape = FloatingPanelShape,
                color = Color.White,
                shadowElevation = 6.dp,
            ) {
                Column(
                    (if (stage == "routes") Modifier else Modifier.height(144.dp))
                        .padding(
                            horizontal = 16.dp,
                            vertical = if (stage == "routes") 16.dp else 12.dp,
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(if (stage == "routes") 10.dp else 4.dp),
                ) {
                    if (stage == "routes") {
                        // Only this body folds. The action below remains outside the clipped area.
                        Column(
                            Modifier.height((routePanelHeight.coerceIn(154f, maxPanelHeight) - 94f).dp)
                                .clipToBounds()
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .height((maxPanelHeight - 94f).dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                        Column(
                            Modifier.fillMaxWidth().draggable(state = panelDrag, orientation = Orientation.Vertical),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                                    .background(FieldBg, CircleShape)
                            )
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 36.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("경로 추천", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }
                        }
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (tallScreen) {
                                routeTypes.forEachIndexed { index, type ->
                                    val route = when (type) {
                                        RouteType.Safe -> multiRouteResult?.safeRoute
                                        RouteType.Recommended -> multiRouteResult?.recommendedRoute
                                        RouteType.Shortest -> multiRouteResult?.shortestRoute
                                    }
                                    RouteOptionCard(type, route, selected = selectedRouteIndex == index, fastest = multiRouteResult?.shortestRoute) {
                                        selectedRouteIndex = index
                                    }
                                }
                            } else {
                            HorizontalPager(
                                state = routePagerState,
                                pageSpacing = 8.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) { page ->
                                val type = routeTypes[page]
                                val route =
                                    when (type) {
                                        RouteType.Safe -> multiRouteResult?.safeRoute
                                        RouteType.Recommended -> multiRouteResult?.recommendedRoute
                                        RouteType.Shortest -> multiRouteResult?.shortestRoute
                                    }
                                RouteOptionCard(type, route, selected = true, fastest = multiRouteResult?.shortestRoute) {}
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                routeTypes.forEachIndexed { index, type ->
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                routePagerState.animateScrollToPage(index)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Circle,
                                            "${type.title} 선택",
                                            tint =
                                                if (routePagerState.settledPage == index) SafeBlue
                                                else FieldBg,
                                            modifier = Modifier.size(8.dp),
                                        )
                                    }
                                }
                            }
                            }
                        }
                        }
                        Button(
                            shape = MaterialTheme.shapes.small,
                            onClick = { selectedTestRoute = activeRoute?.geoJsonLineString; stage = "departure" },
                            enabled = activeRoute != null && (tallScreen || !routePagerState.isScrollInProgress),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Text("이 경로로 출발 준비")
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "자주 가는 곳",
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                                Text("목적지를 빠르게 선택하세요", color = TextMuted, fontSize = 11.sp)
                            }
                            Icon(
                                Icons.Default.Star,
                                null,
                                tint = SkyBlue,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("집", "학교").forEach { label ->
                                FavoriteShortcut(
                                    label,
                                    if (label == "집") Icons.Default.Home else Icons.Default.School,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val favorite = readFavorite(recentPrefs, label)
                                        if (favorite != null) {
                                            onDestinationChanged(favorite)
                                            destinationConfirmation++
                                        } else if (destination.hasCoordinates())
                                            favoriteLabel = label
                                        else {
                                            activeTab = "destination"
                                            isDestinationSearchOpen = true
                                        }
                                    },
                                )
                            }
                            FavoriteShortcut(
                                "추가",
                                Icons.Default.Add,
                                modifier = Modifier.weight(1f),
                                outlined = true,
                                onClick = {
                                    if (destination.hasCoordinates()) favoriteLabel = "집"
                                    else isDestinationSearchOpen = true
                                },
                            )
                        }
                    }
                }
            }
        }

        favoriteLabel?.let { label ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { favoriteLabel = null },
                title = { Text("즐겨찾기 저장") },
                text = {
                    Column {
                        Text("${destination.name}을 즐겨찾기로 저장할까요?")
                        Row {
                            listOf("집", "학교").forEach { option ->
                                OutlinedButton(
                                    shape = MaterialTheme.shapes.small,
                                    onClick = { favoriteLabel = option },
                                ) {
                                    Text(option)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        shape = MaterialTheme.shapes.small,
                        onClick = {
                            recentPrefs
                                .edit()
                                .putString(
                                    "favorite_$label",
                                    org.json
                                        .JSONObject()
                                        .put("name", destination.name)
                                        .put("lat", destination.latitude)
                                        .put("lng", destination.longitude)
                                        .toString(),
                                )
                                .apply()
                            favoriteLabel = null
                        },
                    ) {
                        Text("$label 저장")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        shape = MaterialTheme.shapes.small,
                        onClick = { favoriteLabel = null },
                    ) {
                        Text("취소")
                    }
                },
            )
        }

        if (stage == "navigation") {
            Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 88.dp), shape = RoundedCornerShape(16.dp), color = Color.White) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("귀가 세션 중 · 위치 공유 안 됨", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        endPin = ""; endPinError = false
                        val alias = com.example.safepath_test1.session.JourneyEndAuth.biometricKeyAlias
                        val activity = context as? android.app.Activity
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && alias != null && activity != null) {
                            val challenge = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                            endAuthenticating = true
                            endAuthenticationCancellation = com.example.safepath_test1.session.BiometricSessionSigner.signChallenge(activity, alias, challenge,
                                onSigned = { if (endAuthenticationScopeActive.get()) { endAuthenticating = false; onTestJourneyEnd() } },
                                onFallback = { if (endAuthenticationScopeActive.get()) { endAuthenticating = false; showSessionRecoveryEnd = true } })
                        } else if (com.example.safepath_test1.session.JourneyEndAuth.isAvailable) showJourneyEnd = true
                        else showSessionRecoveryEnd = true
                    }, enabled = !testJourneyPending && !endAuthenticating) { Text("안전귀가 종료") }
                }
            }
        }
        if (showSessionRecoveryEnd) androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSessionRecoveryEnd = false },
            title = { Text("귀가 세션 종료") },
            text = { Text("기기 화면 잠금을 확인한 뒤 안전귀가를 종료할 수 있어요.") },
            confirmButton = { androidx.compose.material3.TextButton(enabled = !testJourneyPending, onClick = {
                val manager = context.getSystemService(android.app.KeyguardManager::class.java)
                val intent = manager?.createConfirmDeviceCredentialIntent("안전귀가 종료", "화면 잠금을 확인해 주세요")
                if (intent == null) Toast.makeText(context, "기기 설정에서 화면 잠금을 먼저 설정해 주세요.", Toast.LENGTH_LONG).show()
                else { showSessionRecoveryEnd = false; recoveryLauncher.launch(intent) }
            }) { Text("본인 확인") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showSessionRecoveryEnd = false }) { Text("취소") } },
        )
        if (showJourneyEnd) androidx.compose.material3.AlertDialog(
            onDismissRequest = { showJourneyEnd = false },
            title = { Text("안전귀가 종료") },
            text = {
                Column {
                androidx.compose.material3.OutlinedTextField(
                    value = endPin,
                    onValueChange = { endPin = it.filter(Char::isDigit).take(4); endPinError = false },
                    label = { Text("종료 비밀번호 4자리") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    isError = endPinError,
                )
                if (endPinError) Text(if (endPinFailures >= 5) "비밀번호가 5회 이상 일치하지 않았습니다. 다시 확인해 주세요." else "비밀번호가 일치하지 않습니다. ($endPinFailures/5)")
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(enabled = !testJourneyPending && endPin.length == 4, onClick = {
                if (com.example.safepath_test1.session.JourneyEndAuth.verifyEndPin(endPin)) { showJourneyEnd = false; endPin = ""; endPinFailures = 0; onTestJourneyEnd() } else { endPinFailures++; endPinError = true }
            }) { Text("종료") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showJourneyEnd = false }) { Text("취소") } },
        )
        MapSideControls(
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = if (stage == "routes") 115.dp else 155.dp, end = 12.dp),
            locationEnabled = hasLocationPermission,
            safetyFacilitiesEnabled = showSafetyFacilities,
            onRecenter = { recenterToken++ },
            onToggleSafetyFacilities = { showSafetyFacilities = !showSafetyFacilities },
        )
        SnackbarHost(facilitySnackbar, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 152.dp))
    }
}
