package com.example.safepath_test1.location

import android.content.Context
import android.util.Log
import kotlin.math.cos
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SafetyFacility(val latitude: Double, val longitude: Double, val address: String = "")

enum class SafetyLevel(
    val title: String,
    val hexColor: Long,
    val icon: String,
    val description: String,
) {
    BAD("나쁨", 0xFFEF4444, "🔴", "주변에 안전시설이 부족하여 주의가 필요합니다."),
    MODERATE("보통", 0xFFEAB308, "🟡", "기본적인 안전시설이 배치되어 있는 구역입니다."),
    GOOD("좋음", 0xFF84CC16, "🟢", "CCTV와 가로등이 잘 갖춰진 안전한 구역입니다."),
    VERY_GOOD("매우 좋음", 0xFF22C55E, "🛡️", "주변 밀집도가 높아 매우 안전한 안심 구역입니다."),
}

data class RadiusAnalysisResult(
    val cctvCount: Int,
    val streetlightCount: Int,
    val level: SafetyLevel,
    val totalScore: Int,
)

data class RouteFacilityAnalysis(val score: Double, val cctvCount: Int, val streetlightCount: Int)

object SafetyRepository {
    private val assessmentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val assessmentCache = java.util.concurrent.ConcurrentHashMap<FacilityType, FacilityDataset>()
    private val assessmentJobs = mutableMapOf<FacilityType, kotlinx.coroutines.Job>()
    private val assessmentSources = listOf(
        Triple(FacilityType.CCTV, "앱 내 cctv.csv", "지도 표시 자료"),
        Triple(FacilityType.LIGHT, "대구공공시설관리공단", "대구"),
        Triple(FacilityType.POLICE, "앱 내 police.csv", "지도 표시 자료"),
        Triple(FacilityType.BELL, "행정안전부 안전비상벨 API", "지도 표시 자료"),
        Triple(FacilityType.FIRE, "생활안전지도 IF_0038", "지도 표시 자료"),
    )

    fun warmAssessmentDatasets(context: Context) {
        synchronized(assessmentJobs) {
            assessmentSources.forEach { (type, source, region) ->
                if (assessmentCache[type]?.availability == DataAvailability.AVAILABLE || assessmentJobs[type]?.isActive == true) return@forEach
                assessmentJobs[type] = assessmentScope.launch {
                    val dataset = try {
                        val facilities = when (type) {
                            FacilityType.CCTV -> getCctvFacilities(context.applicationContext)
                            FacilityType.LIGHT -> getStreetlightFacilities(context.applicationContext)
                            FacilityType.POLICE -> getPoliceFacilities(context.applicationContext)
                            FacilityType.BELL -> getEmergencyBells(context.applicationContext)
                            FacilityType.FIRE -> getFireStations(context.applicationContext)
                        }
                        FacilityDataset(type, facilities, source, region, availability=DataAvailability.AVAILABLE)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e
                    } catch (e: Exception) {
                        FacilityDataset(type, emptyList(), source, region, availability=DataAvailability.FAILED, detail="자료 요청 실패")
                    }
                    assessmentCache[type] = dataset
                }
            }
        }
    }

    fun assessmentSnapshot(): List<FacilityDataset> = assessmentSources.map { (type,source,region) ->
        assessmentCache[type] ?: FacilityDataset(type,emptyList(),source,region,availability=DataAvailability.NOT_RECEIVED,detail="자료 불러오는 중")
    }
    suspend fun loadAssessmentDatasets(context: Context): List<FacilityDataset> = kotlinx.coroutines.coroutineScope {
        val sources = listOf(
            Triple(FacilityType.CCTV, "앱 내 cctv.csv", "제공 지역 메타데이터 미확인"),
            Triple(FacilityType.LIGHT, "대구공공시설관리공단 20260812", "대구 자료, 정확한 제공 경계 미확인"),
            Triple(FacilityType.POLICE, "앱 내 police.csv", "검색 검증된 일부 시설, 완전성 미확인"),
            Triple(FacilityType.BELL, "행정안전부 안전비상벨 API", "참여 자치단체별 범위 미확인"),
            Triple(FacilityType.FIRE, "생활안전지도 IF_0038", "제공 경계 미확인"),
        )
        sources.map { (type, source, region) -> async {
            try {
                val facilities = when(type) {
                    FacilityType.CCTV -> getCctvFacilities(context)
                    FacilityType.LIGHT -> getStreetlightFacilities(context)
                    FacilityType.POLICE -> getPoliceFacilities(context)
                    FacilityType.BELL -> getEmergencyBells(context)
                    FacilityType.FIRE -> getFireStations(context)
                }
                FacilityDataset(type,facilities,source,region,availability=DataAvailability.AVAILABLE,
                    detail="지도에 표시 가능한 위치 자료를 불러왔습니다.")
            } catch(e: kotlinx.coroutines.CancellationException) { throw e
            } catch(e: Exception) {
                FacilityDataset(type,emptyList(),source,region,availability=DataAvailability.FAILED,detail="자료 요청 실패")
            }
        } }.map { it.await() }
    }

