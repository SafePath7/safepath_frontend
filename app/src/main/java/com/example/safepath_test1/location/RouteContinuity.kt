package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import kotlin.math.*

/** Geometric guard, not a replacement for routing on real roads. Never splices a displayed route. */
internal object RouteContinuity {
    private data class XY(val x: Double, val y: Double)
    private data class Visit(val p: XY, val arc: Double, val dx: Double, val dy: Double)
    private fun project(points: List<GeoPoint>): List<XY> {
        if (points.isEmpty()) return emptyList()
        val anchor = points.first()
        val xScale = 111320.0 * cos(Math.toRadians(anchor.latitude))
        return points.map { XY((it.longitude - anchor.longitude) * xScale, (it.latitude - anchor.latitude) * 111320.0) }
    }

    data class Analysis(val repeatedMeters: Double, val longestRepeatedMeters: Double, val closedLoops: Int) {
        val suspicious: Boolean get() = longestRepeatedMeters >= 20 || closedLoops > 0
    }
    fun hasRetracing(points: List<GeoPoint>): Boolean = analyze(points).suspicious
    fun analyze(points: List<GeoPoint>): Analysis {
        val xy = project(points)
        if (xy.size < 2) return Analysis(0.0,0.0,0)
        val visits = HashMap<Pair<Int, Int>, MutableList<Visit>>()
        var arc = 0.0; var nextSample = 0.0; var repeated = 0.0; var repeatedTotal = 0.0; var longestRepeated = 0.0; var loops = 0
        val total = xy.zipWithNext().sumOf { (a,b) -> hypot(b.x-a.x, b.y-a.y) }
        val step = max(5.0, total / 40000.0)
        val tolerance = 3.0
        for (i in 1 until xy.size) {
            val a = xy[i-1]; val b = xy[i]
            val length = hypot(b.x-a.x, b.y-a.y)
            if (length < 0.01) continue
            val dx = (b.x-a.x)/length; val dy = (b.y-a.y)/length
            while (nextSample <= arc + length) {
                val offset = (nextSample - arc).coerceIn(0.0, length)
                val p = XY(a.x+dx*offset, a.y+dy*offset)
                val key = floor(p.x/5).toInt() to floor(p.y/5).toInt()
                var seen = false
                for (x in -1..1) for (y in -1..1) {
                    if (visits[(key.first+x) to (key.second+y)]?.any {
                        nextSample-it.arc >= max(30.0, step*5) &&
                            hypot(p.x-it.p.x, p.y-it.p.y) <= tolerance && abs(dx*it.dx+dy*it.dy) >= 0.9
                    } == true) seen = true
                }
                repeated = if (seen) repeated + step else 0.0
                if (seen) repeatedTotal += step
                longestRepeated = max(longestRepeated,repeated)
                visits.getOrPut(key) { mutableListOf() }.add(Visit(p, nextSample, dx, dy))
                nextSample += step
            }
            arc += length
        }
        // A closed detour can return to a junction without retracing any of its edges.
        val junctions = HashMap<Pair<Int,Int>, MutableList<Pair<XY,Double>>>()
        arc = 0.0
        xy.forEachIndexed { i,p ->
            if (i > 0) arc += hypot(p.x-xy[i-1].x,p.y-xy[i-1].y)
            val key = floor(p.x).toInt() to floor(p.y).toInt()
            for (x in -1..1) for (y in -1..1) {
                if (junctions[(key.first+x) to (key.second+y)]?.any { (old,at) ->
                    arc-at >= 40 && hypot(p.x-old.x,p.y-old.y) <= 1.0
                } == true) loops++
            }
            junctions.getOrPut(key) { mutableListOf() }.add(p to arc)
        }
        return Analysis(repeatedTotal,longestRepeated,loops)
    }

    /** Reduce forcing points while retaining side detours; no backwards ordering or overshooting. */
    fun simplifyWaypoints(points: List<GeoPoint>): List<GeoPoint> {
        if (points.size < 3) return points
        val xy = project(points)
        val end = xy.last(); val direct = hypot(end.x,end.y)
        if (direct < 1.0) return listOf(points.first(),points.last())
        val keep = sortedSetOf(0,points.lastIndex)
        val tolerance = (direct * 0.02).coerceIn(25.0,80.0)
        fun split(first: Int,last: Int) {
            if (last-first <= 1) return
            val a=xy[first]; val b=xy[last]; val dx=b.x-a.x; val dy=b.y-a.y
            val squared=dx*dx+dy*dy
            var maximum=0.0; var chosen=-1
            for (i in first+1 until last) {
                val p=xy[i]
                val t=if(squared==0.0) 0.0 else (((p.x-a.x)*dx+(p.y-a.y)*dy)/squared).coerceIn(0.0,1.0)
                val distance=hypot(p.x-a.x-t*dx,p.y-a.y-t*dy)
                if(distance>maximum) { maximum=distance; chosen=i }
            }
            if(maximum>tolerance && chosen>=0) { keep.add(chosen); split(first,chosen); split(chosen,last) }
        }
        split(0,points.lastIndex)
        var progress=0.0
        val interior=keep.filter { it!=0 && it!=points.lastIndex }.filter { i ->
            val p=xy[i]; val along=(p.x*end.x+p.y*end.y)/(direct*direct)
            val accepted=along>progress+0.02 && along<0.98 && hypot(p.x,p.y)>30 && hypot(p.x-end.x,p.y-end.y)>30
            if(accepted) progress=along
            accepted
        }
        val limited=if(interior.size<=8) interior else (0..7).map { interior[(it*(interior.size-1)/7.0).roundToInt()] }
        return listOf(points.first())+limited.map { points[it] }+points.last()
    }
}
