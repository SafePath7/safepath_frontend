package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RouteSearchEngineTest {
    private fun p(x: Double,y: Double=0.0)=GeoPoint(37+y/111320,127+x/(111320*kotlin.math.cos(Math.toRadians(37.0))))
    private fun route(points: List<GeoPoint>,name: String)=RouteResult(name,
        points.zipWithNext().sumOf { (a,b)->FacilityRoutePlanner.distance(a,b) },durationSeconds=200.0,geometryPoints=points)
    private val cameras=(1..9).flatMap { x->List(10) { p(x*20.0,80.0).let { SafetyFacility(it.latitude,it.longitude) } } }
    @Test fun basicWalkingRouteSurvivesWhenReturnIsRequired() = runBlocking {
        val base=route(listOf(p(0.0),p(100.0),p(20.0),p(20.0,100.0)),"culdesac")
        val result=RouteSearchEngine(WalkingRouteSource { _,_->RouteFetch(listOf(base)) }).search(base.geometryPoints.first(),base.geometryPoints.last()) { emptyList<SafetyFacility>() to emptyList() }
        assertNotNull(result.shortestRoute)
        assertTrue(result.shortestRoute!!.routeWarnings.contains("왕복 구간 포함"))
    }
    @Test fun addedRetracingDetourIsExcludedWhileBaseRouteRemains() = runBlocking {
        val base=route(listOf(p(0.0),p(200.0)),"base")
        val bad=route(listOf(p(0.0),p(150.0),p(20.0),p(200.0)),"backtrack")
        val result=RouteSearchEngine(WalkingRouteSource { _,alternatives->RouteFetch(listOf(if(alternatives) base else bad)) })
            .search(p(0.0),p(200.0)) { cameras to emptyList() }
        assertEquals("base",result.safeRoute?.geoJsonLineString)
        assertTrue(result.diagnostics!!.rejectedDetours>0)
    }
    @Test fun cleanAlternativeReplacesReturningBaselineWhenAvailable() = runBlocking {
        val returning=route(listOf(p(0.0),p(150.0),p(20.0),p(200.0)),"return")
        val clean=route(listOf(p(0.0),p(80.0,80.0),p(200.0)),"clean")
        val result=RouteSearchEngine(WalkingRouteSource { _,alternatives -> RouteFetch(listOf(if(alternatives) returning else clean)) })
            .search(p(0.0),p(200.0)) { cameras to emptyList() }
        assertEquals("clean",result.shortestRoute?.geoJsonLineString)
    }
    @Test fun refinementTimeoutReturnsValidatedBaseline() = runBlocking {
        val base=route(listOf(p(0.0),p(200.0)),"base")
        val result=RouteSearchEngine(WalkingRouteSource { _,alternatives->
            if(!alternatives) delay(10000)
            RouteFetch(listOf(base))
        },25).search(p(0.0),p(200.0)) { cameras to emptyList() }
        assertNotNull(result.shortestRoute)
        assertTrue(result.diagnostics!!.refinementTimedOut)
    }
    @Test fun noSnapReturnsActionableErrorRatherThanFalseRoute() = runBlocking {
        val result=RouteSearchEngine(WalkingRouteSource { _,_->RouteFetch(emptyList(),"NoSegment") })
            .search(p(0.0),p(200.0)) { cameras to emptyList() }
        assertNull(result.safeRoute); assertTrue(result.errorMessage!!.contains("보행 도로"))
    }
    @Test fun overallDeadlinePreservesValidatedRouteDuringSlowFacilityLoad() = runBlocking {
        val base=route(listOf(p(0.0),p(200.0)),"base")
        val result=RouteSearchEngine(WalkingRouteSource { _,_->RouteFetch(listOf(base)) },overallBudgetMillis=100)
            .search(p(0.0),p(200.0)) { delay(10000); cameras to emptyList() }
        assertEquals("base",result.shortestRoute?.geoJsonLineString)
        assertTrue(result.diagnostics!!.refinementTimedOut)
        assertTrue(result.shortestRoute!!.routeWarnings.contains("시설 분석 미완료"))
    }
    @Test fun callerCancellationPropagates() = runBlocking {
        var cancelled=false
        val source=WalkingRouteSource { _,_->try { delay(10000); RouteFetch(emptyList()) } finally { cancelled=true } }
        val job=launch { RouteSearchEngine(source).search(p(0.0),p(200.0)) { cameras to emptyList() } }
        delay(20); job.cancelAndJoin(); assertTrue(cancelled)
    }
}
