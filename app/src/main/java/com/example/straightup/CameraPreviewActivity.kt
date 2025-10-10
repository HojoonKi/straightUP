package com.example.straightup

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Setup sensor
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        setupUI()
        startCamera()
    }
    
    private fun setupUI() {
        binding.closeButton.setOnClickListener {
            finish()
        }
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
        val distanceCategory = when {
            distance < 0.4f -> {
                binding.distanceText.text = "너무 가까움"
                binding.distanceDescription.text = "휴대폰을 더 멀리 떨어뜨리세요"
                binding.distanceText.setTextColor(getColor(android.R.color.holo_red_light))
            }
            distance in 0.4f..0.6f -> {
                binding.distanceText.text = "약간 가까움"
                binding.distanceDescription.text = "조금 더 떨어뜨리면 좋습니다"
                binding.distanceText.setTextColor(getColor(android.R.color.holo_orange_light))
            }
            distance in 0.6f..1.0f -> {
                binding.distanceText.text = "적정 거리 ✓"
                binding.distanceDescription.text = "완벽한 거리입니다!"
                binding.distanceText.setTextColor(getColor(android.R.color.holo_green_light))
            }
            distance in 1.0f..1.3f -> {
                binding.distanceText.text = "약간 멀음"
                binding.distanceDescription.text = "조금 더 가까이 해도 됩니다"
                binding.distanceText.setTextColor(getColor(android.R.color.holo_orange_light))
            }
            else -> {
                binding.distanceText.text = "너무 멀음"
                binding.distanceDescription.text = "휴대폰을 가까이 가져오세요"
                binding.distanceText.setTextColor(getColor(android.R.color.holo_red_light))
            }
        }
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
                    
                    // 기울기: 0도에 가까울수록 빨간색 (거북목), 클수록 초록색 (좋은 자세)
                    when {
                        currentTiltAngle < 30 -> {
                            binding.tiltAngleText.setTextColor(getColor(android.R.color.holo_red_light))
                        }
                        currentTiltAngle < 60 -> {
                            binding.tiltAngleText.setTextColor(getColor(android.R.color.holo_orange_light))
                        }
                        else -> {
                            binding.tiltAngleText.setTextColor(getColor(android.R.color.holo_green_light))
                        }
                    }
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}

