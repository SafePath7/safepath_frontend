package com.example.safepath_test1.location

import android.util.Log
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class KakaoPlace(
    val id: String,
    val name: String,
    val address: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int?,
)

data class PlaceSearchResult(
    val places: List<KakaoPlace> = emptyList(),
    val errorMessage: String? = null,
)

object KakaoPlaceRepository {
    private const val TAG = "KakaoPlaceRepository"
    private const val ENDPOINT = "https://dapi.kakao.com/v2/local/search/keyword.json"

    suspend fun search(
        restApiKey: String,
        query: String,
        centerLatitude: Double? = null,
        centerLongitude: Double? = null,
    ): PlaceSearchResult =
        withContext(Dispatchers.IO) {
            val normalizedQuery = query.trim()
            if (normalizedQuery.length < 2) return@withContext PlaceSearchResult()
            val normalizedApiKey = restApiKey.trim().replace("\"", "")
            if (normalizedApiKey.isBlank()) {
                return@withContext PlaceSearchResult(
                    errorMessage = "카카오 REST API 키를 local.properties에 입력해 주세요."
                )
            }

            var connection: HttpURLConnection? = null
            try {
                val parameters =
                    buildList {
                            add(
                                "query=${URLEncoder.encode(normalizedQuery, StandardCharsets.UTF_8.name())}"
                            )
                            add("size=15")
                            if (centerLatitude != null && centerLongitude != null) {
                                add("x=$centerLongitude")
                                add("y=$centerLatitude")
                                add("sort=distance")
                            }
                        }
                        .joinToString("&")
                connection =
                    URI.create("$ENDPOINT?$parameters").toURL().openConnection()
                        as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "KakaoAK $normalizedApiKey")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        PlaceSearchResult(places = parsePlaces(response))
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        Log.e(
                            TAG,
                            "Kakao place search authorization failed: HTTP ${connection.responseCode}",
                        )
                        PlaceSearchResult(errorMessage = "카카오 REST API 키 설정을 확인해 주세요.")
                    }
                    else -> {
                        Log.e(TAG, "Kakao place search failed: HTTP ${connection.responseCode}")
                        PlaceSearchResult(errorMessage = "장소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.")
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Kakao place search network or response parsing failed", exception)
                PlaceSearchResult(errorMessage = "장소 검색에 실패했습니다. 네트워크를 확인해 주세요.")
            } finally {
                connection?.disconnect()
            }
        }

    internal fun parsePlaces(json: String): List<KakaoPlace> {
        val documents = JSONObject(json).optJSONArray("documents") ?: return emptyList()
        return buildList {
            for (index in 0 until documents.length()) {
                val document = documents.optJSONObject(index) ?: continue
                val latitude = document.optString("y").toDoubleOrNull() ?: continue
                val longitude = document.optString("x").toDoubleOrNull() ?: continue
                val name = document.optString("place_name").trim()
                if (name.isEmpty() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0)
                    continue
                val roadAddress = document.optString("road_address_name").trim()
                val lotAddress = document.optString("address_name").trim()
                add(
                    KakaoPlace(
                        id = document.optString("id", "$latitude,$longitude"),
                        name = name,
                        address = roadAddress.ifBlank { lotAddress },
                        category = document.optString("category_name").trim(),
                        latitude = latitude,
                        longitude = longitude,
                        distanceMeters = document.optString("distance").toIntOrNull(),
                    )
                )
            }
        }
    }
}
