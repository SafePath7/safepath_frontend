package com.example.safepath_test1.wear.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** 위치가 새로 왔을 때 점과 지도가 이전 자리에서 새 자리로 미끄러지는 시간(ms). */
private const val GLIDE_DURATION_MS = 1_000

/** 이보다 멀리(m) 떨어진 새 위치는 미끄러지지 않고 바로 옮긴다(처음 위치를 잡았을 때, GPS가 크게 튀었을 때). */
private const val MAX_GLIDE_METERS = 150.0

/**
 * 지도 보기 상태: 보는 지름(핀치)과 내 위치에서 끌어서 옮긴 거리.
 * 값은 지도를 그리는 단계에서만 읽는다. 그래서 손가락을 움직여 값이 바뀌어도 화면 전체를 다시 계산하지 않고
 * 지도 그림만 다시 그린다.
 */
@Stable
internal class MapViewState {
    var viewMeters by mutableStateOf(DEFAULT_VIEW_METERS)
    var panEastMeters by mutableStateOf(0.0)
    var panNorthMeters by mutableStateOf(0.0)

    /** 마지막으로 손가락으로 지도를 움직인 시각(ms). 0이면 아직 없음. */
    var lastTouchAt by mutableStateOf(0L)

    /** 끌어서 옮긴 것만 되돌린다(확대 정도는 유지). */
    fun returnToCurrent() {
        panEastMeters = 0.0
        panNorthMeters = 0.0
    }

    /** 내 위치, 기본 확대로 되돌린다. */
    fun reset() {
        returnToCurrent()
        viewMeters = DEFAULT_VIEW_METERS
    }
}

/**
 * 화면에 그릴 현재 위치. 폰이 몇 초마다 보내는 위치 사이를 [progress](0..1)로 이어서 점이 뚝뚝 끊기지 않게 한다.
 * [progress]만 상태라서, 미끄러지는 동안에도 지도 그림만 다시 그린다.
 */
private class PositionGlide(initial: LatLng?) {
    var from: LatLng? = initial
    var to: LatLng? = initial
    val progress = Animatable(1f)

    fun displayed(): LatLng? {
        val end = to ?: return null
        val start = from ?: return end
        val t = progress.value
        return (start.first + (end.first - start.first) * t) to
            (start.second + (end.second - start.second) * t)
    }
}

/** 지도 이미지가 없을 때 쓰는 스케치 지도의 좌표 변환: 경로 전체가 화면에 들어오게 맞춘다. */
private class SketchProjection(points: List<LatLng>, private val width: Float, height: Float) {
    private val minLat = points.minOf { it.first }
    private val maxLat = points.maxOf { it.first }
    private val minLng = points.minOf { it.second }
    private val maxLng = points.maxOf { it.second }

    // 위도와 경도 1도의 실제 길이가 달라서, 경도에 cos(위도)를 곱해 가로세로 비율을 맞춘다.
    private val metersPerDegreeLat = 111_320.0
    private val metersPerDegreeLng = 111_320.0 * cos(Math.toRadians((minLat + maxLat) / 2))

    // 점이 거의 한 곳에 모여 있어도 0으로 나누지 않도록 최소 폭을 둔다.
    private val heightMeters = max((maxLat - minLat) * metersPerDegreeLat, 20.0)
    private val widthMeters = max((maxLng - minLng) * metersPerDegreeLng, 20.0)
    private val padding = min(width, height) * 0.2f // 원 안에 깃발까지 들어오도록 넉넉히
    private val scale =
        min(
            (width - 2 * padding).toDouble() / widthMeters,
            (height - 2 * padding).toDouble() / heightMeters,
        )
    private val offsetX = ((width - widthMeters * scale) / 2).toFloat()
    private val offsetY = ((height - heightMeters * scale) / 2).toFloat()

    fun project(point: LatLng): Offset =
        Offset(
            x = ((point.second - minLng) * metersPerDegreeLng * scale).toFloat() + offsetX,
            // 위도는 북쪽이 위인데 화면 y는 아래로 늘어나므로 뒤집는다.
            y = ((maxLat - point.first) * metersPerDegreeLat * scale).toFloat() + offsetY,
        )
}

