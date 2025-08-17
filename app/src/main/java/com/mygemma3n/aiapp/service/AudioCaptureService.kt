package com.mygemma3n.aiapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mygemma3n.aiapp.MainActivity
import com.mygemma3n.aiapp.feature.caption.AudioCapture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AudioCaptureService : Service() {

    @Inject
    lateinit var audioCapture: AudioCapture

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val ACTION_START_CAPTURE = "com.mygemma3n.aiapp.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.mygemma3n.aiapp.STOP_CAPTURE"

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
        Timber.tag("AudioCaptureService").d("Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag("AudioCaptureService").d("onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_CAPTURE -> startCapture()
            ACTION_STOP_CAPTURE -> stopCapture()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCapture() {
        if (_isRunning.value) {
            Timber.tag("AudioCaptureService").d("startCapture: Already running")
            return
        }

        // Check permission first
        if (!audioCapture.hasRecordPermission()) {
            Timber.tag("AudioCaptureService").e("startCapture: RECORD_AUDIO permission not granted")
            _audioDataFlow.value = null
            stopSelf()
            return
        }

        Timber.tag("AudioCaptureService").d("startCapture: Starting audio capture")
        _isRunning.value = true

        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Start audio capture
        serviceScope.launch {
            try {
                audioCapture.startCapture()
                    .collect { audioData ->
                        _audioDataFlow.value = audioData
                        Timber.tag("AudioCaptureService")
                            .d("Emitted audioData of size: ${audioData.size}")
                    }
            } catch (e: SecurityException) {
                Timber.tag("AudioCaptureService").e(e, "SecurityException: ${e.message}")
                // Permission was revoked while running
                stopCapture()
            } catch (e: Exception) {
                Timber.tag("AudioCaptureService").e(e, "Exception: ${e.message}")
                stopCapture()
            }
        }
    }

    private fun stopCapture() {
        Timber.tag("AudioCaptureService").d("stopCapture called")
        _isRunning.value = false
        audioCapture.stopCapture()
        _audioDataFlow.value = null

        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)

        stopSelf()
    }

    private fun createNotificationChannel() {
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

        // Get logo resource ID, fallback to system icon if not found
        val logoResId = resources.getIdentifier("logo", "drawable", packageName)
        val iconResId = if (logoResId != 0) logoResId else android.R.drawable.ic_dialog_info

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Live Caption Active")
            .setContentText("Capturing audio for live transcription")
            .setSmallIcon(iconResId)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                iconResId,
                "Stop",
                stopPendingIntent
            )
            .build()
    }



    override fun onDestroy() {
        super.onDestroy()
        Timber.tag("AudioCaptureService").d("Service destroyed")
        _isRunning.value = false
        audioCapture.stopCapture()
        serviceScope.cancel()
    }

}