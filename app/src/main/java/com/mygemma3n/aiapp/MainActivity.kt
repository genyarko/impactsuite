package com.mygemma3n.aiapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.compose.rememberNavController
import com.mygemma3n.aiapp.ui.theme.Gemma3nTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.lifecycle.lifecycleScope
import com.mygemma3n.aiapp.data.GeminiApiService
import com.mygemma3n.aiapp.data.UnifiedGemmaService
import com.mygemma3n.aiapp.data.repository.TokenUsageRepository
import com.mygemma3n.aiapp.data.local.entities.TokenUsageSummary
import com.mygemma3n.aiapp.data.GeminiApiConfig
import com.mygemma3n.aiapp.data.SpeechRecognitionService
import com.mygemma3n.aiapp.ui.components.ModelDownloadViewModel
import com.mygemma3n.aiapp.service.CostCalculationService
import java.text.NumberFormat
import java.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.mygemma3n.aiapp.navigation.AppNavigation
import com.mygemma3n.aiapp.service.ModelDownloadService
import com.mygemma3n.aiapp.ui.components.InitializationScreen
import com.mygemma3n.aiapp.util.GoogleServicesUtil
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    @Inject lateinit var costCalculationService: CostCalculationService
    @Inject lateinit var spendingLimitService: com.mygemma3n.aiapp.service.SpendingLimitService
    @Inject lateinit var databaseCleanupService: com.mygemma3n.aiapp.service.DatabaseCleanupService

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

            // Step 4: Initialize pricing configuration and spending limits
            updateInitState("Configuring pricing...", 0.85f)
            val pricingDeferred = lifecycleScope.async {
                costCalculationService.initializeDefaultPricing()
                spendingLimitService.initializeDefaultLimits()
                databaseCleanupService.schedulePeriodicCleanup() // Currently disabled to preserve billing data
                Timber.d("Pricing configuration, spending limits, and cleanup scheduling initialized")
            }
            
            // Step 5: Wait for all to complete
            updateInitState("Finalizing setup...", 0.9f)
            apiKeyDeferred.await()
            gemmaDeferred.await()
            pricingDeferred.await()

            // Step 6: Complete
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
            
            // Universal Sentence Encoder can be downloaded on-demand for size optimization
            val embeddingModel = "universal_sentence_encoder.tflite"
            if (embeddingModel !in assets) {
                Timber.d("Universal Sentence Encoder not in assets - will download on-demand when needed")
            } else {
                Timber.d("Universal Sentence Encoder found in assets")
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
            
            // Continue initialization - embedding model can be downloaded on-demand
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
        AppNavigation(
            navController = navController,
            geminiApiService = geminiApiService,
            unifiedGemmaService = unifiedGemmaService,
            modifier = Modifier.padding(inner)
        )
    }
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
fun TokenUsageSection(tokenUsageRepository: TokenUsageRepository) {
    var todayUsage by remember { mutableStateOf<TokenUsageSummary?>(null) }
    var monthlyUsage by remember { mutableStateOf<TokenUsageSummary?>(null) }
    var selectedPeriod by remember { mutableStateOf("Today") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCalculatingCosts by remember { mutableStateOf(false) }
    var calculatedTodayCost by remember { mutableFloatStateOf(0f) }
    var calculatedMonthlyCost by remember { mutableFloatStateOf(0f) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Load token usage data only
    LaunchedEffect(selectedPeriod) {
        try {
            Timber.d("TokenUsageSection: Loading token usage data for $selectedPeriod")
            isLoading = true
            errorMessage = null
            val today = tokenUsageRepository.getTodayTokenUsage()
            val monthly = tokenUsageRepository.getMonthlyTokenUsage()
            Timber.d("TokenUsageSection: Today usage - tokens: ${today?.totalTokens ?: 0}")
            Timber.d("TokenUsageSection: Monthly usage - tokens: ${monthly?.totalTokens ?: 0}")
            todayUsage = today
            monthlyUsage = monthly
            isLoading = false
        } catch (e: Exception) {
            Timber.e(e, "TokenUsageSection: Error loading token usage")
            errorMessage = e.message
            isLoading = false
        }
    }
    
    // Manual cost calculation function
    val calculateCosts: () -> Unit = {
        coroutineScope.launch {
            try {
                isCalculatingCosts = true
                Timber.d("Manual cost calculation initiated")
                val todayCost = tokenUsageRepository.calculateCostsForSummary(
                    java.time.LocalDateTime.now().toLocalDate().atStartOfDay()
                )
                val monthlyCost = tokenUsageRepository.calculateCostsForSummary(
                    java.time.LocalDateTime.now().toLocalDate().withDayOfMonth(1).atStartOfDay()
                )
                calculatedTodayCost = todayCost.toFloat()
                calculatedMonthlyCost = monthlyCost.toFloat()
                Timber.d("Calculated costs - Today: $$todayCost, Monthly: $$monthlyCost")
                isCalculatingCosts = false
            } catch (e: Exception) {
                Timber.e(e, "Error calculating costs")
                isCalculatingCosts = false
            }
        }
        Unit
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
                                
                                // Manual cost calculation
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val currentCost = if (selectedPeriod == "Today") calculatedTodayCost else calculatedMonthlyCost
                                
                                if (currentCost > 0.0f) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Estimated Cost",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "$${String.format("%.4f", currentCost)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                
                                Button(
                                    onClick = calculateCosts,
                                    enabled = !isLoading && !isCalculatingCosts,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isCalculatingCosts) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Calculating...")
                                        }
                                    } else {
                                        Text("Calculate Estimated Costs")
                                    }
                                }
                                
                                if (currentCost > 0.0f || isCalculatingCosts) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Based on current OpenAI and Gemini API pricing. On-device models are free.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
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
                    enabled = !downloadState.isDownloading // Disable when downloading
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