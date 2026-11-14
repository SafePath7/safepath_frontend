package com.example.safepath_test1.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavigationRepositoryTest {
    @Test
    fun threeCandidates_selectSafeBalancedAndShortestRoutes() {
        val shortest = RouteResult("shortest", distanceMeters = 100.0, safetyFacilityScore = 0.0)
        val balanced = RouteResult("balanced", distanceMeters = 110.0, safetyFacilityScore = 95.0)
        val safest = RouteResult("safest", distanceMeters = 130.0, safetyFacilityScore = 100.0)

        val result = NavigationRepository.selectRoutes(listOf(shortest, balanced, safest))

        assertEquals(balanced, result.safeRoute)
        assertEquals(shortest, result.shortestRoute)
        assertEquals(balanced, result.recommendedRoute)
        assertEquals(3, result.candidateRouteCount)
        assertNotNull(result.noticeMessage)
    }

    @Test
    fun oneCandidate_reportsWhyAllOptionsAreIdentical() {
        val onlyRoute = RouteResult("only", distanceMeters = 100.0, safetyFacilityScore = 10.0)

        val result = NavigationRepository.selectRoutes(listOf(onlyRoute))

        assertEquals(onlyRoute, result.safeRoute)
        assertEquals(onlyRoute, result.shortestRoute)
        assertEquals(onlyRoute, result.recommendedRoute)
        assertEquals(1, result.candidateRouteCount)
        assertNotNull(result.noticeMessage)
    }

    @Test
    fun missingSafetyCoverage_usesShortestAndExplainsFallback() {
        val shortest = RouteResult("shortest", distanceMeters = 100.0, safetyFacilityScore = 0.0)
        val alternative =
            RouteResult("alternative", distanceMeters = 120.0, safetyFacilityScore = 0.0)

        val result = NavigationRepository.selectRoutes(listOf(alternative, shortest))

        assertEquals(shortest, result.safeRoute)
        assertEquals(shortest, result.shortestRoute)
        assertEquals(shortest, result.recommendedRoute)
        assertNotNull(result.noticeMessage)
    }

    @Test fun fastestUsesDurationRatherThanDistance() {
        val shorter = RouteResult("short", 100.0, durationSeconds = 200.0)
        val faster = RouteResult("fast", 150.0, durationSeconds = 100.0)
        assertEquals(faster, NavigationRepository.selectRoutes(listOf(shorter, faster)).shortestRoute)
    }
    @Test fun safestNeverTradesSafetyForTime() {
        val faster = RouteResult("fast", 100.0, 99.0, durationSeconds = 100.0)
        val safer = RouteResult("safe", 1000.0, 100.0, durationSeconds = 1000.0)
        assertEquals(faster, NavigationRepository.selectRoutes(listOf(faster, safer)).safeRoute)
    }
    @Test fun missingDurationUsesWalkingEstimate() {
        assertEquals(100.0, NavigationRepository.travelSeconds(RouteResult("x", 120.0)), 0.001)
    }

    @Test fun recommendationUses65PercentSafetyInsteadOfEqualWeights() {
        val fastest = RouteResult("fast", 100.0, 0.0, durationSeconds = 100.0)
        val middle = RouteResult("middle", 110.0, 50.0, durationSeconds = 110.0)
        val safest = RouteResult("safe", 130.0, 100.0, durationSeconds = 130.0)
        // Absolute safety / 100 and fastest-time / route-time retain safety 65 : time 35.
        val result = NavigationRepository.selectRoutes(listOf(fastest, middle, safest))
        assertEquals(middle, result.recommendedRoute)
        assertEquals(fastest, result.shortestRoute)
        assertEquals(middle, result.safeRoute)
    }

    @Test fun addingDominatedCandidateDoesNotRenormalizeSafetyScale() {
        val fast = RouteResult("fast",100.0,20.0,durationSeconds=100.0)
        val safer = RouteResult("safer",200.0,80.0,durationSeconds=200.0)
        val dominated = RouteResult("dominated",1000.0,0.0,durationSeconds=1000.0)
        assertEquals(fast,NavigationRepository.selectRoutes(listOf(fast,safer)).recommendedRoute)
        assertEquals(fast,NavigationRepository.selectRoutes(listOf(fast,safer,dominated)).recommendedRoute)
    }
}
