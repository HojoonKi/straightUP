package com.example.straightup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PostureMonitoringService : LifecycleService() {
    
    private lateinit var cameraExecutor: ExecutorService
    private var tiltSensorMonitor: TiltSensorMonitor? = null
    private var notificationHelper: PostureNotificationHelper? = null
    
    private var currentTiltAngle = 0f
    private var currentFaceDistance = 1.0f
    private var currentPostureScore = 100
    
    private val monitoringJob = Job()
    private val monitoringScope = CoroutineScope(Dispatchers.Default + monitoringJob)
    
    companion object {
        private const val CHANNEL_ID = "posture_monitoring_channel"
        private const val NOTIFICATION_ID = 1
        private const val MONITORING_INTERVAL_MS = 300000L // 5 minutes (5 * 60 * 1000)
        private const val CAMERA_ANALYSIS_DURATION_MS = 3000L // 3 seconds for each check
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("PostureService", "Service created")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        notificationHelper = PostureNotificationHelper(this)
        
        startTiltSensorMonitoring()
        startPeriodicCameraMonitoring()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "자세 모니터링",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "백그라운드에서 자세를 모니터링합니다"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StraightUP 모니터링 중")
            .setContentText("자세를 확인하고 있습니다...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun startTiltSensorMonitoring() {
        tiltSensorMonitor = TiltSensorMonitor(this) { tiltAngle ->
            currentTiltAngle = tiltAngle
            updatePostureScore()
        }
        tiltSensorMonitor?.start()
    }
    
    private fun startPeriodicCameraMonitoring() {
        monitoringScope.launch {
            while (isActive) {
                // Start camera for brief analysis
                withContext(Dispatchers.Main) {
                    startCameraAnalysis()
                }
                
                // Keep camera on for analysis duration
                delay(CAMERA_ANALYSIS_DURATION_MS)
                
                // Stop camera to save resources
                withContext(Dispatchers.Main) {
                    stopCameraAnalysis()
                }
                
                Log.d("PostureService", "Camera check completed. Next check in 5 minutes.")
                
                // Wait 5 minutes before next check
                delay(MONITORING_INTERVAL_MS)
            }
        }
    }
    
    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        cameraExecutor,
                        FaceDistanceAnalyzer { distance ->
                            currentFaceDistance = distance
                            updatePostureScore()
                        }
                    )
                }
            
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalysis
                )
                Log.d("PostureService", "Camera analysis started")
            } catch (e: Exception) {
                Log.e("PostureService", "Camera binding failed", e)
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun stopCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            Log.d("PostureService", "Camera analysis stopped")
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun updatePostureScore() {
        currentPostureScore = PostureScoreCalculator.calculatePostureScore(
            currentTiltAngle,
            currentFaceDistance
        )
        
        Log.d("PostureService", "Score: $currentPostureScore, Tilt: $currentTiltAngle, Distance: $currentFaceDistance")
        
        // Update notification
        updateForegroundNotification()
        
        // Check if reminder needed
        val reminderLevel = PostureScoreCalculator.getReminderLevel(currentPostureScore)
        notificationHelper?.handleReminder(reminderLevel, currentPostureScore)
    }
    
    private fun updateForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StraightUP - 점수: $currentPostureScore")
            .setContentText(getPostureMessage())
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun getPostureMessage(): String {
        return when {
            currentPostureScore >= 80 -> "좋은 자세입니다! 👍"
            currentPostureScore >= 60 -> "자세가 조금 흐트러졌습니다"
            currentPostureScore >= 40 -> "자세를 바로 잡으세요"
            else -> "자세가 매우 나쁩니다!"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("PostureService", "Service destroyed")
        
        monitoringJob.cancel()
        tiltSensorMonitor?.stop()
        cameraExecutor.shutdown()
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        }, ContextCompat.getMainExecutor(this))
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}

