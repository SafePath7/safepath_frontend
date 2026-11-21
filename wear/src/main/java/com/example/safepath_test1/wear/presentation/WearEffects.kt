package com.example.safepath_test1.wear.presentation

import android.content.Context
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.example.safepath_test1.wear.comm.WatchDataReceiver
import com.example.safepath_test1.wear.sensors.WatchMotionSender
import com.example.safepath_test1.wear.sensors.WatchSensorManager
import com.example.safepath_test1.wear.sensors.WatchVoiceRecorder
import com.example.safepath_test1.wear.sensors.WatchVoiceSender
import com.example.safepath_test1.wear.state.AlertStage
import com.example.safepath_test1.wear.state.CHECK_IN_COUNTDOWN_MS
import com.example.safepath_test1.wear.state.SOS_ACK_TIMEOUT_MS
import kotlinx.coroutines.delay

private const val TAG = "WearEffects"

/** 확인 질문이 뜰 때의 진동: 0.3초씩 세 번. 짝수 칸은 쉬는 시간, 홀수 칸은 울리는 시간(ms)이다. */
private val CHECK_IN_VIBRATION_MS = longArrayOf(0, 300, 200, 300, 200, 300)

// WearApp이 켜 두는 부작용(센서, 마이크, 통신, 타이머)을 화면 코드와 떼어 놓은 효과 함수들.
// 화면이 사라지면 각 효과가 스스로 정리된다.

/** 가속도·자이로를 20Hz로 모아 1초 묶음으로 폰의 WearDataRelayService에 전달한다(초당 한 번 전송). */
@Composable
internal fun SensorEffect(context: Context) {
    val sensorManager = remember { WatchSensorManager(context) }
    DisposableEffect(Unit) {
        sensorManager.startAccelCollection()
        sensorManager.startGyroCollection()
        onDispose { sensorManager.stopCollection() }
    }
    LaunchedEffect(sensorManager) {
        sensorManager.motionBatches.collect { batch -> WatchMotionSender.send(context, batch) }
    }
}

/**
 * 길안내 중([active])에만 마이크로 녹음해서 1초 조각을 폰으로 보낸다.
 * 길안내가 끝나면(상태가 Found가 아니게 되면) 바로 멈춰서, 안내가 끝난 뒤에도 계속 듣거나 배터리를 쓰지 않는다.
 */
@Composable
internal fun VoiceCaptureEffect(context: Context, active: Boolean) {
    val voiceRecorder = remember { WatchVoiceRecorder(context) }
    LaunchedEffect(active) {
        if (active) {
            WatchVoiceSender.startSession()
            voiceRecorder.start()
        } else {
            voiceRecorder.stop()
            WatchVoiceSender.stopSession()
        }
    }
    LaunchedEffect(voiceRecorder) {
        voiceRecorder.audioChunks.collect { chunk -> WatchVoiceSender.send(context, chunk) }
    }
    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.stop()
            WatchVoiceSender.stopSession()
        }
    }
}

/**
 * 폰이 보내는 값(상태, 지도 이미지, 해제 신호, SOS 답장, 위험 확인 질문)을 받아 [state]에 반영한다.
 * 받는 방법과 순서 규칙은 WatchDataReceiver에 있다.
 */
@Composable
internal fun DataLayerEffect(context: Context, state: WearAppState) {
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val receiver =
            WatchDataReceiver(
                context = context,
                scope = scope,
                onStatus = { update ->
                    state.onStatus(update)
                    Log.i(TAG, "New status=${state.status}")
                },
                onMapImage = state::onMapImage,
                onRelease = state::onRelease,
                onSosAck = state::onSosAck,
                onRiskCheck = { check ->
                    val shown = state.onRiskCheck(check)
                    Log.i(
                        TAG,
                        "Risk check eventId=${check.eventId} riskType=${check.riskType} " +
                            "requestedAt=${check.requestedAt} shown=$shown",
                    )
                },
            )
        receiver.start()
        onDispose { receiver.stop() }
    }
}

