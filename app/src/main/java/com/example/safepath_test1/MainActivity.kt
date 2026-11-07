package com.example.safepath_test1

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        val localDemo = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 && intent.getBooleanExtra("local_demo", false)
        setContent {
            if (localDemo) com.example.safepath_test1.ui.theme.SafePathTheme {
                com.example.safepath_test1.session.SessionDemoScreen(onBack = { finish() })
            } else SafePathApp()
        }
        WatchLauncher.launchIfWatchConnected(this)
    }
}
