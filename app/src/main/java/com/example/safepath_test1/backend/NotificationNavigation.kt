package com.example.safepath_test1.api

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal val protectionNotificationTypes = setOf("SOS", "RISK_EVENT_UPDATED", "SESSION_STARTED", "SESSION_ENDED")
/** Preserve a navigation request while the app restores the signed-in screen. */
internal class ProtectionTarget(val type: String, val sessionId: String?, val userId: Long?)
internal object NotificationNavigation {
    private val pending = MutableStateFlow<ProtectionTarget?>(null)
    val openProtection = pending.asStateFlow()

    fun accept(intent: Intent?) {
        if (intent?.getStringExtra("type") in protectionNotificationTypes) {
            pending.value = ProtectionTarget(requireNotNull(intent?.getStringExtra("type")), intent?.getStringExtra("sessionId"), intent?.getStringExtra("userId")?.toLongOrNull())
            intent?.removeExtra("type")
        }
    }

    fun consume(target: ProtectionTarget) { pending.compareAndSet(target, null) }
}
