package com.example.safepath_test1.api

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preserve a navigation request while the app restores the signed-in screen. */
object NotificationNavigation {
    private val pending = MutableStateFlow(false)
    val openProtection = pending.asStateFlow()

    fun accept(intent: Intent?) {
        if (intent?.getStringExtra("type") in setOf("SOS", "RISK_EVENT_UPDATED")) {
            pending.value = true
            intent?.removeExtra("type")
        }
    }

    fun consume() { pending.value = false }
}
