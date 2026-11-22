package com.example.safepath_test1.ui.home

import com.example.safepath_test1.api.SelectedRouteBody
import com.example.safepath_test1.model.GeoPoint
import com.example.safepath_test1.wear.haversineMeters
import kotlin.math.cos

internal data class JourneyProgress(val remainingMeters: Double, val remainingSeconds: Double)

internal fun calculateJourneyProgress(route: SelectedRouteBody, location: GeoPoint): JourneyProgress? {
    val points = route.coordinates.map { GeoPoint(it.latitude, it.longitude) }
    if (points.size < 2 || route.distanceMeters <= 0 || route.durationSeconds <= 0) return null
    val lengths = points.zipWithNext { a, b -> haversineMeters(a, b) }
    val total = lengths.sum()
    if (!total.isFinite() || total <= 0) return null
    val latitudeScale = 111_195.0
    val longitudeScale = latitudeScale * cos(Math.toRadians(location.latitude))
    var nearestDistance = Double.POSITIVE_INFINITY
    var nearestIndex = 0
    var nearestFraction = 0.0
    points.zipWithNext().forEachIndexed { index, (a, b) ->
        val ax = (a.longitude - location.longitude) * longitudeScale
        val ay = (a.latitude - location.latitude) * latitudeScale
        val dx = (b.longitude - a.longitude) * longitudeScale
        val dy = (b.latitude - a.latitude) * latitudeScale
        val squaredLength = dx * dx + dy * dy
        val fraction = if (squaredLength == 0.0) 0.0 else (-(ax * dx + ay * dy) / squaredLength).coerceIn(0.0, 1.0)
        val distance = (ax + fraction * dx) * (ax + fraction * dx) + (ay + fraction * dy) * (ay + fraction * dy)
        if (distance < nearestDistance) {
            nearestDistance = distance
            nearestIndex = index
            nearestFraction = fraction
        }
    }
    val remaining = lengths[nearestIndex] * (1 - nearestFraction) + lengths.drop(nearestIndex + 1).sum()
    val fraction = (remaining / total).coerceIn(0.0, 1.0)
    return JourneyProgress(route.distanceMeters * fraction, route.durationSeconds * fraction)
}
