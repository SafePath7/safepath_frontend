package com.example.safepath_test1.wear.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** lat to first, lng to second - matches the phone's WearMessenger "lat,lng" encoding. */
typealias LatLng = Pair<Double, Double>

/**
 * Draws a lightweight sketch of the active route directly from the lat/lng points WearMessenger
 * (app module) sends over the Wearable Data Layer - no map SDK on the watch. The route polyline
 * is static per route, but [current] is expected to move every time a fresh DataItem arrives
 * (the phone resends it roughly every 5s/5m as the traveler walks), so this redraws the
 * current-location dot along the path rather than just showing a one-time snapshot.
 */
@Composable
fun RouteMiniMap(
    routePoints: List<LatLng>,
    destination: LatLng?,
    current: LatLng?,
    modifier: Modifier = Modifier,
) {
    if (routePoints.size < 2) return

    Canvas(modifier = modifier.size(120.dp)) {
        val allPoints = buildList {
            addAll(routePoints)
            destination?.let { add(it) }
            current?.let { add(it) }
        }
        val minLat = allPoints.minOf { it.first }
        val maxLat = allPoints.maxOf { it.first }
        val minLng = allPoints.minOf { it.second }
        val maxLng = allPoints.maxOf { it.second }
        // Floor so a near-point route (or a single point) doesn't divide by ~0.
        val latSpan = max(maxLat - minLat, 0.0002)
        val lngSpan = max(maxLng - minLng, 0.0002)
        val padding = size.minDimension * 0.14f

        fun project(point: LatLng): Offset {
            val xFraction = ((point.second - minLng) / lngSpan).toFloat()
            val yFraction = ((point.first - minLat) / latSpan).toFloat()
            return Offset(
                x = padding + xFraction * (size.width - 2 * padding),
                // Latitude increases northward (up); canvas y increases downward, so flip.
                y = padding + (1 - yFraction) * (size.height - 2 * padding),
            )
        }

        val path =
            Path().apply {
                routePoints.forEachIndexed { index, point ->
                    val offset = project(point)
                    if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                }
            }
        drawPath(path, color = Color(0xFF5FA8D3), style = Stroke(width = 6f))

        // Start of route.
        drawCircle(color = Color(0xFF9AA5AF), radius = 7f, center = project(routePoints.first()))

        destination?.let { drawCircle(color = Color(0xFFDC2626), radius = 9f, center = project(it)) }

        current?.let {
            val center = project(it)
            drawCircle(color = Color(0xFF16A34A), radius = 11f, center = center)
            drawCircle(color = Color.White, radius = 4f, center = center)
        }
    }
}
