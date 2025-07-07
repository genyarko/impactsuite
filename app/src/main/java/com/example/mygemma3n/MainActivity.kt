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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.validateApiKey
import com.example.mygemma3n.feature.caption.LiveCaptionScreen
import com.example.mygemma3n.feature.quiz.QuizScreen
import com.example.mygemma3n.feature.cbt.CBTCoachScreen
import com.example.mygemma3n.feature.plant.PlantScannerScreen
import com.example.mygemma3n.feature.crisis.CrisisHandbookScreen
import com.example.mygemma3n.ui.theme.Gemma3nTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mygemma3n.feature.caption.SpeechRecognitionService
import dagger.hilt.android.EntryPointAccessors
import com.example.mygemma3n.di.SpeechRecognitionServiceEntryPoint


val API_KEY = stringPreferencesKey("gemini_api_key")
val MAPS_API_KEY = stringPreferencesKey("google_maps_api_key")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var geminiApiService: GeminiApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Initialize Gemini API service with stored API key
        lifecycleScope.launch {
            val apiKey = dataStore.data.map { it[API_KEY].orEmpty() }.first()
            if (apiKey.isNotBlank()) {
                try {
                    geminiApiService.initialize(
                        GeminiApiService.Companion.ApiConfig(apiKey = apiKey)
                    )
                } catch (e: Exception) {
                    // Log initialization error
                }
            }
        }

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
@Composable
fun HomeScreen(
    navController: androidx.navigation.NavHostController,
    geminiApiService: GeminiApiService
) {
    var isApiReady by remember { mutableStateOf(false) }
    var isCheckingApi by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Check API key on launch
    LaunchedEffect(Unit) {
        val apiKey = context.dataStore.data.map { it[API_KEY] }.first()
        if (!apiKey.isNullOrBlank()) {
            try {
                // Initialize the API service.
                // By omitting the 'modelName' parameter, ApiConfig will use its default,
                // which is now PRIMARY_GENERATION_MODEL (Gemma 3n).
                geminiApiService.initialize(
                    GeminiApiService.Companion.ApiConfig(
                        apiKey = apiKey
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
    val context = androidx.compose.ui.platform.LocalContext.current

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
                                if (geminiApiService.validateApiKey(apiKey)) {
                                    context.dataStore.edit { settings ->
                                        settings[API_KEY] = apiKey
                                    }
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
}


@Composable
fun CBTCoachScreen() {
    Text("CBT Coach Screen")
}