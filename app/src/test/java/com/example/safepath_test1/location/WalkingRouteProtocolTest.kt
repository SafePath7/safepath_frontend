package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WalkingRouteProtocolTest {
    private val points=listOf(GeoPoint(37.0,127.0),GeoPoint(37.0,127.001),GeoPoint(37.0,127.002))
    private fun fixture(): JSONObject {
        val locations=JSONArray(points.map { listOf(it.longitude,it.latitude) })
        val route=JSONObject().put("geometry",JSONObject().put("type","LineString").put("coordinates",locations))
            .put("distance",FacilityRoutePlanner.distance(points.first(),points.last())).put("duration",150)
            .put("waypoints",JSONArray(points.map { JSONObject().put("location",JSONArray(listOf(it.longitude,it.latitude))) }))
        return JSONObject().put("code","Ok").put("routes",JSONArray().put(route))
    }
    private fun route(f: JSONObject)=f.getJSONArray("routes").getJSONObject(0)
    @Test fun acceptsOnlyValidatedWalkingGeometryAndSnapLocations() {
        val parsed=WalkingRouteProtocol.parse(fixture().toString(),points)
        assertEquals(1,parsed.routes.size); assertEquals(3,parsed.routes.first().snapDistancesMeters.size)
    }
    @Test fun rejectsViaSnappedBeyondFortyMeters() {
        val f=fixture()
        route(f).getJSONArray("waypoints").getJSONObject(1).put("location",JSONArray(listOf(127.001,37.001)))
        assertTrue(WalkingRouteProtocol.parse(f.toString(),points).routes.isEmpty())
    }
    @Test fun rejectsGeometryWhoseDestinationDoesNotReachRequestedArea() {
        val f=fixture(); route(f).getJSONObject("geometry").getJSONArray("coordinates").put(2,JSONArray(listOf(127.02,37.0)))
        assertTrue(WalkingRouteProtocol.parse(f.toString(),points).routes.isEmpty())
    }
    @Test fun rejectsMissingSnapEvidenceAndInvalidDistanceOrDuration() {
        val f=fixture(); route(f).remove("waypoints")
        assertTrue(WalkingRouteProtocol.parse(f.toString(),points).routes.isEmpty())
        val g=fixture(); route(g).put("distance",1)
        assertTrue(WalkingRouteProtocol.parse(g.toString(),points).routes.isEmpty())
        val h=fixture(); route(h).put("duration",-1)
        assertTrue(WalkingRouteProtocol.parse(h.toString(),points).routes.isEmpty())
    }
    @Test fun serverAndMalformedResponsesDoNotBecomeFakeRoutes() {
        assertEquals("NoSegment",WalkingRouteProtocol.parse("{\"code\":\"NoSegment\"}",points).problem)
        assertTrue(WalkingRouteProtocol.parse("not json",points).routes.isEmpty())
    }
    @Test fun malformedAlternativeDoesNotRemoveOtherValidRoutes() {
        val f=fixture(); f.getJSONArray("routes").put(JSONObject().put("geometry","invalid"))
        assertEquals(1,WalkingRouteProtocol.parse(f.toString(),points).routes.size)
    }
}
