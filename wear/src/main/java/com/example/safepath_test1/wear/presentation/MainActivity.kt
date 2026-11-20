package com.example.safepath_test1.wear.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    // POST_NOTIFICATIONS: Android 13+에서는 실행 중 권한이라, WatchLaunchListenerService의 대체 알림
    // (폰 → 워치 자동 실행)이 실제로 뜨게 하려면 필요하다.
    // RECORD_AUDIO: 길안내 중 WatchVoiceRecorder가 마이크로 녹음하는 데 필요하다.
    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissions =
            buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        requestPermissions.launch(permissions.toTypedArray())
        setContent { WearApp() }
    }
}
