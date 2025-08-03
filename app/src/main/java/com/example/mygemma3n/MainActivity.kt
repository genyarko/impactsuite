// Fixed MainActivity.kt
package com.example.mygemma3n

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mygemma3n.ui.theme.Gemma3nTheme
import com.example.mygemma3n.feature.caption.LiveCaptionScreen
import com.example.mygemma3n.feature.quiz.QuizScreen
import com.example.mygemma3n.feature.plant.PlantScannerScreen
import com.example.mygemma3n.feature.plant.PlantScannerViewModel
import com.example.mygemma3n.feature.summarizer.SummarizerScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.mygemma3n.data.GeminiApiConfig
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.repository.TokenUsageRepository
import com.example.mygemma3n.data.local.entities.TokenUsageSummary
import com.example.mygemma3n.data.validateKey
import com.example.mygemma3n.di.SpeechRecognitionServiceEntryPoint
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.example.mygemma3n.feature.caption.SpeechRecognitionService
import com.example.mygemma3n.SPEECH_API_KEY
import com.example.mygemma3n.GEMINI_API_KEY
import com.example.mygemma3n.USE_ONLINE_SERVICE
import com.example.mygemma3n.feature.cbt.CBTCoachScreen
import com.example.mygemma3n.feature.chat.ChatScreen
import com.example.mygemma3n.feature.crisis.CrisisHandbookScreen

import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import java.text.NumberFormat
import java.util.*
import com.example.mygemma3n.di.GemmaServiceEntryPoint
import com.example.mygemma3n.feature.chat.ChatListScreen
import com.example.mygemma3n.feature.tutor.TutorScreen
import com.example.mygemma3n.feature.analytics.AnalyticsDashboardScreen
import com.example.mygemma3n.feature.story.StoryScreen
import com.example.mygemma3n.ui.components.InitializationScreen
import com.example.mygemma3n.ui.settings.QuizSettingsScreen
import com.example.mygemma3n.service.ModelDownloadService
import com.example.mygemma3n.util.GoogleServicesUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import com.example.mygemma3n.ui.components.ModelDownloadViewModel


