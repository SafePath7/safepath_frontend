package com.example.safepath_test1.api

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AccountApiTest {
    @Test fun requestsMatchBackendContract() = runBlocking {
        val requests = mutableListOf<List<String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            synchronized(requests) { requests.add(listOf(exchange.requestMethod, exchange.requestURI.path, body, exchange.requestHeaders.getFirst("Authorization").orEmpty())) }
            val response = when (exchange.requestURI.path) {
                "/api/auth/login" -> "{\"userId\":1,\"status\":\"REGISTERED\"}"
                "/api/users/me/registration" -> "{\"userId\":1}"
                "/api/users/me" -> if (exchange.requestMethod == "GET") "{\"userId\":1,\"name\":\"A\",\"email\":null,\"phoneNumber\":null}" else ""
                else -> ""
            }.toByteArray()
            val status = if (exchange.requestURI.path.endsWith("/phone")) 503 else if (response.isEmpty()) 204 else 200
            exchange.sendResponseHeaders(status, if (response.isEmpty()) -1 else response.size.toLong())
            if (response.isNotEmpty()) exchange.responseBody.use { it.write(response) } else exchange.close()
        }
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/")
                .addConverterFactory(GsonConverterFactory.create()).build().create(AccountApi::class.java)
            val token = "Bearer test-token"
            assertEquals("REGISTERED", api.login(token).status)
            assertEquals(1L, api.register(token, RegistrationBody("A")).userId)
            assertEquals("A", api.profile(token).name)
            api.update(token, ProfileBody("B"))
            try { api.changePhone(token, PhoneBody("+821012345678")); fail("503 must fail") }
            catch (error: HttpException) { assertEquals(503, error.code()) }
            api.withdraw(token)
            assertEquals(listOf("POST", "POST", "GET", "PATCH", "PATCH", "DELETE"), requests.map { it[0] })
            assertEquals(listOf("/api/auth/login", "/api/users/me/registration", "/api/users/me", "/api/users/me", "/api/users/me/phone", "/api/users/me"), requests.map { it[1] })
            assertEquals("{\"name\":\"A\"}", requests[1][2])
            assertEquals("{\"name\":\"B\"}", requests[3][2])
            assertEquals("{\"phoneNumber\":\"+821012345678\"}", requests[4][2])
            assertTrue(requests.all { it[3] == token })
        } finally { server.stop(0) }
    }

    @Test fun baseUrlRestrictsInsecureConnections() {
        assertEquals("http://10.0.2.2:8080/", checkedAccountBaseUrl("http://10.0.2.2:8080", true))
        assertEquals("https://example.com/", checkedAccountBaseUrl("https://example.com", false))
        for (url in listOf("http://example.com", "http://8.8.8.8", "http://192.168.1.2/path")) {
            try { checkedAccountBaseUrl(url, true); fail("accepted $url") } catch (_: IllegalArgumentException) { }
        }
        try { checkedAccountBaseUrl("http://127.0.0.1:8080", false); fail("release accepted HTTP") } catch (_: IllegalArgumentException) { }
    }
}
