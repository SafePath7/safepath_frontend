package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import java.util.PriorityQueue
import kotlin.math.*

/** Facility-field search only. Its grid edges are NOT walkable roads: Directions must resolve them. */
internal object FacilityRoutePlanner {
    data class Area(val center: GeoPoint, val radiusMeters: Double) {
        private val latPadding = radiusMeters / 110000.0
        private val lngPadding = radiusMeters / (110000.0 * cos(Math.toRadians((abs(center.latitude)+latPadding).coerceAtMost(89.9)))).coerceAtLeast(1.0)
        fun contains(point: GeoPoint): Boolean = abs(point.latitude-center.latitude)<=latPadding+1e-6 &&
            abs(point.longitude-center.longitude)<=lngPadding+1e-6 && distance(center,point)<=radiusMeters+0.01
    }
    data class Plan(val area: Area, val cctvs: List<SafetyFacility>, val lights: List<SafetyFacility>, val corridors: List<List<GeoPoint>>)

    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lng / 2).pow(2)
        return 6371000.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun area(origin: GeoPoint, destination: GeoPoint): Area {
        require(listOf(origin, destination).all { it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 })
        // Midpoint of the user's imaginary straight segment, not the midpoint along a road route.
        return Area(GeoPoint((origin.latitude + destination.latitude) / 2, (origin.longitude + destination.longitude) / 2), 2 * distance(origin, destination))
    }

    fun plan(origin: GeoPoint, destination: GeoPoint, cctvs: List<SafetyFacility>, lights: List<SafetyFacility>, checkCancelled: () -> Unit = {}): Plan {
        val area = area(origin, destination)
        fun inside(f: SafetyFacility) = area.contains(GeoPoint(f.latitude, f.longitude))
        val cameras = cctvs.filterIndexed { i,f -> if(i % 1024 == 0) checkCancelled(); inside(f) }
        val lamps = lights.filterIndexed { i,f -> if(i % 1024 == 0) checkCancelled(); inside(f) }
        if (area.radiusMeters < 1 || cameras.isEmpty() && lamps.isEmpty()) return Plan(area, cameras, lamps, emptyList())
        // Bounded grid complexity; EVERY in-circle facility contributes, without a marker-count cap.
        val halfCells = ceil(area.radiusMeters / 50.0).toInt().coerceIn(8,50)
        val n = halfCells * 2 + 1
        val step = area.radiusMeters / halfCells
        val latScale = 111320.0
        val lngScale = latScale * cos(Math.toRadians(area.center.latitude)).coerceAtLeast(0.001)
        fun point(id: Int) = GeoPoint(area.center.latitude + (id / n - halfCells) * step / latScale, area.center.longitude + (id % n - halfCells) * step / lngScale)
        fun cell(p: GeoPoint): Int {
            val x = ((p.longitude - area.center.longitude) * lngScale / step + halfCells).roundToInt().coerceIn(0, n - 1)
            val y = ((p.latitude - area.center.latitude) * latScale / step + halfCells).roundToInt().coerceIn(0, n - 1)
            return y * n + x
        }
        val weights = DoubleArray(n * n)
        val representatives = arrayOfNulls<GeoPoint>(n * n)
        fun add(f: SafetyFacility, weight: Double) {
            val p = GeoPoint(f.latitude, f.longitude)
            val id = cell(p)
            weights[id] += weight
            val previous = representatives[id]
            if (previous == null || distance(p, point(id)) < distance(previous, point(id))) representatives[id] = p
        }
        cameras.forEach { add(it, 4.0) }; lamps.forEach { add(it, 1.0) }
        val valid = BooleanArray(n * n) { area.contains(point(it)) }
        val start = cell(origin); val end = cell(destination)
        valid[start] = true; valid[end] = true
        // Smooth only adjacent cells; all facilities retain their accumulated contribution.
        val field = DoubleArray(n * n) { id ->
            var sum = 0.0
            for (dy in -1..1) for (dx in -1..1) {
                val x = id % n + dx; val y = id / n + dy
                if (x in 0 until n && y in 0 until n) sum += weights[y * n + x] / (1 + abs(dx) + abs(dy))
            }
            sum * 2500.0 / (step * step).coerceAtLeast(1.0)
        }
        data class Entry(val id: Int, val cost: Double)
        fun corridor(safetyWeight: Double): List<GeoPoint> {
            val costs = DoubleArray(n * n) { Double.POSITIVE_INFINITY }
            val parents = IntArray(n * n) { -1 }
            val queue = PriorityQueue<Entry>(compareBy { it.cost })
            costs[start] = 0.0; queue.add(Entry(start, 0.0))
            while (queue.isNotEmpty()) {
                checkCancelled()
                val current = queue.remove()
                if (current.cost > costs[current.id]) continue
                if (current.id == end) break
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val x = current.id % n + dx; val y = current.id / n + dy
                    if (x !in 0 until n || y !in 0 until n) continue
                    val next = y * n + x
                    if (!valid[next]) continue
                    val density = (field[current.id] + field[next]) / 2
                    val cost = current.cost + hypot(dx.toDouble(), dy.toDouble()) * step * ((1 - safetyWeight) + safetyWeight / (1 + density))
                    if (cost < costs[next]) { costs[next] = cost; parents[next] = current.id; queue.add(Entry(next, cost)) }
                }
            }
            if (!costs[end].isFinite()) return emptyList()
            val ids = mutableListOf<Int>(); var id = end
            while (id != start) { ids.add(id); id = parents[id]; if (id < 0) return emptyList() }
            ids.add(start); ids.reverse()
            val turns = ids.filterIndexed { i, v -> i == 0 || i == ids.lastIndex || v - ids[i - 1] != ids[i + 1] - v }
            // Keep at most 23 interior coordinates to honor Directions' 25-coordinate limit.
            val interior = turns.drop(1).dropLast(1)
            val selected = if (interior.size <= 23) interior else (0 until 23).map { interior[(it * (interior.size - 1) / 22.0).roundToInt()] }
            return RouteContinuity.simplifyWaypoints((listOf(origin) + selected.map { representatives[it] ?: point(it) } + destination).distinct())
        }
        return Plan(area, cameras, lamps, listOf(0.98, 0.75, NavigationRepository.recommendedSafetyWeight).map(::corridor).filter { it.size > 2 }.distinct())
    }
}
