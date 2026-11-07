package com.example.safepath_test1.wear.launch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safepath_test1.wear.presentation.MainActivity
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WatchLaunchListener"

/** Must match WatchLauncher.PATH_LAUNCH_WATCH (app module) - the two modules can't share code. */
private const val PATH_LAUNCH_WATCH = "/safepath/launch_watch"

private const val CHANNEL_ID = "safepath_launch"
private const val NOTIFICATION_ID = 1001

/**
 * Receives the phone's "open the watch app" signal (WatchLauncher, app module) and brings
 * MainActivity to the foreground.
 *
 * Starting an activity directly from a WearableListenerService's background context isn't
 * guaranteed to work on every OEM build (Android 10+ restricts starting activities from the
 * background), so this always ALSO posts a tap-to-open notification as a fallback: if the direct
 * launch is silently blocked, the user can still open the app from the notification.
 */
class WatchLaunchListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != PATH_LAUNCH_WATCH) continue

            Log.i(TAG, "Launch request received from phone")
            launchDirectly()
            showFallbackNotification()
        }
    }

    private fun launchDirectly() {
        try {
            val intent =
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Direct launch failed, relying on notification fallback", e)
        }
    }

    private fun showFallbackNotification() {
        // Defensive: notify() throws SecurityException on API 33+ if POST_NOTIFICATIONS was
        // ever revoked after install, and this whole method runs inside onDataChanged() with no
        // caller to catch it - an uncaught exception here crashes the service, which is exactly
        // the "Force stopping service" / "crashed service" cycle seen in testing (the system
        // then has to kill-and-restart the service, costing a couple of minutes before the next
        // delivery attempt succeeds). Never let this call take the whole launch path down with it.
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "SafePath 실행",
                        NotificationManager.IMPORTANCE_DEFAULT,
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
