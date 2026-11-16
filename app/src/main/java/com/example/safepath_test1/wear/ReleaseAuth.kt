package com.example.safepath_test1.wear

import android.app.Activity
import android.content.Context
import com.example.safepath_test1.session.BiometricSessionSigner

/** 위험상황 해제 때 본인 확인을 어떤 방법으로 할지. */
enum class ReleaseAuthMethod {
    /** 확인할 대상이 없어 확인하지 않는다. */
    NONE,

    /** 생체인증. */
    BIOMETRIC,

    /** 세션 PIN 4자리. */
    PIN,
}

/**
 * 건우와 정한 규칙이다. 기본은 생체인증이고, 생체인증이 안 되는 폰은 세션 PIN을 쓴다.
 * 세션이 없으면 확인할 키도 PIN도 없어서 확인하지 않는다.
 */
internal fun chooseReleaseAuth(hasSession: Boolean, biometricReady: Boolean): ReleaseAuthMethod =
    when {
        !hasSession -> ReleaseAuthMethod.NONE
        biometricReady -> ReleaseAuthMethod.BIOMETRIC
        else -> ReleaseAuthMethod.PIN
    }

/**
 * 지금 폰에서 쓸 해제 확인 방법. 세션 시작 흐름(보호자 연동, PIN 설정, 생체 키 등록)이 아직 없어서
 * 세션이 있는지는 항상 false다. 그 흐름이 붙으면 이 값만 실제 세션 여부로 바꾼다.
 */
fun releaseAuthMethod(context: Context): ReleaseAuthMethod =
    chooseReleaseAuth(
        hasSession = false,
        biometricReady = BiometricSessionSigner.isAvailable(context),
    )

/**
 * 해제 직전에 본인 확인을 하고 결과를 [onResult]로 알린다. true일 때만 해제 신호를 보낸다.
 * 확인할 대상이 없으면(NONE) 바로 true다.
 * 생체인증과 PIN 확인은 세션의 키와 PIN이 생겨야 연결할 수 있다. 연결 전에는 안전하게 실패로 처리한다.
 */
fun authenticateRelease(activity: Activity, onResult: (Boolean) -> Unit) {
    when (releaseAuthMethod(activity)) {
        ReleaseAuthMethod.NONE -> onResult(true)
        ReleaseAuthMethod.BIOMETRIC,
        ReleaseAuthMethod.PIN -> onResult(false)
    }
}
