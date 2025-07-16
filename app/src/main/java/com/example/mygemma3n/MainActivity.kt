// Fixed MainActivity.kt
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import com.example.mygemma3n.data.GeminiApiConfig
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.validateKey
import com.example.mygemma3n.di.SpeechRecognitionServiceEntryPoint
import com.example.mygemma3n.feature.caption.SpeechRecognitionService
import com.example.mygemma3n.feature.cbt.CBTCoachScreen
import com.example.mygemma3n.feature.crisis.CrisisHandbookScreen
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.google.android.play.core.assetpacks.AssetPackManager
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


// ───── Preference keys ──────────────────────────────────────────────────────
val API_KEY        = stringPreferencesKey("gemini_api_key")
val MAPS_API_KEY   = stringPreferencesKey("google_maps_api_key")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var geminiApiService: GeminiApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val key = dataStore.data.map { it[API_KEY].orEmpty() }.first()
            if (key.isNotBlank()) {
                runCatching {
                    geminiApiService.initialize(GeminiApiConfig(apiKey = key))
                }.onFailure { Timber.e(it) }
            }
        }

        setContent { Gemma3nTheme { Gemma3nApp(geminiApiService) } }
    }
}

// ───── Root Composable ──────────────────────────────────────────────────────
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Gemma3nApp(geminiApiService: com.example.mygemma3n.data.GeminiApiService) {
    val navController = rememberNavController()

    // ask for CAMERA / AUDIO / LOCATION once at start‑up
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
            modifier = Modifier.padding(inner)
        )
    }
}

// Navigation
@Composable
fun Gemma3nNavigation(
    navController: NavHostController,
    geminiApiService: GeminiApiService,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(navController)
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
            PlantScannerScreen()
        }
        composable("crisis_handbook") {
            CrisisHandbookScreen()
        }
        composable("api_settings") {
            val context = LocalContext.current
            val speechService = remember {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    SpeechRecognitionServiceEntryPoint::class.java
                ).speechRecognitionService()
            }
            ApiSettingsScreen(
                geminiApiService = geminiApiService,
                speechService = speechService
            )
        }
    }
}

