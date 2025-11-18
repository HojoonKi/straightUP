package com.example.straightup

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import kotlin.text.compareTo

class OverlayServiceStrong : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        @Volatile
        var isShowing = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isShowing && overlayView == null) {
            showOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (isShowing) return

        isShowing = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.activity_overlay_layout_strong, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            alpha = 0f
        }

        windowManager?.addView(overlayView, params)

        // WindowManager params의 alpha를 점진적으로 증가
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val duration = 500L

        val runnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

                params?.alpha = progress

                try {
                    windowManager?.updateViewLayout(overlayView, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (progress < 1f) {
                    handler.postDelayed(this, 16)
                }
            }
        }
        handler.postDelayed(runnable, 50)

        overlayView?.findViewById<Button>(R.id.dismissButton)?.setOnClickListener {
            dismissOverlay()
        }
    }

    private fun dismissOverlay() {
        val view = overlayView ?: return
        val layoutParams = params ?: return

        // WindowManager params의 alpha를 점진적으로 감소
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val duration = 300L

        val runnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

                layoutParams.alpha = 1f - progress

                try {
                    windowManager?.updateViewLayout(view, layoutParams)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (progress < 1f) {
                    handler.postDelayed(this, 16)
                } else {
                    try {
                        windowManager?.removeView(view)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    overlayView = null
                    params = null
                    isShowing = false
                    stopSelf()
                }
            }
        }
        handler.post(runnable)
    }

    override fun onDestroy() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        overlayView = null
        params = null
        windowManager = null
        isShowing = false
        super.onDestroy()
    }
}