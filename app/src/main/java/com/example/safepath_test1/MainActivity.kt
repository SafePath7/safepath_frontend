package com.example.safepath_test1

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.safepath_test1.wear.WatchAlertState
import com.example.safepath_test1.wear.WatchLauncher

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.light(scrim = Color.TRANSPARENT, darkScrim = Color.TRANSPARENT),
            navigationBarStyle =
                SystemBarStyle.light(scrim = Color.TRANSPARENT, darkScrim = Color.TRANSPARENT),
        )
        // 화면 회전 등으로 다시 만들어진 경우에는 이미 처리한 알림 정보를 다시 읽지 않는다.
        if (savedInstanceState == null) restoreWatchAlert(intent)
        if (savedInstanceState == null) com.example.safepath_test1.api.NotificationNavigation.accept(intent)
        setContent { SafePathApp() }
        WatchLauncher.launchIfWatchConnected(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        restoreWatchAlert(intent)
        com.example.safepath_test1.api.NotificationNavigation.accept(intent)
    }

    /** SOS 알림을 눌러 들어온 경우 알림에 실린 요청 시각으로 위험 중 상태를 되살린다. 한 번 읽으면 지운다. */
    private fun restoreWatchAlert(intent: Intent?) {
        val requestedAt = intent?.getLongExtra(WatchAlertState.EXTRA_REQUESTED_AT, 0L) ?: 0L
        if (requestedAt > 0L) {
            WatchAlertState.restore(requestedAt)
            intent?.removeExtra(WatchAlertState.EXTRA_REQUESTED_AT)
        }
    }
}
