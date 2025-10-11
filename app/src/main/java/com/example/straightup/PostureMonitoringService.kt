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

    private suspend fun captureSingleFaceDistance(): Float? =
        suspendCancellableCoroutine { cont ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                /* [TODO][Hojoon] FaceDistanceAnalyzer 구현 제대로 되어 있는지 확인 및 수정. */
                /* 이거 구현할 때 만약에 넘긴 사진에 얼굴이 없으면 무한 대기가 걸릴거거든? 그래서 재촬영을 한다거나 default distance를 설정한다거나 그런 에러 핸들링 로직도 같이 구현 부탁쓰 */
                val analyzer = FaceDistanceAnalyzer { distance ->
                    if (cont.isActive) cont.resume(distance)
                    cameraProvider.unbindAll()
                }

                imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                try {
                    cameraProvider.bindToLifecycle(
                        this@PostureMonitoringService,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("PostureService", "Camera binding failed", e)
                    if (cont.isActive) cont.resume(null)
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
    private fun updatePostureScore(faceDistance: Float?, tiltAngle: Float?) {
        if (faceDistance == null || tiltAngle == null) {
            Log.w("PostureService", "Incomplete data for posture score")
            return
        }
//        val score = PostureScoreCalculator.getReminderLevel(faceDistance, tiltAngle)
//        Log.d("PostureService", "Posture Score: $score")
          /* [TODO][Hojoon] score에 따라 goodCounter와 badCounter 값을 변경 하는 어떤 로직. */
//        when (score) {
//            PostureScoreCalculator.ReminderLevel.GOOD -> {
//                goodCounter++
//                badCounter = 0
//            }
//            PostureScoreCalculator.ReminderLevel.BAD -> {
//                badCounter++
//                goodCounter = 0
//            }
//            PostureScoreCalculator.ReminderLevel.NEUTRAL -> {
//                // Neutral state, do not change counters
//            }
//        }
    }

    /* [TODO][Seungjun] Implement hierarchical reminder based on 'badCounter' */
    private fun notification() {
        /* [TODO][Seungjun] PostureNotificationHelper 구현 제대로 되어 있는지 확인 및 수정. */
//        notificationHelper?.handleReminder(badCounter)
    }

    /* [TODO][Seungjun] Implement an adaptive interval calculator based on 'goodCounter' with using TCP Tahoe policy */
    private fun calculateDelayToNextInterval(): Long {
        return 300000L
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