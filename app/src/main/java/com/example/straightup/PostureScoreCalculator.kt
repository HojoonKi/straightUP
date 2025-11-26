package com.example.straightup

import android.content.Context
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Advanced posture score calculator combining tilt angle and face distance
 * Uses personalized baseline values from calibration
 */
object PostureScoreCalculator {
    
    // Default ranges (used if calibration not done)
    private const val DEFAULT_GOOD_TILT = 70f
    private const val DEFAULT_GOOD_DISTANCE = 0.8f
    private const val DEFAULT_BAD_TILT = 30f
    private const val DEFAULT_BAD_DISTANCE = 0.4f
    
    // Weights for combined score (distance is more important for screen proximity)
    private const val TILT_WEIGHT = 0.40f
    private const val DISTANCE_WEIGHT = 0.60f
    
    // Margin for "excellent" zone (beyond good posture)
    private const val EXCELLENT_MARGIN = 0.15f
    
    // SharedPreferences keys
    private const val PREFS_NAME = "PostureCalibration"
    private const val KEY_GOOD_DISTANCE = "good_posture_distance"
    private const val KEY_GOOD_TILT = "good_posture_tilt"
    private const val KEY_BAD_DISTANCE = "bad_posture_distance"
    private const val KEY_BAD_TILT = "bad_posture_tilt"
    
    /**
     * Calculate overall posture score (0-100) using personalized baseline
     * Higher score = better posture
     * 
     * @param faceDistance null if face not detected (likely looking down too much)
     */
    fun calculatePostureScore(
        context: Context,
        tiltAngle: Float?, 
        faceDistance: Float?
    ): Int {
        val baseline = loadBaseline(context)
        
        // Case 1: Face not detected (null) - likely looking down severely
        if (faceDistance == null) {
            return if (tiltAngle == null) {
                // No data at all
                50 // Neutral score
            } else {
                // Only tilt available - face likely out of camera view due to bad posture
                calculateScoreWithoutFace(tiltAngle, baseline)
            }
        }
        
        // Case 2: Tilt data missing but face detected
        if (tiltAngle == null) {
            val distanceScore = calculateDistanceScore(faceDistance, baseline)
            return (distanceScore * 100).toInt().coerceIn(0, 100)
        }
        
        // Case 3: Both available - normal scoring
        val tiltScore = calculateTiltScore(tiltAngle, baseline)
        val distanceScore = calculateDistanceScore(faceDistance, baseline)
        
        // Weighted average approach
        val combinedScore = (tiltScore * TILT_WEIGHT + distanceScore * DISTANCE_WEIGHT) * 100
        
        return combinedScore.toInt().coerceIn(0, 100)
    }
    
    /**
     * Calculate score when face is not detected (likely severe bad posture)
     * Uses only tilt angle with stricter penalty
     */
    private fun calculateScoreWithoutFace(tiltAngle: Float, baseline: PostureBaseline): Int {
        return when {
            // If tilt is acceptable, maybe just turned away - give benefit of doubt
            tiltAngle >= baseline.goodTilt * 0.9f -> 70 // Pass with good score
            
            // If tilt is in warning zone (between good and bad)
            tiltAngle >= baseline.badTilt -> {
                // Linear interpolation between bad and good
                val range = baseline.goodTilt - baseline.badTilt
                val position = tiltAngle - baseline.badTilt
                val ratio = position / range
                (40 + ratio * 30).toInt() // 40-70 range
            }
            
            // If tilt is at or below stress threshold - severe bad posture
            tiltAngle >= baseline.badTilt * 0.7f -> {
                // Linear penalty below bad posture
                val range = baseline.badTilt * 0.3f
                val position = tiltAngle - (baseline.badTilt * 0.7f)
                val ratio = position / range
                (10 + ratio * 30).toInt() // 10-40 range
            }
            
            // Critical - extremely low tilt angle
            else -> {
                // Give score 0-10 based on how bad it is
                val criticalThreshold = baseline.badTilt * 0.7f
                val ratio = (tiltAngle / criticalThreshold).coerceIn(0f, 1f)
                (ratio * 10).toInt()
            }
        }
    }
    
    /**
     * Alternative: Multiplicative approach (more strict)
     * Uncomment to use this instead
     */
    fun calculatePostureScoreMultiplicative(
        context: Context,
        tiltAngle: Float,
        faceDistance: Float
    ): Int {
        val baseline = loadBaseline(context)
        
        val tiltScore = calculateTiltScore(tiltAngle, baseline)
        val distanceScore = calculateDistanceScore(faceDistance, baseline)
        
        // Geometric mean - if either is bad, overall score drops significantly
        val combinedScore = sqrt(tiltScore * distanceScore) * 100
        
        return combinedScore.toInt().coerceIn(0, 100)
    }
    
