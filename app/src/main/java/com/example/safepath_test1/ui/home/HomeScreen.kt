package com.example.safepath_test1.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.safepath_test1.ui.guardian.DepartureScreen
import com.example.safepath_test1.ui.map.SafePathKakaoMapView
import com.example.safepath_test1.ui.theme.DestRed
import com.example.safepath_test1.ui.theme.FieldBg
import com.example.safepath_test1.ui.theme.SafeBlue
import com.example.safepath_test1.ui.theme.SkyBlue
import com.example.safepath_test1.ui.theme.TextMain
import com.example.safepath_test1.ui.theme.TextMuted
import kotlinx.coroutines.launch

private enum class RouteType(val title: String, val icon: ImageVector) {
    Safe("가장 안전한 경로", Icons.Default.Lock),
    Shortest("최단 경로", Icons.Default.Info),
    Recommended("추천 경로", Icons.Default.Star),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentLocation: GeoPoint?,
    hasLocationPermission: Boolean,
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
    val routeTypes = remember { listOf(RouteType.Safe, RouteType.Recommended, RouteType.Shortest) }
    val routePagerState = rememberPagerState(pageCount = { routeTypes.size })
    val routeType = routeTypes[routePagerState.settledPage].name
    val coroutineScope = rememberCoroutineScope()
    var recenterToken by remember { mutableIntStateOf(0) }
    var showSafetyFacilities by rememberSaveable { mutableStateOf(true) }
    var activeTab by rememberSaveable { mutableStateOf("destination") }
    var isDestinationSearchOpen by rememberSaveable { mutableStateOf(false) }
    var stage by rememberSaveable { mutableStateOf("search") }
    var destinationConfirmation by remember { mutableIntStateOf(0) }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    var favoriteLabel by rememberSaveable { mutableStateOf<String?>(null) }
    val recentPrefs = remember {
        context.getSharedPreferences("safe_path_destinations", android.content.Context.MODE_PRIVATE)
    }
    LaunchedEffect(currentLocation) {
        if (origin.name.isBlank() && currentLocation != null)
            onOriginChanged(
                PlaceSelection("현재 위치", currentLocation.latitude, currentLocation.longitude)
            )
    }
    BackHandler(enabled = stage != "search") { stage = "search" }
    val multiRouteResult = uiState.routes
    val isRouteLoading = uiState.isRouteLoading
    val routeError = uiState.routeError
    val routeNotice = uiState.routeNotice
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
            )
        } else {
            com.example.safepath_test1.wear.WearMessenger.sendIdle(context)
        }
    }

    if (stage == "departure") {
        DepartureScreen(
            destination.name,
            RouteType.valueOf(routeType).title,
            activeRoute?.let {
                String.format(java.util.Locale.KOREA, "%.1fkm", it.distanceMeters / 1000)
            } ?: "—",
            onBack = { stage = "routes" },
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
            destinationPoint = destinationPoint,
            routeLineGeoJson = activeRoute?.geoJsonLineString,
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
                    androidx.compose.material3.IconButton(onClick = { stage = "search" }) {
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
            val statusMessage = routeError ?: routeNotice ?: "안전 경로를 검색하고 있습니다."
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

        if (!isDestinationSearchOpen) {
            Surface(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = if (stage == "routes") 108.dp else 80.dp,
                        ),
                shape = FloatingPanelShape,
                color = Color.White,
                shadowElevation = 6.dp,
            ) {
                Column(
                    (if (stage == "routes") Modifier.heightIn(max = 430.dp)
                        else Modifier.height(144.dp))
                        .padding(
                            horizontal = 16.dp,
                            vertical = if (stage == "routes") 16.dp else 12.dp,
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(if (stage == "routes") 10.dp else 4.dp),
                ) {
                    if (stage == "routes")
                        Box(
                            Modifier.align(Alignment.CenterHorizontally)
                                .width(36.dp)
                                .height(4.dp)
                                .background(FieldBg, CircleShape)
                        )
                    if (stage == "routes") {
                        Column(
                            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "경로 추천",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "좌우로 넘겨 선택 · ${routePagerState.settledPage + 1}/3",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
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
                                RouteOptionCard(type, route, selected = true) {}
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
                            if (routeNotice != null)
                                Text(routeNotice, color = TextMuted, fontSize = 12.sp)
                        }
                        Button(
                            shape = MaterialTheme.shapes.small,
                            onClick = { stage = "departure" },
                            enabled = activeRoute != null && !routePagerState.isScrollInProgress,
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
    }
}

@Composable
private fun FavoriteShortcut(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (outlined) Color.White else SafeBlue.copy(alpha = 0.06f),
        border = if (outlined) BorderStroke(1.dp, SkyBlue.copy(alpha = 0.35f)) else null,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (outlined) SkyBlue else SafeBlue,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, color = SafeBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DestinationSearchPanel(
    query: String,
    places: List<KakaoPlace>,
    isLoading: Boolean,
    errorMessage: String?,
    onQueryChanged: (String) -> Unit,
    onPlaceSelected: (KakaoPlace) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(
        modifier = modifier.fillMaxWidth().shadow(5.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = SafeBlue,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isBlank()) {
                        Text(text = "장소명이나 주소를 입력하세요", color = TextMuted, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChanged,
                        singleLine = true,
                        textStyle =
                            TextStyle(
                                color = TextMain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        cursorBrush = SolidColor(SafeBlue),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "검색 닫기",
                    tint = TextMuted,
                    modifier = Modifier.size(48.dp).clickable(onClick = onDismiss).padding(12.dp),
                )
            }

            val trimmedQuery = query.trim()
            when {
                trimmedQuery.length < 2 -> SearchMessage("목적지를 2글자 이상 입력해 주세요.")
                isLoading ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = SafeBlue,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("카카오에서 장소를 검색하고 있습니다.", color = TextMuted, fontSize = 12.sp)
                    }
                errorMessage != null -> SearchMessage(errorMessage, color = DestRed)
                places.isEmpty() -> SearchMessage("검색 결과가 없습니다.")
                else ->
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        itemsIndexed(items = places, key = { _, place -> place.id }) { index, place
                            ->
                            if (index > 0) {
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = Color(0xFFF1F5F9),
                                )
                            }
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clickable { onPlaceSelected(place) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = DestRed,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(9.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.name,
                                        color = TextMain,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (place.address.isNotBlank()) {
                                        Text(
                                            text = place.address,
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                place.distanceMeters?.let { distance ->
                                    Text(
                                        text = formatDistance(distance),
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun SearchMessage(message: String, color: Color = TextMuted) {
    Text(
        text = message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
        color = color,
        fontSize = 12.sp,
    )
}

private fun formatDistance(distanceMeters: Int): String =
    if (distanceMeters < 1_000) "${distanceMeters}m"
    else String.format(java.util.Locale.KOREA, "%.1fkm", distanceMeters / 1_000.0)

@Composable
private fun RouteSearchCard(
    originError: Boolean,
    destinationError: Boolean,
    modifier: Modifier = Modifier,
    origin: PlaceSelection,
    destination: PlaceSelection,
    activeTab: String,
    onActiveTabChanged: (String) -> Unit,
    onOriginChanged: (PlaceSelection) -> Unit,
    onDestinationChanged: (PlaceSelection) -> Unit,
    onSwap: () -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.98f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RouteFieldRow(
                        leading = { Box(Modifier.size(9.dp).background(SkyBlue, CircleShape)) },
                        isError = originError,
                        value = origin.name,
                        placeholder = "출발지 · 현재 위치",
                        isSelected = activeTab == "origin",
                        onSelect = { onActiveTabChanged("origin") },
                        onValueChange = { onOriginChanged(PlaceSelection(name = it)) },
                        trailing = {
                            Icon(
                                Icons.Default.MyLocation,
                                "내 위치 설정",
                                tint = SafeBlue,
                                modifier = Modifier.size(24.dp).clickable { onUseCurrentLocation() },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RouteFieldRow(
                        leading = {
                            Icon(
                                Icons.Default.LocationOn,
                                null,
                                tint = SafeBlue,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        isError = destinationError,
                        value = destination.name,
                        placeholder = "어디로 가시나요?",
                        isSelected = activeTab == "destination",
                        onSelect = { onActiveTabChanged("destination") },
                        onValueChange = { onDestinationChanged(PlaceSelection(name = it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Surface(
                    modifier =
                        Modifier.padding(start = 8.dp).size(40.dp).clickable(onClick = onSwap),
                    shape = CircleShape,
                    color = FieldBg,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Refresh, "출발지와 도착지 바꾸기", tint = SafeBlue)
                    }
                }
            }

            if (originError)
                Text(
                    "출발지를 선택해 주세요.",
                    color = com.example.safepath_test1.ui.theme.DangerRed,
                    fontSize = 11.sp,
                )
            if (destinationError)
                Text(
                    "검색 결과 또는 지도에서 목적지를 선택해 주세요.",
                    color = com.example.safepath_test1.ui.theme.DangerRed,
                    fontSize = 11.sp,
                )
        }
    }
}

@Composable
private fun RouteFieldRow(
    isError: Boolean = false,
    leading: @Composable () -> Unit,
    value: String,
    placeholder: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val backgroundColor = if (isSelected) Color.White else FieldBg
    val borderColor =
        if (isError) com.example.safepath_test1.ui.theme.DangerRed
        else if (isSelected) SafeBlue else Color.Transparent

    Surface(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .shadow(if (isSelected) 2.dp else 0.dp, RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelect,
                ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSelect,
                    )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    leading()
                }
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            color = if (isSelected) TextMain else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = {
                            onSelect()
                            onValueChange(it)
                        },
                        singleLine = true,
                        textStyle =
                            TextStyle(
                                color = TextMain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        cursorBrush = SolidColor(SafeBlue),
                        modifier =
                            Modifier.fillMaxWidth().onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    onSelect()
                                }
                            },
                    )
                }
                if (trailing != null) {
                    Spacer(Modifier.width(6.dp))
                    trailing()
                }
            }
        }
    }
}

@Composable
private fun MapSideControls(
    modifier: Modifier,
    locationEnabled: Boolean,
    safetyFacilitiesEnabled: Boolean,
    onRecenter: () -> Unit,
    onToggleSafetyFacilities: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundMapButton(
            icon = Icons.Default.LocationOn,
            enabled = locationEnabled,
            selected = false,
            onClick = onRecenter,
        )
        RoundMapButton(
            icon = Icons.Default.Lock,
            enabled = true,
            selected = safetyFacilitiesEnabled,
            onClick = onToggleSafetyFacilities,
        )
    }
}

@Composable
private fun RoundMapButton(
    icon: ImageVector,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier.size(48.dp)
                .shadow(4.dp, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (selected) SafeBlue else Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    when {
                        selected -> Color.White
                        enabled -> SafeBlue
                        else -> Color.Gray
                    },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RouteOptionCard(
    type: RouteType,
    route: RouteResult?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val lineColor =
        when (type) {
            RouteType.Safe -> SafeBlue
            RouteType.Recommended -> SkyBlue
            RouteType.Shortest -> Color(0xFF9AA5AF)
        }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = route != null, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFFF3F8FA) else Color.White,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) lineColor else FieldBg),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(16.dp).height(4.dp).background(lineColor, CircleShape))
                Text(
                    type.title,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    fontSize = 13.sp,
                )
                if (type == RouteType.Safe)
                    Text("가장 안전", color = SafeBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text(
                    route?.let {
                        String.format(java.util.Locale.KOREA, "%.1fkm", it.distanceMeters / 1000)
                    } ?: "—",
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 12.sp,
                )
            }
            // 저장소의 점수는 시설 밀도이며 0~100으로 보정된 안전 점수가 아니다.
            Text(
                route?.let {
                    String.format(
                        java.util.Locale.KOREA,
                        "시간 — · 안전점수 — · 시설지표 %.1f",
                        it.safetyFacilityScore,
                    )
                } ?: "경로 조회 대기 중",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
            LinearProgressIndicator(
                progress = { 0f },
                color = lineColor,
                trackColor = FieldBg,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "CCTV —   비상벨 —   지킴이집 —   경찰서 —",
                color = TextMuted,
                fontSize = 10.sp,
                lineHeight = 13.sp,
            )
        }
    }
}

private fun readFavorite(prefs: android.content.SharedPreferences, label: String): PlaceSelection? =
    runCatching {
            val raw = prefs.getString("favorite_$label", null) ?: return null
            val item = org.json.JSONObject(raw)
            val lat = item.getDouble("lat")
            val lng = item.getDouble("lng")
            if (lat.isFinite() && lng.isFinite()) PlaceSelection(item.getString("name"), lat, lng)
            else null
        }
        .getOrNull()
