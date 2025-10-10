package com.example.straightup

import kotlin.math.max
import kotlin.math.min

/**
 * Advanced posture score calculator combining tilt angle and face distance
 */
object PostureScoreCalculator {
    
    // Optimal ranges
    private const val OPTIMAL_TILT_MIN = 0f
    private const val OPTIMAL_TILT_MAX = 30f
    private const val OPTIMAL_DISTANCE_MIN = 0.6f
    private const val OPTIMAL_DISTANCE_MAX = 1.0f
    
    // Warning ranges
    private const val WARNING_TILT_MAX = 60f
    private const val WARNING_DISTANCE_MIN = 0.4f
    private const val WARNING_DISTANCE_MAX = 1.3f
    
    // Weights for combined score
    private const val TILT_WEIGHT = 0.6f
    private const val DISTANCE_WEIGHT = 0.4f
    
    /**
     * Calculate overall posture score (0-100)
     * Higher score = better posture
     */
    fun calculatePostureScore(tiltAngle: Float, faceDistance: Float): Int {
        val tiltScore = calculateTiltScore(tiltAngle)
        val distanceScore = calculateDistanceScore(faceDistance)
        
        val combinedScore = (tiltScore * TILT_WEIGHT + distanceScore * DISTANCE_WEIGHT) * 100
        return combinedScore.toInt().coerceIn(0, 100)
    }
    
    /**
     * Calculate tilt score (0.0-1.0)
     */
    private fun calculateTiltScore(tiltAngle: Float): Float {
        return when {
            tiltAngle <= OPTIMAL_TILT_MAX -> 1.0f
            tiltAngle >= WARNING_TILT_MAX -> 0.0f
            else -> {
                // Linear interpolation between optimal and warning
                val range = WARNING_TILT_MAX - OPTIMAL_TILT_MAX
                val position = tiltAngle - OPTIMAL_TILT_MAX
                1.0f - (position / range)
            }
        }
    }
    
    /**
     * Calculate distance score (0.0-1.0)
     */
    private fun calculateDistanceScore(distance: Float): Float {
        return when {
            distance in OPTIMAL_DISTANCE_MIN..OPTIMAL_DISTANCE_MAX -> 1.0f
            distance < WARNING_DISTANCE_MIN || distance > WARNING_DISTANCE_MAX -> 0.0f
            distance < OPTIMAL_DISTANCE_MIN -> {
                // Too close
                val range = OPTIMAL_DISTANCE_MIN - WARNING_DISTANCE_MIN
                val position = distance - WARNING_DISTANCE_MIN
                position / range
            }
            else -> {
                // Too far
                val range = WARNING_DISTANCE_MAX - OPTIMAL_DISTANCE_MAX
                val position = distance - OPTIMAL_DISTANCE_MAX
                1.0f - (position / range)
            }
        }
    }
    
    /**
     * Determine reminder level based on posture score
     */
    fun getReminderLevel(score: Int): ReminderLevel {
        return when {
            score >= 80 -> ReminderLevel.NONE
            score >= 60 -> ReminderLevel.GENTLE
            score >= 40 -> ReminderLevel.MODERATE
            else -> ReminderLevel.STRONG
        }
    }
}

enum class ReminderLevel {
    NONE,       // Good posture, no reminder needed
    GENTLE,     // Slight deviation, gentle reminder
    MODERATE,   // Moderate deviation, notification
    STRONG      // Poor posture, strong alert
}

