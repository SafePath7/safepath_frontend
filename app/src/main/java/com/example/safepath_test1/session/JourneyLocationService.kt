package com.example.safepath_test1.session

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.safepath_test1.MainActivity
import com.example.safepath_test1.model.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Phone-side foreground collector. No backend URL or production transmission cadence is assumed. */
class JourneyLocationService : Service() {
    private var manager: LocationManager? = null
    private val listener = LocationListener { location ->
        latestMutable.value = SessionLocation(GeoPoint(location.latitude, location.longitude), location.time, location.accuracy)
        messageMutable.value = "위치 수집 중 · 서버 전송 없음"
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { messageMutable.value = "위치 권한이 필요합니다."; stopSelf(); return START_NOT_STICKY }
        val notifications = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(NotificationChannel(CHANNEL, "귀가 위치 수집", NotificationManager.IMPORTANCE_LOW))
        val content = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("SafePath 위치 수집 시험")
            .setContentText("휴대폰 위치 수집 중 · 서버 전송 없음")
            .setContentIntent(content).setOngoing(true).build()
        startForeground(4201, notification)
        runningMutable.value = true
        manager = getSystemService(LocationManager::class.java)
        manager?.removeUpdates(listener)
        var registered = false
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            if (provider == LocationManager.GPS_PROVIDER && !fine) return@forEach
            try {
                if (manager?.isProviderEnabled(provider) == true) {
                    // Local collection test interval only. Server transmission interval remains TBD-1.
                    manager?.requestLocationUpdates(provider, 5_000L, 5f, listener)
                    registered = true
                }
            } catch (_: SecurityException) { messageMutable.value = "위치 권한을 다시 확인해 주세요." }
        }
        messageMutable.value = if (registered) "GPS 위치 대기 중 · 서버 전송 없음" else "GPS를 켜 주세요. 마지막 위치가 있으면 유지합니다."
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        manager?.removeUpdates(listener)
        runningMutable.value = false
        latestMutable.value = null
        messageMutable.value = "위치 수집 종료 · 임시 위치 폐기"
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "journey_location_test"
        private val latestMutable = MutableStateFlow<SessionLocation?>(null)
        val latest = latestMutable.asStateFlow()
        private val runningMutable = MutableStateFlow(false)
        val running = runningMutable.asStateFlow()
        private val messageMutable = MutableStateFlow("위치 수집 시작 전")
        val message = messageMutable.asStateFlow()
    }
}
