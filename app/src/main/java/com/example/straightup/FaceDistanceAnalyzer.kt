package com.example.straightup

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceDistanceAnalyzer(
    private val onDistanceCalculated: (Float) -> Unit
) : ImageAnalysis.Analyzer {
    
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()
    
    private val detector = FaceDetection.getClient(faceDetectorOptions)
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        // Calculate approximate distance based on face size
                        val faceWidth = face.boundingBox.width()
                        val faceHeight = face.boundingBox.height()
                        val faceSize = (faceWidth + faceHeight) / 2f
                        
                        // Normalize distance (inverse of face size)
                        val referenceSize = 200f
                        val distance = referenceSize / faceSize
                        
                        onDistanceCalculated(distance)
                        
                        Log.d("FaceDistance", "Face detected - Size: $faceSize, Distance ratio: $distance")
                    } else {
                        Log.d("FaceDistance", "No face detected")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FaceDistance", "Face detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

