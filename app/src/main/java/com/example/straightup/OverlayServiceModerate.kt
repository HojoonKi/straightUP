package com.example.straightup

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.os.Handler
import android.os.Looper

class OverlayServiceModerate : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.activity_overlay_layout_moderate, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(overlayView, params)

        overlayView?.apply {
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(500)
                .start()
        }

        overlayView?.postDelayed({
            overlayView?.animate()
                ?.alpha(0f)
                ?.setDuration(500)
                ?.withEndAction {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            overlayView?.let { windowManager?.removeView(it) }
                        } catch (e: Exception) {
                            // ignore
                        }
                        overlayView = null

                        stopSelf()
                    }
                }
                ?.start()
        }, 1500)
    }

    override fun onDestroy() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            // ignore
        }
        overlayView = null
        windowManager = null
        super.onDestroy()
    }
}

