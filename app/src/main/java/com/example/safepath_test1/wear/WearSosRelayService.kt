package com.example.safepath_test1.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safepath_test1.MainActivity
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.SosKeys
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WearSosRelayService"

private const val CHANNEL_ID = "safepath_sos"
private const val NOTIFICATION_ID = 2001

/**
 * 워치의 SOS(wear 모듈의 WatchSosSender)를 Wearable Data Layer로 받아 폰에 알림을 띄운다.
 *
 * 지금은 짝지어진 폰까지만 전달되고 보호자에게는 가지 않는다(워치 SOS를 서버로 보내는 연결은 아직 없다).
 * 알림 방식은 워치 쪽 WatchLaunchListenerService의 "눌러서 열기" 대체 알림과 같다.
 *
 * 받은 뒤에는 워치에 답장(SOS_ACK)을 보내서, 워치가 "휴대폰에 전달됨"을 보여 줄 수 있게 한다.
 */
class WearSosRelayService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != DataLayerPaths.SOS) continue

            val triggeredAt =
                DataMapItem.fromDataItem(event.dataItem).dataMap.getLong(SosKeys.TRIGGERED_AT)
            Log.i(TAG, "SOS received from watch triggeredAt=$triggeredAt")
            val requestedAt = System.currentTimeMillis()
            WatchAlertState.onSosReceived(requestedAt)
            showSosNotification(requestedAt)
            // 워치가 보낸 시각을 그대로 돌려줘야 워치가 어느 SOS에 대한 답장인지 안다. 값이 없으면 답장하지 않는다.
            if (triggeredAt > 0L) WearMessenger.sendSosAck(this, triggeredAt)
        }
    }

    private fun showSosNotification(requestedAt: Long) {
        // WatchLaunchListenerService.showFallbackNotification()과 같은 이유의 방어 코드다. onDataChanged() 안에서
        // 불려서 SecurityException(API 33+에서 설치 뒤 POST_NOTIFICATIONS 권한을 끈 경우)을 잡아 줄 호출자가 없고,
        // 여기서 예외가 새면 서비스 전체가 죽는다.
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "SafePath SOS",
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                )
            }

            val contentIntent =
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    // 앱 프로세스가 죽었어도 알림을 누르면 위험 중 상태를 되살릴 수 있게 요청 시각을 싣는다.
                    putExtra(WatchAlertState.EXTRA_REQUESTED_AT, requestedAt)
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notification =
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("SOS 수신")
                    .setContentText("워치에서 SOS 버튼이 눌렸습니다")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()

            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "SOS notification failed", e)
        }
    }
}

/** 해제했을 때 폰에 남은 "SOS 수신" 알림을 지운다. */
internal fun cancelSosNotification(context: Context) {
    context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
}
