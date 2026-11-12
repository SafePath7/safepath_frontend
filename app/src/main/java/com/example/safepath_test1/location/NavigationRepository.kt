package com.example.safepath_test1.location

import android.content.Context
import android.util.Log
import com.example.safepath_test1.model.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 경로 위의 회전 지점 하나. Mapbox Directions 응답(steps=true)의 step.maneuver에서 꺼낸다.
 * [type]은 "turn", "depart", "arrive" 등, [modifier]는 "left", "slight right", "uturn" 등(없을 수 있음).
 * 워치의 회전 안내("120m 후 우회전")에 쓴다.
 */
data class RouteManeuver(val point: GeoPoint, val type: String, val modifier: String?)

data class RouteResult(
    val geoJsonLineString: String,
    val distanceMeters: Double,
    val safetyFacilityScore: Double = 0.0,
    /** 회전 지점 목록(경로 순서). 응답에 없으면 비어 있다. */
    val maneuvers: List<RouteManeuver> = emptyList(),
    /** 걸어서 걸리는 예상 시간(초). 응답에 없으면 null. */
    val durationSeconds: Double? = null,
)

data class MultiRouteResult(
    val safeRoute: RouteResult?,
    val shortestRoute: RouteResult?,
    val recommendedRoute: RouteResult?,
    val errorMessage: String? = null,
    val candidateRouteCount: Int = 0,
    val noticeMessage: String? = null,
)

object NavigationRepository {
    private const val tag = "NavigationRepository"

    suspend fun fetchMultiRoutes(
        context: Context,
        accessToken: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): MultiRouteResult =
        withContext(Dispatchers.IO) {
            if (accessToken.isBlank()) {
                Log.e(tag, "Mapbox access token is blank")
                return@withContext MultiRouteResult(
                    null,
                    null,
                    null,
                    errorMessage = "지도 서비스 설정을 확인해 주세요.",
                )
            }
            val candidates =
                fetchWalkingRoutes(accessToken, originLat, originLng, destLat, destLng).distinctBy {
                    it.geoJsonLineString
                }
            val shortestRoute =
                candidates.minByOrNull { it.distanceMeters }
                    ?: return@withContext MultiRouteResult(
                        null,
                        null,
                        null,
                        errorMessage = "경로를 찾지 못했습니다. 네트워크와 위치를 확인해 주세요.",
                    )
            val scoredCandidates =
                try {
                    candidates.map {
                        it.copy(
                            safetyFacilityScore =
                                SafetyRepository.scoreRouteFacilities(
                                    context = context,
                                    routeGeoJson = it.geoJsonLineString,
                                    routeDistanceMeters = it.distanceMeters,
                                )
                        )
                    }
                } catch (exception: Exception) {
                    Log.e(
                        tag,
                        "Failed to score walking routes with safety facilities; using shortest route",
                        exception,
                    )
                    return@withContext MultiRouteResult(
                        safeRoute = shortestRoute,
                        shortestRoute = shortestRoute,
                        recommendedRoute = shortestRoute,
                        candidateRouteCount = candidates.size,
                        noticeMessage = "안전시설 분석에 실패해 최단 경로를 공통으로 표시합니다.",
                    )
                }
            selectRoutes(scoredCandidates)
        }

