package com.example.safepath_test1.wear.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class MapViewStateTest {
    @Test
    fun dragRight_movesTheViewWestByTheDraggedMeters() {
        val view = MapViewState()
        // 보는 지름 300m, 화면 폭 200px → 1px = 1.5m. 오른쪽으로 100px 끌면 150m 서쪽.
        view.applyGesture(panXPx = 100f, panYPx = 0f, zoom = 1f, widthPx = 200f, nowMs = 5L)
        assertEquals(-150.0, view.panEastMeters, 0.0001)
        assertEquals(0.0, view.panNorthMeters, 0.0001)
        assertEquals(5L, view.lastTouchAt)
    }

    @Test
    fun dragDown_movesTheViewNorth() {
        val view = MapViewState()
        view.applyGesture(panXPx = 0f, panYPx = 40f, zoom = 1f, widthPx = 200f, nowMs = 1L)
        assertEquals(60.0, view.panNorthMeters, 0.0001)
    }

    @Test
    fun drag_isLimitedToTheMaximumPan() {
        val view = MapViewState()
        view.applyGesture(
            panXPx = 10_000f,
            panYPx = -10_000f,
            zoom = 1f,
            widthPx = 200f,
            nowMs = 1L,
        )
        assertEquals(-MAX_PAN_METERS, view.panEastMeters, 0.0001)
        assertEquals(-MAX_PAN_METERS, view.panNorthMeters, 0.0001)
    }

    @Test
    fun pinch_isLimitedToTheViewRange() {
        val view = MapViewState()
        view.applyGesture(0f, 0f, zoom = 10f, widthPx = 200f, nowMs = 1L)
        assertEquals(MIN_VIEW_METERS, view.viewMeters, 0.0001)
        view.applyGesture(0f, 0f, zoom = 0.01f, widthPx = 200f, nowMs = 2L)
        assertEquals(MAX_VIEW_METERS, view.viewMeters, 0.0001)
    }

    @Test
    fun pinchOut_showsAWiderArea() {
        val view = MapViewState()
        view.applyGesture(0f, 0f, zoom = 0.5f, widthPx = 200f, nowMs = 1L)
        assertEquals(DEFAULT_VIEW_METERS / 0.5, view.viewMeters, 0.0001)
    }

    @Test
    fun dragAndPinchTogether_measureTheDragWithTheViewBeforeThePinch() {
        val view = MapViewState()
        // 확대 전 지름 300m 기준(1px = 1.5m)으로 끈 거리를 계산한다. 확대 후 값(200m)을 쓰면 -100이 된다.
        view.applyGesture(panXPx = 100f, panYPx = 0f, zoom = 2f, widthPx = 200f, nowMs = 1L)
        assertEquals(-150.0, view.panEastMeters, 0.0001)
    }

    @Test
    fun returnToCurrent_keepsTheZoom_andResetRestoresIt() {
        val view = MapViewState()
        view.applyGesture(panXPx = 50f, panYPx = 50f, zoom = 0.5f, widthPx = 200f, nowMs = 1L)
        view.returnToCurrent()
        assertEquals(0.0, view.panEastMeters, 0.0001)
        assertEquals(0.0, view.panNorthMeters, 0.0001)
        assertEquals(DEFAULT_VIEW_METERS / 0.5, view.viewMeters, 0.0001)

        view.reset()
        assertEquals(DEFAULT_VIEW_METERS, view.viewMeters, 0.0001)
    }
}
