package com.example.safepath_test1.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.asImageBitmap
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

internal enum class RouteType(val title: String, val icon: ImageVector) {
    Safe("안전시설 우선 경로", Icons.Default.Lock),
    Shortest("가장 빠른 경로", Icons.Default.Info),
    Recommended("추천 경로", Icons.Default.Star),
}

@Composable
internal fun FavoriteShortcut(
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
internal fun DestinationSearchPanel(
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
internal fun SearchMessage(message: String, color: Color = TextMuted) {
    Text(
        text = message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
        color = color,
        fontSize = 12.sp,
    )
}

internal fun formatDistance(distanceMeters: Int): String =
    if (distanceMeters < 1_000) "${distanceMeters}m"
    else String.format(java.util.Locale.KOREA, "%.1fkm", distanceMeters / 1_000.0)

@Composable
internal fun RouteSearchCard(
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
internal fun RouteFieldRow(
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
internal fun MapSideControls(
    modifier: Modifier,
    locationEnabled: Boolean,
    safetyFacilitiesEnabled: Boolean,
    onRecenter: () -> Unit,
    onToggleSafetyFacilities: () -> Unit,
    failedFacilities: Set<com.example.safepath_test1.location.FacilityType>,
    onRetryFacilities: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        if (safetyFacilitiesEnabled) {
            val facilities = listOf(
                Triple(com.example.safepath_test1.location.FacilityType.CCTV, "CCTV", "marker_cctv.png"),
                Triple(com.example.safepath_test1.location.FacilityType.LIGHT, "가로등", "marker_streetlight.png"),
                Triple(com.example.safepath_test1.location.FacilityType.BELL, "비상벨", "marker_safety_bell.png"),
                Triple(com.example.safepath_test1.location.FacilityType.POLICE, "경찰시설", "marker_police_station.png"),
                Triple(com.example.safepath_test1.location.FacilityType.FIRE, "소방시설", "marker_fire_station.png"),
            )
            facilities.filter { (type, _, _) -> type in failedFacilities }.forEach { (type, label, asset) ->
                val context = LocalContext.current
                val bitmap = remember(asset) {
                    runCatching { context.assets.open(asset).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
                }
                val failed = type in failedFacilities
                Surface(
                    modifier = Modifier.size(40.dp).shadow(3.dp, CircleShape)
                        .clickable(enabled = failed, onClick = onRetryFacilities),
                    shape = CircleShape,
                    color = Color.White,
                    border = if (failed) BorderStroke(2.dp, Color(0xFFD32F2F)) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (bitmap != null) androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = if (failed) "$label 불러오기 실패, 다시 시도" else "$label",
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RoundMapButton(
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
internal fun RouteOptionCard(
    type: RouteType,
    route: RouteResult?,
    selected: Boolean,
    fastest: RouteResult? = null,
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
                if (type == RouteType.Safe && (route?.safetyMetrics?.coveredRatio ?: 0.0) >= 0.05)
                    Text("시설 우선", color = SafeBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text(
                    route?.let {
                        String.format(java.util.Locale.KOREA, "%.1fkm", it.distanceMeters / 1000)
                    } ?: "—",
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 12.sp,
                )
            }
            // 시설 밀도는 범죄 확률이나 보정된 절대 안전 점수가 아니다.
            Text(
                route?.let {
                    if(it.availableSafetyScore == null) "약 ${kotlin.math.ceil(NavigationRepository.travelSeconds(it)/60).toInt()}분 · 안전도 판단 불가" else String.format(
                        java.util.Locale.KOREA,
                        "약 ${kotlin.math.ceil(NavigationRepository.travelSeconds(it) / 60).toInt()}분${if (it.durationSeconds == null) " (추정)" else ""} · 시설 기반 지표 %.0f/100",
                        it.safetyFacilityScore,
                    )
                } ?: "경로 조회 대기 중",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
            if(route?.availableSafetyScore != null) LinearProgressIndicator(
                progress = { ((route?.safetyFacilityScore ?: 0.0) / 100.0).coerceIn(0.0,1.0).toFloat() },
                color = lineColor,
                trackColor = FieldBg,
                modifier = Modifier.fillMaxWidth(),
            )
            if(route != null && fastest != null) {
                val extraSeconds = NavigationRepository.travelSeconds(route)-NavigationRepository.travelSeconds(fastest)
                val extraMeters = route.distanceMeters-fastest.distanceMeters
                Text("총 ${route.distanceMeters.toInt()}m · 가장 빠른 경로 대비 ${if(extraSeconds>=0) "+" else ""}${kotlin.math.ceil(extraSeconds/60).toInt()}분 / ${if(extraMeters>=0) "+" else ""}${extraMeters.toInt()}m", color=TextMuted, fontSize=10.sp)
            }
        }
    }
}

internal fun readFavorite(prefs: android.content.SharedPreferences, label: String): PlaceSelection? =
    runCatching {
            val raw = prefs.getString("favorite_$label", null) ?: return null
            val item = org.json.JSONObject(raw)
            val lat = item.getDouble("lat")
            val lng = item.getDouble("lng")
            if (lat.isFinite() && lng.isFinite()) PlaceSelection(item.getString("name"), lat, lng)
            else null
        }
        .getOrNull()
