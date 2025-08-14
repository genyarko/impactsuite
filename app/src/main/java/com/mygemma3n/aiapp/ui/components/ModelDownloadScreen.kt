package com.mygemma3n.aiapp.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mygemma3n.aiapp.service.ModelDownloadService
import com.mygemma3n.aiapp.service.ModelDownloadForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ModelDownloadState(
    val isDownloading: Boolean = false,
    val currentFile: String? = null,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val overallProgress: Float = 0f,
    val isComplete: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String = "Initializing..."
)

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val downloadService: ModelDownloadService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _downloadState = MutableStateFlow(ModelDownloadState())
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()
    
    private var currentDownloadJob: Job? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    init {
        checkModelStatus()
        setupBroadcastReceiver()
    }
    
    override fun onCleared() {
        super.onCleared()
        broadcastReceiver?.let {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
        }
    }

    private fun checkModelStatus() {
        viewModelScope.launch {
            try {
                val isDownloaded = downloadService.isModelDownloaded()
                if (isDownloaded) {
                    _downloadState.value = _downloadState.value.copy(
                        isComplete = true,
                        statusMessage = "AI Model Ready"
                    )
                } else {
                    _downloadState.value = _downloadState.value.copy(
                        isComplete = false,
                        isDownloading = false,
                        statusMessage = "AI models not found. Click 'Download Models' to install them."
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check model status")
                _downloadState.value = _downloadState.value.copy(
                    isError = true,
                    errorMessage = "Failed to check model status: ${e.message}"
                )
            }
        }
    }
    
    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ModelDownloadForegroundService.ACTION_DOWNLOAD_PROGRESS -> {
                        val currentFile = intent.getStringExtra(ModelDownloadForegroundService.EXTRA_CURRENT_FILE)
                        val fileIndex = intent.getIntExtra(ModelDownloadForegroundService.EXTRA_FILE_INDEX, 0)
                        val totalFiles = intent.getIntExtra(ModelDownloadForegroundService.EXTRA_TOTAL_FILES, 0)
                        val progress = intent.getFloatExtra(ModelDownloadForegroundService.EXTRA_OVERALL_PROGRESS, 0f)
                        
                        _downloadState.value = _downloadState.value.copy(
                            isDownloading = true,
                            currentFile = currentFile,
                            currentFileIndex = fileIndex,
                            totalFiles = totalFiles,
                            overallProgress = progress,
                            statusMessage = when {
                                currentFile != null -> "Downloading $currentFile... ($fileIndex/$totalFiles)"
                                else -> "Preparing download..."
                            },
                            isError = false,
                            errorMessage = null
                        )
                    }
                    ModelDownloadForegroundService.ACTION_DOWNLOAD_COMPLETE -> {
                        _downloadState.value = _downloadState.value.copy(
                            isDownloading = false,
                            isComplete = true,
                            statusMessage = "AI Models Ready! All features enhanced with offline capability.",
                            overallProgress = 1.0f
                        )
                    }
                    ModelDownloadForegroundService.ACTION_DOWNLOAD_ERROR -> {
                        val error = intent.getStringExtra(ModelDownloadForegroundService.EXTRA_ERROR_MESSAGE)
                        _downloadState.value = _downloadState.value.copy(
                            isDownloading = false,
                            isError = true,
                            errorMessage = error,
                            statusMessage = "Download failed: $error"
                        )
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(ModelDownloadForegroundService.ACTION_DOWNLOAD_PROGRESS)
            addAction(ModelDownloadForegroundService.ACTION_DOWNLOAD_COMPLETE)
            addAction(ModelDownloadForegroundService.ACTION_DOWNLOAD_ERROR)
        }
        
        LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver!!, filter)
    }

    fun startModelDownload() {
        // Prevent multiple simultaneous download attempts
        if (_downloadState.value.isDownloading) {
            Timber.w("Download already in progress, ignoring duplicate request")
            return
        }
        
        // Start the foreground service for background download
        // This will handle the actual download independently of UI lifecycle
        try {
            ModelDownloadForegroundService.startDownloadService(context)
            _downloadState.value = _downloadState.value.copy(
                isDownloading = true,
                isError = false,
                errorMessage = null,
                statusMessage = "Download started in background service. Safe to navigate away or minimize app."
            )
            Timber.d("Started foreground download service")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start foreground service")
            _downloadState.value = _downloadState.value.copy(
                isDownloading = false,
                isError = true,
                errorMessage = "Failed to start background download: ${e.message}"
            )
        }
    }
    
    fun cancelDownload() {
        currentDownloadJob?.cancel()
        currentDownloadJob = null
        
        // Stop the foreground service
        try {
            ModelDownloadForegroundService.stopDownloadService(context)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop foreground service")
        }
        
        _downloadState.value = _downloadState.value.copy(
            isDownloading = false,
            statusMessage = "Download cancelled by user",
            isError = false,
            errorMessage = null
        )
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    onDownloadComplete: () -> Unit = {},
    viewModel: ModelDownloadViewModel = hiltViewModel()
) {
    val downloadState by viewModel.downloadState.collectAsState()
    
    // Navigate to main app when download is complete
    LaunchedEffect(downloadState.isComplete) {
        if (downloadState.isComplete && !downloadState.isError) {
            kotlinx.coroutines.delay(1500) // Show success state briefly
            onDownloadComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "G3N Setup",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Status Icon
                    when {
                        downloadState.isError -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        downloadState.isComplete -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Complete",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        else -> {
                            CircularProgressIndicatorWithIcon(
                                progress = downloadState.overallProgress,
                                isIndeterminate = !downloadState.isDownloading
                            )
                        }
                    }

                    // Title
                    Text(
                        text = when {
                            downloadState.isError -> "Setup Failed"
                            downloadState.isComplete -> "Ready to Go!"
                            else -> "Setting up AI Models"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    // Status Message
                    Text(
                        text = downloadState.statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Progress Bar (only show when downloading)
                    if (downloadState.isDownloading && downloadState.totalFiles > 0) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                            progress = { downloadState.overallProgress },
                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(8.dp)
                                                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${(downloadState.overallProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Text(
                                    text = "${downloadState.currentFileIndex}/${downloadState.totalFiles} files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Error message or retry button
                    if (downloadState.isError) {
                        downloadState.errorMessage?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Button(
                            onClick = { viewModel.startModelDownload() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Retry Download")
                        }
                    }

                    // Info text
                    if (!downloadState.isError) {
                        Text(
                            text = when {
                                downloadState.isComplete -> "G3N is ready! All AI features are now available."
                                downloadState.isDownloading -> "The download continues in the background even if you minimize the app or the screen turns off. This may take several minutes depending on your internet connection."
                                else -> "G3N needs to download AI models to provide you with the best offline experience. Most features will still work with online APIs while models are being downloaded."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularProgressIndicatorWithIcon(
    progress: Float,
    isIndeterminate: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isIndeterminate) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(64.dp)
                    .rotate(rotationAngle),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
        } else {
            CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
            )
        }
        
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = "Downloading",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}