    private const val tag = "SafetyRepository"
    private var cctvList: List<SafetyFacility>? = null
    private var policeList: List<SafetyFacility>? = null
    private val policeLock = Mutex()

    suspend fun getPoliceFacilities(context: Context): List<SafetyFacility> = policeLock.withLock {
        policeList ?: withContext(Dispatchers.IO) {
            context.assets.open("police.csv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).mapNotNull { line ->
                    val cols = parseCsvLine(line)
                    val lat = cols.getOrNull(0)?.toDoubleOrNull()
                    val lng = cols.getOrNull(1)?.toDoubleOrNull()
                    if (lat != null && lng != null && lat in 30.0..40.0 && lng in 120.0..135.0)
                        SafetyFacility(lat, lng, cols.getOrElse(2) { "" }) else null
                }.toList()
            }
        }.also { policeList = it }
    }
    private var streetlightList: List<SafetyFacility>? = null
    private var emergencyBellList: List<SafetyFacility>? = null
    private var fireStationList: List<SafetyFacility>? = null
    private val fireStationLock = Mutex()

    suspend fun getFireStations(context: Context): List<SafetyFacility> = fireStationLock.withLock {
        fireStationList ?: PublicFacilityApi.loadFireStations(context).also { fireStationList = it }
    }
    private val emergencyBellLock = Mutex()
    // CSV는 한 번만 읽고 동시 요청에서도 캐시가 중복 생성되지 않도록 보호한다.
    private val cctvCacheLock = Mutex()
    private val streetlightCacheLock = Mutex()

    suspend fun getCctvFacilities(context: Context): List<SafetyFacility> =
        cctvCacheLock.withLock { cctvList ?: loadCctv(context).also { cctvList = it } }

    suspend fun getStreetlightFacilities(context: Context): List<SafetyFacility> =
        streetlightCacheLock.withLock {
            streetlightList ?: PublicFacilityApi.loadStreetlights(context).also { streetlightList = it }
        }

    suspend fun getEmergencyBells(context: Context): List<SafetyFacility> =
        emergencyBellLock.withLock { emergencyBellList ?: PublicFacilityApi.loadBells(context).also { emergencyBellList = it } }

    suspend fun analyzeRadius(
        context: Context,
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Double,
    ): RadiusAnalysisResult =
        withContext(Dispatchers.IO) {
            val cctvs = getCctvFacilities(context)
            val lights = getStreetlightFacilities(context)

            var cctvInRadius = 0
            for (f in cctvs) {
                if (
                    distanceInMeters(centerLat, centerLng, f.latitude, f.longitude) <= radiusMeters
                ) {
                    cctvInRadius++
                }
            }

            var lightsInRadius = 0
            for (f in lights) {
                if (
                    distanceInMeters(centerLat, centerLng, f.latitude, f.longitude) <= radiusMeters
                ) {
                    lightsInRadius++
                }
            }

            val areaFactor = (radiusMeters / 300.0).let { it * it }.coerceAtLeast(0.1)
            val weightedScore = ((cctvInRadius * 4 + lightsInRadius * 0.5) / areaFactor).toInt()

            val level =
                when {
                    weightedScore >= 35 -> SafetyLevel.VERY_GOOD
                    weightedScore >= 18 -> SafetyLevel.GOOD
                    weightedScore >= 6 -> SafetyLevel.MODERATE
                    else -> SafetyLevel.BAD
                }

            RadiusAnalysisResult(
                cctvCount = cctvInRadius,
                streetlightCount = lightsInRadius,
                level = level,
                totalScore = weightedScore.coerceIn(0, 100),
            )
        }

    suspend fun analyzeRouteFacilities(
        context: Context,
        routeGeoJson: String,
        routeDistanceMeters: Double,
        scopedCctvs: List<SafetyFacility>? = null,
        scopedLights: List<SafetyFacility>? = null,
    ): RouteFacilityAnalysis =
        withContext(Dispatchers.IO) {
            val routePoints =
                try {
                    val coordinates =
                        org.json.JSONObject(routeGeoJson).optJSONArray("coordinates")
                            ?: return@withContext RouteFacilityAnalysis(0.0, 0, 0)
                    buildList<SafetyFacility> {
                        for (index in 0 until coordinates.length()) {
                            val coordinate = coordinates.optJSONArray(index) ?: continue
                            val longitude = coordinate.optDouble(0, Double.NaN)
                            val latitude = coordinate.optDouble(1, Double.NaN)
                            if (latitude.isFinite() && longitude.isFinite())
                                add(SafetyFacility(latitude, longitude))
                        }
                    }
                } catch (exception: Exception) {
                    Log.e(tag, "Failed to parse route geometry for facility scoring", exception)
                    return@withContext RouteFacilityAnalysis(0.0, 0, 0)
                }
            if (routePoints.size < 2) return@withContext RouteFacilityAnalysis(0.0, 0, 0)
            val cctvs = scopedCctvs ?: getCctvFacilities(context)
            val lights = scopedLights ?: getStreetlightFacilities(context)
            if (cctvs.isEmpty() && lights.isEmpty()) return@withContext RouteFacilityAnalysis(0.0, 0, 0)
            val points = routePoints.map { com.example.safepath_test1.model.GeoPoint(it.latitude,it.longitude) }
            val metrics = RouteSafetyEvaluator.evaluate(points,cctvs,lights)
            RouteFacilityAnalysis(metrics.score,metrics.cctvCount,metrics.streetlightCount)
        }

    suspend fun scoreRouteFacilities(context: Context, routeGeoJson: String, routeDistanceMeters: Double): Double =
        analyzeRouteFacilities(context, routeGeoJson, routeDistanceMeters).score

    internal fun facilitiesInsideRouteBounds(
        facilities: List<SafetyFacility>,
        route: List<SafetyFacility>,
        paddingMeters: Double,
    ): List<SafetyFacility> {
        if (route.isEmpty()) return emptyList()
        val centerLatitude = route.sumOf { it.latitude } / route.size
        val latitudePadding = paddingMeters / 111_320.0
        val longitudeScale = (111_320.0 * cos(Math.toRadians(centerLatitude))).coerceAtLeast(1.0)
        val longitudePadding = paddingMeters / longitudeScale
        val minLatitude = route.minOf { it.latitude } - latitudePadding
        val maxLatitude = route.maxOf { it.latitude } + latitudePadding
        val minLongitude = route.minOf { it.longitude } - longitudePadding
        val maxLongitude = route.maxOf { it.longitude } + longitudePadding
        return facilities.filter {
            it.latitude in minLatitude..maxLatitude && it.longitude in minLongitude..maxLongitude
        }
    }

    /** 특정 지점 주변의 시설만 반환해 지도 라벨이 수만 개 생성되는 것을 방지한다. */
    internal fun facilitiesNearPoint(
        facilities: List<SafetyFacility>,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        limit: Int,
    ): List<SafetyFacility> {
        if (limit <= 0 || radiusMeters <= 0.0) return emptyList()
        val latitudePadding = radiusMeters / 111_320.0
        val longitudeScale = (111_320.0 * cos(Math.toRadians(latitude))).coerceAtLeast(1.0)
        val longitudePadding = radiusMeters / longitudeScale
        return facilities
            .asSequence()
            .filter {
                it.latitude in (latitude - latitudePadding)..(latitude + latitudePadding) &&
                    it.longitude in (longitude - longitudePadding)..(longitude + longitudePadding)
            }
            .filter { distanceInMeters(latitude, longitude, it.latitude, it.longitude) <= radiusMeters }
            .sortedBy { distanceInMeters(latitude, longitude, it.latitude, it.longitude) }
            .take(limit)
            .toList()
    }

    private fun distanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) *
                    Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) *
                    Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    // 큰따옴표로 감싼 주소의 쉼표를 열 구분자로 처리하지 않도록 CSV를 분리한다.
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        result.add(current.toString())
        return result
    }

    // 전처리한 UTF-8 좌표 전용 자료를 읽는다.
    private fun loadCctv(context: Context): List<SafetyFacility> {
        val list = mutableListOf<SafetyFacility>()
        try {
            context.assets.open("cctv.csv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = parseCsvLine(line)
                    if (cols.size >= 2) {
                        val lat = cols[0].trim().toDoubleOrNull()
                        val lng = cols[1].trim().toDoubleOrNull()
                        val addr = if (cols.size > 3) cols[3].trim() else ""
                        if (
                            lat != null && lng != null && lat in 30.0..40.0 && lng in 120.0..135.0
                        ) {
                            list.add(
                                SafetyFacility(latitude = lat, longitude = lng, address = addr)
                            )
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            throw java.io.IOException("CCTV 자료를 읽지 못했습니다.", exception)
        }
        return list
    }

    private fun loadStreetlights(context: Context): List<SafetyFacility> {
        val list = mutableListOf<SafetyFacility>()
        try {
            context.assets.open("streetlight.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = parseCsvLine(line)
                    if (cols.size >= 3) {
                        val lat = cols[1].trim().toDoubleOrNull()
                        val lng = cols[2].trim().toDoubleOrNull()
                        if (
                            lat != null && lng != null && lat in 30.0..40.0 && lng in 120.0..135.0
                        ) {
                            list.add(SafetyFacility(latitude = lat, longitude = lng))
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            throw java.io.IOException("가로등 자료를 읽지 못했습니다.", exception)
        }
        return list
    }
}
