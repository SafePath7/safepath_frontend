package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class RouteSafetyEvaluatorTest {
    private fun p(x: Double)=GeoPoint(37.0,127.0+x/(111320*kotlin.math.cos(Math.toRadians(37.0))))
    private fun facility(x: Double)=p(x).let { SafetyFacility(it.latitude,it.longitude) }
    @Test fun evenlyCoveredRouteBeatsDenseClusterWithLongDarkStretch() {
        val path=listOf(p(0.0),p(1000.0))
        val cluster=RouteSafetyEvaluator.evaluate(path,List(30) { facility(it.toDouble()) },emptyList())
        val spread=RouteSafetyEvaluator.evaluate(path,(0..13).map { facility(it*75.0) },emptyList())
        assertTrue(spread.score>cluster.score)
        assertTrue(cluster.longestUncoveredMeters>850)
        assertTrue(spread.coveredRatio>0.95)
    }
    @Test fun moreCamerasAtSameSpotCannotInflateCoverageScore() {
        val path=listOf(p(0.0),p(1000.0))
        val one=RouteSafetyEvaluator.evaluate(path,listOf(facility(50.0)),emptyList())
        val hundred=RouteSafetyEvaluator.evaluate(path,List(100) { facility(50.0) },emptyList())
        assertEquals(one.score,hundred.score,0.0001)
    }
    @Test fun absentFacilitiesAreUnknownCoverageNotSafety() {
        val m=RouteSafetyEvaluator.evaluate(listOf(p(0.0),p(1000.0)),emptyList(),emptyList())
        assertEquals(0.0,m.score,0.0); assertEquals(1000.0,m.longestUncoveredMeters,0.1)
        assertEquals(1000.0,m.uncoveredMeters,0.1)
    }
    @Test fun vertexSpacingDoesNotDistortLengthWeightedCoverage() {
        val cameras=(0..10).map { facility(it*90.0) }
        val a=RouteSafetyEvaluator.evaluate(listOf(p(0.0),p(1000.0)),cameras,emptyList())
        val b=RouteSafetyEvaluator.evaluate((0..10).map { p(it*100.0) },cameras,emptyList())
        assertEquals(a.score,b.score,0.01); assertEquals(a.longestUncoveredMeters,b.longestUncoveredMeters,0.01)
    }
}
