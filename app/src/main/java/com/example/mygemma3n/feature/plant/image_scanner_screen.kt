package com.example.mygemma3n.feature.plant

import android.graphics.BitmapFactory
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mygemma3n.shared_ui.CameraPreview
import com.google.accompanist.permissions.*
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlantScannerScreen(
    viewModel: PlantScannerViewModel = hiltViewModel()
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val camPerm = rememberPermissionState(android.Manifest.permission.CAMERA)

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            setImageCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        }
    }

    LaunchedEffect(camPerm.status.isGranted) {
        if (camPerm.status.isGranted) controller.bindToLifecycle(lifecycleOwner)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with improved styling
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Grass,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Image Scanner",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (camPerm.status.isGranted) {
                // Camera preview with overlay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    CameraPreview(
                        controller = controller,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    )

                    // Camera overlay with scanning frame
                    CameraScanningOverlay(
                        isScanning = scanState.isAnalyzing,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Capture button with improved design
                FloatingActionButton(
                    onClick = {
                        val photo = File(context.cacheDir, "${System.currentTimeMillis()}.jpg")
                        val opts = ImageCapture.OutputFileOptions.Builder(photo).build()
                        val exec = ContextCompat.getMainExecutor(context)

                        controller.takePicture(opts, exec,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                    viewModel.analyzeImage(
                                        BitmapFactory.decodeFile(photo.absolutePath)
                                    )
                                }
                                override fun onError(e: ImageCaptureException) = e.printStackTrace()
                            })
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .padding(bottom = 16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    if (scanState.isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = "Capture Photo",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Results section with improved styling
                AnimatedVisibility(
                    visible = scanState.error != null || scanState.currentAnalysis != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    ResultsSection(
                        scanState = scanState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    )
                }

            } else {
                // Permission request section with improved design
                PermissionRequestSection(
                    permissionState = camPerm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CameraScanningOverlay(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Scanning frame
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.Center)
                .background(
                    Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            // Corner indicators
            val cornerColor = if (isScanning) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White.copy(alpha = 0.8f)
            }

            // Top-left corner
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.TopStart)
                    .background(cornerColor, RoundedCornerShape(topStart = 8.dp))
            )

            // Top-right corner
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.TopEnd)
                    .background(cornerColor, RoundedCornerShape(topEnd = 8.dp))
            )

            // Bottom-left corner
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.BottomStart)
                    .background(cornerColor, RoundedCornerShape(bottomStart = 8.dp))
            )

            // Bottom-right corner
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.BottomEnd)
                    .background(cornerColor, RoundedCornerShape(bottomEnd = 8.dp))
            )
        }

        // Instruction text
        Text(
            text = if (isScanning) "Analyzing..." else "Position plant in frame",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ResultsSection(
    scanState: ImageScanState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when {
                scanState.error != null -> {
                    ErrorCard(error = scanState.error)
                }
                scanState.currentAnalysis != null -> {
                    val analysis = scanState.currentAnalysis
                    AnalysisResultCard(
                        analysis = analysis,
                        isUsingOnlineService = scanState.isUsingOnlineService
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Error: $error",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AnalysisResultCard(
    analysis: GeneralAnalysis,
    isUsingOnlineService: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Service mode indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = if (isUsingOnlineService) Icons.Default.Info else Icons.Default.Grass,
                contentDescription = null,
                tint = if (isUsingOnlineService) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isUsingOnlineService) "Online Analysis" else "Offline Analysis",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Main result with confidence
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Grass,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = analysis.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Confidence: ${(analysis.confidence * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                // Confidence indicator
                CircularProgressIndicator(
                progress = { analysis.confidence },
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
                )
            }
        }

        // Plant-specific details
        analysis.plantSpecies?.let { species ->
            DetailCard(
                icon = Icons.Default.Info,
                title = "Plant Species",
                content = species
            )

            analysis.disease?.let { disease ->
                DetailCard(
                    icon = Icons.Default.Warning,
                    title = "Disease Detected",
                    content = "$disease\nSeverity: ${analysis.severity ?: "N/A"}",
                    isWarning = true
                )
            }

            if (analysis.recommendations.isNotEmpty()) {
                RecommendationsCard(recommendations = analysis.recommendations)
            }

            analysis.additionalInfo?.let { info ->
                DetailCard(
                    icon = Icons.Default.Info,
                    title = "Care Instructions",
                    content = "Watering: ${info.wateringNeeds}\nSunlight: ${info.sunlightNeeds}"
                )
            }
        }
    }
}

@Composable
private fun DetailCard(
    icon: ImageVector,
    title: String,
    content: String,
    isWarning: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isWarning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isWarning) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isWarning) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun RecommendationsCard(recommendations: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Recommendations",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            recommendations.forEach { recommendation ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = recommendation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionRequestSection(
    permissionState: PermissionState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Camera Access Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            val rationale = if (permissionState.status.shouldShowRationale) {
                "Camera permission is required to scan and analyze plant images. Please grant access to continue."
            } else {
                "To identify plants and detect diseases, we need access to your camera."
            }

            Text(
                text = rationale,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { permissionState.launchPermissionRequest() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Camera Permission")
            }
        }
    }
}
