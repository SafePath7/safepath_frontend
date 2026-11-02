package com.example.safepath_test1.wear.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    // POST_NOTIFICATIONS: so WatchLaunchListenerService's fallback notification (phone -> watch
    // auto-open) can actually show on Android 13+, where it's a runtime permission.
    // RECORD_AUDIO: so WatchVoiceRecorder can capture mic audio while route guidance is active.
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
