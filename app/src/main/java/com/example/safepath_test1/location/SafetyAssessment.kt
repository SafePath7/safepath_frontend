package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import kotlin.math.*

enum class FacilityType { CCTV, LIGHT, POLICE, BELL, FIRE }
enum class DataAvailability { AVAILABLE, FAILED, OUTSIDE_COVERAGE, UNKNOWN_COVERAGE, NOT_RECEIVED }
enum class AssessmentStatus { NORMAL, PARTIAL, UNAVAILABLE }
data class FacilityDataset(
    val type: FacilityType, val facilities: List<SafetyFacility>, val source: String,
    val providedRegion: String, val updatedAt: String? = null,
    val availability: DataAvailability, val detail: String? = null,
) {
    val loadingSucceeded: Boolean get() = availability == DataAvailability.AVAILABLE || availability == DataAvailability.UNKNOWN_COVERAGE || availability == DataAvailability.OUTSIDE_COVERAGE
    val usableForScoring: Boolean get() = availability == DataAvailability.AVAILABLE
}
data class FacilityContribution(val type: FacilityType, val normalizedScore: Double?, val method: String)
data class SafetyAssessment(val score: Double?, val status: AssessmentStatus,
    val contributions: List<FacilityContribution>, val datasets: List<FacilityDataset>)

/** Provisional engineering parameters; not calibrated crime-prevention effects. */
object SafetyPolicy {
    const val maxTimeRatio = 1.20
    const val maxExtraSeconds = 300.0
    val weights = mapOf(FacilityType.CCTV to .25, FacilityType.LIGHT to .35,
        FacilityType.POLICE to .15, FacilityType.BELL to .20, FacilityType.FIRE to .05)
    fun withinDetour(seconds: Double, fastest: Double) = seconds <= fastest * maxTimeRatio + 1e-6 &&
        seconds - fastest <= maxExtraSeconds + 1e-6
}

internal object FiveFacilityEvaluator {
    private fun cctv(count: Int) = 1-exp(-count.toDouble())
    private fun light(count: Int) = if(count>0) 1.0 else 0.0
    private fun police(distance: Double) = proximity(distance,500.0)
    private fun bell(distance: Double) = proximity(distance,100.0)
    private fun fire(distance: Double) = proximity(distance,500.0)
    private fun proximity(distance: Double, scale: Double) = if(distance.isFinite()) exp(-distance/scale) else 0.0
    fun evaluate(points: List<GeoPoint>, datasets: List<FacilityDataset>, cancelled: () -> Unit = {}): SafetyAssessment {
        val samples = mutableListOf<GeoPoint>()
        val lengths = mutableListOf<Double>()
        points.zipWithNext().forEach { (a,b) ->
            val length = FacilityRoutePlanner.distance(a,b)
            if (length > .01) {
                val count = ceil(length / 25).toInt()
                repeat(count) { i ->
                    val t = (i + .5) / count
                    samples.add(GeoPoint(a.latitude+(b.latitude-a.latitude)*t,a.longitude+(b.longitude-a.longitude)*t))
                    lengths.add(length/count)
                }
            }
        }
        val total = lengths.sum()
        val contributions = FacilityType.entries.map { type ->
            val dataset = datasets.firstOrNull { it.type == type }
            val score = if (dataset?.availability != DataAvailability.AVAILABLE || total <= 0) null else {
                var sum = 0.0
                var gap = 0.0
                var longestGap = 0.0
                val cellSize = when(type) { FacilityType.CCTV -> 50.0; FacilityType.LIGHT -> 40.0; FacilityType.BELL -> 100.0; else -> 500.0 }
                val anchor = points.first()
                val longitudeScale = 111320*cos(Math.toRadians(anchor.latitude))
                fun cell(latitude: Double,longitude: Double) = floor((latitude-anchor.latitude)*111320/cellSize).toInt() to
                    floor((longitude-anchor.longitude)*longitudeScale/cellSize).toInt()
                val minLat = points.minOf { it.latitude } - 1600.0/111320.0
                val maxLat = points.maxOf { it.latitude } + 1600.0/111320.0
                val longitudePadding = 1600.0 / abs(longitudeScale).coerceAtLeast(1.0)
                val minLng = points.minOf { it.longitude } - longitudePadding
                val maxLng = points.maxOf { it.longitude } + longitudePadding
                val index = dataset.facilities.asSequence().filterIndexed { i, f ->
                    if(i % 1024 == 0) cancelled()
                    f.latitude in minLat..maxLat && f.longitude in minLng..maxLng
                }.groupBy { cell(it.latitude,it.longitude) }
                samples.forEachIndexed { i, p ->
                    cancelled()
                    // Proximity only: no visibility, lighting, opening hours or response-time assumptions.
                    val radius = when(type) { FacilityType.CCTV -> 50.0; FacilityType.LIGHT -> 40.0
                        FacilityType.BELL -> 100.0; FacilityType.POLICE -> 500.0; FacilityType.FIRE -> 500.0 }
                    var nearest = Double.POSITIVE_INFINITY
                    var count = 0
                    val (y,x) = cell(p.latitude,p.longitude)
                    for(dy in -3..3) for(dx in -3..3) index[(y+dy) to (x+dx)].orEmpty().forEach { f ->
                        run {
                            val d = FacilityRoutePlanner.distance(p,GeoPoint(f.latitude,f.longitude))
                            nearest = min(nearest,d)
                            if (d <= radius) count++
                        }
                    }
                    if(type == FacilityType.LIGHT && count == 0) { gap += lengths[i]; longestGap = max(longestGap,gap) } else gap = 0.0
                    val value = when(type) {
                        FacilityType.CCTV -> cctv(count)
                        FacilityType.LIGHT -> light(count)
                        FacilityType.POLICE -> police(nearest)
                        FacilityType.BELL -> bell(nearest)
                        FacilityType.FIRE -> fire(nearest)
                    }
                    sum += value * lengths[i]
                }
                ((sum/total) * if(type == FacilityType.LIGHT) exp(-longestGap/500) else 1.0).coerceIn(0.0,1.0)
            }
            FacilityContribution(type,score, when(type) {
                FacilityType.CCTV -> "CCTV 위치 인접·포화 지표, 촬영 범위 미확인"
                FacilityType.LIGHT -> "가로등 위치 분포, 작동·조도 미확인"
                else -> "직선거리 보조 지표, 보행 접근·운영 상태 미확인"
            })
        }
        // At least one environmental dataset must have confirmed coverage. Missing data never becomes zero.
        val eligible = contributions.filter { it.normalizedScore != null }
        val status = if(eligible.none { it.type == FacilityType.CCTV || it.type == FacilityType.LIGHT }) AssessmentStatus.UNAVAILABLE
            else if(eligible.size == FacilityType.entries.size) AssessmentStatus.NORMAL else AssessmentStatus.PARTIAL
        val weight = eligible.sumOf { SafetyPolicy.weights.getValue(it.type) }
        val score = if(status == AssessmentStatus.UNAVAILABLE || weight == 0.0) null else
            100 * eligible.sumOf { it.normalizedScore!! * SafetyPolicy.weights.getValue(it.type) } / weight
        return SafetyAssessment(score,status,contributions,datasets)
    }
}
