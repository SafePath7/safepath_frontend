package com.example.safepath_test1.location

import android.content.Context
import android.util.Log
import com.example.safepath_test1.api.AuthenticatedBackend
import com.example.safepath_test1.api.RouteCoordinate
import com.example.safepath_test1.api.RouteSearchApi
import com.example.safepath_test1.api.RouteSearchBody
import com.example.safepath_test1.model.GeoPoint
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

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
    val serverSafetyScore: Double? = null,
    val observedFraction: Double? = null,
    val safetyFactors: Map<String, Int> = emptyMap(),
) {
    val availableSafetyScore: Double? get() = serverSafetyScore ?: assessment?.score
}

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

    suspend fun fetchMultiRoutes(
        context: Context, originLat: Double, originLng: Double,
        destLat: Double, destLng: Double,
    ): MultiRouteResult = try {
        // 서버에서 여러 경로 후보를 조회하므로 응답에 충분한 시간을 둔다.
        val backend = AuthenticatedBackend(context, readTimeoutSeconds = 65)
        val api = backend.createApi(RouteSearchApi::class.java)
        val response = backend.request {
            api.routes(it, RouteSearchBody(RouteCoordinate(originLat, originLng), RouteCoordinate(destLat, destLng)))
        }
        mapServerRoutes(response)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Log.w("RouteSearch", "Route API HTTP " + e.code())
        val message = when (e.code()) {
            400 -> "출발지와 목적지를 확인해 주세요."
            401, 403 -> "로그인 상태와 접근 권한을 확인해 주세요."
            422 -> "연결되는 보행 경로를 찾지 못했어요. 출발지와 목적지를 확인해 주세요."
            502, 503, 504 -> "경로 서버에 문제가 있어요. 잠시 후 다시 시도해 주세요."
            else -> "경로를 불러오지 못했어요. 잠시 후 다시 시도해 주세요. (HTTP ${e.code()})"
        }
        MultiRouteResult(null, null, null, errorMessage = message)
    } catch (e: IOException) {
        Log.w("RouteSearch", "Route request failed: " + e.javaClass.simpleName)
        MultiRouteResult(null, null, null, errorMessage = "경로 서버에 연결하지 못했어요. 네트워크와 서버 연결을 확인해 주세요.")
    } catch (e: Exception) {
        Log.w("RouteSearch", "Route response or configuration failed: " + e.javaClass.simpleName)
        MultiRouteResult(null, null, null, errorMessage = "경로를 불러오지 못했어요. 로그인과 서버 설정을 확인해 주세요.")
    }

    internal fun mapServerRoutes(response: com.example.safepath_test1.api.ServerRoutes): MultiRouteResult {
        val byType = mutableMapOf<String, RouteResult>()
        for (route in response.routes) {
            val points = route.coordinates.map { GeoPoint(it.latitude, it.longitude) }
            if (points.size < 2 || !route.distanceMeters.isFinite() || route.distanceMeters <= 0 ||
                !route.durationSeconds.isFinite() || route.durationSeconds <= 0 ||
                points.any { !it.latitude.isFinite() || !it.longitude.isFinite() ||
                    it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 }) continue
            val coordinates = JSONArray()
            points.forEach { coordinates.put(JSONArray().put(it.longitude).put(it.latitude)) }
            val score = route.safetyScore?.takeIf { it.isFinite() && it in 0.0..100.0 }
            val result = RouteResult(
                geoJsonLineString = JSONObject().put("type", "LineString").put("coordinates", coordinates).toString(),
                distanceMeters = route.distanceMeters, safetyFacilityScore = score ?: 0.0,
                durationSeconds = route.durationSeconds, geometryPoints = points,
                serverSafetyScore = score,
                observedFraction = route.observedFraction.takeIf { it.isFinite() && it in 0.0..1.0 },
                safetyFactors = route.safetyFactors,
                cctvCount = route.safetyFactors["CCTV"] ?: 0,
                streetlightCount = route.safetyFactors["SECURITY_LIGHT"] ?: 0,
            )
            for (type in listOf(route.type) + route.sameAsTypes) byType.putIfAbsent(type, result)
        }
        return if (byType.keys.containsAll(listOf("SAFEST", "FASTEST", "RECOMMENDED"))) {
            MultiRouteResult(byType["SAFEST"], byType["FASTEST"], byType["RECOMMENDED"], candidateRouteCount = response.candidateCount)
        } else {
            Log.w("RouteSearch", "Route response missing required route types")
            MultiRouteResult(null, null, null, errorMessage = "서버에서 경로 정보를 완전히 받지 못했어요. 다시 시도해 주세요.")
        }
    }

    internal fun selectRoutes(candidates: List<RouteResult>): MultiRouteResult =
        RouteSelection.selectRoutes(candidates)

    // API 시간이 없으면 보행 속도 1.2m/s로 추정한다. UI에는 추정임을 표시한다.
    internal fun travelSeconds(route: RouteResult): Double =
        route.durationSeconds?.takeIf { it.isFinite() && it > 0 } ?: (route.distanceMeters / 1.2)

}
