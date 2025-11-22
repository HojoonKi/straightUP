package com.example.straightup

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for collecting and logging posture data
 */
object DataCollectionService {
    
    private const val FILE_NAME = "straightup_data.csv"
    private const val TAG = "DataCollection"
    
    private var isCollecting = false
    private var dataFile: File? = null
    
    /**
     * Start data collection
     */
    fun startCollection(context: Context) {
        if (isCollecting) {
            Log.w(TAG, "Data collection already started")
            return
        }
        
        isCollecting = true
        dataFile = getDataFile(context)
        
        // Create file with header if it doesn't exist
        if (!dataFile!!.exists()) {
            createFileWithHeader()
        }
        
        Log.d(TAG, "Data collection started. File: ${dataFile!!.absolutePath}")
    }
    
    /**
     * Stop data collection
     */
    fun stopCollection() {
        isCollecting = false
        Log.d(TAG, "Data collection stopped")
    }
    
    /**
     * Check if currently collecting data
     */
    fun isCollecting(): Boolean = isCollecting
    
    /**
     * Log a posture event
     */
    fun logPostureEvent(
        timestamp: Long = System.currentTimeMillis(),
        isGoodPosture: Boolean,
        score: Int,
        tiltAngle: Float?,
        faceDistance: Float?
    ) {
        if (!isCollecting || dataFile == null) {
            return
        }
        
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val dateTime = dateFormat.format(Date(timestamp))
            val postureStatus = if (isGoodPosture) "GOOD" else "BAD"
            
            val line = "$dateTime,$postureStatus,$score,${tiltAngle ?: "NULL"},${faceDistance ?: "NULL"}\n"
            
            FileWriter(dataFile, true).use { writer ->
                writer.append(line)
            }
            
            Log.d(TAG, "Logged: $postureStatus (score: $score)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log posture event", e)
        }
    }
    
    /**
     * Get data file
     */
    private fun getDataFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }
    
    /**
     * Create file with CSV header
     */
    private fun createFileWithHeader() {
        try {
            FileWriter(dataFile).use { writer ->
                writer.append("timestamp,posture_status,score,tilt_angle,face_distance\n")
            }
            Log.d(TAG, "Created new data file with header")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create file with header", e)
        }
    }
    
    /**
     * Get file path for external access
     */
    fun getDataFilePath(context: Context): String? {
        return getDataFile(context).absolutePath
    }
    
    /**
     * Get collected data summary
     */
    fun getDataSummary(context: Context): DataSummary? {
        val file = getDataFile(context)
        if (!file.exists()) {
            return null
        }
        
        try {
            val lines = file.readLines()
            if (lines.size <= 1) { // Only header or empty
                return DataSummary(0, 0, 0)
            }
            
            var goodCount = 0
            var badCount = 0
            
            lines.drop(1).forEach { line ->
                if (line.contains("GOOD")) goodCount++
                if (line.contains("BAD")) badCount++
            }
            
            return DataSummary(
                totalRecords = goodCount + badCount,
                goodPostureCount = goodCount,
                badPostureCount = badCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read data summary", e)
            return null
        }
    }
    
    /**
     * Export data file to Downloads folder
     */
    fun exportToDownloads(context: Context): Boolean {
        val sourceFile = getDataFile(context)
        if (!sourceFile.exists()) {
            Log.w(TAG, "No data file to export")
            return false
        }
        
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ use MediaStore
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Log.d(TAG, "Data exported to Downloads")
                    true
                } else {
                    false
                }
            } else {
                // Android 9 and below
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val destFile = File(downloadsDir, FILE_NAME)
                sourceFile.copyTo(destFile, overwrite = true)
                Log.d(TAG, "Data exported to Downloads: ${destFile.absolutePath}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export data", e)
            false
        }
    }
    
    /**
     * Get URI for sharing the data file
     */
    fun getShareUri(context: Context): android.net.Uri? {
        val file = getDataFile(context)
        if (!file.exists()) {
            return null
        }
        
        return try {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get share URI", e)
            null
        }
    }
}

data class DataSummary(
    val totalRecords: Int,
    val goodPostureCount: Int,
    val badPostureCount: Int
)
