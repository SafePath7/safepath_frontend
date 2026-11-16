package com.example.safepath_test1.wear.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {
    @Test
    fun haversine_oneThousandthDegreeOfLatitudeIsAbout111Meters() {
        assertEquals(111.19, haversineMeters(37.000 to 127.0, 37.001 to 127.0), 0.5)
    }

    @Test
    fun metersPerDegree_matchesTheHaversineSphere() {
        // 위도 1도 거리와 위도 1도 상수가 같은 구를 쓰는지 확인한다(스케치 지도와 진행률 계산이 같은 값을 공유).
        assertEquals(METERS_PER_DEGREE, haversineMeters(37.0 to 127.0, 38.0 to 127.0), 1.0)
    }

    @Test
    fun mercator_centerOfTheWorldIsHalfHalf() {
        assertEquals(0.5, mercatorX(0.0), 1e-9)
        assertEquals(0.5, mercatorY(0.0), 1e-9)
    }

    @Test
    fun mercator_eastAndNorthDirections() {
        // 동쪽으로 갈수록 x가 커지고, 북쪽으로 갈수록 y는 작아진다(이미지 위쪽).
        assertEquals(true, mercatorX(127.1) > mercatorX(127.0))
        assertEquals(true, mercatorY(37.1) < mercatorY(37.0))
    }
}
