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

class PostureMonitoringService : LifecycleService() {


    private lateinit var cameraExecutor: ExecutorService
    private var tiltSensorMonitor: TiltSensorMonitor? = null
    private var notificationHelper: PostureNotificationHelper? = null
    private var goodCounter = 0 /* counts consecutive good posture */
    private var badCounter = 0 /* counts consecutive bad posture */
    private val monitoringJob = Job()
    private val monitoringScope = CoroutineScope(Dispatchers.Default + monitoringJob)
    private var isChannelCreated = false

    // TCP Tahoe policy variables for adaptive interval
    private var currentDelay = INITIAL_DELAY_MS
    private val maxDelay = 60000L // 1 min max

    companion object {
        private const val CHANNEL_ID = "posture_monitoring_channel"
        private const val NOTIFICATION_ID = 1
        private const val INITIAL_DELAY_MS = 5000L // 5 seconds
        private const val GOOD_THRESHOLD = 3 // goodCounter threshold to increase delay
        private const val INCREASE_FACTOR = 2.0 //increase factor
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
                        Log.d("PostureService", "Screen off - skipping monitoring and resetting delay")
                        resetDelay() // Reset delay when screen turns off
                        delay(calculateDelayToNextInterval())
                    } else {
                        startSingleFrameMonitoring()
                        notification()
                        while (OverlayServiceStrong.isShowing) {
                            delay(100)
                        }
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

                /* [Hojoon] FaceDistanceAnalyzer 구현 제대로 되어 있는지 확인 및 수정. */
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

                // Timeout handler
                monitoringScope.launch {
                    delay(timeoutMs)
                    val shouldUnbind = synchronized(this@PostureMonitoringService) {
                        if (cont.isActive && !hasResumed) {
                            hasResumed = true
                            Log.d("PostureService", "Face detection timeout after ${timeoutMs}ms")
                            cont.resume(null)
                            true
                        } else {
                            false
                        }
                    }

                    if (shouldUnbind) {
                        withContext(Dispatchers.Main) {
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

    /* [Seungjun] TiltSensorMonitor 구현 제대로 되어 있는지 확인 및 수정. */
    /* 11.19 구현 완료 : 가로/세로 모두 지원 */
    private suspend fun captureSingleTiltAngle(): Float? =
        suspendCancellableCoroutine { cont ->
            tiltSensorMonitor = TiltSensorMonitor(this) { tiltAngle ->
                if (cont.isActive) cont.resume(tiltAngle)
                tiltSensorMonitor?.stop()
            }
            tiltSensorMonitor?.start()
        }

    /* [Hojoon] PostureScoreCalculator 구현 제대로 되어 있는지 확인 및 수정. */
    /* 11.13 구현 완료 */
    private fun updatePostureScore(faceDistance: Float?, tiltAngle: Float?) {
        // Calculate score even with partial or null data
        val score = PostureScoreCalculator.calculatePostureScore(this, tiltAngle, faceDistance)
        Log.d("PostureService", "Posture Score: $score (tilt: $tiltAngle, distance: $faceDistance)")

        val reminderLevel = PostureScoreCalculator.getReminderLevel(score)
        val feedback = PostureScoreCalculator.getFeedbackMessage(this, tiltAngle, faceDistance, score)

        Log.d("PostureService", "Reminder Level: $reminderLevel, Feedback: $feedback")

        // Determine if good or bad posture
        val isGoodPosture = when (reminderLevel) {
            ReminderLevel.NONE,
            ReminderLevel.GENTLE -> true
            ReminderLevel.MODERATE,
            ReminderLevel.STRONG -> false
        }

        // Log to data collection if active
        if (DataCollectionService.isCollecting()) {
            DataCollectionService.logPostureEvent(
                timestamp = System.currentTimeMillis(),
                isGoodPosture = isGoodPosture,
                score = score,
                tiltAngle = tiltAngle,
                faceDistance = faceDistance
            )
        }
        // Update counters based on score
        when (reminderLevel) {
            ReminderLevel.NONE,
            ReminderLevel.GENTLE,
            ReminderLevel.MODERATE -> {
                // Good posture
                goodCounter++
                badCounter = 0
                Log.d("PostureService", "Good posture count: $goodCounter")
            }
            ReminderLevel.STRONG -> {
                // Bad posture - reset delay (TCP Tahoe policy)
                badCounter++
                goodCounter = 0
                resetDelay()
                Log.d("PostureService", "Bad posture count: $badCounter, delay reset to ${currentDelay}ms")
            }
        }
    }

    private suspend fun notification() {
        /* [Seungjun] PostureNotificationHelper 구현 제대로 되어 있는지 확인 및 수정. */
        /* 11.19 구현 완료 */
        notificationHelper?.handleReminder(badCounter)
    }


    private fun calculateDelayToNextInterval(): Long {
        /* [TODO] energy profile 가능할듯? 그냥 간단하게 5초 지정하고 돌리면 될듯! */

        /* TCP Tahoe policy implementation:
           1. 화면 꺼지면 딜레이 초기화 (resetDelay 호출됨)
           2. goodCounter가 GOOD_THRESHOLD 이상 쌓이면 딜레이 증가 (multiplicative increase)
           3. badCounter가 한번이라도 쌓이면 딜레이 초기화 (resetDelay 호출됨)
           4. 초기 딜레이는 5초
         */
        if (goodCounter >= GOOD_THRESHOLD) {
            currentDelay = (currentDelay * INCREASE_FACTOR).toLong().coerceAtMost(maxDelay)
            Log.d("PostureService", "Delay increased to ${currentDelay}ms due to good posture")
        }

        return currentDelay
    }

    private fun resetDelay() {
        currentDelay = INITIAL_DELAY_MS
        Log.d("PostureService", "Delay reset to ${currentDelay}ms")
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