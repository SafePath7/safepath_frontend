package com.example.safepath_test1.wear.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "WatchSensorManager"

/** 20Hz (1000ms / 20 = 50ms per sample) - matches the window rate the AI motion model expects. */
private const val SAMPLING_PERIOD_US = 50_000

/**
 * Reads the watch's built-in accelerometer and gyroscope.
 *
 * Usage (see SensorEffect in WearEffects.kt): start both collections from a DisposableEffect and
 * call stopCollection() in onDispose so collection stops when the screen leaves composition, then
 * collect [motionData] to relay each reading.
 *
 * Samples at a fixed 20Hz (SAMPLING_PERIOD_US) rather than the old ~5Hz default so the relayed data
 * has enough resolution for motion analysis. No runtime permission is needed for this rate -
 * HIGH_SAMPLING_RATE_SENSORS is only required above 200Hz on Android 12+.
 */
class WatchSensorManager(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val accumulator = MotionSampleAccumulator()

    private val _motionData = MutableStateFlow<MotionData?>(null)
    /** Latest combined reading. null until both sensors have reported at least once. */
    val motionData: StateFlow<MotionData?> = _motionData.asStateFlow()

    private val listener =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val updated =
                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER ->
                            accumulator.updateAcceleration(
                                event.values[0],
                                event.values[1],
                                event.values[2],
                                event.timestamp,
                                System.currentTimeMillis(),
                            )
                        Sensor.TYPE_GYROSCOPE ->
                            accumulator.updateRotation(
                                event.values[0],
                                event.values[1],
                                event.values[2],
                                event.timestamp,
                                System.currentTimeMillis(),
                            )
                        else -> return
                    } ?: return
                _motionData.value = updated
                // 20Hz라 로그 문자열을 매번 만들면 낭비다. 필요할 때만
                // `adb shell setprop log.tag.WatchSensorManager DEBUG`로 켠다.
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(
                        TAG,
                        "accel=(%.2f, %.2f, %.2f) gyro=(%.2f, %.2f, %.2f)"
                            .format(
                                updated.accelX,
                                updated.accelY,
                                updated.accelZ,
                                updated.gyroX,
                                updated.gyroY,
                                updated.gyroZ,
                            ),
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    /**
     * Starts listening to the accelerometer. Safe to call even if startGyroCollection() is also
     * called.
     */
    fun startAccelCollection() {
        accelerometer?.let { sensorManager.registerListener(listener, it, SAMPLING_PERIOD_US) }
    }

    /**
     * Starts listening to the gyroscope. Safe to call even if startAccelCollection() is also
     * called.
     */
    fun startGyroCollection() {
        gyroscope?.let { sensorManager.registerListener(listener, it, SAMPLING_PERIOD_US) }
    }

    /** Stops both. Call this from onDispose so the sensors don't keep draining the battery. */
    fun stopCollection() {
        sensorManager.unregisterListener(listener)
        accumulator.reset()
        _motionData.value = null
    }
}
