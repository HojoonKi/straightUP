package com.example.straightup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import android.util.Log

/**
 * Hierarchical reminder system with adaptive intervals
 */
class PostureNotificationHelper(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    private var lastReminderTime = 0L
    private var consecutivePoorPostureCount = 0
    private var lastReminderLevel = ReminderLevel.NONE
    
    companion object {
        private const val ALERT_CHANNEL_ID = "posture_alert_channel"
        private const val ALERT_NOTIFICATION_ID = 100
        
        // Adaptive reminder intervals (in milliseconds)
        private const val GENTLE_INTERVAL = 60000L      // 1 minute
        private const val MODERATE_INTERVAL = 30000L    // 30 seconds
        private const val STRONG_INTERVAL = 15000L      // 15 seconds
    }
    
    init {
        createAlertChannel()
    }
    
    private fun createAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "자세 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "잘못된 자세에 대한 알림을 표시합니다"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun handleReminder(level: ReminderLevel, score: Int) {
        val currentTime = System.currentTimeMillis()
        
        // Update consecutive poor posture count
        if (level != ReminderLevel.NONE) {
            if (level == lastReminderLevel) {
                consecutivePoorPostureCount++
            } else {
                consecutivePoorPostureCount = 1
            }
        } else {
            consecutivePoorPostureCount = 0
        }
        
        lastReminderLevel = level
        
        // Check if enough time has passed for next reminder
        val interval = when (level) {
            ReminderLevel.NONE -> return
            ReminderLevel.GENTLE -> GENTLE_INTERVAL
            ReminderLevel.MODERATE -> MODERATE_INTERVAL
            ReminderLevel.STRONG -> STRONG_INTERVAL
        }
        
        // Adaptive interval: reduce interval if posture hasn't improved
        val adaptiveInterval = if (consecutivePoorPostureCount > 3) {
            (interval * 0.7).toLong() // 30% faster reminders
        } else {
            interval
        }
        
        if (currentTime - lastReminderTime >= adaptiveInterval) {
            showReminder(level, score)
            lastReminderTime = currentTime
        }
    }
    
    private fun showReminder(level: ReminderLevel, score: Int) {
        when (level) {
            ReminderLevel.GENTLE -> showGentleReminder(score)
            ReminderLevel.MODERATE -> showModerateReminder(score)
            ReminderLevel.STRONG -> showStrongReminder(score)
            ReminderLevel.NONE -> {}
        }
        
        Log.d("PostureNotification", "Reminder shown: $level, Score: $score")
    }
    
    private fun showGentleReminder(score: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("자세 확인")
            .setContentText("자세가 조금 흐트러졌습니다 (점수: $score)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
        
        // Light vibration
        vibratePhone(100)
    }
    
    private fun showModerateReminder(score: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 자세 교정 필요")
            .setContentText("목과 허리를 펴주세요 (점수: $score)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
        
        // Medium vibration
        vibratePhone(200)
    }
    
    private fun showStrongReminder(score: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 즉시 자세 교정!")
            .setContentText("거북목 상태입니다! 지금 바로 자세를 바로잡으세요 (점수: $score)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .build()
        
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
        
        // Strong vibration pattern
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 300, 100, 300),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 300, 100, 300), -1)
        }
    }
    
    private fun vibratePhone(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}

