package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import kotlin.math.*

data class RouteSafetyMetrics(
    val score: Double,
    val coveredRatio: Double,
    val cctvCoveredRatio: Double,
    val lightCoveredRatio: Double,
    val uncoveredMeters: Double,
    val longestUncoveredMeters: Double,
    val cctvCount: Int,
    val streetlightCount: Int,
    val sampledLengthMeters: Double,
)

/** Coverage is a facility-data proxy, never a probability of being safe. */
internal object RouteSafetyEvaluator {
    private data class P(val x: Double, val y: Double)
    fun evaluate(points: List<GeoPoint>, cameras: List<SafetyFacility>, lights: List<SafetyFacility>, checkCancelled: () -> Unit = {}): RouteSafetyMetrics {
        fun empty() = RouteSafetyMetrics(0.0,0.0,0.0,0.0,0.0,0.0,0,0,0.0)
        if (points.size < 2) return empty()
        val anchor=points.first(); val scaleX=111320*cos(Math.toRadians(anchor.latitude))
        fun project(lat: Double,lng: Double)=P((lng-anchor.longitude)*scaleX,(lat-anchor.latitude)*111320)
        val path=points.map { project(it.latitude,it.longitude) }
        class Index(facilities: List<SafetyFacility>) {
            val positions=facilities.mapIndexed { i,it -> if(i % 1024 == 0) checkCancelled(); project(it.latitude,it.longitude) }
            val cells=positions.indices.groupBy { floor(positions[it].x/50).toInt() to floor(positions[it].y/50).toInt() }
            val used=HashSet<Int>()
            fun near(p: P,radius: Double): Boolean {
                val x=floor(p.x/50).toInt(); val y=floor(p.y/50).toInt(); var found=false
                for(dx in -1..1) for(dy in -1..1) for(i in cells[(x+dx) to (y+dy)].orEmpty()) {
                    val f=positions[i]
                    if(hypot(p.x-f.x,p.y-f.y)<=radius) { used.add(i); found=true }
                }
                return found
            }
        }
        val cameraIndex=Index(cameras); val lightIndex=Index(lights)
        var total=0.0; var covered=0.0; var cameraCovered=0.0; var lightCovered=0.0
        var gap=0.0; var longest=0.0
        for(i in 1 until path.size) {
            val a=path[i-1]; val b=path[i]; val length=hypot(b.x-a.x,b.y-a.y)
            if(length<0.01) continue
            // Length-weighted midpoint samples, at most 10m apart, independent of API vertex spacing.
            val count=ceil(length/10).toInt().coerceAtLeast(1); val part=length/count
            for(j in 0 until count) {
                if(j % 128 == 0) checkCancelled()
                val t=(j+0.5)/count; val p=P(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t)
                val c=cameraIndex.near(p,50.0); val l=lightIndex.near(p,40.0)
                total+=part
                if(c) cameraCovered+=part
                if(l) lightCovered+=part
                if(c||l) { covered+=part; gap=0.0 } else { gap+=part; longest=max(longest,gap) }
            }
        }
        if(total==0.0) return empty()
        val unprotected=(total-covered).coerceAtLeast(0.0)
        val coverage=0.45*covered/total+0.35*lightCovered/total+0.20*cameraCovered/total
        val score=(100*coverage*exp(-longest/500)*exp(-unprotected/2000)).coerceIn(0.0,100.0)
        return RouteSafetyMetrics(score,covered/total,cameraCovered/total,lightCovered/total,
            unprotected,longest,cameraIndex.used.size,lightIndex.used.size,total)
    }
}
