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
) {
    /** 확인 질문을 시작한다. 지금은 방패 길게 누르기(테스트용)로 호출되고, AI 서버가 붙으면 실제 위험 신호에서 호출한다. */
    fun startCheckIn(now: Long): AlertState =
        copy(stage = AlertStage.CheckIn, checkInStartedAt = now)

    /** "괜찮아요"나 해제로 평소 화면으로 돌아간다. */
    fun dismiss(): AlertState = copy(stage = AlertStage.None)

    /** 도움 요청, SOS, 확인 질문 시간 초과 중 하나로 위험상황 단계에 들어간다. */
    fun requestHelp(now: Long): AlertState =
        copy(stage = AlertStage.Alerted, helpRequestedAt = now, delivery = SosDelivery.Sending)

    /**
     * 요청 시각이 [requestedAt]인 SOS를 보낼 때 연결된 폰이 없었다. 지금 위험상황의 것이고 아직 답장이 오지 않았을 때만 바꾼다.
     * (연결 확인보다 답장이 먼저 오는 경우 전달됨을 덮지 않는다.)
     */
    fun markNotConnected(requestedAt: Long): AlertState =
        if (stage == AlertStage.Alerted && helpRequestedAt == requestedAt && delivery == SosDelivery.Sending) {
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
