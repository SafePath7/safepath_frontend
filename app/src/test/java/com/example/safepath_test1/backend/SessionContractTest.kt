package com.example.safepath_test1.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionContractTest {
    @Test fun routeTypesMatchBackendContract() {
        assertEquals("SAFEST", serverRouteType("Safe"))
        assertEquals("FASTEST", serverRouteType("Shortest"))
        assertEquals("RECOMMENDED", serverRouteType("Recommended"))
        assertThrows(IllegalArgumentException::class.java) { serverRouteType("unknown") }
    }

    @Test fun startBodyContainsGuardianUserIdsAndSelectedRoute() {
        val body = StartSessionBody(listOf(42), SelectedRouteBody("SAFEST", listOf(
            RouteCoordinate(35.0, 128.0), RouteCoordinate(35.1, 128.1)
        ), 500.0, 420.0, null, "도착지"))
        val json = Gson().toJsonTree(body).asJsonObject
        assertEquals(42L, json.getAsJsonArray("guardianIds")[0].asLong)
        val route = json.getAsJsonObject("selectedRoute")
        assertEquals("SAFEST", route["type"].asString)
        assertEquals(35.0, route.getAsJsonArray("coordinates")[0].asJsonObject["latitude"].asDouble, 0.0)
        assertEquals(128.0, route.getAsJsonArray("coordinates")[0].asJsonObject["longitude"].asDouble, 0.0)
    }
}
