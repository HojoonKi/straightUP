package com.example.straightup

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.straightup.databinding.ActivityCameraPreviewBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.sqrt

class CameraPreviewActivity : AppCompatActivity(), SensorEventListener {
    
    private lateinit var binding: ActivityCameraPreviewBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    private val gravity = FloatArray(3)
    private val alpha = 0.8f
    
    private var currentDistance = 0f
    private var currentTiltAngle = 0f

    private var goodPostureDistance: Float? = null
    private var goodPostureTilt: Float? = null
    private var badPostureDistance: Float? = null
    private var badPostureTilt: Float? = null
    
    companion object {
        private const val PREFS_NAME = "PostureCalibration"
        private const val KEY_GOOD_DISTANCE = "good_posture_distance"
        private const val KEY_GOOD_TILT = "good_posture_tilt"
        private const val KEY_BAD_DISTANCE = "bad_posture_distance"
        private const val KEY_BAD_TILT = "bad_posture_tilt"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Setup sensor
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        loadSavedCalibration()
        setupUI()
        startCamera()
    }
    
    private fun loadSavedCalibration() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        if (prefs.contains(KEY_GOOD_DISTANCE)) {
            goodPostureDistance = prefs.getFloat(KEY_GOOD_DISTANCE, 0f)
            goodPostureTilt = prefs.getFloat(KEY_GOOD_TILT, 0f)

            binding.goodPostureStatus.setTextColor(getColor(R.color.accent_green))
        }
        
        if (prefs.contains(KEY_BAD_DISTANCE)) {
            badPostureDistance = prefs.getFloat(KEY_BAD_DISTANCE, 0f)
            badPostureTilt = prefs.getFloat(KEY_BAD_TILT, 0f)

            binding.badPostureStatus.setTextColor(getColor(R.color.accent_orange))
        }
        
        checkCalibrationComplete()
    }
    
    private fun setupUI() {
        binding.closeButton.setOnClickListener {
            finish()
        }
        
        binding.captureGoodPostureButton.setOnClickListener {
            captureGoodPosture()
        }
        
        binding.captureBadPostureButton.setOnClickListener {
            captureBadPosture()
        }
        
        binding.completeButton.setOnClickListener {
            completeCalibration()
        }
    }
    
    private fun captureGoodPosture() {
        if (currentDistance <= 0f) {
            Toast.makeText(this, "얼굴이 감지되지 않았습니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        goodPostureDistance = currentDistance
        goodPostureTilt = currentTiltAngle

        binding.goodPostureStatus.setTextColor(getColor(R.color.accent_green))
        binding.goodPostureStatus.alpha = 1.0f
        
        Toast.makeText(this, "건강한 자세가 저장되었습니다", Toast.LENGTH_SHORT).show()
        checkCalibrationComplete()
    }
    
    private fun captureBadPosture() {
        if (currentDistance <= 0f) {
            Toast.makeText(this, "얼굴이 감지되지 않았습니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        badPostureDistance = currentDistance
        badPostureTilt = currentTiltAngle

        binding.badPostureStatus.setTextColor(getColor(R.color.accent_orange))
        binding.badPostureStatus.alpha = 1.0f
        
        Toast.makeText(this, "스트레스 자세가 저장되었습니다", Toast.LENGTH_SHORT).show()
        checkCalibrationComplete()
    }
    
    private fun checkCalibrationComplete() {
        if (goodPostureDistance != null && goodPostureTilt != null &&
            badPostureDistance != null && badPostureTilt != null) {
            binding.completeButton.isEnabled = true
        }
    }
    
    private fun completeCalibration() {
        // Save to SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat(KEY_GOOD_DISTANCE, goodPostureDistance ?: 0f)
            putFloat(KEY_GOOD_TILT, goodPostureTilt ?: 0f)
            putFloat(KEY_BAD_DISTANCE, badPostureDistance ?: 0f)
            putFloat(KEY_BAD_TILT, badPostureTilt ?: 0f)
            apply()
        }
        
        Toast.makeText(this, "설정이 완료되었습니다!\n개인 맞춤 자세 범위가 저장되었습니다", Toast.LENGTH_LONG).show()
        
        // Return to previous activity
        finish()
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
            
            // Image Analysis
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        cameraExecutor,
                        FaceDistanceAnalyzer { distance ->
                            currentDistance = distance
                            runOnUiThread {
                                updateDistanceUI(distance)
                            }
                        }
                    )
                }
            
            // Select front camera
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun updateDistanceUI(distance: Float) {
        binding.distanceText.text = String.format("%.2f", distance)
        binding.distanceText.setTextColor(getColor(android.R.color.white))
    }
    
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }
    
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                // Apply low-pass filter
                gravity[0] = alpha * gravity[0] + (1 - alpha) * it.values[0]
                gravity[1] = alpha * gravity[1] + (1 - alpha) * it.values[1]
                gravity[2] = alpha * gravity[2] + (1 - alpha) * it.values[2]
                
                // Calculate tilt angle
                val pitch = Math.toDegrees(
                    atan2(gravity[1].toDouble(), sqrt(gravity[0] * gravity[0] + gravity[2] * gravity[2].toDouble()))
                ).toFloat()
                
                currentTiltAngle = Math.abs(pitch)
                
                runOnUiThread {
                    binding.tiltAngleText.text = "${currentTiltAngle.toInt()}°"
                    binding.tiltAngleText.setTextColor(getColor(android.R.color.white))
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}

