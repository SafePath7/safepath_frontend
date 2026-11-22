package com.example.safepath_test1.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.safepath_test1.MainActivity
import com.example.safepath_test1.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class JourneyLocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var retry: Job? = null
    private var channelId: String? = null
    private var ownerUid: String? = null
    private var subscribed = false
    private var latest: Location? = null
    private lateinit var pendingLocations: LocationQueue
    private var inFlight: Long? = null
    private var sentAt = 0L
    private lateinit var locations: LocationManager
    private val listener = LocationListener { location ->
        if (location.accuracy >= 0 && location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0) {
            latest = location
        }
    }
    private val authListener = FirebaseAuth.AuthStateListener {
        if (ownerUid != null && it.currentUser?.uid != ownerUid) stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        locations = getSystemService(LocationManager::class.java)
        pendingLocations = LocationQueue(this)
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(4301, notification("위치 연결 중"))
        val uid = intent?.getStringExtra("uid") ?: return stopAndReturn()
        val channel = intent.getStringExtra("channel") ?: return stopAndReturn()
        if (FirebaseAuth.getInstance().currentUser?.uid != uid || !channel.matches(Regex("[A-Za-z0-9_-]{1,200}"))) return stopAndReturn()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return stopAndReturn()
        if (ownerUid == uid && channelId == channel) return START_NOT_STICKY
        socket?.cancel()
        retry?.cancel()
        ownerUid = uid
        channelId = channel
        subscribed = false
        inFlight = null
        locations.removeUpdates(listener)
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (locations.isProviderEnabled(provider)) locations.requestLocationUpdates(provider, 5_000L, 0f, listener)
            } catch (e: SecurityException) {
                Log.w("JourneyLocation", "Location permission unavailable", e)
            }
        }
        connect()
        scope.launch {
            var lastStoredAt = 0L
            while (channelId == channel && ownerUid == uid) {
                delay(500)
                if (inFlight != null && System.currentTimeMillis() - sentAt > 15_000) {
                    socket?.cancel()
                    inFlight = null
                }
                val location = latest
                if (location != null && System.currentTimeMillis() - location.time in 0..15_000 &&
                    System.currentTimeMillis() - lastStoredAt >= 5_000) {
                    try {
                        pendingLocations.add(uid, channel, location)
                        lastStoredAt = System.currentTimeMillis()
                        latest = null
                    } catch (e: Exception) {
                        Log.e("JourneyLocation", "Could not save location for retry", e)
                        getSystemService(NotificationManager::class.java).notify(4301, notification("위치 저장 실패"))
                    }
                }
                val queued = if (subscribed && inFlight == null) pendingLocations.first(uid, channel) else null
                if (queued != null) {
                    val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date(queued.capturedAt))
                    val body = JSONObject().put("latitude", queued.latitude).put("longitude", queued.longitude)
                        .put("capturedAt", stamp)
                    queued.accuracy?.let { body.put("accuracyMeters", it) }
                    if (socket?.send("SEND\ndestination:/app/connections/$channel/locations\ncontent-type:application/json\nreceipt:location-${queued.id}\n\n$body\u0000") == true) {
                        inFlight = queued.id
                        sentAt = System.currentTimeMillis()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun connect() {
        val uid = ownerUid ?: return
        val channel = channelId ?: return
        retry?.cancel()
        retry = scope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                check(user?.uid == uid)
                val token = user.getIdToken(false).await().token ?: error("Missing token")
                val base = com.example.safepath_test1.api.checkedAccountBaseUrl(
                    getString(R.string.safepath_api_base_url), com.example.safepath_test1.BuildConfig.DEBUG
                ).trimEnd('/')
                val url = when {
                    base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
                    base.startsWith("http://") && com.example.safepath_test1.BuildConfig.DEBUG -> "ws://" + base.removePrefix("http://")
                    else -> error("Invalid server address")
                }
                if (ownerUid != uid || channelId != channel) return@launch
                socket = client.newWebSocket(Request.Builder().url("$url/ws").build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer $token\n\n\u0000")
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (text.startsWith("CONNECTED\n")) {
                            webSocket.send("SUBSCRIBE\nid:location\ndestination:/topic/connections/$channel/locations\nreceipt:joined\n\n\u0000")
                        } else if (text.startsWith("RECEIPT\n") && text.contains("receipt-id:joined")) {
                            scope.launch {
                                if (socket === webSocket) {
                                    subscribed = true
                                    getSystemService(NotificationManager::class.java).notify(4301, notification("위치 서버 연결됨"))
                                }
                            }
                        } else if (text.startsWith("RECEIPT\n")) {
                            val receipt = text.lineSequence().firstOrNull { it.startsWith("receipt-id:location-") }
                                ?.substringAfter("receipt-id:location-")?.trim()?.toLongOrNull()
                            scope.launch {
                                if (socket === webSocket && receipt != null && receipt == inFlight) {
                                    pendingLocations.remove(uid, channel, receipt)
                                    inFlight = null
                                }
                            }
                        } else if (text.startsWith("ERROR\n")) {
                            webSocket.cancel()
                        }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = reconnect(webSocket)
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = reconnect(webSocket)
                })
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                scheduleRetry()
            }
        }
    }

    private fun reconnect(failed: WebSocket) {
        scope.launch {
            if (socket !== failed) return@launch
            socket = null
            subscribed = false
            inFlight = null
            getSystemService(NotificationManager::class.java).notify(4301, notification("위치 연결 재시도 중"))
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        retry = scope.launch { delay(5_000); connect() }
    }

    private fun notification(status: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("journey_location", "안전귀가 위치 공유", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "journey_location")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("안전귀가 진행 중")
            .setContentText(status).setContentIntent(pending).setOngoing(true).build()
    }

    private fun stopAndReturn(): Int { stopSelf(); return START_NOT_STICKY }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        channelId = null
        ownerUid = null
        subscribed = false
        FirebaseAuth.getInstance().removeAuthStateListener(authListener)
        locations.removeUpdates(listener)
        socket?.close(1000, "session ended")
        scope.cancel()
        client.dispatcher().executorService().shutdown()
        client.connectionPool().evictAll()
        pendingLocations.close()
        super.onDestroy()
    }
}
