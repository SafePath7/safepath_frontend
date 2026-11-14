package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class SafetyAssessmentTest {
    private val path = listOf(GeoPoint(35.86,128.59),GeoPoint(35.861,128.59))
    private val facility = SafetyFacility(35.8605,128.59)
    private fun data(type: FacilityType, state: DataAvailability = DataAvailability.AVAILABLE,
        facilities: List<SafetyFacility> = listOf(facility)) = FacilityDataset(type,facilities,"test","explicit fixture",availability=state)
    @Test fun allFiveTypesHaveSeparateNormalizedContributions() {
        val result=FiveFacilityEvaluator.evaluate(path,FacilityType.entries.map { data(it) })
        assertEquals(AssessmentStatus.NORMAL,result.status)
        assertTrue(result.score!! in 0.0..100.0)
        assertEquals(5,result.contributions.size)
        assertTrue(result.contributions.all { it.normalizedScore!! in 0.0..1.0 })
    }
    @Test fun policeAndBellProximityContributeWithoutClaimingWalkability() {
        val result=FiveFacilityEvaluator.evaluate(path,FacilityType.entries.map { data(it) })
        assertTrue(result.contributions.first { it.type==FacilityType.POLICE }.normalizedScore!!>0)
        assertTrue(result.contributions.first { it.type==FacilityType.BELL }.normalizedScore!!>0)
        assertTrue(result.contributions.first { it.type==FacilityType.BELL }.method.contains("보행 접근"))
    }
    @Test fun failureIsMissingNotZero() {
        val result=FiveFacilityEvaluator.evaluate(path,listOf(data(FacilityType.CCTV),data(FacilityType.LIGHT,DataAvailability.FAILED)))
        assertEquals(AssessmentStatus.PARTIAL,result.status)
        assertNull(result.contributions.first { it.type==FacilityType.LIGHT }.normalizedScore)
    }
    @Test fun outsideCoverageCannotBeScored() {
        val result=FiveFacilityEvaluator.evaluate(path,FacilityType.entries.map { data(it,DataAvailability.OUTSIDE_COVERAGE) })
        assertEquals(AssessmentStatus.UNAVAILABLE,result.status); assertNull(result.score)
    }
    @Test fun unknownCoverageCannotBeScored() {
        assertNull(FiveFacilityEvaluator.evaluate(path,FacilityType.entries.map { data(it,DataAvailability.UNKNOWN_COVERAGE) }).score)
    }
    @Test fun confirmedEmptyDataReallyCanBeZero() {
        val result=FiveFacilityEvaluator.evaluate(path,FacilityType.entries.map { data(it,facilities=emptyList()) })
        assertEquals(AssessmentStatus.NORMAL,result.status); assertEquals(0.0,result.score!!,0.0)
    }
    @Test fun proportionalDetourLimit() {
        assertTrue(SafetyPolicy.withinDetour(1080.0,900.0)); assertFalse(SafetyPolicy.withinDetour(1081.0,900.0))
    }
    @Test fun absoluteDetourLimit() {
        assertTrue(SafetyPolicy.withinDetour(2700.0,2400.0)); assertFalse(SafetyPolicy.withinDetour(2701.0,2400.0))
    }
    @Test fun equalScorePrefersFasterEligibleRoute() {
        val a=RouteResult("a",100.0,80.0,durationSeconds=100.0)
        val b=RouteResult("b",105.0,80.0,durationSeconds=110.0)
        assertEquals(a,NavigationRepository.selectRoutes(listOf(b,a)).safeRoute)
    }
    @Test fun noDatasetGivesUnavailableNotZero() {
        assertNull(FiveFacilityEvaluator.evaluate(path,emptyList()).score)
    }
    @Test fun saturationBoundsDuplicateContribution() {
        val result=FiveFacilityEvaluator.evaluate(path,listOf(data(FacilityType.CCTV,facilities=List(1000) { facility })))
        assertTrue(result.score!!<=100.0)
    }
}
