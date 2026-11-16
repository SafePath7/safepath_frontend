package com.example.safepath_test1.location

import com.example.safepath_test1.location.NavigationRepository.recommendedSafetyWeight
import com.example.safepath_test1.location.NavigationRepository.travelSeconds

internal object RouteSelection {
    /** Absolute facility coverage proxy and fastest-time/route-time efficiency; no candidate min-max rescaling. */
    internal fun selectRoutes(candidates: List<RouteResult>): MultiRouteResult {
        if (candidates.isEmpty()) return MultiRouteResult(null, null, null)
        val valid = candidates.filter { it.distanceMeters.isFinite() && it.distanceMeters > 0 && travelSeconds(it).isFinite() && travelSeconds(it) > 0 }
        if(valid.isEmpty()) return MultiRouteResult(null,null,null,errorMessage="유효한 경로가 없습니다.")
        val shortestRoute = valid.minByOrNull { travelSeconds(it) } ?: candidates.first()
        if (candidates.size == 1) {
            return MultiRouteResult(
                safeRoute = shortestRoute,
                shortestRoute = shortestRoute,
                recommendedRoute = shortestRoute,
                candidateRouteCount = 1,
                noticeMessage = "시설 우선, 가장 빠른, 추천 경로가 모두 같습니다.",
            )
        }

        if (candidates.maxOf { it.safetyFacilityScore } <= 0.0) {
            return MultiRouteResult(shortestRoute,shortestRoute,shortestRoute,candidateRouteCount=candidates.size,
                noticeMessage="시설 자료로 경로를 구분할 수 없습니다.")
        }
        val fastestTime = travelSeconds(shortestRoute).coerceAtLeast(1.0)
        fun safety(route: RouteResult) = (route.safetyFacilityScore / 100.0).coerceIn(0.0,1.0)
        fun timeEfficiency(route: RouteResult) = (fastestTime / travelSeconds(route).coerceAtLeast(1.0)).coerceIn(0.0,1.0)

        // 안전은 시설 점수 우선, 추천은 시설 점수와 시간 효율을 각각 50% 반영한다.
        val permitted = valid.filter { SafetyPolicy.withinDetour(travelSeconds(it), fastestTime) && it.assessment?.status != AssessmentStatus.UNAVAILABLE }
        val safeRoute =
            permitted.sortedWith(compareByDescending<RouteResult> { it.safetyFacilityScore }.thenBy { travelSeconds(it) }).firstOrNull() ?: shortestRoute
        fun recommendedScore(route: RouteResult) =
            safety(route) * recommendedSafetyWeight + timeEfficiency(route) * (1.0 - recommendedSafetyWeight)
        val bestRecommended = permitted.maxByOrNull(::recommendedScore)
        // Prefer a distinct, comparable alternative only when it stays within five points.
        val distinctRecommended = permitted.filter {
            it.geoJsonLineString != safeRoute.geoJsonLineString && it.geoJsonLineString != shortestRoute.geoJsonLineString
        }.maxByOrNull(::recommendedScore)
        val recommendedRoute = if (distinctRecommended != null && bestRecommended != null &&
            recommendedScore(bestRecommended) - recommendedScore(distinctRecommended) <= 0.05)
            distinctRecommended else bestRecommended ?: shortestRoute
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

}
