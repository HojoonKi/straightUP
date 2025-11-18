package com.example.straightup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import android.os.PowerManager
import android.content.Context
import android.widget.Toast
import android.view.WindowManager
import android.view.Surface
import kotlin.text.compareTo
import com.example.straightup.ReminderLevel

class PostureMonitoringService : LifecycleService() {


    private lateinit var cameraExecutor: ExecutorService
    private var tiltSensorMonitor: TiltSensorMonitor? = null
    private var notificationHelper: PostureNotificationHelper? = null
    private var goodCounter = 0 /* counts consecutive good posture */
    private var badCounter = 0 /* counts consecutive bad posture */
    private val monitoringJob = Job()
    private val monitoringScope = CoroutineScope(Dispatchers.Default + monitoringJob)
    private var isChannelCreated = false
    companion object {
        private const val CHANNEL_ID = "posture_monitoring_channel"
        private const val NOTIFICATION_ID = 1
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("PostureService", "Service created")

        createNotificationChannel()
        startMonitoring()

        cameraExecutor = Executors.newSingleThreadExecutor()
        notificationHelper = PostureNotificationHelper(this)

        startPeriodicCameraMonitoring()
    }

    private fun createNotificationChannel() {
        if (isChannelCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoring Service Started",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            isChannelCreated = true
        }
    }

    private fun startMonitoring() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("모니터 중...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun startPeriodicCameraMonitoring() {
        monitoringScope.launch {
            while (isActive) {
                try {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (!powerManager.isInteractive) {
                        Log.d("PostureService", "Screen off - skipping monitoring")
                        delay(calculateDelayToNextInterval())
                    } else {
                        startSingleFrameMonitoring()
                        notification()
                    }
                } catch (e: CancellationException) {
                    Log.d("PostureService", "Monitoring cancelled")
                    throw e
                } catch (e: Exception) {
                    Log.e("PostureService", "Monitoring cycle failed", e)
                }
                delay(calculateDelayToNextInterval())
            }
        }
    }

    private suspend fun startSingleFrameMonitoring() {
        val distanceDeferred = monitoringScope.async { captureSingleFaceDistance() }
        val tiltDeferred = monitoringScope.async { captureSingleTiltAngle() }

        val faceDistance = distanceDeferred.await()
        val tiltAngle = tiltDeferred.await()

        updatePostureScore(faceDistance, tiltAngle)
    }

    private suspend fun captureSingleFaceDistance(): Float? {
        var attemptCount = 0
        val maxAttempts = 5
        val retryDelayMs = 500L

        while (attemptCount < maxAttempts) {
            attemptCount++
            Log.d("PostureService", "Face detection attempt $attemptCount/$maxAttempts")

            val result = captureSingleFaceDistanceAttempt()
            if (result != null) {
                Log.d("PostureService", "Face detected successfully on attempt $attemptCount")
                return result
            }

            if (attemptCount < maxAttempts) {
                Log.d("PostureService", "No face detected, retrying in ${retryDelayMs}ms...")
                delay(retryDelayMs)
            }
        }

        Log.w("PostureService", "Failed to detect face after $maxAttempts attempts, returning null")
        return null
    }

