package com.example.safepath_test1.wear.comm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 폰과 워치가 약속한 값이 실수로 바뀌지 않게 현재 값을 그대로 고정한다.
 * 이 테스트가 깨졌다면 이미 설치된 상대편 앱과 통신이 끊기는 변경인지 먼저 확인한다.
 */
class DataLayerContractTest {
    @Test
    fun paths_matchPhone() {
        assertEquals("/safepath/status", DataLayerPaths.STATUS)
        assertEquals("/safepath/map", DataLayerPaths.MAP)
        assertEquals("/safepath/release", DataLayerPaths.RELEASE)
        assertEquals("/safepath/launch_watch", DataLayerPaths.LAUNCH_WATCH)
        assertEquals("/safepath/motion", DataLayerPaths.MOTION)
        assertEquals("/safepath/voice", DataLayerPaths.VOICE)
        assertEquals("/safepath/sos", DataLayerPaths.SOS)
        assertEquals("/safepath/sos_ack", DataLayerPaths.SOS_ACK)
    }

    @Test
    fun paths_areDistinct() {
        val all =
            listOf(
                DataLayerPaths.STATUS,
                DataLayerPaths.MAP,
                DataLayerPaths.RELEASE,
                DataLayerPaths.LAUNCH_WATCH,
                DataLayerPaths.MOTION,
                DataLayerPaths.VOICE,
                DataLayerPaths.SOS,
                DataLayerPaths.SOS_ACK,
            )
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun statusKeys_matchPhone() {
        assertEquals(
            listOf(
                "updatedAt", "state", "routeType", "distanceMeters", "destinationName",
                "routePoints", "routeSteps", "destLat", "destLng", "curLat", "curLng",
            ),
            listOf(
                StatusKeys.UPDATED_AT, StatusKeys.STATE, StatusKeys.ROUTE_TYPE,
                StatusKeys.DISTANCE_METERS, StatusKeys.DESTINATION_NAME, StatusKeys.ROUTE_POINTS,
                StatusKeys.ROUTE_STEPS, StatusKeys.DEST_LAT, StatusKeys.DEST_LNG,
                StatusKeys.CUR_LAT, StatusKeys.CUR_LNG,
            ),
        )
        assertEquals("IDLE", StatusStates.IDLE)
        assertEquals("SEARCHING", StatusStates.SEARCHING)
        assertEquals("FOUND", StatusStates.FOUND)
    }

    @Test
    fun mapAndReleaseKeys_matchPhone() {
        assertEquals(
            listOf("image", "centerLat", "centerLng", "zoom", "imagePx", "updatedAt"),
            listOf(
                MapKeys.IMAGE, MapKeys.CENTER_LAT, MapKeys.CENTER_LNG, MapKeys.ZOOM, MapKeys.IMAGE_PX,
                MapKeys.UPDATED_AT,
            ),
        )
        assertEquals("updatedAt", ReleaseKeys.UPDATED_AT)
        assertEquals("requestedAt", LaunchKeys.REQUESTED_AT)
    }

    @Test
    fun motionKeys_matchPhone() {
        assertEquals(
            listOf("accelX", "accelY", "accelZ", "gyroX", "gyroY", "gyroZ", "timestamp"),
            listOf(
                MotionKeys.ACCEL_X, MotionKeys.ACCEL_Y, MotionKeys.ACCEL_Z,
                MotionKeys.GYRO_X, MotionKeys.GYRO_Y, MotionKeys.GYRO_Z, MotionKeys.TIMESTAMP,
            ),
        )
    }

    @Test
    fun voiceAndSosKeys_matchPhone() {
        assertEquals(
            listOf("format", "audio", "seq", "capturedAt", "aac", "pcm16"),
            listOf(
                VoiceKeys.FORMAT, VoiceKeys.AUDIO, VoiceKeys.SEQ, VoiceKeys.CAPTURED_AT,
                VoiceKeys.FORMAT_AAC, VoiceKeys.FORMAT_PCM16,
            ),
        )
        assertEquals("triggeredAt", SosKeys.TRIGGERED_AT)
        assertEquals("triggeredAt", SosAckKeys.TRIGGERED_AT)
    }
}
