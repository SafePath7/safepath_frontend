package com.example.safepath_test1.wear.launch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safepath_test1.wear.presentation.MainActivity

private const val TAG = "WatchAppOpener"

/** 앱을 열 때 함께 띄우는 "눌러서 열기" 알림의 내용. 열기 이유마다 채널과 ID를 따로 둔다. */
internal data class OpenAppNotice(
    val channelId: String,
    val channelName: String,
    val importance: Int,
    val notificationId: Int,
    val title: String,
    val text: String,
)

/**
 * 백그라운드(WearableListenerService)에서 워치 앱(MainActivity)을 앞으로 띄운다.
 *
 * Android 10부터 백그라운드에서 화면을 띄우는 것이 제한돼, 바로 띄우는 것이 모든 제조사 기기에서 된다는 보장이 없다.
 * 그래서 항상 "눌러서 열기" 알림([notice])도 함께 띄운다. 바로 띄우기가 조용히 막혀도 사용자는 알림으로 앱을 열 수 있다.
 * [intentFlags]는 직접 실행과 알림 탭에 똑같이 쓴다.
 */
internal object WatchAppOpener {

    fun open(context: Context, intentFlags: Int, notice: OpenAppNotice) {
        launchDirectly(context, intentFlags)
        showFallbackNotification(context, intentFlags, notice)
    }

    private fun mainActivityIntent(context: Context, intentFlags: Int): Intent =
        Intent(context, MainActivity::class.java).apply { flags = intentFlags }

    private fun launchDirectly(context: Context, intentFlags: Int) {
        try {
            context.startActivity(mainActivityIntent(context, intentFlags))
        } catch (e: Exception) {
            Log.e(TAG, "Direct launch failed, relying on notification fallback", e)
        }
    }

    private fun showFallbackNotification(
        context: Context,
        intentFlags: Int,
        notice: OpenAppNotice,
    ) {
        // 방어 코드다. API 33+에서 설치 뒤 POST_NOTIFICATIONS 권한을 끄면 notify()가 SecurityException을 던지는데,
        // 이 함수는 onDataChanged() 안에서 불려 잡아 줄 호출자가 없다. 여기서 예외가 새면 서비스가 죽고,
        // 테스트에서 본 "Force stopping service" / "crashed service" 반복이 생긴다(시스템이 서비스를 다시 띄우느라
        // 다음 전달까지 몇 분이 걸린다). 알림 하나 때문에 실행 경로 전체가 멈추지 않게 한다.
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            // minSdk가 30이라 알림 채널은 항상 만들 수 있다.
            manager.createNotificationChannel(
                NotificationChannel(notice.channelId, notice.channelName, notice.importance),
            )

            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    notice.notificationId,
                    mainActivityIntent(context, intentFlags),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notification =
                NotificationCompat.Builder(context, notice.channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(notice.title)
                    .setContentText(notice.text)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(
                        if (notice.importance >= NotificationManager.IMPORTANCE_HIGH) {
                            NotificationCompat.PRIORITY_HIGH
                        } else {
                            NotificationCompat.PRIORITY_DEFAULT
                        },
                    )
                    .build()

            manager.notify(notice.notificationId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback notification failed, continuing without it", e)
        }
    }
}
