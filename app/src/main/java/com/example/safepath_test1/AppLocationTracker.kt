package com.example.safepath_test1

import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.example.safepath_test1.model.GeoPoint

/** Observes foreground location while permission is available. */
@Composable
internal fun AppLocationTracker(context: Context, enabled: Boolean, onLocation: (GeoPoint, Boolean, Long) -> Unit) {
    val currentCallback by rememberUpdatedState(onLocation)
    DisposableEffect(context, enabled) {
        if (!enabled) {
            onDispose { }
        } else {
            val manager = context.getSystemService(LocationManager::class.java)
            val listener = LocationListener { location ->
                currentCallback(GeoPoint(location.latitude, location.longitude), true, location.time)
            }
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            val locations = providers.mapNotNull { provider ->
                try { manager.getLastKnownLocation(provider) }
                catch (exception: SecurityException) {
                    Log.w("SafePathApp", "No permission to read last known location from $provider", exception)
                    null
                }
            }
            locations.maxByOrNull { it.time }?.let {
                currentCallback(GeoPoint(it.latitude, it.longitude), false, it.time)
            }
            providers.forEach { provider ->
                try {
                    if (manager.isProviderEnabled(provider)) manager.requestLocationUpdates(provider, 5_000L, 5f, listener)
                    else Log.w("SafePathApp", "$provider is disabled on this device")
                } catch (exception: SecurityException) {
                    Log.w("SafePathApp", "No permission to request updates from $provider", exception)
                }
            }
            onDispose { manager.removeUpdates(listener) }
        }
    }
}
