package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

data class RouteSearchDiagnostics(
    val elapsedMillis: Long,
    val requests: Int,
    val cacheHits: Int,
    val rejectedDetours: Int,
    val refinementTimedOut: Boolean,
    val facilityCctvCount: Int,
    val facilityLightCount: Int,
    val problems: List<String>,
)

/** Shares exactly the same orchestration in Android and the live JVM validation suite. */
internal class RouteSearchEngine(
    private val source: WalkingRouteSource,
    private val refinementBudgetMillis: Long = 1000,
    private val overallBudgetMillis: Long = 5000,
    private val datasetLoader: (suspend () -> List<FacilityDataset>)? = null,
) {
    suspend fun search(origin: GeoPoint,destination: GeoPoint,
        loadFacilities: suspend () -> Pair<List<SafetyFacility>,List<SafetyFacility>>,
    ): MultiRouteResult {
        val rescue = java.util.concurrent.atomic.AtomicReference<MultiRouteResult?>()
        val start = System.nanoTime()
        return withTimeoutOrNull(overallBudgetMillis) {
            performSearch(origin,destination,loadFacilities) { rescue.set(it) }
        } ?: rescue.get()?.let { previous ->
            previous.copy(diagnostics=previous.diagnostics?.copy(elapsedMillis=(System.nanoTime()-start)/1000000,
                refinementTimedOut=true,problems=previous.diagnostics.problems+"OverallDeadline"))
        } ?: MultiRouteResult(null,null,null,errorMessage="경로 확인 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.")
    }

    private suspend fun performSearch(origin: GeoPoint,destination: GeoPoint,
        loadFacilities: suspend () -> Pair<List<SafetyFacility>,List<SafetyFacility>>,
        onBaseline: (MultiRouteResult) -> Unit,
    ): MultiRouteResult = withContext(Dispatchers.Default) {
        val searchContext=currentCoroutineContext()
        val started=System.nanoTime(); val requests=AtomicInteger(); val hits=AtomicInteger(); val rejected=AtomicInteger()
        val problems=Collections.synchronizedList(mutableListOf<String>())
        var cameraCount=0; var lightCount=0; var expired=false
        fun diagnostics()=RouteSearchDiagnostics((System.nanoTime()-started)/1000000,requests.get(),hits.get(),
            rejected.get(),expired,cameraCount,lightCount,synchronized(problems) { problems.distinct().toList() })
        fun error(message: String)=MultiRouteResult(null,null,null,errorMessage=message,diagnostics=diagnostics())
        if(!listOf(origin,destination).all { it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 })
            return@withContext error("출발지와 목적지 좌표를 확인해 주세요.")
        if(FacilityRoutePlanner.distance(origin,destination)<5) return@withContext error("출발지와 목적지가 너무 가깝습니다.")
        suspend fun fetch(points: List<GeoPoint>,alternatives: Boolean): List<RouteResult> {
            requests.incrementAndGet()
            val result=source.fetch(points,alternatives)
            currentCoroutineContext().ensureActive()
            if(result.cached) hits.incrementAndGet()
            result.problem?.let { problems.add(it) }
            // Sources are validated at the protocol boundary; fake providers also supply real geometry.
            return result.routes.filter { it.geometryPoints.size>=2 && it.durationSeconds?.let { t -> t.isFinite() && t>0 } == true }
        }
        val direct=withTimeoutOrNull(4200) { fetch(listOf(origin,destination),true) }
            ?: return@withContext error("경로 서버 응답이 늦습니다. 잠시 후 다시 시도해 주세요.")
        if(direct.isEmpty()) {
            val message=when {
                problems.any { it=="HTTP401" || it=="HTTP403" } -> "경로 서비스 인증 설정을 확인해 주세요."
                problems.any { it=="NoSegment" || it=="SnapOrGeometryRejected" } -> "출발지나 목적지 근처의 보행 도로를 찾지 못했습니다. 지도에서 가까운 길을 선택해 주세요."
                else -> "연결되는 보행 경로를 찾지 못했습니다. 네트워크와 위치를 확인해 주세요."
            }
            return@withContext error(message)
        }
        val quality=direct.associateWith { RouteContinuity.analyze(it.geometryPoints) }
        val clean=direct.filter { !quality.getValue(it).suspicious }
        // A cul-de-sac or bridge approach can require a return. Preserve the best provider baseline.
        val baseline=if(clean.isNotEmpty()) clean else listOf(direct.minWithOrNull(
            compareBy<RouteResult> { quality.getValue(it).repeatedMeters+40*quality.getValue(it).closedLoops }
                .thenBy { NavigationRepository.travelSeconds(it) })!!)
        val anchor=baseline.minByOrNull { NavigationRepository.travelSeconds(it) }!!
        val anchorQuality=quality.getValue(anchor)
        val baseWarnings=if(anchorQuality.suspicious) listOf("왕복 구간 포함") else emptyList()
        onBaseline(NavigationRepository.selectRoutes(baseline.map { it.copy(routeWarnings=baseWarnings+"시설 분석 미완료") })
            .copy(diagnostics=diagnostics()))
        var datasets: List<FacilityDataset>? = null
        val facilities=try {
                withTimeoutOrNull(1500) {
                datasets = datasetLoader?.invoke() ?: run {
                    val (cameras,lamps)=loadFacilities()
                    listOf(
                        FacilityDataset(FacilityType.CCTV,cameras,"앱 내 CCTV 자료","지도 표시 데이터",availability=DataAvailability.AVAILABLE),
                        FacilityDataset(FacilityType.LIGHT,lamps,"가로등 자료","지도 표시 데이터",availability=DataAvailability.AVAILABLE),
                    )
                }
                datasets.orEmpty().filter { it.availability == DataAvailability.AVAILABLE }
                    .firstOrNull { it.type == FacilityType.CCTV }?.facilities.orEmpty() to
                    datasets.orEmpty().filter { it.availability == DataAvailability.AVAILABLE }
                        .firstOrNull { it.type == FacilityType.LIGHT }?.facilities.orEmpty()
                }
        } catch(cancelled: CancellationException) { throw cancelled
        } catch(_: Exception) { null }
        if(facilities==null) {
            problems.add("FacilitiesUnavailable")
            datasets = null
            val fallback=baseline.map { route ->
                route.copy(routeWarnings=baseWarnings+"안전시설 평가 생략 · 빠른 경로 제공")
            }
            return@withContext NavigationRepository.selectRoutes(fallback).copy(diagnostics=diagnostics())
        }
        val plan=FacilityRoutePlanner.plan(origin,destination,facilities.first,facilities.second) { searchContext.ensureActive() }
        cameraCount=plan.cctvs.size; lightCount=plan.lights.size
        val candidates=Collections.synchronizedList(baseline.toMutableList())
        val limiter=Semaphore(2)
        fun acceptable(route: RouteResult): Boolean {
            val q=RouteContinuity.analyze(route.geometryPoints)
            return q.repeatedMeters <= anchorQuality.repeatedMeters+15 && q.closedLoops <= anchorQuality.closedLoops &&
                (route.maneuvers.count { it.modifier=="uturn" } <= anchor.maneuvers.count { it.modifier=="uturn" } || !q.suspicious)
        }
        val completed=withTimeoutOrNull(refinementBudgetMillis) {
            coroutineScope {
                plan.corridors.map { corridor -> async {
                    limiter.withPermit {
                        val via=corridor.drop(1).dropLast(1)
                        val first=fetch(listOf(origin)+via+destination,false)
                        var accepted=first.filter(::acceptable)
                        rejected.addAndGet(first.size-accepted.size)
                        if(accepted.isEmpty() && via.size>1) {
                            val relaxed=via.filterIndexed { index,_->index%2==1 }
                            val retry=fetch(listOf(origin)+relaxed+destination,false)
                            accepted=retry.filter(::acceptable)
                            rejected.addAndGet(retry.size-accepted.size)
                        }
                        candidates.addAll(accepted)
                    }
                } }.awaitAll()
            }
            true
        }
        expired=completed==null
        val rawSnapshot=synchronized(candidates) { candidates.toList() }.distinctBy { it.geoJsonLineString }
        val withoutReturns=rawSnapshot.filter { !RouteContinuity.analyze(it.geometryPoints).suspicious }
        val snapshot=withoutReturns.ifEmpty { rawSnapshot }
        val scored=snapshot.map {
            currentCoroutineContext().ensureActive()
            val metrics=RouteSafetyEvaluator.evaluate(it.geometryPoints,plan.cctvs,plan.lights) { searchContext.ensureActive() }
            val q=RouteContinuity.analyze(it.geometryPoints)
            val warnings=buildList {
                if(q.suspicious) add("왕복 구간 포함")
                if(metrics.coveredRatio<0.05) add("시설 자료 부족")
                if(expired || problems.isNotEmpty()) add("일부 대안 경로 미확인")
                it.snapDistancesMeters.firstOrNull()?.takeIf { d -> d>15 }?.let { d -> add("출발 보행로 연결 약 ${kotlin.math.ceil(d).toInt()}m") }
                it.snapDistancesMeters.lastOrNull()?.takeIf { d -> d>15 }?.let { d -> add("도착 보행로 연결 약 ${kotlin.math.ceil(d).toInt()}m") }
            }
            val assessment = datasets?.let { data -> FiveFacilityEvaluator.evaluate(it.geometryPoints,data) { searchContext.ensureActive() } }
            it.copy(assessment=assessment,safetyFacilityScore=assessment?.score ?: if(assessment == null) metrics.score else 0.0,cctvCount=metrics.cctvCount,streetlightCount=metrics.streetlightCount,
                safetyMetrics=metrics,routeWarnings=warnings)
        }
        NavigationRepository.selectRoutes(scored).copy(diagnostics=diagnostics())
    }
}
