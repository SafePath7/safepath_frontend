package com.example.safepath_test1.wear

import com.example.safepath_test1.model.GeoPoint
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearMapFrameTest {
    private val center = GeoPoint(35.85, 128.49)

    @Test
    fun frameIsCenteredOnTheGivenPoint() {
        val frame = mapFrameAround(center, metersPerPixel = 2.5)
        assertEquals(center.latitude, frame.centerLat, 0.0)
        assertEquals(center.longitude, frame.centerLng, 0.0)
    }

    @Test
    fun oneMapPixelIsAboutTheRequestedMeters() {
        val frame = mapFrameAround(center, metersPerPixel = 2.5)
        val actual =
            EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(center.latitude)) /
                (MAP_TILE_PX * 2.0.pow(frame.zoom))
        // 줌을 소수 둘째 자리에서 내림하므로 요청값과 같거나 최대 1% 정도만 크다.
        assertTrue("1픽셀 길이: $actual", actual >= 2.5 && actual <= 2.5 * 1.01)
    }

    @Test
    fun zoomHasAtMostTwoDecimals() {
        val zoom = mapFrameAround(center, metersPerPixel = 2.5).zoom
        assertEquals(floor(zoom * 100) / 100, zoom, 1e-9)
    }

    @Test
    fun zoomIsLimitedToTheAllowedRange() {
        assertEquals(18.0, mapFrameAround(center, metersPerPixel = 0.0001).zoom, 0.0)
        assertEquals(10.0, mapFrameAround(center, metersPerPixel = 100_000.0).zoom, 0.0)
    }

    @Test
    fun haversine_oneThousandthDegreeOfLatitudeIsAbout111Meters() {
        val distance = haversineMeters(GeoPoint(37.000, 127.0), GeoPoint(37.001, 127.0))
        assertEquals(111.19, distance, 0.5)
    }
}