/**
 * 길안내(W-01) 화면 전체를 채우는 지도. 폰이 WearMessenger로 보낸 위경도 점으로 경로를 그린다.
 * 지나온 경로는 파랑(#5FA8D3) 실선, 남은 경로는 흰 점선, 출발점 작은 점, 현재 위치 점(번지는 테두리), 목적지 깃발.
 * 현재 위치가 아직 오지 않았으면 경로 전체를 실선으로만 그린다.
 * [background]가 있으면 폰이 보낸 Mapbox 지도 이미지를 깔고 그 위에 같은 좌표로 경로를 겹친다.
 * 이때는 현재 위치가 가운데 오도록 [view]의 지름만큼만 잘라 보여 주고, 걸으면 창이 따라 움직인다.
 * 사용자가 끌어서 둘러볼 때는 [view]에 적힌 만큼 창을 옮긴다.
 * 이미지가 없거나 낡았으면 도로 없는 스케치 지도로 경로 전체를 그린다.
 * 폰은 약 5초/5m마다 현재 위치를 다시 보내고, 그 사이는 점이 새 위치로 미끄러지듯 이동한다.
 */
@Composable
internal fun RouteMiniMap(
    routePoints: List<LatLng>,
    destination: LatLng?,
    current: LatLng?,
    view: MapViewState,
    modifier: Modifier = Modifier,
    background: MapBackground? = null,
) {
    if (routePoints.size < 2) return

    val glide = remember { PositionGlide(current) }
    LaunchedEffect(current) {
        val shown = glide.displayed()
        glide.from =
            if (current != null && shown != null && haversineMeters(shown, current) <= MAX_GLIDE_METERS) {
                shown
            } else {
                current
            }
        glide.to = current
        if (current == null || glide.from == current) {
            glide.progress.snapTo(1f)
            return@LaunchedEffect
        }
        glide.progress.snapTo(0f)
        glide.progress.animateTo(1f, tween(GLIDE_DURATION_MS, easing = LinearOutSlowInEasing))
    }

    Canvas(modifier = modifier.clip(CircleShape).background(WatchColors.Map)) {
        // 아래에서 읽는 값(미끄러지는 위치, 확대, 끌기)이 바뀌면 이 그리기 블록만 다시 실행된다.
        val shownCurrent = if (current == null) null else glide.displayed() ?: current

        // 지도 이미지는 현재 위치(없으면 경로 시작점) 둘레를 보여 줄 수 있을 때만 쓴다.
        val projection = background?.projection
        val window =
            projection?.windowAround(
                shownCurrent ?: routePoints.first(),
                view.viewMeters,
                view.panEastMeters,
                view.panNorthMeters,
            )
        val sketch =
            if (projection == null || window == null) {
                SketchProjection(
                    buildList {
                        addAll(routePoints)
                        destination?.let { add(it) }
                        shownCurrent?.let { add(it) }
                    },
                    size.width,
                    size.height,
                )
            } else {
                null
            }

        fun project(point: LatLng): Offset =
            if (projection != null && window != null) {
                (projection.project(point) - Offset(window.left.toFloat(), window.top.toFloat())) *
                    (size.width / window.size)
            } else {
                sketch!!.project(point)
            }

        if (background != null && window != null) {
            drawImage(
                image = background.image,
                srcOffset = IntOffset(window.left, window.top),
                srcSize = IntSize(window.size, window.size),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
                colorFilter = MapColorFilter,
            )
        }

        // 화면 전체 크기 기준이라 예전 작은 원일 때보다 선·점을 가늘게 잡았다.
        val lineWidth = size.minDimension * 0.016f
        val solidStroke = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        if (shownCurrent == null) {
            drawPath(pathOf(routePoints.map { project(it) }), WatchColors.Route, style = solidStroke)
        } else {
            val split = computeRouteProgress(routePoints, shownCurrent).traveledIndex
            val currentOffset = project(shownCurrent)
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
        shownCurrent?.let {
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
