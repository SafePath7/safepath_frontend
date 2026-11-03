package com.example.safepath_test1.ui.map

import android.graphics.Bitmap
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
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnMapClick by rememberUpdatedState(onMapClick)
    val latestCurrentLocation by rememberUpdatedState(currentLocation)
    val mapView = remember(context) { MapView(context) }

    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var hasCentered by remember { mutableStateOf(false) }
    var lastRecenterToken by remember { mutableIntStateOf(recenterToken) }
    val facilityLatitudeBucket = currentLocation?.latitude?.times(100.0)?.roundToInt()
    val facilityLongitudeBucket = currentLocation?.longitude?.times(100.0)?.roundToInt()

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
                    val center = latestCurrentLocation ?: SeoulFallback
                    return LatLng.from(center.latitude, center.longitude)
                }

                override fun getZoomLevel(): Int = 16

                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
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
            LabelStyles.from(LabelStyle.from(createFacilityBitmap(camera = true)).setZoomLevel(14))
        val lightStyle =
            LabelStyles.from(LabelStyle.from(createFacilityBitmap(camera = false)).setZoomLevel(14))
        val (cctvOptions, lightOptions) =
            withContext(Dispatchers.Default) {
                val center =
                    if (facilityLatitudeBucket != null && facilityLongitudeBucket != null) {
                        GeoPoint(facilityLatitudeBucket / 100.0, facilityLongitudeBucket / 100.0)
                    } else {
                        SeoulFallback
                    }
                val cctvs =
                    SafetyRepository.facilitiesNearPoint(
                            SafetyRepository.getCctvFacilities(context),
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
                            SafetyRepository.getStreetlightFacilities(context),
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

private fun createFacilityBitmap(camera: Boolean): Bitmap {
    val bitmap = Bitmap.createBitmap(36, 36, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val blue = Color.parseColor("#5FA8D3")
    paint.color = Color.WHITE
    canvas.drawCircle(18f, 18f, 16f, paint)
    paint.color = blue
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawCircle(18f, 18f, 15f, paint)
    if (camera) {
        canvas.drawRoundRect(9f, 12f, 22f, 24f, 2f, 2f, paint)
        val lens =
            android.graphics.Path().apply {
                moveTo(22f, 16f)
                lineTo(28f, 12f)
                lineTo(28f, 24f)
                lineTo(22f, 20f)
                close()
            }
        canvas.drawPath(lens, paint)
    } else {
        canvas.drawLine(18f, 12f, 18f, 27f, paint)
        canvas.drawLine(12f, 27f, 24f, 27f, paint)
        canvas.drawArc(10f, 9f, 26f, 22f, 180f, 180f, false, paint)
        canvas.drawLine(10f, 16f, 26f, 16f, paint)
    }
    return bitmap
}
