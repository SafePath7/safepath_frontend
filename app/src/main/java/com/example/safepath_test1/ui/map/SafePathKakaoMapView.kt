package com.example.safepath_test1.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.safepath_test1.location.SafetyRepository
import com.example.safepath_test1.model.GeoPoint
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import com.kakao.vectormap.route.RouteLineStylesSet
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val SeoulFallback = GeoPoint(37.5665, 126.9780)
private const val MapTag = "SafePathKakaoMapView"
private const val CctvLayerId = "safepath-cctv"
private const val LightLayerId = "safepath-streetlight"
private const val BellLayerId = "safepath-emergency-bell"
private const val FireLayerId = "safepath-fire-stations"
private const val PoliceLayerId = "safepath-police"
private const val LocationLayerId = "safepath-current-location"
private const val DestinationLayerId = "safepath-destination"
private const val FacilityDisplayRadiusMeters = 5_000.0
private const val MaxCctvLabels = 2_000
private const val MaxStreetlightLabels = 4_000

@Composable
fun SafePathKakaoMapView(
    currentLocation: GeoPoint?,
    hasLocationPermission: Boolean,
    recenterToken: Int,
    modifier: Modifier = Modifier,
    showSafetyFacilities: Boolean = true,
    destinationPoint: GeoPoint? = null,
    routeLineGeoJson: String? = null,
    routeLineColor: String = "#5FA8D3",
    onMapClick: ((GeoPoint) -> Unit)? = null,
    alternativeRouteLines: List<Pair<String, String>> = emptyList(),
    remainingRouteGeoJson: String? = null,
    currentMarkerColor: String = "#5FA8D3",
    facilityRetry: Int = 0,
    onFacilityError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnMapClick by rememberUpdatedState(onMapClick)
    val latestCurrentLocation by rememberUpdatedState(currentLocation)
    val latestDestination by rememberUpdatedState(destinationPoint)
    val latestFacilityError by rememberUpdatedState(onFacilityError)
    val mapView = remember(context) { MapView(context) }

    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var hasCentered by remember { mutableStateOf(false) }
    var lastRecenterToken by remember { mutableIntStateOf(recenterToken) }
    var facilityCenter by remember { mutableStateOf(currentLocation ?: SeoulFallback) }
    val facilityLatitudeBucket = (facilityCenter.latitude * 100.0).roundToInt()
    val facilityLongitudeBucket = (facilityCenter.longitude * 100.0).roundToInt()

    // 화면 생명주기에 지도 자원을 맞추고 화면을 떠날 때 리스너와 지도를 해제한다.
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() = Unit

                override fun onMapError(error: Exception) {
                    Log.e(MapTag, "Kakao map failed to start", error)
                }
            },
            object : KakaoMapReadyCallback() {
                override fun getPosition(): LatLng {
                    val center = latestCurrentLocation ?: latestDestination ?: SeoulFallback
                    return LatLng.from(center.latitude, center.longitude)
                }

                override fun getZoomLevel(): Int = 16

                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    map.setOnCameraMoveEndListener { _, position, _ ->
                        facilityCenter = GeoPoint(position.position.latitude, position.position.longitude)
                    }
                    map.setOnMapClickListener { _, position, _, _ ->
                        latestOnMapClick?.invoke(GeoPoint(position.latitude, position.longitude))
                    }
                }
            },
        )
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.pause()
            mapView.finish()
            kakaoMap = null
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    LaunchedEffect(kakaoMap, currentLocation, recenterToken) {
        val map = kakaoMap ?: return@LaunchedEffect
        val location = currentLocation ?: return@LaunchedEffect
        val shouldRecenter = !hasCentered || recenterToken != lastRecenterToken
        if (shouldRecenter) {
            map.moveCamera(
                CameraUpdateFactory.newCenterPosition(
                    LatLng.from(location.latitude, location.longitude),
                    16,
                )
            )
            hasCentered = true
            lastRecenterToken = recenterToken
        }
    }

    LaunchedEffect(kakaoMap, currentLocation, hasLocationPermission, currentMarkerColor) {
        val map = kakaoMap ?: return@LaunchedEffect
        val manager = map.labelManager ?: return@LaunchedEffect
        val layer =
            manager.getLayer(LocationLayerId)
                ?: manager.addLayer(LabelLayerOptions.from(LocationLayerId).setZOrder(7_000))
                ?: return@LaunchedEffect
        layer.removeAll()
        if (hasLocationPermission && currentLocation != null) {
            layer.addLabel(
                LabelOptions.from(LatLng.from(currentLocation.latitude, currentLocation.longitude))
                    .setStyles(createDotBitmap(22, Color.parseColor(currentMarkerColor), 4))
            )
        }
    }

    LaunchedEffect(kakaoMap, destinationPoint) {
        val map = kakaoMap ?: return@LaunchedEffect
        val manager = map.labelManager ?: return@LaunchedEffect
        val layer =
            manager.getLayer(DestinationLayerId)
                ?: manager.addLayer(LabelLayerOptions.from(DestinationLayerId).setZOrder(8_000))
                ?: return@LaunchedEffect
        layer.removeAll()
        destinationPoint?.let { point ->
            layer.addLabel(
                LabelOptions.from(LatLng.from(point.latitude, point.longitude))
                    .setStyles(createDotBitmap(26, Color.parseColor("#1B4965"), 5))
            )
        }
    }

    LaunchedEffect(
        kakaoMap,
        showSafetyFacilities,
        facilityLatitudeBucket,
        facilityLongitudeBucket,
        facilityRetry,
    ) {
        val map = kakaoMap ?: return@LaunchedEffect
        val manager = map.labelManager ?: return@LaunchedEffect
        val cctvLayer =
            manager.getLodLayer(CctvLayerId)
                ?: manager.addLodLayer(
                    LabelLayerOptions.from(CctvLayerId).setLodRadius(55f).setZOrder(5_000)
                )
                ?: return@LaunchedEffect
        val lightLayer =
            manager.getLodLayer(LightLayerId)
                ?: manager.addLodLayer(
                    LabelLayerOptions.from(LightLayerId).setLodRadius(45f).setZOrder(4_000)
                )
                ?: return@LaunchedEffect

        if (!showSafetyFacilities) {
            cctvLayer.setVisible(false)
            lightLayer.setVisible(false)
            return@LaunchedEffect
        }
        cctvLayer.setVisible(true)
        lightLayer.setVisible(true)
        cctvLayer.removeAll()
        lightLayer.removeAll()

        val cctvStyle =
            facilityMarkerStyle(context, "marker_cctv.png")
        val lightStyle =
            facilityMarkerStyle(context, "marker_streetlight.png")
        val (cctvOptions, lightOptions) =
            withContext(Dispatchers.Default) {
                val center = GeoPoint(facilityLatitudeBucket / 100.0, facilityLongitudeBucket / 100.0)
                val cctvs =
                    SafetyRepository.facilitiesNearPoint(
                            try { SafetyRepository.getCctvFacilities(context) } catch (e: java.io.IOException) { Log.w(MapTag, "CCTV 자료 조회 실패", e); withContext(Dispatchers.Main) { latestFacilityError("CCTV 정보를 불러오지 못했어요.") }; emptyList() },
                            center.latitude,
                            center.longitude,
                            FacilityDisplayRadiusMeters,
                            MaxCctvLabels,
                        )
                        .map { facility ->
                            LabelOptions.from(LatLng.from(facility.latitude, facility.longitude))
                                .setStyles(cctvStyle)
                        }
                val lights =
                    SafetyRepository.facilitiesNearPoint(
                            try { SafetyRepository.getStreetlightFacilities(context) } catch (e: java.io.IOException) { Log.w(MapTag, "가로등 자료 조회 실패", e); withContext(Dispatchers.Main) { latestFacilityError("가로등 정보를 불러오지 못했어요.") }; emptyList() },
                            center.latitude,
                            center.longitude,
                            FacilityDisplayRadiusMeters,
                            MaxStreetlightLabels,
                        )
                        .map { facility ->
                            LabelOptions.from(LatLng.from(facility.latitude, facility.longitude))
                                .setStyles(lightStyle)
                        }
                cctvs to lights
            }
        if (cctvOptions.isNotEmpty()) cctvLayer.addLodLabels(cctvOptions)
        if (lightOptions.isNotEmpty()) lightLayer.addLodLabels(lightOptions)
    }

    LaunchedEffect(kakaoMap, showSafetyFacilities, facilityLatitudeBucket, facilityLongitudeBucket, facilityRetry) {
        val manager = kakaoMap?.labelManager ?: return@LaunchedEffect
        val layer = manager.getLodLayer(FireLayerId)
            ?: manager.addLodLayer(LabelLayerOptions.from(FireLayerId).setLodRadius(45f).setZOrder(6_200))
            ?: return@LaunchedEffect
        layer.setVisible(showSafetyFacilities)
        layer.removeAll()
        if (!showSafetyFacilities) return@LaunchedEffect
        val center = GeoPoint(facilityLatitudeBucket / 100.0, facilityLongitudeBucket / 100.0)
        val stations = try { SafetyRepository.getFireStations(context) } catch (e: java.io.IOException) {
            Log.w(MapTag, "소방시설 자료 조회 실패: ${e.message}"); latestFacilityError("소방시설 정보를 불러오지 못했어요."); return@LaunchedEffect
        }
        val style = facilityMarkerStyle(context, "marker_fire_station.png")
        val labels = withContext(Dispatchers.Default) {
            SafetyRepository.facilitiesNearPoint(stations, center.latitude, center.longitude, FacilityDisplayRadiusMeters, 2000)
                .map { LabelOptions.from(LatLng.from(it.latitude, it.longitude)).setStyles(style) }
        }
        if (labels.isNotEmpty()) layer.addLodLabels(labels)
    }

    LaunchedEffect(kakaoMap, showSafetyFacilities, facilityLatitudeBucket, facilityLongitudeBucket, facilityRetry) {
        val manager = kakaoMap?.labelManager ?: return@LaunchedEffect
        val layer = manager.getLodLayer(PoliceLayerId)
            ?: manager.addLodLayer(LabelLayerOptions.from(PoliceLayerId).setLodRadius(45f).setZOrder(6_100))
            ?: return@LaunchedEffect
        layer.setVisible(showSafetyFacilities)
        layer.removeAll()
        if (!showSafetyFacilities) return@LaunchedEffect
        val center = GeoPoint(facilityLatitudeBucket / 100.0, facilityLongitudeBucket / 100.0)
        val police = try { SafetyRepository.getPoliceFacilities(context) } catch (e: java.io.IOException) {
            Log.w(MapTag, "경찰시설 자료 조회 실패", e); latestFacilityError("경찰시설 정보를 불러오지 못했어요."); return@LaunchedEffect
        }
        val style = facilityMarkerStyle(context, "marker_police_station.png")
        val labels = withContext(Dispatchers.Default) {
            SafetyRepository.facilitiesNearPoint(police, center.latitude, center.longitude, FacilityDisplayRadiusMeters, 2000)
                .map { LabelOptions.from(LatLng.from(it.latitude, it.longitude)).setStyles(style) }
        }
        if (labels.isNotEmpty()) layer.addLodLabels(labels)
    }

    LaunchedEffect(kakaoMap, showSafetyFacilities, facilityLatitudeBucket, facilityLongitudeBucket, facilityRetry) {
        val manager = kakaoMap?.labelManager ?: return@LaunchedEffect
        val layer = manager.getLodLayer(BellLayerId)
            ?: manager.addLodLayer(LabelLayerOptions.from(BellLayerId).setLodRadius(45f).setZOrder(6_000))
            ?: return@LaunchedEffect
        layer.setVisible(showSafetyFacilities)
        layer.removeAll()
        if (!showSafetyFacilities) return@LaunchedEffect
        val center = GeoPoint(facilityLatitudeBucket / 100.0, facilityLongitudeBucket / 100.0)
        val bells = try { SafetyRepository.getEmergencyBells(context) } catch (e: java.io.IOException) {
            Log.w(MapTag, "비상벨 자료 조회 실패", e); latestFacilityError("비상벨 정보를 불러오지 못했어요."); return@LaunchedEffect
        }
        val style = facilityMarkerStyle(context, "marker_safety_bell.png")
        val labels = withContext(Dispatchers.Default) {
            SafetyRepository.facilitiesNearPoint(bells, center.latitude, center.longitude, FacilityDisplayRadiusMeters, 2000)
                .map { LabelOptions.from(LatLng.from(it.latitude, it.longitude)).setStyles(style) }
        }
        if (labels.isNotEmpty()) layer.addLodLabels(labels)
    }

    LaunchedEffect(
        kakaoMap,
        routeLineGeoJson,
        routeLineColor,
        alternativeRouteLines,
        remainingRouteGeoJson,
    ) {
        val layer = kakaoMap?.routeLineManager?.layer ?: return@LaunchedEffect
        layer.removeAll()
        val remainingPoints = parseRoutePoints(remainingRouteGeoJson)
        val dashedStyle =
            RouteLineStylesSet.from(
                RouteLineStyles.from(RouteLineStyle.from(5f, Color.parseColor("#5FA8D3")))
            )
        remainingPoints.zipWithNext().forEach { (start, end) ->
            val distance = FloatArray(1)
            android.location.Location.distanceBetween(
                start.latitude,
                start.longitude,
                end.latitude,
                end.longitude,
                distance,
            )
            val steps = kotlin.math.ceil(distance[0] / 10.0).toInt().coerceIn(1, 500)
            for (index in 0 until steps step 2) {
                fun interpolate(fraction: Double) =
                    LatLng.from(
                        start.latitude + (end.latitude - start.latitude) * fraction,
                        start.longitude + (end.longitude - start.longitude) * fraction,
                    )
                val dash =
                    listOf(
                        interpolate(index.toDouble() / steps),
                        interpolate((index + 1).coerceAtMost(steps).toDouble() / steps),
                    )
                layer.addRouteLine(
                    RouteLineOptions.from(RouteLineSegment.from(dash, dashedStyle.getStyles(0)))
                        .setStylesSet(dashedStyle)
                )
            }
        }
        alternativeRouteLines.forEach { (geoJson, hexColor) ->
            val alternativePoints = parseRoutePoints(geoJson)
            if (alternativePoints.size >= 2) {
                val alternativeStyle =
                    RouteLineStyles.from(
                        RouteLineStyle.from(5f, Color.parseColor(hexColor), 2f, Color.WHITE)
                    )
                val alternativeSet = RouteLineStylesSet.from(alternativeStyle)
                layer.addRouteLine(
                    RouteLineOptions.from(
                            RouteLineSegment.from(alternativePoints, alternativeSet.getStyles(0))
                        )
                        .setStylesSet(alternativeSet)
                )
            }
        }
        val points = parseRoutePoints(routeLineGeoJson)
        if (points.size < 2) return@LaunchedEffect
        val color = runCatching { Color.parseColor(routeLineColor) }.getOrDefault(Color.BLUE)
        val style = RouteLineStyles.from(RouteLineStyle.from(7f, color, 4f, Color.WHITE))
        val stylesSet = RouteLineStylesSet.from(style)
        val segment = RouteLineSegment.from(points, stylesSet.getStyles(0))
        layer.addRouteLine(RouteLineOptions.from(segment).setStylesSet(stylesSet))
    }
}

