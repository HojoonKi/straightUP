package com.example.straightup

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class OverlayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 3초 후 자동 종료
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 3000)
    }
}