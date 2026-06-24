package com.sarbikbetal.kestrel.sensors

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
 *  - [azimuthDeg]        heading from TYPE_GAME_ROTATION_VECTOR (accel+gyro only).
 *                        Stable for sky/AR rendering — immune to magnetic interference.
 *                        Not compass-calibrated; use [compassBearingDeg] for true north.
 *  - [elevationDeg]      angle above the horizon (0 = level, 90 = straight up)
 *  - [rollDeg]           rotation about the viewing axis, used to keep overlays upright
 *  - [compassBearingDeg] true compass bearing from TYPE_ROTATION_VECTOR (magnetometer-fused).
 *                        Use this for heading display; do NOT use for rendering.
 *  - [compassAccuracy]   SensorManager accuracy constant (3=high, 2=medium, 1=low,
 *                        0=unreliable, -1=not yet received).
 */
data class DeviceOrientation(
    val azimuthDeg: Float,
    val elevationDeg: Float,
    val rollDeg: Float,
    val compassBearingDeg: Float = azimuthDeg,
    val compassAccuracy: Int = -1,
)

class OrientationProvider(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // TYPE_GAME_ROTATION_VECTOR: accel + gyro only, no magnetometer.
    // Stable for rendering — immune to magnetic interference.
    // Falls back to TYPE_ROTATION_VECTOR on devices that lack the game sensor.
    private val gameSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // TYPE_ROTATION_VECTOR: magnetometer-fused, compass-accurate bearing.
    // Used only for the heading display and compass accuracy indicator.
    private val compassSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isAvailable: Boolean get() = gameSensor != null

    fun stream(): Flow<DeviceOrientation> = callbackFlow {
        if (gameSensor == null) { close(); return@callbackFlow }

        // The game rotation vector is already gyro-fused and smooth, so the render
        // path only needs a light EMA to shave high-frequency jitter. A heavy filter
        // (the old alpha = 0.15) adds a ~110 ms first-order lag at SENSOR_DELAY_GAME,
        // which is the "overlay drags behind the phone" feel. Keep it responsive.
        val gameAlpha = 0.8f
        val compassAlpha = 0.4f // display-only bearing; magnetometer noise needs a touch more

        // --- Game sensor state (stable azimuth / elevation / roll) ---
        val gameRot = FloatArray(9)
        var gse = 0f; var gsn = 0f; var gsu = 0f; var gsrx = 0f; var gsry = 0f
        var gameSeeded = false

        // --- Compass sensor state (true bearing, display only) ---
        val compassRot = FloatArray(9)
        var cse = 0f; var csn = 0f
        var compassSeeded = false
        var lastAccuracy = -1

        fun buildAndSend() {
            if (!gameSeeded) return
            val horiz = hypot(gse.toDouble(), gsn.toDouble())
            val azimuth   = ((Math.toDegrees(atan2(gse.toDouble(), gsn.toDouble())) + 360.0) % 360.0).toFloat()
            val elevation = Math.toDegrees(atan2(gsu.toDouble(), horiz)).toFloat()
            val roll      = Math.toDegrees(atan2(gsrx.toDouble(), gsry.toDouble())).toFloat()
            val bearing   = if (compassSeeded)
                ((Math.toDegrees(atan2(cse.toDouble(), csn.toDouble())) + 360.0) % 360.0).toFloat()
            else azimuth
            trySend(DeviceOrientation(azimuth, elevation, roll, bearing, lastAccuracy))
        }

        val gameListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(gameRot, event.values)
                val east = -gameRot[2]; val north = -gameRot[5]; val up = -gameRot[8]
                val rx = gameRot[6]; val ry = gameRot[7]
                if (!gameSeeded) {
                    gse = east; gsn = north; gsu = up; gsrx = rx; gsry = ry; gameSeeded = true
                } else {
                    gse  += gameAlpha * (east  - gse);  gsn  += gameAlpha * (north - gsn)
                    gsu  += gameAlpha * (up    - gsu);  gsrx += gameAlpha * (rx    - gsrx)
                    gsry += gameAlpha * (ry    - gsry)
                }
                if (lastAccuracy == -1) lastAccuracy = event.accuracy
                buildAndSend()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Only register a separate compass listener if it's a different physical sensor.
        val compassListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(compassRot, event.values)
                val east = -compassRot[2]; val north = -compassRot[5]
                if (!compassSeeded) {
                    cse = east; csn = north; compassSeeded = true
                } else {
                    cse += compassAlpha * (east  - cse)
                    csn += compassAlpha * (north - csn)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                lastAccuracy = accuracy
            }
        }

        sensorManager.registerListener(gameListener, gameSensor, SensorManager.SENSOR_DELAY_GAME)
        // Register compass only when it is a distinct sensor (avoid double-registering
        // on devices where the fallback made gameSensor == compassSensor).
        if (compassSensor != null && compassSensor != gameSensor) {
            sensorManager.registerListener(compassListener, compassSensor, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sensorManager.unregisterListener(gameListener)
            if (compassSensor != null && compassSensor != gameSensor) {
                sensorManager.unregisterListener(compassListener)
            }
        }
    }
}
