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
import android.app.NotificationManager
import kotlin.text.compareTo

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var isMonitoring = false
    private var isCollectingData = false
    
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
        
        binding.dataCollectionButton.setOnClickListener {
            if (isCollectingData) {
                stopDataCollection()
            } else {
                startDataCollection()
            }
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
        // Check if calibration is done first
        if (!PostureScoreCalculator.isCalibrated(this)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("초기 설정 필요")
                .setMessage("자세 모니터링을 시작하기 전에 개인 맞춤 자세 범위를 설정해야 합니다.\n\n'초기 설정 시작' 버튼을 눌러 건강한 자세와 스트레스 자세를 측정해주세요.")
                .setPositiveButton("초기 설정하러 가기") { _, _ ->
                    startCameraPreview()
                }
                .setNegativeButton("취소", null)
                .show()
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("다른 앱 위에 표시 권한 필요")
                    .setMessage("다른 앱 사용 중에도 자세 알림을 표시하려면 권한이 필요합니다.")
                    .setPositiveButton("설정으로 이동") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton("나중에", null)
                    .show()
                return
            }
        }
        checkCameraPermission { startMonitoring() }
    }

    private fun checkPermissionsAndStartCameraPreview() {
        checkCameraPermission { startCameraPreview() }
    }
    
    private fun startDataCollection() {
        isCollectingData = true
        updateDataCollectionUI()
        
        DataCollectionService.startCollection(this)
        Toast.makeText(this, "데이터 수집을 시작합니다", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopDataCollection() {
        isCollectingData = false
        updateDataCollectionUI()
        
        DataCollectionService.stopCollection()
        
        // Show summary
        val summary = DataCollectionService.getDataSummary(this)
        if (summary != null) {
            val message = "데이터 수집 중지\n" +
                    "총 ${summary.totalRecords}건 기록\n" +
                    "좋음: ${summary.goodPostureCount}건, 나쁨: ${summary.badPostureCount}건"
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("데이터 수집 완료")
                .setMessage(message)
                .setPositiveButton("파일 내보내기") { _, _ ->
                    exportDataFile()
                }
                .setNegativeButton("닫기", null)
                .show()
        } else {
            Toast.makeText(this, "데이터 수집을 중지합니다", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateDataCollectionUI() {
        if (isCollectingData) {
            binding.dataCollectionButton.text = "데이터 수집 중지"
            binding.dataCollectionButton.strokeColor = 
                ContextCompat.getColorStateList(this, R.color.accent_orange)
            binding.dataCollectionButton.setTextColor(
                ContextCompat.getColor(this, R.color.accent_orange)
            )
        } else {
            binding.dataCollectionButton.text = "데이터 수집 시작"
            binding.dataCollectionButton.strokeColor = 
                ContextCompat.getColorStateList(this, R.color.card_border)
            binding.dataCollectionButton.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
        }
    }
    
    private fun exportDataFile() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("파일 내보내기")
            .setMessage("데이터 파일을 어떻게 내보내시겠습니까?")
            .setPositiveButton("다운로드 폴더로 복사") { _, _ ->
                if (DataCollectionService.exportToDownloads(this)) {
                    Toast.makeText(this, "다운로드 폴더에 저장되었습니다\nstraightup_data.csv", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "파일 내보내기에 실패했습니다", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("다른 앱으로 공유") { _, _ ->
                shareDataFile()
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun shareDataFile() {
        val uri = DataCollectionService.getShareUri(this)
        if (uri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "데이터 파일 공유"))
        } else {
            Toast.makeText(this, "공유할 데이터가 없습니다", Toast.LENGTH_SHORT).show()
        }
    }
}