package com.example.safepath_test1.wear.state

/** 확인 질문(UI-W3)에 응답이 없으면 보호자 알림으로 넘어가기까지의 시간. */
internal const val CHECK_IN_COUNTDOWN_MS = 30_000L

/** 폰·워치 시계가 조금 어긋나도 방금 한 해제 신호를 놓치지 않도록 두는 여유(ms). */
internal const val RELEASE_CLOCK_SKEW_MS = 5_000L

/**
 * SOS를 보낸 뒤 이 시간 안에 폰의 답장(SOS_ACK)이 없으면 아직 전달되지 않은 것으로 보여 준다.
 * 워치의 연결 목록은 연결이 막 끊긴 직후에도 폰을 "연결됨"으로 돌려줄 수 있어서, 실제 답장 여부로 한 번 더 확인한다.
 */
internal const val SOS_ACK_TIMEOUT_MS = 10_000L

/**
 * 폰의 위험 확인 질문(RISK_CHECK)을 띄워도 되는 최대 나이(ms).
 * 질문 시각에서 확인 질문 제한 시간이 지났으면 이미 응답 없음으로 처리됐어야 할 질문이라 띄우지 않는다.
 * 워치 연결이 끊겼다가 다시 이어지면 옛 질문이 늦게 오고, 앱을 켤 때도 Data Layer에 남은 마지막 질문이 다시 읽히기 때문이다.
 * 폰·워치 시계 차이를 [RELEASE_CLOCK_SKEW_MS]만큼 봐준다.
 */
internal const val RISK_CHECK_MAX_AGE_MS = CHECK_IN_COUNTDOWN_MS + RELEASE_CLOCK_SKEW_MS

/** 위험 감지 흐름: 평소(길안내) → 확인 질문(UI-W3) → 보호자 알림(UI-W4). */
internal enum class AlertStage {
    None,
    CheckIn,
    Alerted,
}

/**
 * 위험상황(SOS)이 휴대폰까지 전달됐는지. 워치는 폰의 답장(SOS_ACK)이 와야 [Delivered]로 본다.
 */
internal enum class SosDelivery {
    /** 보냈고 폰의 답장을 기다리는 중. */
    Sending,

    /** 폰이 받았다고 답장했다. */
    Delivered,

    /**
     * 보낼 때 연결된 폰이 없었거나 [SOS_ACK_TIMEOUT_MS] 안에 답장이 없었다.
     * SOS는 워치에 남아 있다가 연결되면 넘어가고, 그때 답장이 오면 [Delivered]가 된다.
     */
    NotConnected,
}

/**
 * 위험 감지 흐름의 현재 상태. 값만 들고 있고, 바뀔 때마다 새 값을 돌려주는 함수로만 전이한다.
 * SOS 전송 같은 부작용은 여기서 하지 않는다. 호출하는 쪽(WearApp)이 [requestHelp]를 부를 때 함께 한다.
 */
