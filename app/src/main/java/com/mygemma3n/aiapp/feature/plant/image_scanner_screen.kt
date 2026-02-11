package com.mygemma3n.aiapp.feature.plant

import android.graphics.BitmapFactory
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Quiz
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.selection.SelectionContainer
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.launch
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mygemma3n.aiapp.shared_ui.CameraPreview
import com.google.accompanist.permissions.*
import java.io.File
import kotlin.math.roundToInt
import com.mygemma3n.aiapp.feature.voice.VoiceCommandViewModel
import timber.log.Timber

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlantScannerScreen(
    viewModel: PlantScannerViewModel = hiltViewModel(),
    onNavigateToQuiz: (() -> Unit)? = null,
    voiceCommandViewModel: VoiceCommandViewModel = hiltViewModel()
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    
    // Update voice command context when OCR text changes
    LaunchedEffect(scanState.extractedText) {
        voiceCommandViewModel.setCurrentContent(scanState.extractedText)
    }

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val camPerm = rememberPermissionState(android.Manifest.permission.CAMERA)
    
    // Voice command integration for camera initialization
    val voiceUiState by voiceCommandViewModel.uiState.collectAsStateWithLifecycle()
    
    // Simple camera controller that refreshes when needed
    var refreshCamera by remember { mutableStateOf(0) }
    
    val controller = remember(refreshCamera) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            setImageCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            
            // OCR-optimized camera settings
            if (scanState.isOcrMode) {
                // Optimize for text capture
                cameraControl?.enableTorch(false) // Use natural lighting when possible
            }
        }
    }
    
    // Bind camera to lifecycle when permission is granted
    LaunchedEffect(camPerm.status.isGranted, controller) {
        if (camPerm.status.isGranted) {
            Timber.d("Binding camera to lifecycle")
            // Small delay to ensure proper initialization
            kotlinx.coroutines.delay(100L)
            try {
                controller.bindToLifecycle(lifecycleOwner)
                Timber.d("Camera bound successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind camera to lifecycle")
            }
        }
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
            // Header with improved styling and dynamic colors
            val headerColor by animateColorAsState(
                targetValue = if (scanState.isOcrMode) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                animationSpec = tween(400),
                label = "headerColor"
            )
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = headerColor,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mainIconColor by animateColorAsState(
                        targetValue = if (scanState.isOcrMode) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        animationSpec = tween(300),
                        label = "mainIconColor"
                    )
                    
                    Crossfade(
                        targetState = scanState.isOcrMode,
                        animationSpec = tween(300),
                        label = "mainIconCrossfade"
                    ) { isOcr ->
                        Icon(
                            imageVector = if (isOcr) Icons.Default.TextFields else Icons.Default.Grass,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = mainIconColor
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val textColor by animateColorAsState(
                            targetValue = if (scanState.isOcrMode) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            animationSpec = tween(300),
                            label = "textColor"
                        )
                        
                        Text(
                            text = if (scanState.isOcrMode) "OCR Text Scanner" else "Plant & Food Scanner",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        
                        AnimatedVisibility(
                            visible = scanState.isOcrMode,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            Text(
                                text = "Handwriting & Text Recognition",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // OCR mode toggle button with animation
                    AnimatedToggleButton(
                        isOcrMode = scanState.isOcrMode,
                        onClick = { viewModel.toggleOcrMode() }
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
                        isOcrMode = scanState.isOcrMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Capture button with improved design
                FloatingActionButton(
                    onClick = {
                        Timber.d("Capture button clicked - Analyzing: ${scanState.isAnalyzing}")
                        
                        if (scanState.isAnalyzing) {
                            Timber.w("Cannot take picture - already analyzing")
                            return@FloatingActionButton
                        }
                        
                        try {
                            val photo = File(context.cacheDir, "${System.currentTimeMillis()}.jpg")
                            val opts = ImageCapture.OutputFileOptions.Builder(photo).apply {
                                // OCR-optimized capture settings
                                if (scanState.isOcrMode) {
                                    // Higher quality for text recognition
                                    setMetadata(ImageCapture.Metadata().apply {
                                        isReversedHorizontal = false
                                    })
                                }
                            }.build()
                            val exec = ContextCompat.getMainExecutor(context)

                            controller.takePicture(opts, exec,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                        Timber.d("Photo saved successfully")
                                        val bitmap = BitmapFactory.decodeFile(photo.absolutePath)
                                        if (scanState.isOcrMode) {
                                            viewModel.analyzeImageForOCR(bitmap)
                                        } else {
                                            viewModel.analyzeImage(bitmap)
                                        }
                                    }
                                    override fun onError(e: ImageCaptureException) {
                                        Timber.e(e, "Error taking picture")
                                        if (e.message?.contains("Not bound to a valid Camera") == true) {
                                            Timber.w("Camera binding issue detected, will rebind on next attempt")
                                            // Trigger camera refresh by incrementing counter
                                            refreshCamera++
                                        }
                                        e.printStackTrace()
                                    }
                                })
                        } catch (e: Exception) {
                            Timber.e(e, "Exception in capture button onClick")
                        }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .padding(bottom = 16.dp),
                    containerColor = if (scanState.isAnalyzing) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
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
                    visible = scanState.error != null || scanState.currentAnalysis != null || scanState.extractedText != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    ResultsSection(
                        scanState = scanState,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        onNavigateToQuiz = onNavigateToQuiz
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
private fun AnimatedToggleButton(
    isOcrMode: Boolean,
    onClick: () -> Unit
) {
    // Animation values
    val animationSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    
    val rotationAngle by animateFloatAsState(
        targetValue = if (isOcrMode) 360f else 0f,
        animationSpec = animationSpec,
        label = "rotation"
    )
    
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isOcrMode) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300),
        label = "iconColor"
    )
    
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .scale(scale)
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = rotationAngle
                }
        ) {
            Crossfade(
                targetState = isOcrMode,
                animationSpec = tween(300),
                label = "iconCrossfade"
            ) { ocrMode ->
                Icon(
                    imageVector = if (ocrMode) Icons.Default.Grass else Icons.Default.TextFields,
                    contentDescription = if (ocrMode) "Switch to Scanner" else "Switch to OCR",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun CameraScanningOverlay(
    isScanning: Boolean,
    isOcrMode: Boolean = false,
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    isScanning -> "Analyzing with enhanced recognition..."
                    isOcrMode -> "Position handwritten text in frame"
                    else -> "Position object in frame"
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            if (isOcrMode && !isScanning) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💡 Tip: Good lighting & clear writing work best",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ResultsSection(
    scanState: ImageScanState,
    viewModel: PlantScannerViewModel,
    modifier: Modifier = Modifier,
    onNavigateToQuiz: (() -> Unit)? = null
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
                scanState.isOcrMode && scanState.extractedText != null -> {
                    val navigateToQuiz = onNavigateToQuiz
                    val extractedText = scanState.extractedText
                    
                    OCRResultCard(
                        extractedText = extractedText,
                        viewModel = viewModel,
                        isUsingGeminiOCR = scanState.isUsingGeminiOCR,
                        onGenerateQuiz = if (navigateToQuiz != null) {
                            {
                                // Set up content for quiz generation
                                com.mygemma3n.aiapp.shared_utilities.QuizContentManager.setContent(
                                    content = extractedText,
                                    title = "Handwritten Text Quiz"
                                )
                                // Navigate to quiz screen
                                navigateToQuiz()
                            }
                        } else null
                    )
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
private fun OCRResultCard(
    extractedText: String,
    viewModel: PlantScannerViewModel,
    isUsingGeminiOCR: Boolean = false,
    onGenerateQuiz: (() -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with OCR indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TextFields,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isUsingGeminiOCR) "Extracted Text (Gemini 2.5 Flash)" else "Extracted Text (Local ML Kit)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Main extracted text display
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recognized Text",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (isUsingGeminiOCR) {
                            Text(
                                text = "✨ Enhanced AI Recognition",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Selectable text content
                SelectionContainer {
                    Text(
                        text = extractedText.ifBlank { "No text detected in image" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    )
                }
                
                if (extractedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Action buttons - First Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Copy to clipboard button
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(extractedText))
                                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelMedium)
                        }
                        
                        // Share button
                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, extractedText)
                                    putExtra(Intent.EXTRA_SUBJECT, "OCR Extracted Text")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share text"))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Download buttons - Second Row
                    DownloadButtonsRow(
                        text = extractedText,
                        viewModel = viewModel,
                        context = context
                    )
                    
                    // Generate Quiz Button - Third Row (if text is suitable for quiz generation)
                    // Debug: Log button conditions
                    val hasCallback = onGenerateQuiz != null
                    val hasEnoughText = extractedText.length >= 10
                    Timber.d("Quiz button conditions: hasCallback=$hasCallback, textLength=${extractedText.length}, hasEnoughText=$hasEnoughText")
                    
                    if (hasEnoughText && hasCallback) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onGenerateQuiz() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generate Quiz from Text",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadButtonsRow(
    text: String,
    viewModel: PlantScannerViewModel,
    context: Context
) {
    val coroutineScope = rememberCoroutineScope()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // TXT Download
        OutlinedButton(
            onClick = {
                coroutineScope.launch {
                    val file = viewModel.downloadAsTXT(text)
                    if (file != null) {
                        Toast.makeText(context, "TXT saved: ${file.name}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save TXT file", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text("TXT", style = MaterialTheme.typography.labelSmall)
        }
        
        // DOCX Download
        OutlinedButton(
            onClick = {
                coroutineScope.launch {
                    val file = viewModel.downloadAsDOCX(text)
                    if (file != null) {
                        Toast.makeText(context, "DOCX saved: ${file.name}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save DOCX file", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text("DOCX", style = MaterialTheme.typography.labelSmall)
        }
        
        // PDF Download (text format for now)
        OutlinedButton(
            onClick = {
                coroutineScope.launch {
                    val file = viewModel.downloadAsPDF(text)
                    if (file != null) {
                        Toast.makeText(context, "PDF-ready text saved: ${file.name}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save PDF-ready file", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text("PDF", style = MaterialTheme.typography.labelSmall)
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
                imageVector = if (isUsingOnlineService) Icons.Default.Info else when (analysis.analysisType) {
                    AnalysisType.FOOD -> Icons.Default.Restaurant
                    AnalysisType.PLANT -> Icons.Default.Grass
                    else -> Icons.Default.Info
                },
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
                    imageVector = when (analysis.analysisType) {
                        AnalysisType.FOOD -> Icons.Default.Restaurant
                        AnalysisType.PLANT -> Icons.Default.Grass
                        else -> Icons.Default.Info
                    },
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

        // Content based on analysis type
        when (analysis.analysisType) {
            AnalysisType.FOOD -> {
                FoodAnalysisContent(analysis)
            }
            AnalysisType.PLANT -> {
                PlantAnalysisContent(analysis)
            }
            else -> {
                GeneralAnalysisContent(analysis)
            }
        }
    }
}

@Composable
private fun FoodAnalysisContent(analysis: GeneralAnalysis) {
    // Total calories card
    if (analysis.totalCalories > 0) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Calories",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "${analysis.totalCalories} kcal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }

    // Nutritional information
    analysis.nutritionalInfo?.let { nutritionInfo ->
        NutritionalInfoCard(nutritionInfo)
    }

    // Food items breakdown
    if (analysis.foodItems.isNotEmpty()) {
        FoodItemsCard(analysis.foodItems)
    }

    // Recommendations
    if (analysis.recommendations.isNotEmpty()) {
        RecommendationsCard(recommendations = analysis.recommendations)
    }
}

@Composable
private fun PlantAnalysisContent(analysis: GeneralAnalysis) {
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

@Composable
private fun GeneralAnalysisContent(analysis: GeneralAnalysis) {
    if (analysis.recommendations.isNotEmpty()) {
        RecommendationsCard(recommendations = analysis.recommendations)
    }
}

@Composable
private fun NutritionalInfoCard(nutritionInfo: NutritionalInfo) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Nutritional Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NutrientColumn("Carbs", "${nutritionInfo.totalCarbs.roundToInt()}g")
                NutrientColumn("Protein", "${nutritionInfo.totalProtein.roundToInt()}g")
                NutrientColumn("Fat", "${nutritionInfo.totalFat.roundToInt()}g")
                NutrientColumn("Fiber", "${nutritionInfo.totalFiber.roundToInt()}g")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Serving size: ${nutritionInfo.servingSize}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun NutrientColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun FoodItemsCard(foodItems: List<FoodItem>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Food Items",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            foodItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${item.estimatedWeight}g • ${(item.confidence * 100).roundToInt()}% confidence",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = "${item.totalCalories} kcal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (item != foodItems.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
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
