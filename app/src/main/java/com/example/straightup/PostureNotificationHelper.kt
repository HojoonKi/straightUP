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
import android.widget.Toast
import kotlin.or
import kotlin.text.compareTo

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

    companion object {
        private const val ALERT_CHANNEL_ID = "posture_alert_channel"
        private const val ALERT_NOTIFICATION_ID = 100
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
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun handleReminder(badcounter: Int) {

        val level = when {
            badcounter == 0 -> ReminderLevel.NONE
            badcounter == 1 -> ReminderLevel.GENTLE
            badcounter == 2 -> ReminderLevel.MODERATE
            badcounter >= 3 -> ReminderLevel.STRONG
            else -> ReminderLevel.STRONG
        }

        showReminder(level)
    }
    
    private fun showReminder(level: ReminderLevel) {
        when (level) {
            ReminderLevel.GENTLE -> showGentleReminder()
            ReminderLevel.MODERATE -> showModerateReminder()
            ReminderLevel.STRONG -> showStrongReminder()
            ReminderLevel.NONE -> {}
        }
        
        Log.d("PostureNotification", "Reminder shown: $level")
    }
    
    private fun showGentleReminder() {
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("거북목 감지")
            .setContentText("자세를 바로잡아 주세요")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun showModerateReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(context)) {
                Log.e("PostureNotification", "Overlay permission not granted - showing fallback notification")
                showGentleReminder()
                return
            }
        }

        val serviceIntent = Intent(context, OverlayServiceModerate::class.java)
        context.startService(serviceIntent)
    }

    
    private fun showStrongReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(context)) {
                Log.e(
                    "PostureNotification",
                    "Overlay permission not granted - showing fallback notification"
                )
                showGentleReminder()
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect = VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(vibrationEffect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }

        val serviceIntent = Intent(context, OverlayServiceStrong::class.java)
        context.startService(serviceIntent)
    }
}

