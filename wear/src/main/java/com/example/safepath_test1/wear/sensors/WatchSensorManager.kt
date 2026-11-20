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

/** 20Hz(1000ms / 20 = 샘플당 50ms). AI 동작 모델이 기대하는 측정 속도와 같다. */
private const val SAMPLING_PERIOD_US = 50_000

/**
 * 워치에 내장된 가속도계와 자이로스코프를 읽는다.
 *
 * 사용법(WearEffects.kt의 SensorEffect 참고): DisposableEffect에서 두 센서 수집을 시작하고 onDispose에서
 * stopCollection()을 불러 화면이 사라지면 멈추게 한 뒤, [motionData]를 collect해서 값을 전달한다.
 *
 * 동작 분석에 충분한 해상도를 위해 예전 기본값(약 5Hz)이 아니라 20Hz(SAMPLING_PERIOD_US)로 고정해 읽는다.
 * 이 속도에는 실행 중 권한이 필요 없다. HIGH_SAMPLING_RATE_SENSORS는 Android 12+에서 200Hz를 넘을 때만 필요하다.
 */
class WatchSensorManager(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val accumulator = MotionSampleAccumulator()

    private val _motionData = MutableStateFlow<MotionData?>(null)
    /** 두 센서를 합친 최신 값. 두 센서가 모두 한 번 이상 값을 보내기 전까지는 null이다. */
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

    /** 가속도계 수집을 시작한다. startGyroCollection()도 함께 불러도 안전하다. */
    fun startAccelCollection() {
        accelerometer?.let { sensorManager.registerListener(listener, it, SAMPLING_PERIOD_US) }
    }

    /** 자이로스코프 수집을 시작한다. startAccelCollection()도 함께 불러도 안전하다. */
    fun startGyroCollection() {
        gyroscope?.let { sensorManager.registerListener(listener, it, SAMPLING_PERIOD_US) }
    }

    /** 두 센서를 모두 멈춘다. 센서가 배터리를 계속 쓰지 않도록 onDispose에서 부른다. */
    fun stopCollection() {
        sensorManager.unregisterListener(listener)
        accumulator.reset()
        _motionData.value = null
    }
}
