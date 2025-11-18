package com.example.straightup

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.abs

class TiltSensorMonitor(
    private val context: Context,
    private val onTiltChanged: (Float) -> Unit
) : SensorEventListener {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    private val gravity = FloatArray(3)
    private val alpha = 0.8f // Low-pass filter constant
    
    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Log.d("TiltSensor", "Tilt sensor monitoring started")
        }
    }
    
    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d("TiltSensor", "Tilt sensor monitoring stopped")
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                // Initialize gravity on first reading
                if (gravity[0] == 0f && gravity[1] == 0f && gravity[2] == 0f) {
                    gravity[0] = it.values[0]
                    gravity[1] = it.values[1]
                    gravity[2] = it.values[2]
                } else {
                    // Apply low-pass filter to reduce noise
                    gravity[0] = alpha * gravity[0] + (1 - alpha) * it.values[0]
                    gravity[1] = alpha * gravity[1] + (1 - alpha) * it.values[1]
                    gravity[2] = alpha * gravity[2] + (1 - alpha) * it.values[2]
                }

                // Calculate tilt angle
                val tiltAngle = calculateTiltAngle(gravity)
                onTiltChanged(tiltAngle)
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
    
    private fun getScreenRotation(): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        return windowManager.defaultDisplay.rotation
    }

    private fun calculateTiltAngle(gravity: FloatArray): Float {
        val normOfG = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])

        // Avoid division by zero
        if (normOfG < 0.001f) return 0f

        // Get screen rotation
        val rotation = getScreenRotation()

        val tiltAngle = when (rotation) {
            Surface.ROTATION_0 -> {
                // Portrait - use pitch (Y-axis)
                Math.toDegrees(
                    atan2(gravity[1].toDouble(),
                          sqrt((gravity[0] * gravity[0] + gravity[2] * gravity[2]).toDouble()))
                ).toFloat()
            }
            Surface.ROTATION_90 -> {
                // Landscape (left) - use roll (X-axis)
                Math.toDegrees(
                    atan2(gravity[0].toDouble(),
                          sqrt((gravity[1] * gravity[1] + gravity[2] * gravity[2]).toDouble()))
                ).toFloat()
            }
            Surface.ROTATION_180 -> {
                // Portrait (upside down) - use pitch (Y-axis, inverted)
                Math.toDegrees(
                    atan2(-gravity[1].toDouble(),
                          sqrt((gravity[0] * gravity[0] + gravity[2] * gravity[2]).toDouble()))
                ).toFloat()
            }
            Surface.ROTATION_270 -> {
                // Landscape (right) - use roll (X-axis, inverted)
                Math.toDegrees(
                    atan2(-gravity[0].toDouble(),
                          sqrt((gravity[1] * gravity[1] + gravity[2] * gravity[2]).toDouble()))
                ).toFloat()
            }
            else -> 0f
        }

        return abs(tiltAngle)
    }
}