    /**
     * Calculate tilt score (0.0-1.0) based on personalized baseline
     */
    private fun calculateTiltScore(tiltAngle: Float, baseline: PostureBaseline): Float {
        return when {
            // Excellent zone (better than good posture)
            tiltAngle >= baseline.goodTilt * (1 + EXCELLENT_MARGIN) -> 1.0f
            
            // Good zone (at or better than good posture)
            tiltAngle >= baseline.goodTilt -> {
                val range = baseline.goodTilt * EXCELLENT_MARGIN
                val position = tiltAngle - baseline.goodTilt
                0.85f + (position / range) * 0.15f // 85-100%
            }
            
            // Acceptable zone (between good and bad)
            tiltAngle > baseline.badTilt -> {
                val range = baseline.goodTilt - baseline.badTilt
                val position = tiltAngle - baseline.badTilt
                0.5f + (position / range) * 0.35f // 50-85%
            }
            
            // Warning zone (at or below bad posture threshold)
            tiltAngle >= baseline.badTilt * 0.8f -> {
                val range = baseline.badTilt * 0.2f
                val position = tiltAngle - (baseline.badTilt * 0.8f)
                (position / range) * 0.5f // 0-50%
            }
            
            // Critical zone (much worse than bad posture)
            else -> 0.0f
        }
    }
    
    /**
     * Calculate distance score (0.0-1.0) based on personalized baseline
     */
    private fun calculateDistanceScore(distance: Float, baseline: PostureBaseline): Float {
        // Calculate deviation from good posture
        val deviation = abs(distance - baseline.goodDistance)
        val badDeviation = abs(baseline.badDistance - baseline.goodDistance)
        
        return when {
            // Excellent zone (very close to good posture)
            deviation <= badDeviation * 0.15f -> 1.0f
            
            // Good zone
            deviation <= badDeviation * 0.5f -> {
                val range = badDeviation * 0.35f
                val position = deviation - (badDeviation * 0.15f)
                0.85f + (1.0f - position / range) * 0.15f // 85-100%
            }
            
            // Acceptable zone
            deviation <= badDeviation -> {
                val range = badDeviation * 0.5f
                val position = deviation - (badDeviation * 0.5f)
                0.5f + (1.0f - position / range) * 0.35f // 50-85%
            }
            
            // Warning zone
            deviation <= badDeviation * 1.5f -> {
                val range = badDeviation * 0.5f
                val position = deviation - badDeviation
                0.5f * (1.0f - position / range) // 0-50%
            }
            
            // Critical zone
            else -> 0.0f
        }
    }
    
    /**
     * Determine reminder level based on posture score
     */
    fun getReminderLevel(score: Int): ReminderLevel {
        return when {
            score >= 85 -> ReminderLevel.NONE      // Excellent posture
            score >= 70 -> ReminderLevel.GENTLE    // Good, minor adjustments
            score >= 40 -> ReminderLevel.MODERATE  // Needs attention
            else -> ReminderLevel.STRONG           // Poor posture, immediate correction
        }
    }
    
    /**
     * Check if calibration has been completed
     */
    fun isCalibrated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_GOOD_DISTANCE) && 
               prefs.contains(KEY_GOOD_TILT) &&
               prefs.contains(KEY_BAD_DISTANCE) && 
               prefs.contains(KEY_BAD_TILT)
    }
    
    /**
     * Load baseline from SharedPreferences
     */
    private fun loadBaseline(context: Context): PostureBaseline {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        return PostureBaseline(
            goodTilt = prefs.getFloat(KEY_GOOD_TILT, DEFAULT_GOOD_TILT),
            goodDistance = prefs.getFloat(KEY_GOOD_DISTANCE, DEFAULT_GOOD_DISTANCE),
            badTilt = prefs.getFloat(KEY_BAD_TILT, DEFAULT_BAD_TILT),
            badDistance = prefs.getFloat(KEY_BAD_DISTANCE, DEFAULT_BAD_DISTANCE)
        )
    }
    
    /**
     * Get personalized feedback message
     */
    fun getFeedbackMessage(
        context: Context,
        tiltAngle: Float?,
        faceDistance: Float?,
        score: Int
    ): String {
        val baseline = loadBaseline(context)
        
        // Face not detected case
        if (faceDistance == null) {
            return when {
                tiltAngle == null -> "측정 데이터가 부족합니다"
                tiltAngle >= baseline.goodTilt * 0.9f -> "괜찮은 자세입니다 ✓"
                tiltAngle >= baseline.badTilt -> "목 각도를 조금 더 세워주세요"
                else -> "고개를 많이 숙이셨습니다! 목을 세워주세요 ⚠️"
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

/**
 * Data class for personalized posture baseline
 */
data class PostureBaseline(
    val goodTilt: Float,        // Healthy posture tilt angle
    val goodDistance: Float,    // Healthy posture distance
    val badTilt: Float,         // Stress posture tilt angle (warning threshold)
    val badDistance: Float      // Stress posture distance (warning threshold)
)

enum class ReminderLevel {
    NONE,       // Good posture, no reminder needed
    GENTLE,     // Slight deviation, gentle reminder
    MODERATE,   // Moderate deviation, notification
    STRONG      // Poor posture, strong alert
}

