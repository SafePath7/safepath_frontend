package com.example.safepath_test1.wear.comm

/*
 * 폰(app 모듈)과 워치(wear 모듈)가 Wearable Data Layer로 주고받는 경로와 DataMap 키의 유일한 원본.
 *
 * 두 모듈 모두 이 wear-contract 모듈을 가져다 쓴다. 경로나 키를 문자열로 직접 적지 말고 여기 상수를 쓴다.
 * 한 글자만 달라도 에러 없이 조용히 데이터가 안 오기 때문이다.
 * 값을 바꾸면 이미 설치된 상대편 앱과 통신이 끊기므로 폰과 워치를 함께 다시 설치하고, DataLayerContractTest도 같이 고친다.
 * 예외: AndroidManifest.xml의 pathPrefix는 상수를 쓸 수 없어 문자열로 적혀 있다(app·wear 둘 다). 경로를 바꾸면 그곳도 고친다.
 */

/** DataItem 경로. */
object DataLayerPaths {
    /** 폰 → 워치: 길안내 상태, 경로, 현재 위치, 회전 안내. */
    const val STATUS = "/safepath/status"

    /** 폰 → 워치: 지도 이미지와 그 투영 정보. */
    const val MAP = "/safepath/map"

    /** 폰 → 워치: 위험상황 해제 신호. DataMap의 "updatedAt"(밀리초)만 쓴다. */
    const val RELEASE = "/safepath/release"

    /** 폰 → 워치: 워치 앱 열기. AndroidManifest.xml의 WatchLaunchListenerService pathPrefix와 같은 값이어야 한다. */
    const val LAUNCH_WATCH = "/safepath/launch_watch"

    /** 워치 → 폰: 가속도·자이로. 20Hz 샘플을 1초씩 묶어 보낸다. */
    const val MOTION = "/safepath/motion"

    /** 워치 → 폰: 음성 조각. */
    const val VOICE = "/safepath/voice"

    /** 워치 → 폰: SOS. */
    const val SOS = "/safepath/sos"

    /** 폰 → 워치: SOS를 받았다는 답장. 워치는 이 답장이 와야 "전달됨"으로 보여 준다. */
    const val SOS_ACK = "/safepath/sos_ack"

    /**
     * 폰 → 워치: 위험 확인 질문("괜찮으신가요?"). 워치는 확인 질문 화면을 띄운다.
     * "/safepath/sos"로 시작하지 않게 지었다. 폰 매니페스트의 SOS pathPrefix에 걸리지 않게 하기 위해서다.
     */
    const val RISK_CHECK = "/safepath/risk_check"

    /** 워치 → 폰: 위험 확인 질문에 대한 답. */
    const val RISK_ANSWER = "/safepath/risk_answer"
}

/** [DataLayerPaths.STATUS] DataMap 키와 값. */
object StatusKeys {
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
object StatusStates {
    /** 길안내 없음. 워치는 모르는 값도 대기로 보지만, 폰은 이 값을 보낸다. */
    const val IDLE = "IDLE"
    const val SEARCHING = "SEARCHING"
    const val FOUND = "FOUND"
}

/** [DataLayerPaths.MAP] DataMap 키. */
object MapKeys {
    const val IMAGE = "image"
    const val CENTER_LAT = "centerLat"
    const val CENTER_LNG = "centerLng"
    const val ZOOM = "zoom"
    const val IMAGE_PX = "imagePx"
    const val UPDATED_AT = "updatedAt"
}

/** [DataLayerPaths.RELEASE] DataMap 키. */
object ReleaseKeys {
    const val UPDATED_AT = "updatedAt"
}

/** [DataLayerPaths.LAUNCH_WATCH] DataMap 키. 같은 요청이 중복으로 걸러지지 않도록 매번 요청 시각을 담는다. */
object LaunchKeys {
    const val REQUESTED_AT = "requestedAt"
}

/**
 * [DataLayerPaths.MOTION] DataMap 키. 워치는 20Hz 샘플을 1초씩 모아 배열로 보낸다.
 * 모든 배열의 길이가 같고, 같은 위치(i번째)의 값들이 샘플 하나다.
 */
object MotionKeys {
    /** 측정 속도(Hz, Int). */
    const val RATE_HZ = "rateHz"

    /** 샘플별 측정 시각(LongArray, Unix epoch 밀리초). AI 서버의 `captured_at`과 같은 기준이다. */
    const val TIMESTAMPS = "timestamps"

    /** 샘플별 가속도(FloatArray, m/s²). */
    const val ACCEL_X = "accelX"
    const val ACCEL_Y = "accelY"
    const val ACCEL_Z = "accelZ"

    /** 샘플별 각속도(FloatArray, rad/s). */
    const val GYRO_X = "gyroX"
    const val GYRO_Y = "gyroY"
    const val GYRO_Z = "gyroZ"
}

/** [DataLayerPaths.VOICE] DataMap 키와 값. */
object VoiceKeys {
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
object SosKeys {
    const val TRIGGERED_AT = "triggeredAt"
}

/** [DataLayerPaths.SOS_ACK] DataMap 키. */
object SosAckKeys {
    /** 받은 SOS의 [SosKeys.TRIGGERED_AT]을 그대로 돌려준다. 워치는 지금 위험상황의 요청 시각과 같을 때만 전달됨으로 본다. */
    const val TRIGGERED_AT = "triggeredAt"
}

/** [DataLayerPaths.RISK_CHECK] DataMap 키. */
object RiskCheckKeys {
    /** 위험 이벤트 ID(String). 숫자 ID여도 문자열로 담는다. 워치는 답할 때 그대로 돌려준다. */
    const val EVENT_ID = "eventId"

    /** 위험 종류(String). 값 목록이 아직 정해지지 않아 워치는 기록만 하고 해석하지 않는다. */
    const val RISK_TYPE = "riskType"

    /** 폰이 질문을 보낸 시각(Long, Unix epoch 밀리초). 같은 질문을 다시 보내도 걸러지지 않게 매번 담는다. */
    const val REQUESTED_AT = "requestedAt"
}

/** [DataLayerPaths.RISK_ANSWER] DataMap 키. */
object RiskAnswerKeys {
    /** 답한 질문의 [RiskCheckKeys.EVENT_ID]를 그대로 돌려준다. */
    const val EVENT_ID = "eventId"

    /** 답([RiskAnswers]의 값 중 하나, String). */
    const val ANSWER = "answer"

    /** 워치에서 답이 정해진 시각(Long, Unix epoch 밀리초). */
    const val ANSWERED_AT = "answeredAt"
}

/** [RiskAnswerKeys.ANSWER]가 가질 수 있는 값. */
object RiskAnswers {
    /** "괜찮아요"를 눌렀다. */
    const val SAFE = "SAFE"

    /** "도움 요청"을 눌렀다. */
    const val HELP = "HELP"

    /** 제한 시간 안에 아무것도 누르지 않았다. */
    const val NO_RESPONSE = "NO_RESPONSE"
}
