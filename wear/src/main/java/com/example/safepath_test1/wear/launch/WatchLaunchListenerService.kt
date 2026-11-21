package com.example.safepath_test1.wear.launch

import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WatchLaunchListener"

/**
 * 폰의 "워치 앱 열기" 신호(app 모듈의 WatchLauncher)를 받아 MainActivity를 앞으로 띄운다.
 * 바로 띄우기와 "눌러서 열기" 알림을 함께 쓰는 이유는 [WatchAppOpener]를 본다.
 */
class WatchLaunchListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != DataLayerPaths.LAUNCH_WATCH) continue

            Log.i(TAG, "Launch request received from phone")
            WatchAppOpener.open(
                context = this,
                intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                notice = LAUNCH_NOTICE,
            )
        }
    }
}

private val LAUNCH_NOTICE =
    OpenAppNotice(
        channelId = "safepath_launch",
        channelName = "SafePath 실행",
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        notificationId = 1001,
        title = "SafePath",
        text = "탭하여 열기",
    )