// Updated Home screen with Gemini API
// ───── Home screen ──────────────────────────────────────────────────────────
@Composable
fun HomeScreen(navController: NavHostController) {
    var downloadProgress  by remember { mutableStateOf(0f) }
    var isModelReady      by remember { mutableStateOf(false) }
    var isPreparingModel  by remember { mutableStateOf(false) }
    var errorMessage      by remember { mutableStateOf<String?>(null) }
    var availableModels   by remember { mutableStateOf<List<String>>(emptyList()) }
    val ctx = LocalContext.current

    // run once
    LaunchedEffect(Unit) {
        checkModelAvailability(ctx) { prog, ready, prep, err, models ->
            downloadProgress = prog
            isModelReady     = ready
            isPreparingModel = prep
            errorMessage     = err
            availableModels  = models
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Gemma 3n Impact Suite", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

        // status UI (download, errors, list of found models, …)
        when {
            errorMessage != null -> ErrorCard(errorMessage!!)
            isPreparingModel     -> StatusBar("Preparing model…")
            downloadProgress in 0f..99f && !isModelReady ->
                StatusBar("Downloading model: ${downloadProgress.toInt()}%", downloadProgress / 100)
            isModelReady && availableModels.isNotEmpty() -> AvailableModelsCard(availableModels)
        }

        FeatureButton("Live Caption & Translation", "live_caption", navController, isModelReady)
        FeatureButton("Offline Quiz Generator",     "quiz_generator", navController, isModelReady)
        FeatureButton("Voice CBT Coach",            "cbt_coach",      navController, isModelReady)
        FeatureButton("Plant Disease Scanner",      "plant_scanner",  navController, isModelReady)
        FeatureButton("Crisis Handbook",            "crisis_handbook",navController, isModelReady)

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { navController.navigate("api_settings") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("API Settings")
        }

        if (!isModelReady && !isPreparingModel) {
            Spacer(Modifier.height(16.dp))
            Text("Model needs to be available before using features",
                style = MaterialTheme.typography.bodySmall)
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

// ───── Model‑availability helper  (TOP‑LEVEL, visible to HomeScreen) ────────
fun checkModelAvailability(
    ctx: Context,
    onStatusUpdate: (Float, Boolean, Boolean, String?, List<String>) -> Unit
) = CoroutineScope(Dispatchers.IO).launch {
    try {
        val assets = ctx.assets.list("models")?.toSet().orEmpty()

        val required = listOf(
            "gemma-3n-E2B-it-int4.task",
            "universal_sentence_encoder.tflite"
        )
        val optional = listOf(
            "gemma-3n-E4B-it-int4.task"
        )

        val missing = required.filterNot(assets::contains)
        if (missing.isNotEmpty()) {
            onStatusUpdate(0f, false, false,
                "Missing required files:\n${missing.joinToString("\n")}", emptyList())
            return@launch
        }

        val availableModels = (required + optional).filter(assets::contains)
        onStatusUpdate(0f, false, true, null, availableModels)

        // copy to cache
        val copied = mutableListOf<String>()
        val outDir = File(ctx.cacheDir, "models").apply { mkdirs() }

        assets.filter { it.endsWith(".task") || it.endsWith(".tflite") }.forEach { name ->
            kotlin.runCatching {
                ctx.assets.open("models/$name").use { input ->
                    File(outDir, name).outputStream().use { input.copyTo(it) }
                }; copied += name
            }.onFailure { Timber.e(it, "Copy failed: $name") }
        }

        if (copied.isEmpty())
            onStatusUpdate(0f, false, false, "Failed to copy model files", availableModels)
        else
            onStatusUpdate(100f, true, false, null, availableModels)

    } catch (e: Exception) {
        onStatusUpdate(0f, false, false, "Error: ${e.localizedMessage}", emptyList())
    }
}



@Composable
fun ApiSettingsScreen(
    geminiApiService: GeminiApiService,
    speechService: SpeechRecognitionService // <-- Inject this!
) {
    var apiKey by remember { mutableStateOf("") }
    var mapsApiKey by remember { mutableStateOf("") }
    var speechApiKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Load existing API keys
    LaunchedEffect(Unit) {
        context.dataStore.data.map { it[API_KEY] }.first()?.let {
            apiKey = it
        }
        context.dataStore.data.map { it[MAPS_API_KEY] }.first()?.let {
            mapsApiKey = it
        }
        context.dataStore.data.map { it[SPEECH_API_KEY] }.first()?.let {
            speechApiKey = it
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
            text = "Enter your API keys to enable all features",
            style = MaterialTheme.typography.bodyMedium
        )

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
            supportingText = { Text("Required for Live Caption audio transcription") }
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
                                if (geminiApiService.validateKey(apiKey)) {   // ← rename here
                                    context.dataStore.edit { it[API_KEY] = apiKey }
                                    messages.add("✓ Gemini API key validated")
                                } else {
                                    messages.add("✗ Invalid Gemini API key")
                                    allValid = false
                                }
                            }

                            // Save Speech API key and initialize SpeechRecognitionService
                            if (speechApiKey.isNotBlank()) {
                                if (speechApiKey.startsWith("AIza") && speechApiKey.length > 20) {
                                    context.dataStore.edit { settings ->
                                        settings[SPEECH_API_KEY] = speechApiKey
                                    }
                                    // Initialize the speech service with this API key
                                    try {
                                        speechService.initializeWithApiKey(speechApiKey)
                                        messages.add("✓ Speech API key saved & initialized")
                                    } catch (e: Exception) {
                                        messages.add("✗ Failed to initialize SpeechRecognitionService: ${e.message}")
                                        allValid = false
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
                    CoroutineScope(Dispatchers.IO).launch {
                        context.dataStore.edit { settings ->
                            settings.remove(API_KEY)
                            settings.remove(MAPS_API_KEY)
                            settings.remove(SPEECH_API_KEY)
                        }
                    }
                    validationMessage = "API keys cleared"
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

    // Updated model availability check with better error handling
    fun checkModelAvailability(
        ctx: Context,
        onStatusUpdate: (Float, Boolean, Boolean, String?, List<String>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val assets = ctx.assets.list("models")?.toSet() ?: emptySet()
                val required = listOf(
                    "gemma-3n-E2B-it-int4.task",  // Currently available
                    "universal_sentence_encoder.tflite"
                )

                // Optional models that might be added later
                val optional = listOf(
                    "gemma-3n-E4B-it-int4.task"  // Will be available in future
                )

                val missing = required.filterNot { it in assets }
                if (missing.isNotEmpty()) {
                    onStatusUpdate(
                        0f,
                        false,
                        false,
                        "Missing required files in assets/models:\n${missing.joinToString("\n")}",
                        emptyList()
                    )
                    return@launch
                }

                // Check which models are actually available
                val availableModels = mutableListOf<String>()

                // Check required models
                required.forEach { model ->
                    if (model in assets) {
                        availableModels.add(model)
                    }
                }

                // Check optional models
                optional.forEach { model ->
                    if (model in assets) {
                        availableModels.add(model)
                        Timber.d("Optional model available: $model")
                    }
                }

                onStatusUpdate(0f, false, true, null, availableModels)

                // Copy available models to cache
                val modelsCopied = mutableListOf<String>()
                val modelsDir = File(ctx.cacheDir, "models")
                modelsDir.mkdirs()

                for (modelName in assets.filter { it.endsWith(".task") || it.endsWith(".tflite") }) {
                    try {
                        val cacheFile = File(modelsDir, modelName)
                        if (!cacheFile.exists()) {
                            ctx.assets.open("models/$modelName").use { input ->
                                cacheFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Timber.d("Copied $modelName to cache")
                        }
                        modelsCopied.add(modelName)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to copy $modelName")
                    }
                }

                if (modelsCopied.isEmpty()) {
                    onStatusUpdate(
                        0f,
                        false,
                        false,
                        "Failed to copy models to cache",
                        availableModels
                    )
                    return@launch
                }

                onStatusUpdate(100f, true, false, null, availableModels)
            } catch (e: Exception) {
                onStatusUpdate(0f, false, false, "Error: ${e.localizedMessage}", emptyList())
            }
        }
    }
}


