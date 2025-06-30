package com.example.mygemma3n.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.mygemma3n.MainActivity
import com.example.mygemma3n.R
import com.example.mygemma3n.feature.caption.AudioCapture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AudioCaptureService : Service() {

    @Inject
    lateinit var audioCapture: AudioCapture

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val ACTION_START_CAPTURE = "com.example.mygemma3n.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.example.mygemma3n.STOP_CAPTURE"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audio_capture_channel"

        // Service state
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        // Audio data flow
        private val _audioDataFlow = MutableStateFlow<FloatArray?>(null)
        val audioDataFlow: StateFlow<FloatArray?> = _audioDataFlow
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> startCapture()
            ACTION_STOP_CAPTURE -> stopCapture()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCapture() {
        if (_isRunning.value) return

        // Check permission first
        if (!audioCapture.hasRecordPermission()) {
            _audioDataFlow.value = null
            stopSelf()
            return
        }

        _isRunning.value = true

        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Start audio capture
        serviceScope.launch {
            try {
                audioCapture.startCapture()
                    .collect { audioData ->
                        _audioDataFlow.value = audioData
                    }
            } catch (e: SecurityException) {
                // Permission was revoked while running
                e.printStackTrace()
                stopCapture()
            } catch (e: Exception) {
                e.printStackTrace()
                stopCapture()
            }
        }
    }

    private fun stopCapture() {
        _isRunning.value = false
        audioCapture.stopCapture()
        _audioDataFlow.value = null

        // Stop foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live captioning audio capture"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = ACTION_STOP_CAPTURE
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Live Caption Active")
            .setContentText("Capturing audio for live transcription")
            .setSmallIcon(R.drawable.ic_launcher_background) // You'll need to add this icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground, // You'll need to add this icon
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        audioCapture.stopCapture()
        serviceScope.cancel()
    }
}