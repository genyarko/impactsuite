package com.mygemma3n.aiapp.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.mygemma3n.aiapp.GEMINI_API_KEY
import com.mygemma3n.aiapp.MAPS_API_KEY
import com.mygemma3n.aiapp.ModelDownloadSection
import com.mygemma3n.aiapp.ONLINE_MODEL_PROVIDER
import com.mygemma3n.aiapp.OPENAI_API_KEY
import com.mygemma3n.aiapp.SPEECH_API_KEY
import com.mygemma3n.aiapp.TokenUsageSection
import com.mygemma3n.aiapp.USE_ONLINE_SERVICE
import com.mygemma3n.aiapp.data.GeminiApiService
import com.mygemma3n.aiapp.data.repository.TokenUsageRepository
import com.mygemma3n.aiapp.data.validateKey
import com.mygemma3n.aiapp.dataStore
import com.mygemma3n.aiapp.data.SpeechRecognitionService
import com.mygemma3n.aiapp.util.GoogleServicesUtil
import com.mygemma3n.aiapp.config.ApiConfiguration
import com.mygemma3n.aiapp.ui.settings.QuizPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    var openaiApiKey by remember { mutableStateOf("") }
    var useOnlineService by remember { mutableStateOf(true) }
    var modelProvider by remember { mutableStateOf("gemini") }
    var isLoading by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    
    // Access quiz preferences repository through DI
    val quizPreferencesRepository = remember {
        QuizPreferencesRepository(context.dataStore)
    }
    
    // Quiz behavior settings with persistent storage
    var showHints by remember { mutableStateOf(true) }
    var autoAdvance by remember { mutableStateOf(false) }
    var showExplanations by remember { mutableStateOf(true) }

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
        context.dataStore.data.map { it[OPENAI_API_KEY] }.first()?.let {
            openaiApiKey = it
        }
        context.dataStore.data.map { it[USE_ONLINE_SERVICE] }.first()?.let {
            useOnlineService = it
        } ?: run {
            useOnlineService = true // Default to online
        }
        context.dataStore.data.map { it[ONLINE_MODEL_PROVIDER] }.first()?.let {
            modelProvider = it
        } ?: run {
            modelProvider = "gemini" // Default to Gemini
        }
        
        // Load quiz preferences
        quizPreferencesRepository.preferences.first().let { prefs ->
            showHints = prefs.showHints
            autoAdvance = prefs.autoAdvanceQuestions
            showExplanations = prefs.showExplanationsImmediately
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
                                    "Online: Uses Gemini/OpenAI APIs (requires internet & API key)"
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

            // Model Provider Selection (only shown when online is enabled)
            if (useOnlineService) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Online AI Provider",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        var expanded by remember { mutableStateOf(false) }
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = when (modelProvider) {
                                    "openai" -> "OpenAI (GPT-5 mini)"
                                    else -> "Google Gemini"
                                },
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                enabled = !isLoading
                            )
                            
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Google Gemini") },
                                    onClick = {
                                        modelProvider = "gemini"
                                        expanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("OpenAI (GPT-5 mini)") },
                                    onClick = {
                                        modelProvider = "openai"
                                        expanded = false
                                    }
                                )
                            }
                        }
                        
                        Text(
                            text = "Choose which AI provider to use for online features",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }

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

            // OpenAI API Key
            OutlinedTextField(
                value = openaiApiKey,
                onValueChange = { openaiApiKey = it },
                label = { Text("OpenAI API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                supportingText = { Text("For GPT-5 mini model access") }
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
                                
                                // Validate OpenAI API key
                                if (openaiApiKey.isNotBlank()) {
                                    if (ApiConfiguration.Validation.isValidOpenAIApiKey(openaiApiKey)) {
                                        context.dataStore.edit { it[OPENAI_API_KEY] = openaiApiKey }
                                        messages.add("✓ OpenAI API key saved")
                                    } else {
                                        messages.add("✗ Invalid OpenAI API key format")
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

                                // Save online/offline preference and model provider
                                context.dataStore.edit { settings ->
                                    settings[USE_ONLINE_SERVICE] = useOnlineService
                                    settings[ONLINE_MODEL_PROVIDER] = modelProvider
                                }
                                messages.add("✓ Service mode saved: ${if (useOnlineService) "Online ($modelProvider)" else "Offline"}")

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
                    enabled = (apiKey.isNotBlank() || openaiApiKey.isNotBlank() || mapsApiKey.isNotBlank() || speechApiKey.isNotBlank()) && !isLoading,
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
                        openaiApiKey = ""
                        mapsApiKey = ""
                        speechApiKey = ""
                        useOnlineService = true
                        modelProvider = "gemini"
                        CoroutineScope(Dispatchers.IO).launch {
                            context.dataStore.edit { settings ->
                                settings.remove(GEMINI_API_KEY)
                                settings.remove(OPENAI_API_KEY)
                                settings.remove(MAPS_API_KEY)
                                settings.remove(SPEECH_API_KEY)
                                settings.remove(USE_ONLINE_SERVICE)
                                settings.remove(ONLINE_MODEL_PROVIDER)
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

            // Quiz Behavior Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Quiz Behavior Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Show Hints Setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Hints",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Display hint button when available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showHints,
                            onCheckedChange = { newValue ->
                                showHints = newValue
                                CoroutineScope(Dispatchers.IO).launch {
                                    quizPreferencesRepository.updateShowHints(newValue)
                                }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Auto-advance Questions Setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-advance Questions",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Move to next question after answering",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoAdvance,
                            onCheckedChange = { newValue ->
                                autoAdvance = newValue
                                CoroutineScope(Dispatchers.IO).launch {
                                    quizPreferencesRepository.updateAutoAdvance(newValue)
                                }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Show Explanations Setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Explanations Immediately",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Display explanations right after answering",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showExplanations,
                            onCheckedChange = { newValue ->
                                showExplanations = newValue
                                CoroutineScope(Dispatchers.IO).launch {
                                    quizPreferencesRepository.updateShowExplanations(newValue)
                                }
                            }
                        )
                    }
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
                        text = "OpenAI: ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "https://platform.openai.com/api-keys",
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

            Spacer(modifier = Modifier.height(24.dp))

            // Help Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "💡 Help & Tips",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "How to use G3N:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val helpTips = listOf(
                        "🤖 AI Tutor: Get personalized lessons on any topic with voice interaction",
                        "🎯 Quiz Generator: Create custom quizzes to test your knowledge",
                        "💬 Chat: Have open conversations with the AI assistant",
                        "🧠 CBT Coach: Practice mindfulness and emotional regulation techniques",
                        "📄 Summarizer: Extract and summarize text from PDF, DOCX, or TXT files",
                        "🎤 Live Caption: Real-time speech transcription and translation",
                        "📸 Image Classification: Identify plants and objects using your camera",
                        "🆘 Crisis Handbook: Access emergency resources and safety information",
                        "📊 Analytics: Track your learning progress and usage patterns",
                        "📚 Story Mode: Interactive storytelling with AI-generated content"
                    )

                    helpTips.forEach { tip ->
                        Text(
                            text = "• $tip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Getting Started:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val gettingStartedTips = listOf(
                        "1. Add your Gemini API key above for online AI features",
                        "2. Download models for offline functionality (recommended)",
                        "3. Add Speech API key for voice features (optional)",
                        "4. All features work offline once models are downloaded",
                        "5. Switch between Online/Offline modes as needed"
                    )

                    gettingStartedTips.forEach { tip ->
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Version Information Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val context = LocalContext.current
                    val packageInfo = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Version:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = packageInfo?.versionName ?: "Unknown",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Build:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                packageInfo?.longVersionCode?.toString() ?: "Unknown"
                            } else {
                                @Suppress("DEPRECATION")
                                packageInfo?.versionCode?.toString() ?: "Unknown"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Package:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = context.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}