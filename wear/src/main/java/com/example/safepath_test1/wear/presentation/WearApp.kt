package com.example.safepath_test1.wear.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.MaterialTheme
import com.example.safepath_test1.wear.comm.RiskAnswers
import com.example.safepath_test1.wear.sensors.WatchRiskAnswerSender
import com.example.safepath_test1.wear.sensors.WatchSosSender
import com.example.safepath_test1.wear.state.AlertStage
import com.example.safepath_test1.wear.state.CHECK_IN_COUNTDOWN_MS

/**
 * 워치 앱의 최상위 화면. 폰이 Wearable Data Layer(DataClient)로 보내는 길안내 상태·경로·지도 이미지·해제 신호를
 * 받아 보여 주고, 워치의 센서와 마이크 값을 폰으로 보낸다. MessageClient가 아니라 DataClient를 쓰는 이유는
 * 폰 쪽 WearMessenger.kt를 본다. 블루투스로 이미 짝지어진 폰만 값을 보낼 수 있어서 별도 페어링 화면은 없다.
 *
 * 상태는 [WearAppState], 센서·마이크·통신·타이머는 WearEffects.kt의 효과 함수, 화면은 MainScreen과
 * SafetyScreens의 화면들이 맡는다. 여기서는 그것들을 이어 붙인다.
 */
@Composable
fun WearApp() {
    val context = LocalContext.current
    val state = rememberWearAppState()

    // 보호자 연동 전이라 SOS 버튼과 같은 경로(워치 → 폰 알림)로 보낸다.
    // 화면의 요청 시각과 보내는 SOS의 시각을 같은 값으로 맞춰야 폰의 답장(SOS_ACK)을 이 위험상황과 짝지을 수 있다.
    val requestHelp: () -> Unit = {
        val now = System.currentTimeMillis()
        state.requestHelp(now)
        WatchSosSender.send(context, triggeredAt = now)
        WatchSosSender.checkPhoneConnected(context) { connected ->
            if (!connected) state.markSosNotConnected(now)
        }
    }

    // 폰의 위험 확인 질문으로 띄운 확인 질문이면 답을 폰에 보낸다. 테스트로 띄운 질문은 AlertState가 응답을 만들지 않는다.
    // 상태를 바꾸기 전에 불러야 한다. 답한 뒤에는 질문의 이벤트 ID가 지워진다.
    val answerRiskCheck: (String) -> Unit = { answer ->
        state.alert.riskAnswer(answer, System.currentTimeMillis())?.let {
            WatchRiskAnswerSender.send(context, it)
        }
    }

    SensorEffect(context)
    // 길안내 중에만 마이크로 녹음하고, 길안내가 끝나면 바로 멈춘다.
    VoiceCaptureEffect(context, active = state.isNavigating)
    DataLayerEffect(context, state)
    StaleStatusEffect(state)
    AlertEffects(
        state,
        onCheckInTimeout = {
            answerRiskCheck(RiskAnswers.NO_RESPONSE)
            requestHelp()
        },
    )

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when (state.alert.stage) {
                AlertStage.CheckIn ->
                    CheckInScreen(
                        remainingMs = state.checkInRemainingMs,
                        totalMs = CHECK_IN_COUNTDOWN_MS,
                        onOkay = {
                            answerRiskCheck(RiskAnswers.SAFE)
                            state.dismissAlert()
                        },
                        onHelp = {
                            answerRiskCheck(RiskAnswers.HELP)
                            requestHelp()
                        },
                    )
                AlertStage.Alerted ->
                    GuardianAlertedScreen(
                        requestedAt = state.alert.helpRequestedAt,
                        delivery = state.alert.delivery,
                        onTestRelease = state::dismissAlert,
                    )
                AlertStage.None ->
                    MainScreen(
                        status = state.status,
                        routeMap = state.routeMap,
                        mapBackground = state.mapBackground,
                        // 명세: SOS는 어느 상태에서든 바로 위험상황으로 보낸다.
                        onSos = requestHelp,
                        // 테스트용. 폰의 실제 위험 확인 질문(RISK_CHECK)은 DataLayerEffect로 들어온다.
                        onShieldLongPress = { state.startCheckIn(System.currentTimeMillis()) },
                    )
            }
        }
    }
}
