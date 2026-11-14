package com.example.safepath_test1.location

import android.content.Context
import com.example.safepath_test1.R
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException

internal object PublicFacilityApi {
    suspend fun loadFireStations(context: Context): List<SafetyFacility> = withContext(Dispatchers.IO) {
        val key = context.getString(R.string.safemap_api_key).trim()
        if (key.isBlank()) throw IOException("생활안전지도 인증키가 설정되지 않았습니다.")
        val encodedKey = URLEncoder.encode(key, "UTF-8")
        val result = ArrayList<SafetyFacility>()
        var received = 0
        var pageNumber = 1
        while (true) {
            currentCoroutineContext().ensureActive()
            val json = get("https://safemap.go.kr/openapi2/IF_0038?serviceKey=$encodedKey&pageNo=$pageNumber&numOfRows=100&returnType=json")
            val parsed = parseFireStationPage(json)
            result.addAll(parsed.facilities)
            received += parsed.rowCount
            if (received >= parsed.total) break
            if (parsed.rowCount == 0) throw IOException("소방시설 자료가 일부만 수신되었습니다.")
            pageNumber++
        }
        if (received > 0 && result.isEmpty()) throw IOException("소방시설 좌표계를 확인할 수 없습니다.")
        result
    }

    internal fun parseFireStationPage(json: JSONObject): Page = parseResponse {
        val response = json.optJSONObject("response") ?: json
        val header = response.optJSONObject("header") ?: response
        val resultCode = header.optString("resultCode", "")
        if (resultCode != "0" && resultCode != "00") throw IOException("소방시설 API 조회 실패")
        val body = response.optJSONObject("body") ?: response
        val total = body.getInt("totalCount")
        val rawItems = body.optJSONObject("items")?.opt("item") ?: body.opt("items")
        val items = when (rawItems) {
            is JSONArray -> rawItems
            is JSONObject -> JSONArray().put(rawItems)
            else -> if (total == 0) JSONArray() else throw IOException("소방시설 응답 형식 오류")
        }
        if (total < 0) throw IOException("소방시설 건수가 올바르지 않습니다.")
        val facilities = (0 until items.length()).mapNotNull { fireStationPoint(items.getJSONObject(it)) }
        Page(facilities, items.length(), total)
    }
    private fun key(context: Context): String = context.getString(R.string.public_data_api_key).trim().also {
        if (it.isEmpty()) throw IOException("공공데이터 인증키가 설정되지 않았습니다.")
    }

    private fun get(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 20000
            if (connection.responseCode != 200) throw IOException("공공데이터 서버 요청 실패")
            return JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (_: JSONException) {
            throw IOException("공공데이터 응답 형식이 올바르지 않습니다.")
        } finally { connection.disconnect() }
    }

    suspend fun loadStreetlights(context: Context): List<SafetyFacility> = withContext(Dispatchers.IO) {
        val encodedKey = URLEncoder.encode(key(context), "UTF-8")
        val result = ArrayList<SafetyFacility>()
        var page = 1
        var received = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val json = get("https://api.odcloud.kr/api/15120529/v1/uddi:dd3a775c-d0f1-4c8f-84df-de167acb9265?page=$page&perPage=1000&serviceKey=$encodedKey")
            val parsed = parseStreetlightPage(json)
            result.addAll(parsed.facilities)
            received += parsed.rowCount
            if (received >= parsed.total) break
            if (parsed.rowCount == 0) throw IOException("가로등 자료가 일부만 수신되었습니다.")
            page++
        }
        result
    }

    suspend fun loadBells(context: Context): List<SafetyFacility> = withContext(Dispatchers.IO) {
        val encodedKey = URLEncoder.encode(key(context), "UTF-8")
        val result = ArrayList<SafetyFacility>()
        var page = 1
        var received = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val json = get("https://apis.data.go.kr/1741000/emergency_call_box_info/info?pageNo=$page&numOfRows=100&returnType=json&serviceKey=$encodedKey")
            val parsed = parseBellPage(json)
            result.addAll(parsed.facilities)
            received += parsed.rowCount
            if (received >= parsed.total) break
            if (parsed.rowCount == 0) throw IOException("비상벨 자료가 일부만 수신되었습니다.")
            page++
        }
        result
    }

    internal data class Page(val facilities: List<SafetyFacility>, val rowCount: Int, val total: Int)

    internal fun parseStreetlightPage(json: JSONObject): Page = parseResponse {
        val total = json.getInt("matchCount")
        val items = json.getJSONArray("data")
        page(items, total, "위도", "경도")
    }

    internal fun parseBellPage(json: JSONObject): Page = parseResponse {
        val response = json.getJSONObject("response")
        if (response.getJSONObject("header").optString("resultCode") != "0") throw IOException("비상벨 API 조회 실패")
        val body = response.getJSONObject("body")
        val total = body.getInt("totalCount")
        val raw = body.optJSONObject("items")?.opt("item")
        val items = when (raw) {
            is JSONArray -> raw
            is JSONObject -> JSONArray().put(raw)
            else -> if (total == 0) JSONArray() else throw IOException("비상벨 응답 형식 오류")
        }
        page(items, total, "WGS84_LAT", "WGS84_LOT")
    }

    private fun page(items: JSONArray, total: Int, latKey: String, lngKey: String): Page {
        if (total < 0) throw IOException("공공데이터 건수가 올바르지 않습니다.")
        val facilities = (0 until items.length()).mapNotNull { point(items.getJSONObject(it), latKey, lngKey) }
        return Page(facilities, items.length(), total)
    }

    private inline fun parseResponse(block: () -> Page): Page = try { block() } catch (_: JSONException) {
        throw IOException("공공데이터 응답 형식이 올바르지 않습니다.")
    }

    private fun point(item: JSONObject, latKey: String, lngKey: String): SafetyFacility? {
        val lat = item.optString(latKey).toDoubleOrNull() ?: return null
        val lng = item.optString(lngKey).toDoubleOrNull() ?: return null
        return if (lat in 30.0..40.0 && lng in 120.0..135.0) SafetyFacility(lat, lng) else null
    }

    private fun fireStationPoint(item: JSONObject): SafetyFacility? {
        val x = item.optString("x").toDoubleOrNull() ?: return null
        val y = item.optString("y").toDoubleOrNull() ?: return null
        if (!x.isFinite() || !y.isFinite()) return null
        // IF_0038 returns projected Web Mercator metres, not longitude/latitude.
        val longitude = Math.toDegrees(x / 6378137.0)
        val latitude = Math.toDegrees(2.0 * kotlin.math.atan(kotlin.math.exp(y / 6378137.0)) - Math.PI / 2.0)
        return if (latitude in 30.0..40.0 && longitude in 120.0..135.0) SafetyFacility(latitude, longitude) else null
    }
}
