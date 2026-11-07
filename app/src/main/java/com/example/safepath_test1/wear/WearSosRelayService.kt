package com.example.safepath_test1.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safepath_test1.MainActivity
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WearSosRelayService"

/** Must match WatchSosSender.PATH_SOS (wear module) - the two modules can't share code. */
private const val PATH_SOS = "/safepath/sos"

private const val CHANNEL_ID = "safepath_sos"
private const val NOTIFICATION_ID = 2001

/**
 * Receives the watch's SOS button press (WatchSosSender, wear module) over the Wearable Data
 * Layer and posts a notification on the phone.
 *
 * This only reaches the paired phone, not an actual guardian - there's no backend risk-event API
 * yet to relay it further (see the milestone doc's risk notes), so for now "SOS sent" means "this
 * phone got notified". Notification pattern mirrors WatchLaunchListenerService's tap-to-open
 * fallback notification on the watch side.
 */
class WearSosRelayService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != PATH_SOS) continue

            Log.i(TAG, "SOS received from watch")
            showSosNotification()
        }
    }

    private fun showSosNotification() {
        // Defensive, same reasoning as WatchLaunchListenerService.showFallbackNotification(): this
        // runs inside onDataChanged() with no caller to catch a thrown SecurityException (API 33+
        // if POST_NOTIFICATIONS was revoked after install), and an uncaught exception here would
        // crash the whole service.
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
