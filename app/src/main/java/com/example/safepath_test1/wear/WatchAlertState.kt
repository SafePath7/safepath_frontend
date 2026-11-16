package com.example.safepath_test1.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 워치에서 SOS가 눌려 폰이 "위험 중"으로 보고 있는 상태. 요청 시각(ms) 하나만 메모리에 들고 있고, 없으면 null이다.
 * WearSosRelayService가 SOS를 받으면 켜고, 해제하면 끈다. 앱 프로세스가 죽었다가 알림을 눌러 다시 열리면 [restore]로 되살린다.
 * 서버에는 저장하지 않는다.
 */
object WatchAlertState {
    /** SOS 알림 인텐트에 요청 시각을 실어 두는 키. MainActivity가 읽어 [restore]에 넘긴다. */
    const val EXTRA_REQUESTED_AT = "safepath.watch_sos_requested_at"

    private val requestedAtFlow = MutableStateFlow<Long?>(null)

    /** 위험 중이면 SOS를 받은 시각(ms), 아니면 null. */
    val requestedAt: StateFlow<Long?> = requestedAtFlow.asStateFlow()

    /** SOS를 받았다. 이미 위험 중이어도 가장 최근 시각으로 바꾼다. */
    fun onSosReceived(at: Long) {
        requestedAtFlow.value = at
    }

    /** 알림을 눌러 앱이 새로 열렸을 때 상태를 되살린다. 이미 켜져 있거나 시각이 올바르지 않으면 그대로 둔다. */
    fun restore(at: Long) {
        if (requestedAtFlow.value == null && at > 0L) requestedAtFlow.value = at
    }

    /** 해제했다. */
    fun clear() {
        requestedAtFlow.value = null
    }
}
