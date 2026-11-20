package com.example.safepath_test1.wear.launch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.presentation.MainActivity
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WatchLaunchListener"

private const val CHANNEL_ID = "safepath_launch"
private const val NOTIFICATION_ID = 1001

/**
 * 폰의 "워치 앱 열기" 신호(app 모듈의 WatchLauncher)를 받아 MainActivity를 앞으로 띄운다.
 *
 * Android 10부터 백그라운드에서 화면을 띄우는 것이 제한돼, WearableListenerService에서 바로 띄우는 것이
 * 모든 제조사 기기에서 된다는 보장이 없다. 그래서 항상 "눌러서 열기" 알림도 함께 띄운다.
 * 바로 띄우기가 조용히 막혀도 사용자는 알림으로 앱을 열 수 있다.
 */
class WatchLaunchListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != DataLayerPaths.LAUNCH_WATCH) continue

            Log.i(TAG, "Launch request received from phone")
            launchDirectly()
            showFallbackNotification()
        }
    }

    /** 앱을 앞으로 가져오는 Intent. 직접 실행과 알림 탭이 같은 것을 쓴다. */
    private fun mainActivityIntent(): Intent =
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    private fun launchDirectly() {
        try {
            startActivity(mainActivityIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Direct launch failed, relying on notification fallback", e)
        }
    }

    private fun showFallbackNotification() {
        // 방어 코드다. API 33+에서 설치 뒤 POST_NOTIFICATIONS 권한을 끄면 notify()가 SecurityException을 던지는데,
        // 이 함수는 onDataChanged() 안에서 불려 잡아 줄 호출자가 없다. 여기서 예외가 새면 서비스가 죽고,
        // 테스트에서 본 "Force stopping service" / "crashed service" 반복이 생긴다(시스템이 서비스를 다시 띄우느라
        // 다음 전달까지 몇 분이 걸린다). 알림 하나 때문에 실행 경로 전체가 멈추지 않게 한다.
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            // minSdk가 30이라 알림 채널은 항상 만들 수 있다.
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "SafePath 실행",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )

            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    mainActivityIntent(),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notification =
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("SafePath")
                    .setContentText("탭하여 열기")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback notification failed, continuing without it", e)
        }
    }
}
