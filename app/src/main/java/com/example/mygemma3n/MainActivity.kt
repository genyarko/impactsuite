// Updated MainActivity.kt with Gemini API integration
package com.example.mygemma3n

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mygemma3n.ui.theme.Gemma3nTheme
import com.example.mygemma3n.feature.caption.LiveCaptionScreen
import com.example.mygemma3n.feature.quiz.QuizScreen
import com.example.mygemma3n.feature.plant.PlantScannerScreen
import com.example.mygemma3n.feature.plant.PlantScannerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.hilt.navigation.compose.hiltViewModel
// Commented out local model imports
// import com.google.android.play.core.assetpacks.AssetPackManagerFactory
// import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
// import com.google.android.play.core.assetpacks.model.AssetPackStatus
// import com.google.android.play.core.assetpacks.AssetPackManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
// import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.validateApiKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// DataStore for API key storage

val API_KEY = stringPreferencesKey("gemini_api_key")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var geminiApiService: GeminiApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            Gemma3nTheme {
                Gemma3nApp(geminiApiService)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Gemma3nApp(geminiApiService: GeminiApiService) {
    val navController = rememberNavController()

    // Request necessary permissions
    val permissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    LaunchedEffect(Unit) {
        permissions.launchMultiplePermissionRequest()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Gemma3nNavigation(
            navController = navController,
            geminiApiService = geminiApiService,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

// Navigation
@Composable
fun Gemma3nNavigation(
    navController: androidx.navigation.NavHostController,
    geminiApiService: GeminiApiService,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(navController, geminiApiService)
        }
        composable("live_caption") {
            LiveCaptionScreen()
        }
        composable("quiz_generator") {
            QuizScreen()
        }
        composable("cbt_coach") {
            CBTCoachScreen()
        }
        composable("plant_scanner") {
            val vm: PlantScannerViewModel = hiltViewModel()
            PlantScannerScreen(onScanClick = { bitmap ->
                vm.analyzeImage(bitmap)
            })
        }
        composable("crisis_handbook") {
            CrisisHandbookScreen()
        }
        composable("api_settings") {
            ApiSettingsScreen(geminiApiService)
        }
    }
}

// Updated Home screen with Gemini API
@Composable
fun HomeScreen(
    navController: androidx.navigation.NavHostController,
    geminiApiService: GeminiApiService
) {
    // Commented out local model states
    // var downloadProgress by remember { mutableStateOf(0f) }
    // var isModelReady by remember { mutableStateOf(false) }
    // var isPreparingModel by remember { mutableStateOf(false) }

    var isApiReady by remember { mutableStateOf(false) }
    var isCheckingApi by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Check API key on launch
    LaunchedEffect(Unit) {
        val apiKey = context.dataStore.data.map { it[API_KEY] }.first()
        if (!apiKey.isNullOrBlank()) {
            try {
                geminiApiService.initialize(
                    GeminiApiService.ApiConfig(
                        apiKey = apiKey,
                        modelName = GeminiApiService.GEMINI_FLASH_MODEL
                    )
                )
                isApiReady = true
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Failed to initialize API: ${e.message}"
                isApiReady = false
            }
        } else {
            errorMessage = "API key not configured"
            isApiReady = false
        }
        isCheckingApi = false
    }

    /* Commented out local model checking
    LaunchedEffect(Unit) {
        checkModelAvailability(context) { progress, ready, preparing, error ->
            downloadProgress = progress
            isModelReady    = ready
            isPreparingModel= preparing
            errorMessage    = error
        }
    }
    */

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Gemma 3n Impact Suite",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "(Powered by Gemini API)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Show appropriate status
        when {
            isCheckingApi -> {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Checking API configuration...")
                Spacer(modifier = Modifier.height(16.dp))
            }
            errorMessage != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Configuration Issue:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { navController.navigate("api_settings") }
                        ) {
                            Text("Configure API Key")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            /* Commented out local model UI
            isPreparingModel -> {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Preparing model for first use...")
                Spacer(modifier = Modifier.height(16.dp))
            }
            downloadProgress > 0 && downloadProgress < 100 && !isModelReady -> {
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Downloading model: ${downloadProgress.toInt()}%")
                Spacer(modifier = Modifier.height(16.dp))
            }
            */
        }

        Button(
            onClick = { navController.navigate("live_caption") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isApiReady
        ) {
            Text("Live Caption & Translation")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("quiz_generator") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isApiReady
        ) {
            Text("Quiz Generator")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("cbt_coach") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isApiReady
        ) {
            Text("Voice CBT Coach")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("plant_scanner") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isApiReady
        ) {
            Text("Plant Disease Scanner")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("crisis_handbook") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isApiReady
        ) {
            Text("Crisis Handbook")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // API Settings button
        OutlinedButton(
            onClick = { navController.navigate("api_settings") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("API Settings")
        }

        if (!isApiReady && !isCheckingApi) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Please configure your Gemini API key to use the features",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// New API Settings Screen
@Composable
fun ApiSettingsScreen(geminiApiService: GeminiApiService) {
    var apiKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Load existing API key
    LaunchedEffect(Unit) {
        context.dataStore.data.map { it[API_KEY] }.first()?.let {
            apiKey = it
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "API Configuration",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enter your Gemini API key to enable all features",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Gemini API Key") },
            placeholder = { Text("AIza...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isLoading
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
                            if (geminiApiService.validateApiKey(apiKey)) {
                                // Save API key
                                context.dataStore.edit { settings ->
                                    settings[API_KEY] = apiKey
                                }
                                withContext(Dispatchers.Main) {
                                    validationMessage = "API key validated successfully!"
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    validationMessage = "Invalid API key"
                                }
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
                enabled = apiKey.isNotBlank() && !isLoading,
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
                    CoroutineScope(Dispatchers.IO).launch {
                        context.dataStore.edit { settings ->
                            settings.remove(API_KEY)
                        }
                    }
                    validationMessage = "API key cleared"
                },
                enabled = !isLoading
            ) {
                Text("Clear")
            }
        }

        validationMessage?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.contains("success", ignoreCase = true))
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    color = if (message.contains("success", ignoreCase = true))
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Get your API key from:",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "https://makersuite.google.com/app/apikey",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}



@Composable
fun CBTCoachScreen() {
    Text("CBT Coach Screen")
}

@Composable
fun CrisisHandbookScreen() {
    Text("Crisis Handbook Screen")
}

/* Commented out all local model checking code below */

/*
// Updated model availability check with error handling
fun checkModelAvailability(
    ctx: Context,
    onStatusUpdate: (progress: Float, ready: Boolean, preparing: Boolean, error: String?) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val root = File("C:\\Users\\genya\\Downloads\\MyGemma3N")

            val requiredPaths = listOf(
                "app/src/main/ml/TF_LITE_EMBEDDER.tflite",
                "app/src/main/ml/TF_LITE_PER_LAYER_EMBEDDER.tflite",
                "app/src/main/ml/TF_LITE_PREFILL_DECODE.tflite",
                "app/src/main/ml/TF_LITE_VISION_ADAPTER.tflite",
                "app/src/main/ml/TF_LITE_VISION_ENCODER.tflite",
                "app/src/main/ml/TOKENIZER_MODEL.tflite"
            )

            val missing = requiredPaths.filterNot { rel ->
                File(root, rel).exists()
            }

            if (missing.isEmpty()) {
                onStatusUpdate(100f, true, false, null)
            } else {
                onStatusUpdate(
                    0f, false, false,
                    "Missing model files:\n${missing.joinToString("\n")}"
                )
            }
        } catch (e: Exception) {
            onStatusUpdate(
                0f, false, false,
                "Error checking model files: ${e.localizedMessage}"
            )
        }
    }
}

private fun checkAssetModel(ctx: Context): Boolean = try {
    val dir = "models"
    ctx.assets.list(dir)?.any {
        it == "gemma-3n-E2B-it-int4.task" || it == "gemma-3n-E4B-it-int4.task"
    } ?: false
} catch (e: Exception) {
    Timber.e(e, "Error checking asset model")
    false
}

private suspend fun copyAssetModelToCache(ctx: Context): String? = withContext(Dispatchers.IO) {
    val names = listOf("gemma-3n-E2B-it-int4.task", "gemma-3n-E4B-it-int4.task")
    for (name in names) {
        try {
            val cacheFile = File(ctx.cacheDir, name)
            if (!cacheFile.exists()) {
                ctx.assets.open("models/$name").use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return@withContext cacheFile.absolutePath
        } catch (_: Exception) { }
    }
    Timber.e("No model found in assets")
    null
}

// ... rest of the commented local model code ...
*/