    private suspend fun captureSingleFaceDistanceAttempt(): Float? =
        suspendCancellableCoroutine { cont ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            val timeoutMs = 3000L
            var hasResumed = false

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                /* [TODO][Hojoon] FaceDistanceAnalyzer 구현 제대로 되어 있는지 확인 및 수정. */
                /* 이거 구현할 때 만약에 넘긴 사진에 얼굴이 없으면 무한 대기가 걸릴거거든? 그래서 재촬영을 한다거나 default distance를 설정한다거나 그런 에러 핸들링 로직도 같이 구현 부탁쓰 */
                /* 11.13 구현 완료. 인식 안 될 시 5번까지 재시도, 여전히 인식 안 될 시 null 반환. null은 점수 계산 로직에서 따로 분기 처리 */
                val analyzer = FaceDistanceAnalyzer { distance ->
                    synchronized(this) {
                        if (cont.isActive && !hasResumed) {
                            hasResumed = true
                            cont.resume(distance)
                            cameraProvider.unbindAll()
                        }
                    }
                }

                imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                // Timeout handler - if no face detected within timeoutMs, resume with null
                monitoringScope.launch {
                    delay(timeoutMs)
                    synchronized(this@PostureMonitoringService) {
                        if (cont.isActive && !hasResumed) {
                            hasResumed = true
                            Log.d("PostureService", "Face detection timeout after ${timeoutMs}ms")
                            cont.resume(null)
                            cameraProvider.unbindAll()
                        }
                    }
                }

                try {
                    cameraProvider.bindToLifecycle(
                        this@PostureMonitoringService,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("PostureService", "Camera binding failed", e)
                    synchronized(this) {
                        if (cont.isActive && !hasResumed) {
                            hasResumed = true
                            cont.resume(null)
                        }
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        }

    /* [TODO][Seungjun] TiltSensorMonitor 구현 제대로 되어 있는지 확인 및 수정. */
    private suspend fun captureSingleTiltAngle(): Float? =
        suspendCancellableCoroutine { cont ->
            tiltSensorMonitor = TiltSensorMonitor(this) { tiltAngle ->
                if (cont.isActive) cont.resume(tiltAngle)
                tiltSensorMonitor?.stop()
            }
            tiltSensorMonitor?.start()
        }

    /* [TODO][Hojoon] PostureScoreCalculator 구현 제대로 되어 있는지 확인 및 수정. */
    /* 11.13 구현 완료 */
    private fun updatePostureScore(faceDistance: Float?, tiltAngle: Float?) {
        // Calculate score even with partial or null data
        val score = PostureScoreCalculator.calculatePostureScore(this, tiltAngle, faceDistance)
        Log.d("PostureService", "Posture Score: $score (tilt: $tiltAngle, distance: $faceDistance)")

        val reminderLevel = PostureScoreCalculator.getReminderLevel(score)
        val feedback = PostureScoreCalculator.getFeedbackMessage(this, tiltAngle, faceDistance, score)

        Log.d("PostureService", "Reminder Level: $reminderLevel, Feedback: $feedback")

        // Update counters based on score
        when (reminderLevel) {
            ReminderLevel.NONE,
            ReminderLevel.GENTLE -> {
                // Good posture
                goodCounter++
                badCounter = 0
                Log.d("PostureService", "Good posture count: $goodCounter")
            }
            ReminderLevel.MODERATE,
            ReminderLevel.STRONG -> {
                // Bad posture
                badCounter++
                goodCounter = 0
                Log.d("PostureService", "Bad posture count: $badCounter")
            }
        }
    }

    /* [TODO][Seungjun] Implement hierarchical reminder based on 'badCounter' */
    private fun notification() {
        /* [TODO][Seungjun] PostureNotificationHelper 구현 제대로 되어 있는지 확인 및 수정. */
//        notificationHelper?.handleReminder(badCounter)
    }

    /* [TODO][Seungjun] Implement an adaptive interval calculator based on 'goodCounter' with using TCP Tahoe policy */
    private fun calculateDelayToNextInterval(): Long {
//        return 300000L
        return 10000L
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("PostureService", "Service destroyed")

        monitoringJob.cancel()

        tiltSensorMonitor?.stop()
        tiltSensorMonitor = null

        cameraExecutor.shutdown()

        runBlocking {
            try {
                withTimeout(2000L) {
                    monitoringJob.join()
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("PostureService", "Job cancellation timeout")
            }
        }

        try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            Log.e("PostureService", "Camera cleanup failed", e)
        }

        try {
            if (!cameraExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                cameraExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            cameraExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }

        notificationHelper = null
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}