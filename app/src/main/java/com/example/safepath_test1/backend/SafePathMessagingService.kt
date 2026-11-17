package com.example.safepath_test1.api

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.safepath_test1.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafePathMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { BackendRepository(applicationContext).registerToken(token) }
            catch (e: Exception) { Log.w("SafePathFCM", "Token registration failed", e) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        val type = message.data["type"]
        if (notification == null && type !in setOf("SOS", "RISK_EVENT_UPDATED")) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("safepath_alerts", "SafePath 알림", NotificationManager.IMPORTANCE_HIGH))
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("type", type)
        val pending = PendingIntent.getActivity(this, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = notification?.title ?: if (type == "SOS") "긴급 SOS 알림" else "위험 알림"
        val body = notification?.body ?: "보호 대상자의 알림을 확인해 주세요."
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), NotificationCompat.Builder(this, "safepath_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title)
            .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }
}