private fun parseRoutePoints(geoJson: String?): List<LatLng> {
    if (geoJson.isNullOrBlank()) return emptyList()
    return try {
        val coordinates = JSONObject(geoJson).optJSONArray("coordinates") ?: return emptyList()
        buildList {
            for (index in 0 until coordinates.length()) {
                val coordinate = coordinates.optJSONArray(index) ?: continue
                val longitude = coordinate.optDouble(0, Double.NaN)
                val latitude = coordinate.optDouble(1, Double.NaN)
                if (latitude.isFinite() && longitude.isFinite())
                    add(LatLng.from(latitude, longitude))
            }
        }
    } catch (exception: Exception) {
        Log.e(MapTag, "Failed to parse route GeoJSON", exception)
        emptyList()
    }
}

private fun facilityMarkerStyle(context: android.content.Context, assetName: String): LabelStyles {
    val bitmap = context.assets.open(assetName).use { stream ->
        requireNotNull(BitmapFactory.decodeStream(stream)) { "Cannot decode facility marker" }
    }
    val scaled = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
    if (scaled !== bitmap) bitmap.recycle()
    return LabelStyles.from(LabelStyle.from(scaled).setAnchorPoint(0.5f, 0.95f).setZoomLevel(14))
}

private fun createDotBitmap(size: Int, fillColor: Int, strokeWidth: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    if (strokeWidth > 0) {
        canvas.drawCircle(
            center,
            center,
            center,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
        )
    }
    canvas.drawCircle(
        center,
        center,
        center - strokeWidth,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor },
    )
    return bitmap
}
