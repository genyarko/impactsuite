package com.mygemma3n.aiapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mygemma3n.aiapp.MainActivity
import com.mygemma3n.aiapp.R
import com.mygemma3n.aiapp.data.UnifiedGemmaService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ModelDownloadForegroundService : Service() {
    
    companion object {
        const val ACTION_START_DOWNLOAD = "START_DOWNLOAD"
        const val ACTION_STOP_DOWNLOAD = "STOP_DOWNLOAD"
        const val NOTIFICATION_CHANNEL_ID = "model_download_foreground_channel"
        const val NOTIFICATION_ID = 1002
        
        // Broadcast actions for UI updates
        const val ACTION_DOWNLOAD_PROGRESS = "com.mygemma3n.aiapp.DOWNLOAD_PROGRESS"
        const val ACTION_DOWNLOAD_COMPLETE = "com.mygemma3n.aiapp.DOWNLOAD_COMPLETE"
        const val ACTION_DOWNLOAD_ERROR = "com.mygemma3n.aiapp.DOWNLOAD_ERROR"
        
        // Broadcast extras
        const val EXTRA_CURRENT_FILE = "current_file"
        const val EXTRA_FILE_INDEX = "file_index"
        const val EXTRA_TOTAL_FILES = "total_files"
        const val EXTRA_OVERALL_PROGRESS = "overall_progress"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        
        fun startDownloadService(context: Context) {
            val intent = Intent(context, ModelDownloadForegroundService::class.java).apply {
                action = ACTION_START_DOWNLOAD
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopDownloadService(context: Context) {
            val intent = Intent(context, ModelDownloadForegroundService::class.java).apply {
                action = ACTION_STOP_DOWNLOAD
            }
            context.startService(intent)
        }
    }
    
    @Inject
    lateinit var modelDownloadService: ModelDownloadService
    
    @Inject
    lateinit var unifiedGemmaService: UnifiedGemmaService
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var downloadJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> startModelDownload()
            ACTION_STOP_DOWNLOAD -> stopModelDownload()
        }
        return START_STICKY // Restart if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        downloadJob?.cancel()
        serviceScope.cancel()
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "G3N::ModelDownloadWakeLock"
            ).apply {
                acquire(30 * 60 * 1000L) // 30 minutes max
            }
            Timber.d("WakeLock acquired for model download")
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire WakeLock")
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Timber.d("WakeLock released")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to release WakeLock")
        }
        wakeLock = null
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Model Download Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Downloads AI models in the background"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(
        title: String = "Downloading AI Models",
        content: String = "Preparing download...",
        progress: Int = -1
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        
        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(100, 0, true)
        }
        
        return builder.build()
    }
    
    private fun startModelDownload() {
        // Prevent concurrent downloads
        if (downloadJob?.isActive == true) {
            Timber.w("Download already in progress in service, ignoring duplicate request")
            return
        }
        
        val initialNotification = createNotification()
        startForeground(NOTIFICATION_ID, initialNotification)
        
        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            try {
                Timber.d("Starting model download in foreground service")
                var downloadCompleted = false
                
                val result = modelDownloadService.downloadModel { progress ->
                    // Update foreground notification with progress
                    val notification = createNotification(
                        title = "Downloading AI Models",
                        content = when {
                            progress.error != null -> "Error: ${progress.error}"
                            progress.isComplete -> "Download complete!"
                            progress.currentFile != null -> "${progress.currentFile} (${progress.currentFileIndex}/${progress.totalFiles})"
                            else -> "Preparing download..."
                        },
                        progress = (progress.overallProgress * 100).toInt()
                    )
                    
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notification)
                    
                    // Broadcast progress to UI
                    if (progress.error != null) {
                        broadcastError(progress.error!!)
                    } else if (progress.isComplete) {
                        downloadCompleted = true
                        broadcastComplete()
                    } else {
                        broadcastProgress(progress)
                    }
                }
                
                if (result.isSuccess) {
                    Timber.d("Model download completed successfully")
                    
                    // Reinitialize UnifiedGemmaService with newly downloaded model
                    try {
                        Timber.d("Reinitializing UnifiedGemmaService with downloaded model")
                        unifiedGemmaService.initializeBestAvailable()
                        Timber.d("UnifiedGemmaService reinitialized successfully")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to reinitialize UnifiedGemmaService after download")
                        // Continue anyway - user can restart app if needed
                    }
                    
                    val completionNotification = createNotification(
                        title = "AI Models Ready",
                        content = "All models downloaded and initialized! App is ready for offline use.",
                        progress = 100
                    )
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, completionNotification)
                    
                    // Show completion notification briefly
                    delay(3000)
                } else {
                    Timber.e("Model download failed: ${result.exceptionOrNull()?.message}")
                    val errorNotification = createNotification(
                        title = "Download Failed",
                        content = "Model download failed. Please try again.",
                        progress = -1
                    )
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, errorNotification)
                    
                    // Show error notification briefly
                    delay(5000)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Exception during model download")
                val errorNotification = createNotification(
                    title = "Download Failed",
                    content = "Unexpected error occurred. Please try again.",
                    progress = -1
                )
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, errorNotification)
                delay(5000)
            } finally {
                stopSelf()
            }
        }
    }
    
    private fun stopModelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        stopSelf()
    }
    
    private fun broadcastProgress(progress: ModelDownloadService.DownloadProgress) {
        val intent = Intent(ACTION_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_CURRENT_FILE, progress.currentFile)
            putExtra(EXTRA_FILE_INDEX, progress.currentFileIndex)
            putExtra(EXTRA_TOTAL_FILES, progress.totalFiles)
            putExtra(EXTRA_OVERALL_PROGRESS, progress.overallProgress)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    private fun broadcastComplete() {
        val intent = Intent(ACTION_DOWNLOAD_COMPLETE)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    private fun broadcastError(error: String) {
        val intent = Intent(ACTION_DOWNLOAD_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, error)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}