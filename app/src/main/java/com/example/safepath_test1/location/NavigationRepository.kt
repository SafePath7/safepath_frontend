package com.example.safepath_test1.location

import android.content.Context
import com.example.safepath_test1.api.AuthenticatedBackend
import com.example.safepath_test1.api.RouteCoordinate
import com.example.safepath_test1.api.RouteSearchApi
import com.example.safepath_test1.api.RouteSearchBody
import com.example.safepath_test1.model.GeoPoint
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val cctvCount: Int = 0,
    val streetlightCount: Int = 0,
    /** 회전 지점 목록(경로 순서). 응답에 없으면 비어 있다. */
    val maneuvers: List<RouteManeuver> = emptyList(),
    /** 걸어서 걸리는 예상 시간(초). 응답에 없으면 null. */
    val durationSeconds: Double? = null,
    val geometryPoints: List<GeoPoint> = emptyList(),
    val snapDistancesMeters: List<Double> = emptyList(),
    val safetyMetrics: RouteSafetyMetrics? = null,
    val routeWarnings: List<String> = emptyList(),
    val assessment: SafetyAssessment? = null,
)

data class MultiRouteResult(
    val safeRoute: RouteResult?,
    val shortestRoute: RouteResult?,
    val recommendedRoute: RouteResult?,
    val errorMessage: String? = null,
    val candidateRouteCount: Int = 0,
    val noticeMessage: String? = null,
    val diagnostics: RouteSearchDiagnostics? = null,
) {
    internal fun duplicateRouteLabel(): String? =
        listOf("추천" to recommendedRoute, "안전" to safeRoute, "가장 빠른" to shortestRoute)
            .mapNotNull { (name, route) -> route?.geoJsonLineString?.takeIf { it.isNotBlank() }?.let { name to it } }
            .groupBy({ it.second }, { it.first })
            .values.filter { it.size > 1 }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" / ") { "${it.joinToString("·")} 동일" }
}

object NavigationRepository {
    internal const val recommendedSafetyWeight = 0.50

    @Volatile private var routingClient: Pair<String, MapboxWalkingClient>? = null

    suspend fun fetchMultiRoutes(
        context: Context, accessToken: String, originLat: Double, originLng: Double,
        destLat: Double, destLng: Double,
    ): MultiRouteResult {
        val serverResult = try {
            val backend = AuthenticatedBackend(context, readTimeoutSeconds = 12)
            val api = backend.createApi(RouteSearchApi::class.java)
            val response = backend.request {
                api.routes(it, RouteSearchBody(RouteCoordinate(originLat, originLng), RouteCoordinate(destLat, destLng)))
            }
            val byType = buildMap {
                for (route in response.routes) {
                    val points = route.coordinates.map { GeoPoint(it.latitude, it.longitude) }
                    if (points.size < 2 || !route.distanceMeters.isFinite() || route.distanceMeters <= 0 ||
                        points.any { it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 }) continue
                    val coordinates = JSONArray()
                    points.forEach { coordinates.put(JSONArray().put(it.longitude).put(it.latitude)) }
                    val result = RouteResult(
                        geoJsonLineString = JSONObject().put("type", "LineString").put("coordinates", coordinates).toString(),
                        distanceMeters = route.distanceMeters,
                        safetyFacilityScore = route.safetyScore ?: 0.0,
                        durationSeconds = route.durationSeconds,
                        geometryPoints = points,
                    )
                    for (type in listOf(route.type) + route.sameAsTypes) putIfAbsent(type, result)
                }
            }
            if (byType.keys.containsAll(listOf("SAFEST", "FASTEST", "RECOMMENDED"))) {
                MultiRouteResult(byType["SAFEST"], byType["FASTEST"], byType["RECOMMENDED"], candidateRouteCount = response.candidateCount)
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (serverResult != null) return serverResult
        if (accessToken.isBlank()) return MultiRouteResult(null,null,null,errorMessage="지도 서비스 설정을 확인해 주세요.")
        val source = synchronized(this) {
            routingClient?.takeIf { it.first == accessToken }?.second
                ?: MapboxWalkingClient(accessToken).also { routingClient = accessToken to it }
        }
        SafetyRepository.warmAssessmentDatasets(context.applicationContext)
        return RouteSearchEngine(source, datasetLoader = {
            repeat(8) {
                val snapshot = SafetyRepository.assessmentSnapshot()
                if (snapshot.any { (it.type == FacilityType.CCTV || it.type == FacilityType.LIGHT) && it.availability == DataAvailability.AVAILABLE })
                    return@RouteSearchEngine snapshot
                kotlinx.coroutines.delay(100)
            }
            SafetyRepository.assessmentSnapshot()
        }).search(GeoPoint(originLat,originLng),GeoPoint(destLat,destLng)) {
            withContext(Dispatchers.IO) {
                SafetyRepository.getCctvFacilities(context) to SafetyRepository.getStreetlightFacilities(context)
            }
        }
    }

    internal fun selectRoutes(candidates: List<RouteResult>): MultiRouteResult =
        RouteSelection.selectRoutes(candidates)

    // API 시간이 없으면 보행 속도 1.2m/s로 추정한다. UI에는 추정임을 표시한다.
    internal fun travelSeconds(route: RouteResult): Double =
        route.durationSeconds?.takeIf { it.isFinite() && it > 0 } ?: (route.distanceMeters / 1.2)

}