internal data class AlertState(
    val stage: AlertStage = AlertStage.None,
    /** 확인 질문이 시작된 시각(ms). 남은 시간 계산에 쓴다. */
    val checkInStartedAt: Long = 0L,
    /** 도움을 요청한 시각(ms). 위험상황 화면의 "요청 시각", 해제 신호 비교, 폰 답장 구분에 쓴다. */
    val helpRequestedAt: Long = 0L,
    /** 이번 위험상황이 폰까지 전달됐는지. */
    val delivery: SosDelivery = SosDelivery.Sending,
    /** 지금 확인 질문이 폰의 어느 위험 이벤트에 대한 것인지. 방패 길게 누르기(테스트)로 띄웠으면 null이다. */
    val riskEventId: String? = null,
    /** 마지막으로 띄운 위험 이벤트. 같은 질문이 두 번 와도(리스너와 기존 값 읽기) 다시 띄우지 않게 기억한다. */
    val lastRiskEventId: String? = null,
) {
    /** 확인 질문을 시작한다. 방패 길게 누르기(테스트용)로 호출된다. 폰의 질문은 [receiveRiskCheck]로 들어온다. */
    fun startCheckIn(now: Long): AlertState =
        copy(stage = AlertStage.CheckIn, checkInStartedAt = now, riskEventId = null)

    /**
     * 폰의 위험 확인 질문(이벤트 [eventId], 질문 시각 [requestedAt])을 받아 확인 질문을 띄운다.
     * 평소 상태가 아니거나(이미 질문 중이거나 위험상황), 이미 띄운 이벤트이거나, 오래된 질문이면 그대로 둔다.
     * 남은 시간은 질문 시각이 아니라 워치에 띄운 [now]부터 센다. 앱을 여느라 늦어져도 사용자가 답할 시간을 다 주기 위해서다.
     */
    fun receiveRiskCheck(eventId: String, requestedAt: Long, now: Long): AlertState =
        if (
            stage == AlertStage.None &&
                eventId.isNotBlank() &&
                eventId != lastRiskEventId &&
                isRiskCheckFresh(requestedAt, now)
        ) {
            copy(
                stage = AlertStage.CheckIn,
                checkInStartedAt = now,
                riskEventId = eventId,
                lastRiskEventId = eventId,
            )
        } else {
            this
        }

    /**
     * 지금 확인 질문에 [answer](RiskAnswers의 값)로 답할 때 폰에 보낼 응답. 폰의 질문으로 띄운 확인 질문일 때만 있고,
     * 테스트로 띄웠거나 확인 질문 중이 아니면 null이다. 상태는 바꾸지 않는다. 호출하는 쪽이 보낸 뒤 [dismiss]나 [requestHelp]를 부른다.
     */
    fun riskAnswer(answer: String, now: Long): RiskAnswer? {
        val eventId = riskEventId ?: return null
        return if (stage == AlertStage.CheckIn) RiskAnswer(eventId, answer, now) else null
    }

    /** "괜찮아요"나 해제로 평소 화면으로 돌아간다. */
    fun dismiss(): AlertState = copy(stage = AlertStage.None, riskEventId = null)

    /** 도움 요청, SOS, 확인 질문 시간 초과 중 하나로 위험상황 단계에 들어간다. */
    fun requestHelp(now: Long): AlertState =
        copy(
            stage = AlertStage.Alerted,
            helpRequestedAt = now,
            delivery = SosDelivery.Sending,
            riskEventId = null,
        )

    /**
     * 요청 시각이 [requestedAt]인 SOS를 보낼 때 연결된 폰이 없었다. 지금 위험상황의 것이고 아직 답장이 오지 않았을 때만 바꾼다.
     * (연결 확인보다 답장이 먼저 오는 경우 전달됨을 덮지 않는다.)
     */
    fun markNotConnected(requestedAt: Long): AlertState =
        if (
            stage == AlertStage.Alerted &&
                helpRequestedAt == requestedAt &&
                delivery == SosDelivery.Sending
        ) {
            copy(delivery = SosDelivery.NotConnected)
        } else {
            this
        }

    /**
     * 보낸 지 [SOS_ACK_TIMEOUT_MS]가 지나도록 답장이 없으면 아직 전달되지 않은 것으로 바꾼다.
     * 이미 전달됐거나 위험상황이 아니면 그대로 둔다.
     */
    fun ackOverdue(now: Long): AlertState =
        if (now - helpRequestedAt >= SOS_ACK_TIMEOUT_MS) markNotConnected(helpRequestedAt) else this

    /** 폰의 답장([triggeredAt])이 지금 위험상황의 SOS에 대한 것이면 전달됨으로 바꾼다. 옛 SOS의 답장은 무시한다. */
    fun acknowledgedBy(triggeredAt: Long): AlertState =
        if (stage == AlertStage.Alerted && triggeredAt > 0L && triggeredAt == helpRequestedAt) {
            copy(delivery = SosDelivery.Delivered)
        } else {
            this
        }

    /**
     * 폰이 보낸 해제 신호([releaseSignalAt])가 이 위험상황의 것이면 평소 화면으로 돌아간다.
     * 위험상황이 아닐 때나 옛 위험상황의 낡은 신호는 무시한다.
     */
    fun releasedBy(releaseSignalAt: Long): AlertState =
        if (stage == AlertStage.Alerted && shouldReleaseAlert(releaseSignalAt, helpRequestedAt)) {
            dismiss()
        } else {
            this
        }

    /** 확인 질문의 남은 시간(ms). 0 이하면 시간이 다 된 것이다. */
    fun checkInRemainingMs(now: Long): Long = CHECK_IN_COUNTDOWN_MS - (now - checkInStartedAt)
}

/** 이 위험상황(요청 시각 [alertRequestedAt]) 이후에 온 해제 신호인지. 옛 위험상황의 낡은 신호는 무시한다. */
internal fun shouldReleaseAlert(releaseSignalAt: Long, alertRequestedAt: Long): Boolean =
    releaseSignalAt > 0L && releaseSignalAt >= alertRequestedAt - RELEASE_CLOCK_SKEW_MS

/**
 * 질문 시각이 [requestedAt]인 위험 확인 질문을 지금([now]) 띄워도 되는지. 시각이 없거나 [RISK_CHECK_MAX_AGE_MS]보다 오래됐으면 띄우지 않는다.
 * 폰 시계가 조금 빨라 질문 시각이 미래여도 띄운다.
 */
internal fun isRiskCheckFresh(requestedAt: Long, now: Long): Boolean =
    requestedAt > 0L && now - requestedAt <= RISK_CHECK_MAX_AGE_MS

/** 폰에 보낼 위험 확인 질문의 응답. [answer]는 RiskAnswers의 값이다. */
internal data class RiskAnswer(
    val eventId: String,
    val answer: String,
    val answeredAt: Long,
)
