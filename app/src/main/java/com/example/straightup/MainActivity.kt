package com.example.straightup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.straightup.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
            Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        checkBatteryOptimization()
        setupUI()
//        observePostureScore()
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            val packageName = packageName

            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                Toast.makeText(this, "배터리 최적화 예외를 허용해주세요.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupUI() {
        binding.toggleButtonFrame.setOnClickListener {
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
    
//    private fun observePostureScore() {
//        lifecycleScope.launch {
//            postureScore.collect { score ->
//                binding.postureScoreText.text = "$score / 100"
//            }
//        }
//    }
    
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
            binding.toggleButtonBackground.setImageResource(R.drawable.button_toggle_on)
            binding.toggleButtonText.text = "ON"
            binding.toggleButtonText.setTextColor(0xFF00FF00.toInt()) // Bright green
            binding.statusText.text = "모니터링 중..."
            
            // Add glow animation
            binding.toggleButtonBackground.animate()
                .alpha(0.8f)
                .setDuration(500)
                .withEndAction {
                    binding.toggleButtonBackground.animate()
                        .alpha(1.0f)
                        .setDuration(500)
                        .start()
                }
                .start()
        } else {
            binding.toggleButtonBackground.setImageResource(R.drawable.button_toggle_off)
            binding.toggleButtonText.text = "OFF"
            binding.toggleButtonText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.statusText.text = "모니터링이 꺼져있습니다"
            
            // Reset animation
            binding.toggleButtonBackground.clearAnimation()
            binding.toggleButtonBackground.alpha = 1.0f
        }
    }
    
    private fun startCameraPreview() {
        val intent = Intent(this, CameraPreviewActivity::class.java)
        startActivity(intent)
    }

    private fun checkCameraPermission(onGranted: () -> Unit) {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            onGranted()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun checkPermissionsAndStart() {
        checkCameraPermission { startMonitoring() }
    }

    private fun checkPermissionsAndStartCameraPreview() {
        checkCameraPermission { startCameraPreview() }
    }
}