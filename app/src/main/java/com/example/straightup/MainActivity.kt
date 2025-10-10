package com.example.straightup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.straightup.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var isMonitoring = false
    
    private val _postureScore = MutableStateFlow(0)
    private val postureScore: StateFlow<Int> = _postureScore
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startMonitoring()
        } else {
            Toast.makeText(this, "권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        observePostureScore()
    }
    
    private fun setupUI() {
        binding.toggleButton.setOnClickListener {
            if (isMonitoring) {
                stopMonitoring()
            } else {
                checkPermissionsAndStart()
            }
        }
        
        binding.cameraPreviewButton.setOnClickListener {
            checkPermissionsAndStartCameraPreview()
        }
    }
    
    private fun observePostureScore() {
        lifecycleScope.launch {
            postureScore.collect { score ->
                binding.postureScoreText.text = "$score / 100"
            }
        }
    }
    
    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            startMonitoring()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun startMonitoring() {
        isMonitoring = true
        updateUI()
        
        // Start foreground service
        val serviceIntent = Intent(this, PostureMonitoringService::class.java)
        startForegroundService(serviceIntent)

        Toast.makeText(this, "모니터링을 시작합니다", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopMonitoring() {
        isMonitoring = false
        updateUI()
        
        // Stop foreground service
        val serviceIntent = Intent(this, PostureMonitoringService::class.java)
        stopService(serviceIntent)
        
        Toast.makeText(this, "모니터링을 중지합니다", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateUI() {
        if (isMonitoring) {
            binding.toggleButton.text = "ON"
            binding.toggleButton.backgroundTintList = 
                ContextCompat.getColorStateList(this, R.color.button_on)
            binding.toggleButton.strokeColor = 
                ContextCompat.getColorStateList(this, R.color.button_stroke_on)
            binding.statusText.text = "모니터링 중..."
        } else {
            binding.toggleButton.text = "OFF"
            binding.toggleButton.backgroundTintList = 
                ContextCompat.getColorStateList(this, R.color.button_off)
            binding.toggleButton.strokeColor = 
                ContextCompat.getColorStateList(this, R.color.button_stroke)
            binding.statusText.text = "모니터링이 꺼져있습니다"
        }
    }
    
    // Method to update posture score from service
    fun updatePostureScore(score: Int) {
        _postureScore.value = score
    }
    
    private fun checkPermissionsAndStartCameraPreview() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            startCameraPreview()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun startCameraPreview() {
        val intent = Intent(this, CameraPreviewActivity::class.java)
        startActivity(intent)
    }
}