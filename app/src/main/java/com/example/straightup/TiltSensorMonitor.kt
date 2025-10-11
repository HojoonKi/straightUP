package com.example.straightup

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.atan2
import kotlin.math.sqrt

class TiltSensorMonitor(
    context: Context,
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
                // Apply low-pass filter to reduce noise
                gravity[0] = alpha * gravity[0] + (1 - alpha) * it.values[0]
                gravity[1] = alpha * gravity[1] + (1 - alpha) * it.values[1]
                gravity[2] = alpha * gravity[2] + (1 - alpha) * it.values[2]
                
                // Calculate tilt angle
                val tiltAngle = calculateTiltAngle(gravity)
                onTiltChanged(tiltAngle)
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
    
    private fun calculateTiltAngle(gravity: FloatArray): Float {
        val normOfG = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])

        val pitch = Math.toDegrees(
            atan2(gravity[1].toDouble(), sqrt(gravity[0] * gravity[0] + gravity[2] * gravity[2].toDouble()))
        ).toFloat()

        return Math.abs(pitch)
    }
}

