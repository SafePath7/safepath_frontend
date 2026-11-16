package com.example.safepath_test1.wear.geo

/**
 * 폰이 길찾기 서비스(Mapbox Directions)에서 받아 보낸 회전 안내 한 단계.
 * [type]과 [modifier]는 Mapbox의 maneuver.type("turn", "depart", "arrive" ...)과
 * maneuver.modifier("left", "slight right", "uturn", "straight" ...) 값 그대로다.
 */
internal data class RouteStep(val point: LatLng, val type: String, val modifier: String)
