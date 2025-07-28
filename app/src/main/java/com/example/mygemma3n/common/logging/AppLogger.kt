package com.example.mygemma3n.common.logging

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.synchronized

@Singleton
class AppLogger @Inject constructor(
    private val context: Context
) {
    
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
        val metadata: Map<String, String> = emptyMap()
    )
    
    enum class LogLevel(val priority: Int, val displayName: String) {
        VERBOSE(Log.VERBOSE, "VERBOSE"),
        DEBUG(Log.DEBUG, "DEBUG"),
        INFO(Log.INFO, "INFO"),
        WARN(Log.WARN, "WARN"),
        ERROR(Log.ERROR, "ERROR")
    }
    
    private val logEntries = mutableListOf<LogEntry>()
    private val maxLogEntries = 1000
    private val logFile = File(context.filesDir, "app_logs.txt")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    init {
        initializeTimber()
    }
    
    private fun initializeTimber() {
        // Remove any existing trees
        Timber.uprootAll()
        
        // Plant debug tree for debug builds
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Timber.plant(object : Timber.DebugTree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    super.log(priority, tag, message, t)
                    
                    // Also log to our custom system
                    logToSystem(
                        level = LogLevel.values().find { it.priority == priority } ?: LogLevel.DEBUG,
                        tag = tag ?: "UnknownTag",
                        message = message,
                        throwable = t
                    )
                }
                
                override fun createStackElementTag(element: StackTraceElement): String {
                    return "${super.createStackElementTag(element)}:${element.lineNumber}"
                }
            })
        }
        
        // Plant file logging tree for production
        Timber.plant(FileLoggingTree())
    }
    
    private fun logToSystem(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable,
            metadata = metadata
        )
        
        synchronized(logEntries) {
            logEntries.add(entry)
            
            // Keep only the most recent entries
            if (logEntries.size > maxLogEntries) {
                logEntries.removeAt(0)
            }
        }
        
        // Write to file asynchronously
        writeToFileAsync(entry)
    }
    
    private fun writeToFileAsync(entry: LogEntry) {
        try {
            val logLine = formatLogEntry(entry)
            
            // Append to log file
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(logLine)
            }
            
            // Rotate log file if it gets too large (10MB limit)
            if (logFile.length() > 10 * 1024 * 1024) {
                rotateLogFile()
            }
            
        } catch (e: Exception) {
            // Don't crash the app due to logging issues
            Log.e("AppLogger", "Failed to write log to file", e)
        }
    }
    
    private fun formatLogEntry(entry: LogEntry): String {
        val timestamp = dateFormat.format(Date(entry.timestamp))
        val level = entry.level.displayName
        val tag = entry.tag
        val message = entry.message
        
        val baseLog = "$timestamp [$level] $tag: $message"
        
        val metadataString = if (entry.metadata.isNotEmpty()) {
            " | Metadata: ${entry.metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else ""
        
        val throwableString = entry.throwable?.let { 
            " | Exception: ${it.javaClass.simpleName}: ${it.message}\n${it.stackTrace.take(5).joinToString("\n") { "    at $it" }}"
        } ?: ""
        
        return baseLog + metadataString + throwableString
    }
    
    private fun rotateLogFile() {
        try {
            val backupFile = File(context.filesDir, "app_logs_backup.txt")
            
            // Delete old backup
            if (backupFile.exists()) {
                backupFile.delete()
            }
            
            // Move current log to backup
            logFile.renameTo(backupFile)
            
            Timber.i("Log file rotated")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to rotate log file")
        }
    }
    
    /**
     * Get recent log entries
     */
    fun getRecentLogs(count: Int = 100): List<LogEntry> {
        synchronized(logEntries) {
            return logEntries.takeLast(count).toList()
        }
    }
    
    /**
     * Get logs filtered by level
     */
    fun getLogsByLevel(level: LogLevel, count: Int = 100): List<LogEntry> {
        synchronized(logEntries) {
            return logEntries
                .filter { it.level.priority >= level.priority }
                .takeLast(count)
                .toList()
        }
    }
    
    /**
     * Get logs filtered by tag
     */
    fun getLogsByTag(tag: String, count: Int = 100): List<LogEntry> {
        synchronized(logEntries) {
            return logEntries
                .filter { it.tag.contains(tag, ignoreCase = true) }
                .takeLast(count)
                .toList()
        }
    }
    
    /**
     * Clear all logs
     */
    fun clearLogs() {
        synchronized(logEntries) {
            logEntries.clear()
        }
        
        try {
            if (logFile.exists()) {
                logFile.delete()
            }
            Timber.i("Logs cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear log file")
        }
    }
    
    /**
     * Export logs to a file
     */
    fun exportLogs(): File? {
        return try {
            val exportFile = File(context.getExternalFilesDir(null), "exported_logs_${System.currentTimeMillis()}.txt")
            
            exportFile.writer().use { writer ->
                synchronized(logEntries) {
                    logEntries.forEach { entry ->
                        writer.appendLine(formatLogEntry(entry))
                    }
                }
            }
            
            Timber.i("Logs exported to ${exportFile.absolutePath}")
            exportFile
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to export logs")
            null
        }
    }
    
    /**
     * Log performance metrics
     */
    fun logPerformance(
        operation: String,
        durationMs: Long,
        metadata: Map<String, String> = emptyMap()
    ) {
        val enhancedMetadata = metadata + mapOf(
            "operation" to operation,
            "duration_ms" to durationMs.toString(),
            "performance" to "true"
        )
        
        val level = when {
            durationMs > 5000 -> LogLevel.WARN
            durationMs > 1000 -> LogLevel.INFO
            else -> LogLevel.DEBUG
        }
        
        logToSystem(
            level = level,
            tag = "Performance",
            message = "$operation completed in ${durationMs}ms",
            metadata = enhancedMetadata
        )
    }
    
    /**
     * Log user interactions
     */
    fun logUserInteraction(
        action: String,
        screen: String,
        metadata: Map<String, String> = emptyMap()
    ) {
        val enhancedMetadata = metadata + mapOf(
            "action" to action,
            "screen" to screen,
            "user_interaction" to "true"
        )
        
        logToSystem(
            level = LogLevel.INFO,
            tag = "UserInteraction",
            message = "User $action on $screen",
            metadata = enhancedMetadata
        )
    }
    
    /**
     * Log AI/Model operations
     */
    fun logAIOperation(
        operation: String,
        success: Boolean,
        tokensUsed: Int? = null,
        responseTime: Long? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val enhancedMetadata = metadata.toMutableMap().apply {
            put("operation", operation)
            put("success", success.toString())
            put("ai_operation", "true")
            tokensUsed?.let { put("tokens_used", it.toString()) }
            responseTime?.let { put("response_time_ms", it.toString()) }
        }
        
        logToSystem(
            level = if (success) LogLevel.INFO else LogLevel.WARN,
            tag = "AIOperation",
            message = "AI operation '$operation' ${if (success) "succeeded" else "failed"}",
            metadata = enhancedMetadata
        )
    }
    
    /**
     * Get logging statistics
     */
    fun getLoggingStats(): Map<String, Any> {
        synchronized(logEntries) {
            val stats = mutableMapOf<String, Any>()
            
            stats["totalLogEntries"] = logEntries.size
            stats["logLevelCounts"] = LogLevel.entries.associate { level ->
                level.displayName to logEntries.count { it.level == level }
            }
            stats["mostActiveTag"] = logEntries
                .groupBy { it.tag }
                .maxByOrNull { it.value.size }
                ?.key ?: "None"
            stats["logFileSize"] = if (logFile.exists()) logFile.length() else 0L
            stats["oldestLogTimestamp"] = logEntries.firstOrNull()?.timestamp as Any
            stats["newestLogTimestamp"] = logEntries.lastOrNull()?.timestamp as Any
            
            return stats
        }
    }
}

/**
 * Custom Timber tree for file logging
 */
private class FileLoggingTree : Timber.Tree() {
    
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // File logging is handled by AppLogger.logToSystem
        // This tree is just for completeness
    }
    
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        // Log everything above DEBUG level to file
        return priority >= Log.INFO
    }
}

/**
 * Performance measurement utilities
 */
inline fun <T> AppLogger.measurePerformance(
    operation: String,
    metadata: Map<String, String> = emptyMap(),
    block: () -> T
): T {
    val startTime = System.currentTimeMillis()
    try {
        return block()
    } finally {
        val duration = System.currentTimeMillis() - startTime
        logPerformance(operation, duration, metadata)
    }
}

suspend inline fun <T> AppLogger.measureSuspendPerformance(
    operation: String,
    metadata: Map<String, String> = emptyMap(),
    crossinline block: suspend () -> T
): T {
    val startTime = System.currentTimeMillis()
    try {
        return block()
    } finally {
        val duration = System.currentTimeMillis() - startTime
        logPerformance(operation, duration, metadata)
    }
}