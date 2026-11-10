package com.example.safepath_test1.wear.presentation

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal enum class TurnDirection {
    Left,
    Right,
    Straight,
}

/** 다음 안내: 방향과, 지금 위치에서 거기까지 경로를 따라 남은 거리. */
internal data class NextTurn(val direction: TurnDirection, val distanceMeters: Double)

/** 이 각도(도) 이상 꺾이면 회전으로 본다. 완만한 굽이는 직진으로 취급한다. */
private const val TURN_ANGLE_DEGREES = 35.0

/** 모퉁이의 각도를 잴 때 앞뒤로 이만큼(m)은 떨어진 점을 쓴다. 점이 촘촘할 때 흔들림을 줄이려는 값. */
private const val TURN_LOOK_METERS = 15.0

/** 이 거리(m) 안이면 "지금 우회전"처럼 말한다. */
private const val TURN_NOW_METERS = 15.0

/** 두 좌표 사이의 방위각(도, 북쪽 0, 시계방향). */
internal fun bearingDegrees(from: LatLng, to: LatLng): Double {
    val lat1 = Math.toRadians(from.first)
    val lat2 = Math.toRadians(to.first)
    val dLng = Math.toRadians(to.second - from.second)
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** [before]→[at]로 오다가 [at]→[after]로 갈 때 꺾이는 각도(도). 양수면 오른쪽, 음수면 왼쪽. */
internal fun turnAngleDegrees(before: LatLng, at: LatLng, after: LatLng): Double {
    var angle = bearingDegrees(at, after) - bearingDegrees(before, at)
    while (angle > 180.0) angle -= 360.0
    while (angle <= -180.0) angle += 360.0
    return angle
}

/**
 * 경로 위 [alongMeters] 지점(출발점에서 걸어온 거리) 앞쪽에서 처음 나오는 회전을 찾는다.
 * 폰이 보내는 점은 직선 구간이 단순화돼 있어서 점이 곧 모퉁이다. 회전이 없으면 도착점까지 직진으로 본다.
 * 점이 2개 미만이면 null.
 */
internal fun computeNextTurn(points: List<LatLng>, alongMeters: Double): NextTurn? {
    if (points.size < 2) return null
    val cumulative = cumulativeMeters(points)
    for (i in 1 until points.lastIndex) {
        // 이미 지나친 모퉁이는 건너뛴다(1m 여유).
        if (cumulative[i] <= alongMeters + 1.0) continue
        var before = i - 1
        while (before > 0 && cumulative[i] - cumulative[before] < TURN_LOOK_METERS) before--
        var after = i + 1
        while (after < points.lastIndex && cumulative[after] - cumulative[i] < TURN_LOOK_METERS) after++
        val angle = turnAngleDegrees(points[before], points[i], points[after])
        if (abs(angle) >= TURN_ANGLE_DEGREES) {
            val direction = if (angle > 0) TurnDirection.Right else TurnDirection.Left
            return NextTurn(direction, cumulative[i] - alongMeters)
        }
    }
    return NextTurn(TurnDirection.Straight, (cumulative.last() - alongMeters).coerceAtLeast(0.0))
}

/** 위쪽 상자 문구: "120m 후 우회전", "지금 좌회전", "650m 직진". */
internal fun turnBannerText(turn: NextTurn): String {
    val near = turn.distanceMeters < TURN_NOW_METERS
    return when (turn.direction) {
        TurnDirection.Right ->
            if (near) "지금 우회전" else "${formatDistance(turn.distanceMeters)} 후 우회전"
        TurnDirection.Left ->
            if (near) "지금 좌회전" else "${formatDistance(turn.distanceMeters)} 후 좌회전"
        TurnDirection.Straight -> "${formatDistance(turn.distanceMeters)} 직진"
    }
}

/** 아래쪽 안내 한 줄. */
internal fun turnInstruction(turn: NextTurn): String =
    when (turn.direction) {
        TurnDirection.Right -> "우회전하세요"
        TurnDirection.Left -> "좌회전하세요"
        TurnDirection.Straight -> "직진하세요"
    }
