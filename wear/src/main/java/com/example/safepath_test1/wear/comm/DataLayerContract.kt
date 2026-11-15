package com.example.safepath_test1.wear.comm

/*
 * 폰(app 모듈)과 Wearable Data Layer로 주고받는 경로와 DataMap 키 모음.
 *
 * 폰과 워치는 코드를 공유하지 않는다. 그래서 폰 쪽 같은 값(WearMessenger, WearMapImageSender,
 * WearDataRelayService, WearSosRelayService, WearVoiceRelayService, WatchLauncher)과 한 글자만 달라도
 * 에러 없이 조용히 데이터가 안 온다. 값을 바꿀 때는 폰 쪽을 먼저 맞추고 DataLayerContractTest도 같이 고친다.
 */

/** DataItem 경로. */
internal object DataLayerPaths {
    /** 폰 → 워치: 길안내 상태, 경로, 현재 위치, 회전 안내. */
    const val STATUS = "/safepath/status"

    /** 폰 → 워치: 지도 이미지와 그 투영 정보. */
    const val MAP = "/safepath/map"

    /** 폰 → 워치: 위험상황 해제 신호. DataMap의 "updatedAt"(밀리초)만 쓴다. */
    const val RELEASE = "/safepath/release"

    /** 폰 → 워치: 워치 앱 열기. AndroidManifest.xml의 WatchLaunchListenerService pathPrefix와 같은 값이어야 한다. */
    const val LAUNCH_WATCH = "/safepath/launch_watch"

    /** 워치 → 폰: 가속도·자이로. */
    const val MOTION = "/safepath/motion"

    /** 워치 → 폰: 음성 조각. */
    const val VOICE = "/safepath/voice"

    /** 워치 → 폰: SOS. */
    const val SOS = "/safepath/sos"
}

/** [DataLayerPaths.STATUS] DataMap 키와 값. */
internal object StatusKeys {
    const val UPDATED_AT = "updatedAt"
    const val STATE = "state"
    const val ROUTE_TYPE = "routeType"
    const val DISTANCE_METERS = "distanceMeters"
    const val DESTINATION_NAME = "destinationName"
    const val ROUTE_POINTS = "routePoints"
    const val ROUTE_STEPS = "routeSteps"
    const val DEST_LAT = "destLat"
    const val DEST_LNG = "destLng"
    const val CUR_LAT = "curLat"
    const val CUR_LNG = "curLng"
}

/** [StatusKeys.STATE]가 가질 수 있는 값. */
internal object StatusStates {
    const val SEARCHING = "SEARCHING"
    const val FOUND = "FOUND"
}

/** [DataLayerPaths.MAP] DataMap 키. */
internal object MapKeys {
    const val IMAGE = "image"
    const val CENTER_LAT = "centerLat"
    const val CENTER_LNG = "centerLng"
    const val ZOOM = "zoom"
    const val IMAGE_PX = "imagePx"
}

/** [DataLayerPaths.RELEASE] DataMap 키. */
internal object ReleaseKeys {
    const val UPDATED_AT = "updatedAt"
}

/** [DataLayerPaths.MOTION] DataMap 키. */
internal object MotionKeys {
    const val ACCEL_X = "accelX"
    const val ACCEL_Y = "accelY"
    const val ACCEL_Z = "accelZ"
    const val GYRO_X = "gyroX"
    const val GYRO_Y = "gyroY"
    const val GYRO_Z = "gyroZ"
    const val TIMESTAMP = "timestamp"
}

/** [DataLayerPaths.VOICE] DataMap 키와 값. */
internal object VoiceKeys {
    const val FORMAT = "format"
    const val AUDIO = "audio"
    const val SEQ = "seq"
    const val CAPTURED_AT = "capturedAt"

    /** [FORMAT] 값: AAC-LC(ADTS)로 압축한 조각. */
    const val FORMAT_AAC = "aac"

    /** [FORMAT] 값: 압축하지 못했을 때 보내는 16비트 PCM 원본. */
    const val FORMAT_PCM16 = "pcm16"
}

/** [DataLayerPaths.SOS] DataMap 키. */
internal object SosKeys {
    const val TRIGGERED_AT = "triggeredAt"
}