// ───── Preference keys ──────────────────────────────────────────────────────
val MAPS_API_KEY   = stringPreferencesKey("google_maps_api_key")

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TokenUsageRepositoryEntryPoint {
    fun tokenUsageRepository(): TokenUsageRepository
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var geminiApiService: GeminiApiService
    @Inject lateinit var unifiedGemmaService: UnifiedGemmaService
    @Inject lateinit var modelDownloadService: ModelDownloadService
    @Inject lateinit var speechRecognitionService: SpeechRecognitionService

    // Function to manually re-initialize speech service if needed
    fun reinitializeSpeechService() {
        lifecycleScope.launch {
            try {
                val speechKey = dataStore.data.map { it[SPEECH_API_KEY].orEmpty() }.first()
                if (speechKey.isNotBlank()) {
                    speechRecognitionService.initializeWithApiKey(speechKey)
                    Timber.d("Speech service re-initialized successfully")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to re-initialize speech service")
            }
        }
    }

    // Initialization state that can be observed by Compose
    companion object {
        val initializationState = mutableStateOf(InitializationState())
    }

    data class InitializationState(
        val isInitializing: Boolean = true,
        val status: String = "Starting up...",
        val progress: Float? = null,
        val error: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Start initialization process
        lifecycleScope.launch {
            initializeApp()
        }

        setContent {
            Gemma3nTheme {
                Gemma3nAppWithLoading(
                    geminiApiService = geminiApiService,
                    unifiedGemmaService = unifiedGemmaService
                )
            }
        }
    }

    private suspend fun initializeApp() {
        try {
            // Step 1: Check and download model files if needed
            updateInitState("Checking AI models...", 0.1f)
            
            val modelsReady = checkAndEnsureModelsAvailable()
            if (!modelsReady) {
                throw IllegalStateException("Failed to prepare AI models")
            }

            // Step 2: Initialize Gemini API and Speech Service if keys exist and Google Services available
            updateInitState("Setting up online features...", 0.5f)
            val apiKeyDeferred = lifecycleScope.async {
                val geminiKey = dataStore.data.map { it[GEMINI_API_KEY].orEmpty() }.first()
                val speechKey = dataStore.data.map { it[SPEECH_API_KEY].orEmpty() }.first()
                
                if (geminiKey.isNotBlank()) {
                    GoogleServicesUtil.withGoogleServicesSuspend(
                        context = this@MainActivity,
                        block = {
                            geminiApiService.initialize(GeminiApiConfig(apiKey = geminiKey))
                            Timber.d("Gemini API initialized")
                        },
                        onUnavailable = {
                            Timber.w("Skipping Gemini API initialization - Google Services not available")
                        }
                    )
                }
                
                if (speechKey.isNotBlank() && !speechRecognitionService.isInitialized) {
                    GoogleServicesUtil.withGoogleServicesSuspend(
                        context = this@MainActivity,
                        block = {
                            try {
                                speechRecognitionService.initializeWithApiKey(speechKey)
                                Timber.d("Speech Recognition Service initialized automatically")
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to initialize Speech Recognition Service: ${e.message}")
                                // Don't throw - continue initialization
                            }
                        },
                        onUnavailable = {
                            Timber.w("Skipping Speech Recognition Service initialization - Google Services not available")
                        }
                    )
                }
            }

            // Step 3: Initialize Gemma model
            updateInitState("Loading AI model...", 0.7f)
            val gemmaDeferred = lifecycleScope.async {
                initializeGemmaModel()
            }

            // Step 4: Wait for both to complete
            updateInitState("Finalizing setup...", 0.9f)
            apiKeyDeferred.await()
            gemmaDeferred.await()

            // Step 5: Complete
            updateInitState("Ready!", 1.0f)
            delay(500) // Show complete state briefly

            // Mark as initialized
            initializationState.value = InitializationState(
                isInitializing = false,
                status = "Initialized",
                progress = 1.0f
            )

        } catch (e: Exception) {
            Timber.e(e, "App initialization failed")
            // Instead of failing completely, mark as initialized with partial functionality
            initializationState.value = InitializationState(
                isInitializing = false,
                status = "Initialized with limited features",
                progress = 1.0f,
                error = null // Don't show error - app is still functional
            )
        }
    }

    private suspend fun checkAndEnsureModelsAvailable(): Boolean {
        return try {
            // First check if embedding model is in assets (required)
            val assetsModelsReady = checkAssetsModelsAvailable()
            if (!assetsModelsReady) {
                Timber.e("Universal Sentence Encoder not found in assets - cannot continue")
                return false
            }
            
            // Check if Gemma models are available (assets or downloads)
            updateInitState("Checking AI models...", 0.2f)
            val availableGemmaModels = unifiedGemmaService.getAvailableModels()
            if (availableGemmaModels.isNotEmpty()) {
                Timber.d("Gemma models available: ${availableGemmaModels.map { it.displayName }}")
                return true
            }

            // No Gemma models found - check if already downloaded
            updateInitState("Checking downloaded models...", 0.25f)
            if (modelDownloadService.isModelDownloaded()) {
                Timber.d("Gemma models already downloaded to files directory")
                return true
            }

            // No models available - app will continue with limited functionality
            Timber.d("No Gemma models found. App will continue with limited AI features.")
            Timber.d("Users can manually download models via Settings > AI Model Management")
            return true // Continue initialization, models can be downloaded manually later
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to ensure models are available")
            false
        }
    }

    private suspend fun checkAssetsModelsAvailable(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val assets = assets.list("models")?.toSet().orEmpty()
            
            // Universal Sentence Encoder should always be in assets
            val requiredInAssets = "universal_sentence_encoder.tflite"
            if (requiredInAssets !in assets) {
                Timber.e("Required embedding model not found in assets: $requiredInAssets")
                return@withContext false
            }
            
            // Check if any Gemma model is available in assets (optional)
            val gemmaModels = listOf(
                "gemma-3n-e2b-it-int4.task",
                "gemma-3n-E2B-it-int4.task", // Case variations
                "gemma-3n-e4b-it-int4.task",
                "gemma-3n-E4B-it-int4.task"
            )
            val hasGemmaInAssets = gemmaModels.any { it in assets }
            
            if (hasGemmaInAssets) {
                Timber.d("Gemma model found in assets - no download needed")
            } else {
                Timber.d("No Gemma model in assets - will need to download")
            }
            
            // Return true if embedding model is available (Gemma download handled separately)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to check assets models")
            false
        }
    }

    private suspend fun initializeGemmaModel() {
        try {
            if (!unifiedGemmaService.isInitialized()) {
                Timber.d("Starting Gemma model initialization...")

                // Check available models first
                val availableModels = unifiedGemmaService.getAvailableModels()
                if (availableModels.isEmpty()) {
                    Timber.d("No Gemma models found - skipping initialization. Models can be downloaded manually via Settings.")
                    return // Skip initialization, app will continue with limited functionality
                }

                Timber.d("Available models: ${availableModels.map { it.displayName }}")

                // Initialize the best available
                unifiedGemmaService.initializeBestAvailable()

                val currentModel = unifiedGemmaService.getCurrentModel()
                Timber.d("Gemma model initialized: ${currentModel?.displayName}")
            } else {
                Timber.d("Gemma model already initialized")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Gemma model")
            throw e
        }
    }

    private fun updateInitState(status: String, progress: Float? = null) {
        initializationState.value = initializationState.value.copy(
            status = status,
            progress = progress
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Gemma3nAppWithLoading(
    geminiApiService: GeminiApiService,
    unifiedGemmaService: UnifiedGemmaService
) {
    val activity = LocalActivity.current          // ← safe: inside a composable

    val initState by MainActivity.initializationState
    AnimatedContent(
        targetState = initState.isInitializing,
        /* … */
    ) { isLoading ->
        when {
            isLoading -> InitializationScreen(/* … */)
            initState.error != null -> ErrorScreen(
                error = initState.error!!,
                onRetry = { activity?.recreate() } // ← no composable call here
            )
            else -> Gemma3nApp(geminiApiService, unifiedGemmaService)
        }
    }
}


@Composable
fun ErrorScreen(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Initialization Failed",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

// ───── Root Composable ──────────────────────────────────────────────────────
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Gemma3nApp(
    geminiApiService: GeminiApiService,
    unifiedGemmaService: UnifiedGemmaService
) {
    val navController = rememberNavController()

    // Request permissions
    val perms = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )
    LaunchedEffect(Unit) { perms.launchMultiplePermissionRequest() }

    Scaffold { inner ->
        Gemma3nNavigation(
            navController = navController,
            geminiApiService = geminiApiService,
            unifiedGemmaService = unifiedGemmaService,
            modifier = Modifier.padding(inner)
        )
    }
}

// Navigation
@Composable
fun Gemma3nNavigation(
    navController: NavHostController,
    geminiApiService: GeminiApiService,
    unifiedGemmaService: UnifiedGemmaService,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        // ───── Home & tools ───────────────────────────────────────────────
        composable("home") { HomeScreen(navController, unifiedGemmaService) }
        composable("live_caption")   { LiveCaptionScreen() }
        composable("quiz_generator") { QuizScreen() }
        composable("cbt_coach")      { CBTCoachScreen() }
        composable("summarizer")     { SummarizerScreen() }

        // ───── Chat list first ───────────────────────────────────────────
        composable("chat_list") {
            ChatListScreen(
                onNavigateToChat = { id ->
                    navController.navigate("chat/$id")
                }
            )
        }

        // ───── Individual session ───────────────────────────────────────
        composable(
            route = "chat/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            ChatScreen()
        }

        // ───── Other screens ────────────────────────────────────────────
        composable("plant_scanner")   { PlantScannerScreen() }
        composable("crisis_handbook") { CrisisHandbookScreen() }
        composable("settings") {
            QuizSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable("tutor") {
            TutorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChatList = { navController.navigate("chat_list") }
            )
        }
        composable("analytics") {
            AnalyticsDashboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("story_mode") {
            StoryScreen()
        }
        composable("api_settings") {
            val context = LocalContext.current
            val speechService = remember {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    SpeechRecognitionServiceEntryPoint::class.java
                ).speechRecognitionService()
            }
            val tokenUsageRepository = remember {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    TokenUsageRepositoryEntryPoint::class.java
                ).tokenUsageRepository()
            }
            ApiSettingsScreen(
                geminiApiService = geminiApiService,
                speechService = speechService,
                tokenUsageRepository = tokenUsageRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}


// Updated Home screen with Gemini API
// ───── Home screen ──────────────────────────────────────────────────────────
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
                        text = "API Settings",
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
        enabled = isGemmaInitialized
    ),
    FeatureItem(
        title = "Live Caption",
        route = "live_caption",
        icon = Icons.Default.ClosedCaption,
        enabled = isGemmaInitialized
    ),
    FeatureItem(
        title = "Quiz Generator",
        route = "quiz_generator",
        icon = Icons.Default.Quiz,
        enabled = isGemmaInitialized
    ),
    FeatureItem(
        title = "CBT Coach",
        route = "cbt_coach",
        icon = Icons.Default.Psychology,
        enabled = isGemmaInitialized
    ),
    FeatureItem(
        title = "Chat",
        route = "chat_list",
        icon = Icons.AutoMirrored.Filled.Chat,
        enabled = isGemmaInitialized
    ),
    FeatureItem(
        title = "Summarizer",
        route = "summarizer",
        icon = Icons.Default.Summarize,
        enabled = isGemmaInitialized
    ),
    FeatureItem(
        title = "Image Classification",
        route = "plant_scanner",
        icon = Icons.Default.PhotoCamera,
        enabled = isModelReady
    ),
    FeatureItem(
        title = "Crisis Handbook",
        route = "crisis_handbook",
        icon = Icons.Default.LocalHospital,
        enabled = isGemmaInitialized
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
        enabled = isGemmaInitialized
    )
)

// Helper function to check model availability (stays at top level, not inside HomeScreen)
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

@Composable
private fun GemmaStatusCard(modelName: String?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Gemma initialized: ${modelName ?: "Unknown model"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun FeatureButton(
    label: String,
    route: String,
    nav: NavHostController,
    enabled: Boolean
) {
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { nav.navigate(route) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) { Text(label) }
}

@Composable private fun StatusBar(text: String, progress: Float? = null) {
    Column {
        if (progress != null)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        else
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(text); Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun ErrorCard(msg: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Error loading model:", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable private fun AvailableModelsCard(models: List<String>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Models available:", style = MaterialTheme.typography.labelMedium)
            models.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun ApiSettingsScreen(
    geminiApiService: GeminiApiService,
    speechService: SpeechRecognitionService,
    tokenUsageRepository: TokenUsageRepository? = null,
    onNavigateBack: () -> Unit = {}
) {
    var apiKey by remember { mutableStateOf("") }
    var mapsApiKey by remember { mutableStateOf("") }
    var speechApiKey by remember { mutableStateOf("") }
    var useOnlineService by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Load existing API keys and settings
    LaunchedEffect(Unit) {
        context.dataStore.data.map { it[GEMINI_API_KEY] }.first()?.let {
            apiKey = it
        }
        context.dataStore.data.map { it[MAPS_API_KEY] }.first()?.let {
            mapsApiKey = it
        }
        context.dataStore.data.map { it[SPEECH_API_KEY] }.first()?.let {
            speechApiKey = it
        }
        context.dataStore.data.map { it[USE_ONLINE_SERVICE] }.first()?.let {
            useOnlineService = it
        } ?: run {
            useOnlineService = true // Default to online
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "API Configuration",
                        style = MaterialTheme.typography.headlineMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Configure AI service mode and API keys",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Online/Offline Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Service Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (useOnlineService) {
                                    "Online: Uses Gemini API (requires internet & API key)"
                                } else {
                                    "Offline: Uses on-device Gemma models (works without internet)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useOnlineService,
                            onCheckedChange = { useOnlineService = it },
                            enabled = !isLoading
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gemini API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key") },
                placeholder = { Text("AIza...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                supportingText = { Text("Required for AI features") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Google Cloud Speech API Key
            OutlinedTextField(
                value = speechApiKey,
                onValueChange = { speechApiKey = it },
                label = { Text("Google Cloud Speech API Key") },
                placeholder = { Text("AIza...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                supportingText = { Text("Auto-enables speech features: Live Caption, AI Tutor voice, CBT Coach") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Google Maps API Key
            OutlinedTextField(
                value = mapsApiKey,
                onValueChange = { mapsApiKey = it },
                label = { Text("Google Maps API Key (Optional)") },
                placeholder = { Text("AIza...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                supportingText = { Text("Required for Crisis Handbook map feature") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isLoading = true
                        validationMessage = null
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                var allValid = true
                                val messages = mutableListOf<String>()

                                // Validate Gemini API key
                                if (apiKey.isNotBlank()) {
                                    if (geminiApiService.validateKey(apiKey)) {
                                        context.dataStore.edit { it[GEMINI_API_KEY] = apiKey }
                                        messages.add("✓ Gemini API key validated")
                                    } else {
                                        messages.add("✗ Invalid Gemini API key")
                                        allValid = false
                                    }
                                }

                                // Save Speech API key (will be auto-initialized on app restart)
                                if (speechApiKey.isNotBlank()) {
                                    if (speechApiKey.startsWith("AIza") && speechApiKey.length > 20) {
                                        context.dataStore.edit { settings ->
                                            settings[SPEECH_API_KEY] = speechApiKey
                                        }
                                        // Test the API key validation but auto-initialize on app restart
                                        GoogleServicesUtil.withGoogleServices(
                                            context = context,
                                            block = {
                                                try {
                                                    // Just validate, don't permanently initialize (will happen automatically on app restart)
                                                    speechService.initializeWithApiKey(speechApiKey)
                                                    messages.add("✓ Speech API key saved & validated (will auto-enable for all features on restart)")
                                                } catch (e: Exception) {
                                                    messages.add("✗ Speech API key validation failed: ${e.message}")
                                                    allValid = false
                                                }
                                            },
                                            onUnavailable = {
                                                messages.add("✓ Speech API key saved (will auto-enable when Google Services available)")
                                            }
                                        ) ?: run {
                                            // If Google Services not available, still save the key
                                            messages.add("✓ Speech API key saved (will auto-enable when Google Services available)")
                                        }
                                    } else {
                                        messages.add("✗ Invalid Speech API key format")
                                        allValid = false
                                    }
                                }

                                // Save Maps API key (no validation for now)
                                if (mapsApiKey.isNotBlank()) {
                                    context.dataStore.edit { settings ->
                                        settings[MAPS_API_KEY] = mapsApiKey
                                    }
                                    messages.add("✓ Maps API key saved")
                                }

                                // Save online/offline preference
                                context.dataStore.edit { settings ->
                                    settings[USE_ONLINE_SERVICE] = useOnlineService
                                }
                                messages.add("✓ Service mode saved: ${if (useOnlineService) "Online" else "Offline"}")

                                withContext(Dispatchers.Main) {
                                    validationMessage = messages.joinToString("\n")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    validationMessage = "Error: ${e.message}"
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = (apiKey.isNotBlank() || mapsApiKey.isNotBlank() || speechApiKey.isNotBlank()) && !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save & Validate")
                    }
                }

                OutlinedButton(
                    onClick = {
                        apiKey = ""
                        mapsApiKey = ""
                        speechApiKey = ""
                        useOnlineService = true
                        CoroutineScope(Dispatchers.IO).launch {
                            context.dataStore.edit { settings ->
                                settings.remove(GEMINI_API_KEY)
                                settings.remove(MAPS_API_KEY)
                                settings.remove(SPEECH_API_KEY)
                                settings.remove(USE_ONLINE_SERVICE)
                            }
                        }
                        validationMessage = "API keys and settings cleared"
                    },
                    enabled = !isLoading
                ) {
                    Text("Clear All")
                }
            }

            validationMessage?.let { message ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.contains("✓") && !message.contains("✗"))
                            MaterialTheme.colorScheme.secondaryContainer
                        else if (message.contains("✗"))
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = if (message.contains("✓") && !message.contains("✗"))
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else if (message.contains("✗"))
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Get your API keys from:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "Gemini: ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "https://makersuite.google.com/app/apikey",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "Speech: ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "https://console.cloud.google.com/apis/library/speech.googleapis.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "Maps: ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "https://console.cloud.google.com/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Model Download Section
            ModelDownloadSection()

            Spacer(modifier = Modifier.height(24.dp))

            // Token usage display section
            if (useOnlineService && tokenUsageRepository != null) {
                TokenUsageSection(tokenUsageRepository = tokenUsageRepository)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Note for Speech API:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Enable the Cloud Speech-to-Text API in your Google Cloud Console and create an API key with Speech-to-Text permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun TokenUsageSection(tokenUsageRepository: TokenUsageRepository) {
    var todayUsage by remember { mutableStateOf<TokenUsageSummary?>(null) }
    var monthlyUsage by remember { mutableStateOf<TokenUsageSummary?>(null) }
    var selectedPeriod by remember { mutableStateOf("Today") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            Timber.d("TokenUsageSection: Loading token usage data...")
            val today = tokenUsageRepository.getTodayTokenUsage()
            val monthly = tokenUsageRepository.getMonthlyTokenUsage()
            Timber.d("TokenUsageSection: Today tokens: ${today?.totalTokens ?: 0}, Monthly tokens: ${monthly?.totalTokens ?: 0}")
            todayUsage = today
            monthlyUsage = monthly
            isLoading = false
        } catch (e: Exception) {
            Timber.e(e, "TokenUsageSection: Error loading token usage")
            errorMessage = e.message
            isLoading = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 API Token Usage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            when {
                errorMessage != null -> {
                    Text(
                        text = "Error loading token usage: $errorMessage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                todayUsage != null || monthlyUsage != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Period selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FilterChip(
                                onClick = { selectedPeriod = "Today" },
                                label = { Text("Today") },
                                selected = selectedPeriod == "Today",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                onClick = { selectedPeriod = "Month" },
                                label = { Text("This Month") },
                                selected = selectedPeriod == "Month",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        // Display selected usage data
                        val summary = when (selectedPeriod) {
                            "Today" -> todayUsage
                            "Month" -> monthlyUsage
                            else -> todayUsage
                        }
                        
                        if (summary != null) {
                            val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
                            
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "${selectedPeriod}'s Usage",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Input Tokens",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = numberFormat.format(summary.totalInputTokens),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    Column {
                                        Text(
                                            text = "Output Tokens",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = numberFormat.format(summary.totalOutputTokens),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    Column {
                                        Text(
                                            text = "Total Tokens",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = numberFormat.format(summary.totalTokens),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                if (summary.serviceBreakdown.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "By Feature",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    summary.serviceBreakdown.entries.forEach { (service, usage) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = service.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${numberFormat.format(usage.totalTokens)} tokens",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "No ${selectedPeriod.lowercase()} usage data available yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = "No usage data available yet. Start using online features to see token usage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ModelDownloadSection(
    viewModel: ModelDownloadViewModel = hiltViewModel()
) {
    val downloadState by viewModel.downloadState.collectAsState()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Model Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = when {
                            downloadState.isComplete -> "Models are ready and available"
                            downloadState.isDownloading -> "Downloading models..."
                            downloadState.isError -> "Model download failed"
                            else -> "Download AI models for offline functionality"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Status icon
                when {
                    downloadState.isComplete -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Complete",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    downloadState.isError -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    downloadState.isDownloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            
            // Progress bar when downloading
            if (downloadState.isDownloading && downloadState.totalFiles > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                progress = { downloadState.overallProgress },
                modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
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
                
                // Cancel button during download
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.cancelDownload() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel Download")
                }
            }
            
            // Status message
            if (downloadState.statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = downloadState.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (downloadState.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            // Error message
            if (downloadState.isError && downloadState.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = downloadState.errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            // Download button
            if (!downloadState.isComplete && !downloadState.isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.startModelDownload() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (downloadState.isError) "Retry Download" else "Download Models")
                    }
                }
            }
        }
    }
}