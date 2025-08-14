package com.mygemma3n.aiapp.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mygemma3n.aiapp.data.UnifiedGemmaService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Home screen displaying the main app features and status
 */
@Composable
fun HomeScreen(
    navController: NavHostController,
    unifiedGemmaService: UnifiedGemmaService
) {
    // These should be stable and not re-check on every recomposition
    val isGemmaInitialized = remember { unifiedGemmaService.isInitialized() }
    val gemmaModelName = remember { unifiedGemmaService.getCurrentModel()?.displayName }

    // Only check model files once
    var downloadProgress by remember { mutableFloatStateOf(100f) }
    var isModelReady by remember { mutableStateOf(true) }
    var isPreparingModel by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    val ctx = LocalContext.current

    // Only check model availability if not already checked
    LaunchedEffect(Unit) {
        if (isGemmaInitialized) {
            isModelReady = true
            downloadProgress = 100f

            val assets = ctx.assets.list("models")?.toSet().orEmpty()
            availableModels = assets.filter {
                it.endsWith(".task") || it.endsWith(".tflite")
            }.toList()
        } else {
            checkModelAvailability(ctx) { prog, ready, prep, err, models ->
                downloadProgress = prog
                isModelReady = ready
                isPreparingModel = prep
                errorMessage = err
                availableModels = models
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // App Logo/Icon (you can replace with your actual logo)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "G3N",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "AI Assistant",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Powered by Gemma",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Status Card
        item {
            if (isGemmaInitialized && gemmaModelName != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AI Model Ready",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = gemmaModelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // Feature Categories
        item {
            Text(
                text = "Features",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Main Features Grid
        items(getFeatureItems(isGemmaInitialized, isModelReady).chunked(2)) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { feature ->
                    FeatureCard(
                        feature = feature,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(feature.route) }
                    )
                }

                // Add empty space if odd number of items
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Settings Section
        item {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCard(
                onClick = { navController.navigate("api_settings") },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    feature: FeatureItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        enabled = feature.enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (feature.enabled) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (feature.enabled) 6.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (feature.enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                color = if (feature.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class FeatureItem(
    val title: String,
    val route: String,
    val icon: ImageVector,
    val enabled: Boolean
)

private fun getFeatureItems(isGemmaInitialized: Boolean, isModelReady: Boolean) = listOf(
    FeatureItem(
        title = "AI Tutor",
        route = "tutor",
        icon = Icons.Default.School,
        enabled = true // Can work with online API even without local model
    ),
    FeatureItem(
        title = "Live Caption",
        route = "live_caption",
        icon = Icons.Default.ClosedCaption,
        enabled = true // Can work with online API even without local model
    ),
    FeatureItem(
        title = "Quiz Generator",
        route = "quiz_generator",
        icon = Icons.Default.Quiz,
        enabled = true // Can work with online API even without local model
    ),
    FeatureItem(
        title = "CBT Coach",
        route = "cbt_coach",
        icon = Icons.Default.Psychology,
        enabled = true // Can work with basic features even without model
    ),
    FeatureItem(
        title = "Chat",
        route = "chat_list",
        icon = Icons.AutoMirrored.Filled.Chat,
        enabled = true // Can work with online API even without local model
    ),
    FeatureItem(
        title = "Summarizer",
        route = "summarizer",
        icon = Icons.Default.Summarize,
        enabled = true // Can work with online API even without local model
    ),
    FeatureItem(
        title = "Image Classification",
        route = "plant_scanner",
        icon = Icons.Default.PhotoCamera,
        enabled = true // Can work with online APIs even without local model
    ),
    FeatureItem(
        title = "Crisis Handbook",
        route = "crisis_handbook",
        icon = Icons.Default.LocalHospital,
        enabled = true // Emergency resources don't require AI models
    ),
    FeatureItem(
        title = "Learning Analytics",
        route = "analytics",
        icon = Icons.Default.Analytics,
        enabled = true // Analytics works independently of Gemma models
    ),
    FeatureItem(
        title = "Story Mode",
        route = "story_mode",
        icon = Icons.AutoMirrored.Filled.MenuBook,
        enabled = true // Completely online and doesn't need offline model
    )
)

// Helper function to check model availability
fun checkModelAvailability(
    ctx: Context,
    onStatusUpdate: (Float, Boolean, Boolean, String?, List<String>) -> Unit
) = CoroutineScope(Dispatchers.IO).launch {
    try {
        val required = listOf(
            "gemma-3n-E2B-it-int4.task",
            "universal_sentence_encoder.tflite"
        )
        val optional = listOf(
            "gemma-3n-E4B-it-int4.task"
        )

        // First check assets directory
        val assets = ctx.assets.list("models")?.toSet().orEmpty()
        val assetsAvailable = (required + optional).filter(assets::contains)
        
        if (required.all { it in assets }) {
            // Models available in assets - copy to cache
            onStatusUpdate(0f, false, true, null, assetsAvailable)
            
            val copied = mutableListOf<String>()
            val outDir = File(ctx.cacheDir, "models").apply { mkdirs() }

            assets.filter { it.endsWith(".task") || it.endsWith(".tflite") }.forEach { name ->
                runCatching {
                    ctx.assets.open("models/$name").use { input ->
                        File(outDir, name).outputStream().use { input.copyTo(it) }
                    }
                    copied += name
                }.onFailure { Timber.e(it, "Copy failed: $name") }
            }

            if (copied.isEmpty())
                onStatusUpdate(0f, false, false, "Failed to copy model files", assetsAvailable)
            else
                onStatusUpdate(100f, true, false, null, assetsAvailable)
            return@launch
        }

        // Check downloaded models directory
        val modelsDir = File(ctx.filesDir, "models")
        val downloadedFiles = if (modelsDir.exists()) {
            modelsDir.listFiles()?.map { it.name }?.toSet().orEmpty()
        } else {
            emptySet()
        }
        
        val downloadedAvailable = (required + optional).filter { it in downloadedFiles }
        
        if (required.all { it in downloadedFiles }) {
            // Models available in downloads directory
            onStatusUpdate(100f, true, false, null, downloadedAvailable)
            return@launch
        }

        // Models not found in either location
        val missingFromAssets = required.filterNot(assets::contains)
        val missingFromDownloads = required.filterNot(downloadedFiles::contains)
        
        onStatusUpdate(0f, false, false,
            "Models not found. Missing from assets: ${missingFromAssets.joinToString(", ")}\n" +
            "Missing from downloads: ${missingFromDownloads.joinToString(", ")}", 
            assetsAvailable + downloadedAvailable)

    } catch (e: Exception) {
        onStatusUpdate(0f, false, false, "Error: ${e.localizedMessage}", emptyList())
    }
}