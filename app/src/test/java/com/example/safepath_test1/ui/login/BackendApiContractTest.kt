package com.example.safepath_test1.api

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody

class BackendApiContractTest {
    @Test fun adminRequestsMatchBackendContract() = runBlocking {
        val requests = mutableListOf<List<String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(listOf(exchange.requestMethod, exchange.requestURI.toString(), exchange.requestBody.bufferedReader().readText(), exchange.requestHeaders.getFirst("Authorization").orEmpty()))
            val body = if (exchange.requestMethod == "POST") "{\"id\":9,\"eventId\":4,\"mediaType\":\"IMAGE\",\"fileName\":\"photo.png\",\"uploadedAt\":\"2026-01-01T00:00:00Z\"}" else if (exchange.requestMethod == "PATCH") "{\"id\":3,\"userId\":1,\"guardianId\":2,\"status\":\"SUSPENDED\"}" else ""
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(if (bytes.isEmpty()) 204 else 200, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isEmpty()) exchange.close() else exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/").addConverterFactory(GsonConverterFactory.create()).build().create(BackendApi::class.java)
            val auth = "Bearer test-token"
            assertEquals("SUSPENDED", api.respond(auth, 3, "suspend").status)
            val file = MultipartBody.Part.createFormData("file", "photo.png", RequestBody.create(MediaType.parse("image/png"), byteArrayOf(1, 2, 3)))
            assertEquals(9L, api.uploadRiskMedia(auth, 4, file, RequestBody.create(MediaType.parse("text/plain"), "IMAGE")).id)
            api.deleteRiskMedia(auth, 4, 9)
            assertEquals(listOf("/api/guardian-registrations/3/suspend", "/api/risk-events/4/medias", "/api/risk-events/4/medias/9"), requests.map { it[1] })
            assertTrue(requests[1][2].contains("name=\"file\""))
            assertTrue(requests[1][2].contains("name=\"mediaType\""))
            assertTrue(requests.all { it[3] == auth })
        } finally { server.stop(0) }
    }
    @Test fun riskEventAndMediaRequestsMatchBackend() = runBlocking {
        val requests = mutableListOf<List<String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            requests.add(listOf(exchange.requestMethod, exchange.requestURI.toString(), body, exchange.requestHeaders.getFirst("Authorization").orEmpty()))
            val path = exchange.requestURI.path
            val json = when {
                path.endsWith("/permissions/check") -> "{\"allowed\":true}"
                path.endsWith("/medias") && exchange.requestURI.query == null -> "[]"
                path.endsWith("/medias") -> "file contents"
                path.endsWith("/status") || (path == "/api/risk-events" && exchange.requestMethod == "POST") -> "{\"id\":4,\"sessionId\":\"s\",\"eventType\":\"SOS\",\"description\":\"help\",\"status\":\"OPEN\",\"detectedAt\":\"2026-01-01T00:00:00Z\"}"
                else -> "{\"id\":4,\"userId\":2,\"sessionId\":\"s\",\"eventType\":\"SOS\",\"description\":\"help\",\"status\":\"OPEN\",\"detectedAt\":\"2026-01-01T00:00:00Z\"}"
            }.toByteArray()
            exchange.sendResponseHeaders(200, json.size.toLong())
            exchange.responseBody.use { it.write(json) }
        }
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/")
                .addConverterFactory(GsonConverterFactory.create()).build().create(BackendApi::class.java)
            val auth = "Bearer test-token"
            assertTrue(api.checkPermission(auth, 3, "STATUS").allowed)
            assertEquals(4L, api.riskEvent(auth, 4).id)
            api.createRiskEvent(auth, RiskEventRequest("s", "SOS", "help", "2026-01-01T00:00:00Z"))
            api.updateRiskEventStatus(auth, 4, RiskEventStatusRequest("RESOLVED"))
            assertTrue(api.riskMedia(auth, 4).isEmpty())
            api.downloadRiskMedia(auth, 4, 9).use { assertEquals("file contents", it.string()) }
            assertEquals("/api/guardian-registrations/3/permissions/check?permissionType=STATUS", requests[0][1])
            assertEquals("/api/risk-events/4/medias?mediaId=9", requests[5][1])
            assertEquals("{\"status\":\"RESOLVED\"}", requests[3][2])
            assertTrue(requests.all { it[3] == auth })
        } finally { server.stop(0) }
    }
    @Test fun guardianAndNotificationRequestsMatchBackend() = runBlocking {
        val requests = mutableListOf<List<String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            requests.add(listOf(exchange.requestMethod, exchange.requestURI.toString(), body, exchange.requestHeaders.getFirst("Authorization").orEmpty()))
            val path = exchange.requestURI.path
            val json = when {
                path == "/api/guardian-registrations" && exchange.requestMethod == "GET" -> "[]"
                path.startsWith("/api/guardian-registrations") -> "{\"id\":3,\"userId\":1,\"guardianId\":2,\"status\":\"PENDING\"}"
                path == "/api/notifications/sos" -> "{\"successCount\":1,\"failureCount\":0}"
                path == "/api/risk-events" -> "[]"
                else -> ""
            }.toByteArray()
            exchange.sendResponseHeaders(if (json.isEmpty()) 204 else 200, if (json.isEmpty()) -1 else json.size.toLong())
            if (json.isNotEmpty()) exchange.responseBody.use { it.write(json) } else exchange.close()
        }
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/")
                .addConverterFactory(GsonConverterFactory.create()).build().create(BackendApi::class.java)
            val token = "Bearer test-token"
            assertTrue(api.guardians(token).isEmpty())
            assertEquals(3L, api.requestGuardian(token, GuardianRequest(2)).id)
            api.respond(token, 3, "accept")
            api.permissions(token, 3, GuardianPermissions(true, false, true))
            api.registerToken(token, FcmTokenBody("token"))
            assertEquals(1, api.sos(token).successCount)
            assertTrue(api.riskEvents(token, 2).isEmpty())
            assertEquals(listOf("GET", "POST", "PATCH", "PATCH", "PUT", "POST", "GET"), requests.map { it[0] })
            assertEquals("{\"guardianId\":2}", requests[1][2])
            assertEquals("/api/guardian-registrations/3/accept", requests[2][1])
            assertEquals("{\"canViewLocation\":true,\"canViewStatus\":false,\"canViewPersonalInfo\":true}", requests[3][2])
            assertEquals("{\"fcmToken\":\"token\"}", requests[4][2])
            assertEquals("/api/risk-events?userId=2", requests[6][1])
            assertTrue(requests.all { it[3] == token })
        } finally { server.stop(0) }
    }
}
