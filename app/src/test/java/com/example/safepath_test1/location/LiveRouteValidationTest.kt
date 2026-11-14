package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.Properties

/** Explicitly opt-in. Uses public landmark-adjacent coordinates, never the user's GPS. */
class LiveRouteValidationTest {
    @Test fun validatePublicWalkingCases() = runBlocking {
        assumeTrue("Live API checks require explicit opt-in",System.getenv("SAFEPATH_LIVE_VALIDATION")=="1")
        val root=generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .first { File(it,"settings.gradle.kts").isFile }
        val props=Properties().apply { File(root,"local.properties").inputStream().use { load(it) } }
        val token=props.getProperty("MAPBOX_ACCESS_TOKEN","").trim().trim('"')
        require(token.isNotBlank()) { "Mapbox token not configured" }
        fun facilities(name: String,offset: Int)=File(root,"app/src/main/assets/$name").useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val p=line.split(','); val lat=p.getOrNull(offset)?.toDoubleOrNull(); val lng=p.getOrNull(offset+1)?.toDoubleOrNull()
                if(lat!=null && lng!=null) SafetyFacility(lat,lng) else null
            }.toList()
        }
        val cameras=facilities("cctv.csv",0); val lights=facilities("streetlight.csv",1)
        data class Case(val name: String,val a: GeoPoint,val b: GeoPoint)
        val cases=listOf(
            Case("seoul_cityhall_palace",GeoPoint(37.5663,126.9781),GeoPoint(37.5657,126.9752)),
            Case("seoul_stream",GeoPoint(37.5690,126.9784),GeoPoint(37.5697,126.9876)),
            Case("seoul_station_market",GeoPoint(37.5565,126.9722),GeoPoint(37.5590,126.9770)),
            Case("seoul_bridge",GeoPoint(37.5092,126.9953),GeoPoint(37.5191,126.9950)),
            Case("seoul_park_riverside",GeoPoint(37.5266,126.9243),GeoPoint(37.5295,126.9327)),
            Case("daegu_downtown",GeoPoint(35.8692,128.5940),GeoPoint(35.8666,128.5933)),
            Case("daegu_campus",GeoPoint(35.8552,128.4882),GeoPoint(35.8532,128.4930)),
            Case("daegu_park",GeoPoint(35.8530,128.5592),GeoPoint(35.8493,128.5637)),
            Case("busan_downtown",GeoPoint(35.1577,129.0592),GeoPoint(35.1537,129.0594)),
            Case("gwangju_downtown",GeoPoint(35.1498,126.9192),GeoPoint(35.1466,126.9222)),
            Case("daejeon_market",GeoPoint(36.3322,127.4310),GeoPoint(36.3282,127.4272)),
            Case("jeju_downtown",GeoPoint(33.5113,126.5244),GeoPoint(33.5130,126.5290)),
        )
        val client=MapboxWalkingClient(token); val rows=JSONArray(); val violations=mutableListOf<String>()
        var success=0
        for(case in cases) {
            var baseline: RouteResult?=null
            val instrumented=WalkingRouteSource { points,alternatives ->
                client.fetch(points,alternatives).also { if(alternatives) baseline=it.routes.minByOrNull { route->NavigationRepository.travelSeconds(route) } }
            }
            val result=RouteSearchEngine(instrumented).search(case.a,case.b) { cameras to lights }
            val row=JSONObject().put("case",case.name).put("origin",JSONArray(listOf(case.a.latitude,case.a.longitude)))
                .put("destination",JSONArray(listOf(case.b.latitude,case.b.longitude))).put("success",result.shortestRoute!=null)
                .put("elapsed_ms",result.diagnostics?.elapsedMillis).put("requests",result.diagnostics?.requests)
                .put("cache_hits",result.diagnostics?.cacheHits).put("timeout",result.diagnostics?.refinementTimedOut)
                .put("problems",JSONArray(result.diagnostics?.problems.orEmpty())).put("error",result.errorMessage ?: JSONObject.NULL)
            if(result.shortestRoute!=null) success++
            for((label,route) in listOf("shortest" to result.shortestRoute,"recommended" to result.recommendedRoute,"safe" to result.safeRoute)) {
                if(route==null) continue
                val m=route.safetyMetrics; val q=RouteContinuity.analyze(route.geometryPoints)
                row.put(label,JSONObject().put("meters",route.distanceMeters).put("seconds",route.durationSeconds)
                    .put("score",m?.score).put("covered_ratio",m?.coveredRatio).put("uncovered_m",m?.uncoveredMeters)
                    .put("longest_gap_m",m?.longestUncoveredMeters).put("repeat_m",q.repeatedMeters)
                    .put("closed_loops",q.closedLoops).put("max_snap_m",route.snapDistancesMeters.maxOrNull())
                    .put("warnings",JSONArray(route.routeWarnings)))
                if(route.snapDistancesMeters.isEmpty() || route.snapDistancesMeters.first()>75.5 || route.snapDistancesMeters.last()>75.5 ||
                    route.snapDistancesMeters.drop(1).dropLast(1).any { it>40.5 }) violations.add("${case.name}:$label:snap")
                if(route.geometryPoints.size<2 || !route.safetyFacilityScore.isFinite()) violations.add("${case.name}:$label:geometry")
            }
            baseline?.let {
                row.put("baseline_seconds",it.durationSeconds).put("baseline_repeat_m",RouteContinuity.analyze(it.geometryPoints).repeatedMeters)
            }
            rows.put(row)
            println("LIVE ${case.name}: success=${result.shortestRoute!=null}, elapsed=${result.diagnostics?.elapsedMillis}ms")
        }
        val first=cases.first()
        val warm=RouteSearchEngine(client).search(first.a,first.b) { cameras to lights }
        val output=JSONObject().put("checked_at_utc",Instant.now().toString()).put("cases",cases.size).put("successes",success)
            .put("success_rate",success.toDouble()/cases.size).put("quality_violations",JSONArray(violations))
            .put("warm_first_case",JSONObject().put("elapsed_ms",warm.diagnostics?.elapsedMillis).put("cache_hits",warm.diagnostics?.cacheHits))
            .put("notes","Public coordinate integration checks, not a field safety audit. No tokens or user GPS saved.")
            .put("results",rows)
        val outputFile=File(root,"docs/week11/validation/live-route-validation.json")
        outputFile.parentFile?.mkdirs(); outputFile.writeText(output.toString(2))
        assertTrue("Quality constraints violated; see sanitized validation JSON",violations.isEmpty())
        assertTrue("Live success below 90%; see sanitized validation JSON",success>=cases.size*0.9)
    }
}