/** 폰에서 오래 아무 값도 안 오면 30초마다 확인해서 연결 끊김으로 바꾼다. */
@Composable
internal fun StaleStatusEffect(state: WearAppState) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            state.markStaleIfNeeded()
        }
    }
}

/**
 * 위험 감지 흐름의 시간에 따른 동작.
 * 폰의 해제 신호가 오면 위험상황 화면을 닫고, 확인 질문은 남은 시간을 줄이다가 0이 되면 [onCheckInTimeout]을 부른다.
 * 위험상황이 되면 [SOS_ACK_TIMEOUT_MS] 뒤에 폰의 답장이 왔는지 확인해서, 없으면 아직 전달되지 않았다고 보여 준다.
 */
@Composable
internal fun AlertEffects(state: WearAppState, onCheckInTimeout: () -> Unit) {
    val currentOnCheckInTimeout by rememberUpdatedState(onCheckInTimeout)

    // 폰에서 해제 신호가 오면 위험상황 화면을 닫고 평소 화면(길안내)으로 돌아간다.
    LaunchedEffect(state.releaseSignalAt) { state.releaseIfSignalled() }

    LaunchedEffect(state.alert.stage, state.alert.helpRequestedAt) {
        if (state.alert.stage != AlertStage.Alerted) return@LaunchedEffect
        val waitMs = state.alert.helpRequestedAt + SOS_ACK_TIMEOUT_MS - System.currentTimeMillis()
        delay(waitMs.coerceAtLeast(0L))
        state.checkSosAckOverdue()
    }

    LaunchedEffect(state.alert.stage, state.alert.checkInStartedAt) {
        if (state.alert.stage != AlertStage.CheckIn) return@LaunchedEffect
        while (true) {
            val remaining = state.alert.checkInRemainingMs(System.currentTimeMillis())
            if (remaining <= 0) {
                currentOnCheckInTimeout()
                break
            }
            state.showCheckInRemaining(remaining)
            delay(100)
        }
    }
}

/**
 * 확인 질문이 떠 있는 동안([active]) 진동을 한 번 주고 화면을 켜 둔다. 질문이 바뀌면([startedAt]) 다시 한다.
 *
 * 실기기 시험에서 화면이 어두운 채로 질문이 조용히 떴다가 30초 뒤 그대로 SOS로 넘어갔다.
 * 실제 위험 상황에서 사용자가 질문을 놓치지 않게 손목에 알리고 화면을 밝힌다.
 * 화면 켜기는 ACQUIRE_CAUSES_WAKEUP 웨이크락으로 한다. 꺼진 화면을 켜는 다른 방법(setTurnScreenOn)은
 * 화면이 다시 앞으로 올 때만 적용돼서, 앱이 이미 앞에 있는데 화면만 꺼진 경우를 못 켠다.
 * 웨이크락은 제한 시간을 걸어 잡고, 질문이 끝나면 바로 놓는다.
 */
@Composable
internal fun CheckInAlertEffect(context: Context, active: Boolean, startedAt: Long) {
    DisposableEffect(active, startedAt) {
        if (!active) return@DisposableEffect onDispose {}

        vibrateForCheckIn(context)
        val wakeLock = acquireScreenWakeLock(context)
        onDispose {
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Failed to release check-in wake lock", e)
            }
        }
    }
}

private fun vibrateForCheckIn(context: Context) {
    try {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(CHECK_IN_VIBRATION_MS, -1))
    } catch (e: RuntimeException) {
        // 진동이 안 돼도 질문 화면은 그대로 떠야 한다.
        Log.w(TAG, "Check-in vibration failed", e)
    }
}

// SCREEN_BRIGHT_WAKE_LOCK은 deprecated지만 화면을 켜는 ACQUIRE_CAUSES_WAKEUP과 함께 쓸 수 있는 유일한 종류다.
@Suppress("DEPRECATION")
private fun acquireScreenWakeLock(context: Context): PowerManager.WakeLock? =
    try {
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "SafePath:CheckIn",
            )
            ?.apply { acquire(CHECK_IN_COUNTDOWN_MS) }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Failed to acquire check-in wake lock", e)
        null
    }