    /**
     * 시설 밀도와 우회 정도를 비교 가능한 0~1 범위로 정규화해 경로를 선택한다.
     * 원래 시설 밀도가 고정 우회 점수보다 지나치게 커 두 추천 결과가 같아지는 문제를 방지한다.
     */
    internal fun selectRoutes(candidates: List<RouteResult>): MultiRouteResult {
        if (candidates.isEmpty()) return MultiRouteResult(null, null, null)
        val shortestRoute = candidates.minByOrNull { it.distanceMeters } ?: candidates.first()
        if (candidates.size == 1) {
            return MultiRouteResult(
                safeRoute = shortestRoute,
                shortestRoute = shortestRoute,
                recommendedRoute = shortestRoute,
                candidateRouteCount = 1,
                noticeMessage = "안전, 최단, 추천 경로가 모두 같습니다.",
            )
        }

        val minSafety = candidates.minOf { it.safetyFacilityScore }
        val maxSafety = candidates.maxOf { it.safetyFacilityScore }
        if (maxSafety <= 0.0) {
            return MultiRouteResult(
                safeRoute = shortestRoute,
                shortestRoute = shortestRoute,
                recommendedRoute = shortestRoute,
                candidateRouteCount = candidates.size,
                noticeMessage = "후보 경로 주변의 안전시설 자료가 없어 최단 경로를 공통으로 표시합니다.",
            )
        }

        val minDistance = candidates.minOf { it.distanceMeters }
        val maxDistance = candidates.maxOf { it.distanceMeters }
        fun normalized(value: Double, min: Double, max: Double): Double =
            if (max <= min) 1.0 else ((value - min) / (max - min)).coerceIn(0.0, 1.0)
        fun safety(route: RouteResult) = normalized(route.safetyFacilityScore, minSafety, maxSafety)
        fun distance(route: RouteResult) =
            1.0 - normalized(route.distanceMeters, minDistance, maxDistance)

        // 안전 경로는 시설 밀도를 우선하고 추천 경로는 안전과 거리를 함께 고려한다.
        // 최단 경로는 거리만 기준으로 선택한다.
        val safeRoute =
            candidates.maxByOrNull { safety(it) * 0.8 + distance(it) * 0.2 } ?: shortestRoute
        val recommendedRoute =
            candidates.maxByOrNull { safety(it) * 0.5 + distance(it) * 0.5 } ?: shortestRoute
        val selectedGeometryCount =
            listOf(safeRoute, shortestRoute, recommendedRoute)
                .distinctBy { it.geoJsonLineString }
                .size
        val notice =
            when (selectedGeometryCount) {
                1 -> "후보 경로를 비교했지만 현재 거리·안전도 기준에서는 세 옵션의 최적 경로가 같습니다."
                2 -> "일부 옵션은 평가 결과가 같아 동일한 경로로 표시됩니다."
                else -> null
            }
        return MultiRouteResult(
            safeRoute = safeRoute,
            shortestRoute = shortestRoute,
            recommendedRoute = recommendedRoute,
            candidateRouteCount = candidates.size,
            noticeMessage = notice,
        )
    }

    private fun fetchWalkingRoutes(
        accessToken: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): List<RouteResult> {
        var connection: HttpURLConnection? = null
        return try {
            val url =
                "https://api.mapbox.com/directions/v5/mapbox/walking/$originLng,$originLat;$destLng,$destLat?alternatives=true&geometries=geojson&overview=full&steps=true&access_token=$accessToken"
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(tag, "Mapbox walking route request failed: HTTP ${connection.responseCode}")
                emptyList()
            } else {
                val routes =
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        .optJSONArray("routes")
                buildList {
                    if (routes != null)
                        for (index in 0 until routes.length()) {
                            val route = routes.optJSONObject(index) ?: continue
                            val geometry = route.optJSONObject("geometry")?.toString().orEmpty()
                            val distance = route.optDouble("distance", Double.NaN)
                            if (geometry.isNotBlank() && distance.isFinite() && distance >= 0.0)
                                add(
                                    RouteResult(
                                        geoJsonLineString = geometry,
                                        distanceMeters = distance,
                                        maneuvers = parseManeuvers(route),
                                        durationSeconds =
                                            route.optDouble("duration", Double.NaN).takeIf {
                                                it.isFinite() && it >= 0.0
                                            },
                                    )
                                )
                        }
                }
            }
        } catch (exception: Exception) {
            Log.e(tag, "Mapbox walking route network or response parsing failed", exception)
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 경로 하나의 legs[].steps[].maneuver에서 회전 지점을 순서대로 꺼낸다.
     * 형식이 예상과 다르면 그 단계만 건너뛰고, 아예 없으면 빈 목록이라 워치는 예전처럼 경로 모양으로 추측한다.
     */
    private fun parseManeuvers(route: JSONObject): List<RouteManeuver> {
        val legs = route.optJSONArray("legs") ?: return emptyList()
        return buildList {
            for (legIndex in 0 until legs.length()) {
                val steps = legs.optJSONObject(legIndex)?.optJSONArray("steps") ?: continue
                for (stepIndex in 0 until steps.length()) {
                    val maneuver = steps.optJSONObject(stepIndex)?.optJSONObject("maneuver") ?: continue
                    val location = maneuver.optJSONArray("location") ?: continue
                    // Mapbox 좌표는 [경도, 위도] 순서다.
                    val longitude = location.optDouble(0, Double.NaN)
                    val latitude = location.optDouble(1, Double.NaN)
                    val type = maneuver.optString("type")
                    if (!latitude.isFinite() || !longitude.isFinite() || type.isBlank()) continue
                    add(
                        RouteManeuver(
                            point = GeoPoint(latitude, longitude),
                            type = type,
                            modifier = maneuver.optString("modifier").ifBlank { null },
                        )
                    )
                }
            }
        }
    }
}
