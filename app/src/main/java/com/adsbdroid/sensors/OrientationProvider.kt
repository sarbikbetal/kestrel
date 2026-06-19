package com.adsbdroid.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Where the back camera is pointing, in world terms.
 *  - [azimuthDeg]   compass bearing of the pointing direction (0 = North, 90 = East)
 *  - [elevationDeg] angle above the horizon (0 = level, 90 = straight up)
 *  - [rollDeg]      rotation about the viewing axis, used to keep overlays upright
 */
data class DeviceOrientation(
    val azimuthDeg: Float,
    val elevationDeg: Float,
    val rollDeg: Float,
)

class OrientationProvider(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isAvailable: Boolean get() = rotationSensor != null

    fun stream(): Flow<DeviceOrientation> = callbackFlow {
        val sensor = rotationSensor
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val rotation = FloatArray(9)
        // Smoothed pointing direction (East, North, Up) and roll components.
        var se = 0f; var sn = 0f; var su = 0f
        var srx = 0f; var sry = 0f
        var seeded = false
        val alpha = 0.15f // low-pass factor; smaller = smoother but laggier

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)

                // Back-camera axis (0,0,-1) in the device frame, mapped to world.
                // World axes from the rotation matrix: X=East, Y=North, Z=Up.
                val east = -rotation[2]
                val north = -rotation[5]
                val up = -rotation[8]

                // World-up projected onto the screen plane, for roll.
                val rollX = rotation[6]
                val rollY = rotation[7]

                if (!seeded) {
                    se = east; sn = north; su = up; srx = rollX; sry = rollY
                    seeded = true
                } else {
                    se += alpha * (east - se)
                    sn += alpha * (north - sn)
                    su += alpha * (up - su)
                    srx += alpha * (rollX - srx)
                    sry += alpha * (rollY - sry)
                }

                val horiz = hypot(se.toDouble(), sn.toDouble())

                val azimuth = ((Math.toDegrees(atan2(se.toDouble(), sn.toDouble())) + 360.0) % 360.0)
                val elevation = Math.toDegrees(atan2(su.toDouble(), horiz))
                val roll = Math.toDegrees(atan2(srx.toDouble(), sry.toDouble()))

                trySend(
                    DeviceOrientation(
                        azimuthDeg = azimuth.toFloat(),
                        elevationDeg = elevation.toFloat(),
                        rollDeg = roll.toFloat(),
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
