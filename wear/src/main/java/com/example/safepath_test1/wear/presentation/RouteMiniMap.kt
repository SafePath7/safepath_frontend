package com.example.safepath_test1.wear.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** lat to first, lng to second - matches the phone's WearMessenger "lat,lng" encoding. */
typealias LatLng = Pair<Double, Double>

/**
 * 길안내(W-01) 화면 전체를 채우는 지도. 폰이 WearMessenger로 보낸 위경도 점으로 경로를 그린다.
 * 지나온 경로는 파랑(#5FA8D3) 실선, 남은 경로는 흰 점선, 출발점 작은 점, 현재 위치 점(번지는 테두리), 목적지 깃발.
 * 현재 위치가 아직 오지 않았으면 경로 전체를 실선으로만 그린다.
 * [background]가 있으면 폰이 보낸 Mapbox 지도 이미지를 깔고 그 위에 같은 좌표로 경로를 겹친다.
 * 이때는 현재 위치가 가운데 오도록 지름 [viewMeters]미터만 잘라 보여 주고, 걸으면 창이 따라 움직인다.
 * 사용자가 끌어서 둘러볼 때는 [panEastMeters]/[panNorthMeters]만큼 창을 옮긴다.
 * 이미지가 없거나 낡았으면 도로 없는 스케치 지도로 경로 전체를 그린다.
 * 폰은 약 5초/5m마다 현재 위치를 다시 보내므로 걸을 때마다 점과 실선·점선 경계가 움직인다.
 */
@Composable
internal fun RouteMiniMap(
    routePoints: List<LatLng>,
    destination: LatLng?,
    current: LatLng?,
    modifier: Modifier = Modifier,
    background: MapBackground? = null,
    viewMeters: Double = 1000.0,
    panEastMeters: Double = 0.0,
    panNorthMeters: Double = 0.0,
) {
    if (routePoints.size < 2) return

    Canvas(modifier = modifier.clip(CircleShape).background(WatchColors.Map)) {
        val extent = buildList {
            addAll(routePoints)
            destination?.let { add(it) }
            current?.let { add(it) }
        }
        val minLat = extent.minOf { it.first }
        val maxLat = extent.maxOf { it.first }
        val minLng = extent.minOf { it.second }
        val maxLng = extent.maxOf { it.second }

        // 위도와 경도 1도의 실제 길이가 달라서, 경도에 cos(위도)를 곱해 가로세로 비율을 맞춘다.
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLng = 111_320.0 * cos(Math.toRadians((minLat + maxLat) / 2))
        // 점이 거의 한 곳에 모여 있어도 0으로 나누지 않도록 최소 폭을 둔다.
        val heightMeters = max((maxLat - minLat) * metersPerDegreeLat, 20.0)
        val widthMeters = max((maxLng - minLng) * metersPerDegreeLng, 20.0)

        val padding = size.minDimension * 0.2f // 원 안에 깃발까지 들어오도록 넉넉히
        val scale =
            min(
                (size.width - 2 * padding).toDouble() / widthMeters,
                (size.height - 2 * padding).toDouble() / heightMeters,
            )
        val offsetX = ((size.width - widthMeters * scale) / 2).toFloat()
        val offsetY = ((size.height - heightMeters * scale) / 2).toFloat()

        // 지도 이미지는 현재 위치(없으면 경로 시작점) 둘레를 보여 줄 수 있을 때만 쓴다.
        val projection = background?.projection
        val window = projection?.windowAround(
                current ?: routePoints.first(),
                viewMeters,
                panEastMeters,
                panNorthMeters,
            )

        fun project(point: LatLng): Offset =
            if (projection != null && window != null) {
                (projection.project(point) - Offset(window.left.toFloat(), window.top.toFloat())) *
                    (size.width / window.size)
            } else {
                Offset(
                    x = ((point.second - minLng) * metersPerDegreeLng * scale).toFloat() + offsetX,
                    // 위도는 북쪽이 위인데 화면 y는 아래로 늘어나므로 뒤집는다.
                    y = ((maxLat - point.first) * metersPerDegreeLat * scale).toFloat() + offsetY,
                )
            }

        if (background != null && window != null) {
            drawImage(
                image = background.image,
                srcOffset = IntOffset(window.left, window.top),
                srcSize = IntSize(window.size, window.size),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
                colorFilter = MapColorFilter,
            )
        }

        // 화면 전체 크기 기준이라 예전 작은 원일 때보다 선·점을 가늘게 잡았다.
        val lineWidth = size.minDimension * 0.016f
        val solidStroke = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        if (current == null) {
            drawPath(pathOf(routePoints.map { project(it) }), WatchColors.Route, style = solidStroke)
        } else {
            val split = computeRouteProgress(routePoints, current).traveledIndex
            val currentOffset = project(current)
            val traveled = routePoints.subList(0, split + 1).map { project(it) } + currentOffset
            val remaining =
                listOf(currentOffset) +
                    routePoints.subList(split + 1, routePoints.size).map { project(it) }
            drawPath(pathOf(traveled), WatchColors.Route, style = solidStroke)
            drawPath(
                pathOf(remaining),
                Color.White,
                style =
                    Stroke(
                        width = lineWidth * 0.8f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect =
                            PathEffect.dashPathEffect(
                                floatArrayOf(lineWidth * 1.4f, lineWidth * 2.4f)
                            ),
                    ),
            )
        }

        // 출발점: 작은 점.
        val startOffset = project(routePoints.first())
        drawCircle(Color.White, radius = lineWidth * 1.1f, center = startOffset)
        drawCircle(WatchColors.Surface, radius = lineWidth * 0.6f, center = startOffset)

        // 목적지 깃발: 막대 하나와 삼각형 깃발.
        val flagBase = project(destination ?: routePoints.last())
        val poleHeight = size.minDimension * 0.09f
        drawLine(
            color = Color.White,
            start = flagBase,
            end = Offset(flagBase.x, flagBase.y - poleHeight),
            strokeWidth = lineWidth * 0.6f,
            cap = StrokeCap.Round,
        )
        drawPath(
            Path().apply {
                moveTo(flagBase.x, flagBase.y - poleHeight)
                lineTo(flagBase.x + poleHeight * 0.7f, flagBase.y - poleHeight * 0.75f)
                lineTo(flagBase.x, flagBase.y - poleHeight * 0.5f)
                close()
            },
            WatchColors.Success,
        )

        // 현재 위치: 번지는 테두리 + 흰 테두리가 있는 파란 점.
        current?.let {
            val center = project(it)
            val radius = size.minDimension * 0.022f
            drawCircle(WatchColors.Route.copy(alpha = 0.25f), radius = radius * 3f, center = center)
            drawCircle(Color.White, radius = radius + lineWidth * 0.6f, center = center)
            drawCircle(WatchColors.Route, radius = radius, center = center)
        }
    }
}

private fun pathOf(offsets: List<Offset>): Path =
    Path().apply {
        offsets.forEachIndexed { index, offset ->
            if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
        }
    }

/**
 * 어두운 지도(dark-v11)를 워치 남색 계열로 바꾸면서 도로가 잘 보이게 밝기를 올리는 색 보정.
 * 값은 행마다 [곱할 값 ..., 더할 값(0~255)]이다. 지도가 너무 어둡거나 밝으면 이 숫자만 조정하면 된다.
 */
private val MapColorFilter =
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.9f, 0f, 0f, 0f, -6f,
                0f, 1.25f, 0f, 0f, 6f,
                0f, 0f, 1.55f, 0f, 14f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
