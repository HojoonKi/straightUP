package com.example.straightup

import android.content.Context
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object PostureScoreCalculator {
    
    // Default ranges (used if calibration not done)
    private const val DEFAULT_GOOD_TILT = 70f
    private const val DEFAULT_GOOD_DISTANCE = 0.8f
    private const val DEFAULT_BAD_TILT = 30f
    private const val DEFAULT_BAD_DISTANCE = 0.4f

    private const val TILT_WEIGHT = 0.50f
    private const val DISTANCE_WEIGHT = 0.50f

    private const val EXCELLENT_MARGIN = 0.15f

    private const val PREFS_NAME = "PostureCalibration"
    private const val KEY_GOOD_DISTANCE = "good_posture_distance"
    private const val KEY_GOOD_TILT = "good_posture_tilt"
    private const val KEY_BAD_DISTANCE = "bad_posture_distance"
    private const val KEY_BAD_TILT = "bad_posture_tilt"

    fun calculatePostureScore(
        context: Context,
        tiltAngle: Float?, 
        faceDistance: Float?
    ): Int {
        val baseline = loadBaseline(context)
        
        // Face not detected
        if (faceDistance == null) {
            return if (tiltAngle == null) {
                50
            } else {
                // Only tilt available
                calculateScoreWithoutFace(tiltAngle, baseline)
            }
        }
        
        // Tilt data missing but face detected
        if (tiltAngle == null) {
            val distanceScore = calculateDistanceScore(faceDistance, baseline)
            return (distanceScore * 100).toInt().coerceIn(0, 100)
        }
        
        // Both available
        val tiltScore = calculateTiltScore(tiltAngle, baseline)
        val distanceScore = calculateDistanceScore(faceDistance, baseline)
        
        // Weighted average approach
        val combinedScore = (tiltScore * TILT_WEIGHT + distanceScore * DISTANCE_WEIGHT) * 100
        
        return combinedScore.toInt().coerceIn(0, 100)
    }

    private fun calculateScoreWithoutFace(tiltAngle: Float, baseline: PostureBaseline): Int {
        return when {
            tiltAngle >= baseline.goodTilt * 0.9f -> 70 // Pass with good score

            // Linear interpolation between bad and good
            tiltAngle >= baseline.badTilt -> {
                val range = baseline.goodTilt - baseline.badTilt
                val position = tiltAngle - baseline.badTilt
                val ratio = position / range
                (40 + ratio * 30).toInt() // 40-70 range
            }
            
            // bad posture
            tiltAngle >= baseline.badTilt * 0.7f -> {
                val range = baseline.badTilt * 0.3f
                val position = tiltAngle - (baseline.badTilt * 0.7f)
                val ratio = position / range
                (10 + ratio * 30).toInt() // 10-40 range
            }

            else -> {
                val criticalThreshold = baseline.badTilt * 0.7f
                val ratio = (tiltAngle / criticalThreshold).coerceIn(0f, 1f)
                (ratio * 10).toInt()
            }
        }
    }
    
    
    /**
     * Calculate tilt score (0.0-1.0) based on personalized baseline
     */
    private fun calculateTiltScore(tiltAngle: Float, baseline: PostureBaseline): Float {
        return when {
            tiltAngle >= baseline.goodTilt * (1 + EXCELLENT_MARGIN) -> 1.0f

            tiltAngle >= baseline.goodTilt -> {
                val range = baseline.goodTilt * EXCELLENT_MARGIN
                val position = tiltAngle - baseline.goodTilt
                0.85f + (position / range) * 0.15f // 85-100%
            }

            tiltAngle > baseline.badTilt -> {
                val range = baseline.goodTilt - baseline.badTilt
                val position = tiltAngle - baseline.badTilt
                0.5f + (position / range) * 0.35f // 50-85%
            }

            tiltAngle >= baseline.badTilt * 0.8f -> {
                val range = baseline.badTilt * 0.2f
                val position = tiltAngle - (baseline.badTilt * 0.8f)
                (position / range) * 0.5f // 0-50%
            }

            else -> 0.0f
        }
    }

    private fun calculateDistanceScore(distance: Float, baseline: PostureBaseline): Float {
        val deviation = abs(distance - baseline.goodDistance)
        val badDeviation = abs(baseline.badDistance - baseline.goodDistance)
        
        return when {
            deviation <= badDeviation * 0.15f -> 1.0f

            deviation <= badDeviation * 0.5f -> {
                val range = badDeviation * 0.35f
                val position = deviation - (badDeviation * 0.15f)
                0.85f + (1.0f - position / range) * 0.15f
            }

            deviation <= badDeviation -> {
                val range = badDeviation * 0.5f
                val position = deviation - (badDeviation * 0.5f)
                0.5f + (1.0f - position / range) * 0.35f
            }

            deviation <= badDeviation * 1.5f -> {
                val range = badDeviation * 0.5f
                val position = deviation - badDeviation
                0.5f * (1.0f - position / range)
            }

            else -> 0.0f
        }
    }

    fun getReminderLevel(score: Int): ReminderLevel {
        return when {
            score >= 85 -> ReminderLevel.NONE      // Excellent posture
            score >= 70 -> ReminderLevel.GENTLE    // Good, minor adjustments
            score >= 40 -> ReminderLevel.MODERATE  // Needs attention
            else -> ReminderLevel.STRONG           // Poor posture, immediate correction
        }
    }

    fun isCalibrated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_GOOD_DISTANCE) && 
               prefs.contains(KEY_GOOD_TILT) &&
               prefs.contains(KEY_BAD_DISTANCE) && 
               prefs.contains(KEY_BAD_TILT)
    }

    private fun loadBaseline(context: Context): PostureBaseline {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        return PostureBaseline(
            goodTilt = prefs.getFloat(KEY_GOOD_TILT, DEFAULT_GOOD_TILT),
            goodDistance = prefs.getFloat(KEY_GOOD_DISTANCE, DEFAULT_GOOD_DISTANCE),
            badTilt = prefs.getFloat(KEY_BAD_TILT, DEFAULT_BAD_TILT),
            badDistance = prefs.getFloat(KEY_BAD_DISTANCE, DEFAULT_BAD_DISTANCE)
        )
    }

    fun getFeedbackMessage(
        context: Context,
        tiltAngle: Float?,
        faceDistance: Float?,
        score: Int
    ): String {
        val baseline = loadBaseline(context)

        if (faceDistance == null) {
            return when {
                tiltAngle == null -> "측정 데이터가 부족합니다"
                tiltAngle >= baseline.goodTilt * 0.9f -> "괜찮은 자세입니다"
                tiltAngle >= baseline.badTilt -> "목 각도를 조금 더 세워주세요"
                else -> "고개를 많이 숙이셨습니다! 목을 세워주세요"
            }
        }
        
        // Tilt not available case
        if (tiltAngle == null) {
            return when {
                score >= 85 -> "완벽한 거리입니다! 👍"
                score >= 70 -> "적절한 거리입니다 ✓"
                else -> if (faceDistance < baseline.goodDistance) "휴대폰을 더 멀리 두세요" else "휴대폰을 가까이 두세요"
            }
        }
        
        // Both available case
        return when {
            score >= 85 -> "완벽한 자세입니다! 👍"
            score >= 70 -> "좋은 자세입니다 ✓"
            tiltAngle < baseline.badTilt && abs(faceDistance - baseline.goodDistance) > abs(baseline.badDistance - baseline.goodDistance) -> 
                "목 각도와 거리를 모두 조정하세요"
            tiltAngle < baseline.badTilt -> 
                "목을 더 세워주세요 (현재: ${tiltAngle.toInt()}°, 목표: ${baseline.goodTilt.toInt()}°)"
            abs(faceDistance - baseline.goodDistance) > abs(baseline.badDistance - baseline.goodDistance) -> 
                if (faceDistance < baseline.goodDistance) "휴대폰을 더 멀리 두세요" else "휴대폰을 가까이 두세요"
            else -> "자세를 바르게 해주세요"
        }
    }
}

data class PostureBaseline(
    val goodTilt: Float,
    val goodDistance: Float,
    val badTilt: Float,
    val badDistance: Float
)

enum class ReminderLevel {
    NONE,
    GENTLE,
    MODERATE,
    STRONG
}

