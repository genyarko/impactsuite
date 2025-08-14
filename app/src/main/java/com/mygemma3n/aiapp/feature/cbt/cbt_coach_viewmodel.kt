package com.mygemma3n.aiapp.feature.cbt

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mygemma3n.aiapp.data.UnifiedGemmaService
import com.mygemma3n.aiapp.data.GeminiApiService
import com.mygemma3n.aiapp.data.GeminiApiConfig
import com.mygemma3n.aiapp.domain.repository.SettingsRepository
import com.mygemma3n.aiapp.data.SpeechRecognitionService
import com.mygemma3n.aiapp.service.AudioCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class CBTCoachViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,
    private val geminiApiService: GeminiApiService,
    private val openAIService: com.mygemma3n.aiapp.feature.chat.OpenAIChatService,
    private val settingsRepository: SettingsRepository,
    private val emotionDetector: EmotionDetector,
    val cbtTechniques: CBTTechniques,
    private val sessionRepository: SessionRepository,
    private val sessionManager: CBTSessionManager,
    private val speechService: SpeechRecognitionService
) : ViewModel() {

    private val _sessionState = MutableStateFlow(CBTSessionState())
    val sessionState: StateFlow<CBTSessionState> = _sessionState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentSessionId: String? = null
    private var recordingJob: Job? = null
    private val audioBuffer = mutableListOf<FloatArray>()
    private var isModelInitialized = false

    /* ───────── Online/Offline Service Selection ───────── */
    private fun hasNetworkConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun shouldUseOnlineService(): Boolean {
        return try {
            val useOnlineService = settingsRepository.useOnlineServiceFlow.first()
            val hasNetwork = hasNetworkConnection()
            
            if (!useOnlineService || !hasNetwork) return false
            
            // Check if any API key is available
            val modelProvider = settingsRepository.modelProviderFlow.first()
            val hasValidApiKey = when (modelProvider) {
                "openai" -> openAIService.isInitialized()
                "gemini" -> settingsRepository.apiKeyFlow.first().isNotBlank()
                else -> settingsRepository.apiKeyFlow.first().isNotBlank() // Default to Gemini
            }
            
            hasValidApiKey
        } catch (e: Exception) {
            Timber.w(e, "Error checking service preference, defaulting to offline")
            false
        }
    }

    private suspend fun shouldUseOpenAI(): Boolean {
        return try {
            val modelProvider = settingsRepository.modelProviderFlow.first()
            modelProvider == "openai" && openAIService.isInitialized()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun initializeApiServiceIfNeeded() {
        if (!geminiApiService.isInitialized()) {
            val apiKey = settingsRepository.apiKeyFlow.first()
            if (apiKey.isNotBlank()) {
                try {
                    geminiApiService.initialize(GeminiApiConfig(apiKey = apiKey))
                    Timber.d("GeminiApiService initialized for CBT Coach")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize GeminiApiService")
                    throw e
                }
            } else {
                throw IllegalStateException("API key not found")
            }
        }
    }

    private suspend fun warmUpApiService() {
        try {
            val warmupPrompt = "Hi" // Minimal prompt
            geminiApiService.generateTextComplete(warmupPrompt, "cbt_warmup")
            Timber.d("CBT API service warmed up successfully")
        } catch (e: Exception) {
            Timber.w(e, "CBT API warmup failed, but service should still work")
        }
    }

    private suspend fun ensureModelInitialized(): Boolean {
        return try {
            if (!gemmaService.isInitialized()) {
                gemmaService.initializeBestAvailable()
                isModelInitialized = true
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to ensure Gemma model initialization")
            false
        }
    }

    // Pre-crafted responses for common scenarios
    private val FALLBACK_RESPONSES = mapOf(
        "relax" to """I understand relaxation can be challenging. Let's try a simple technique:

Take a deep breath in for 4 counts, hold for 4, and exhale for 6. This activates your body's relaxation response.

Would you like to try the 5-4-3-2-1 grounding technique next?""",

        "panic" to """I hear you're experiencing panic attacks. You're not alone, and this is manageable.

Right now, let's focus on your breathing: breathe in slowly through your nose, hold briefly, then exhale longer through your mouth.

Can you tell me what usually triggers these attacks?""",

        "anxious" to """Anxiety can feel overwhelming. Let's work through this together.

First, notice where you feel tension in your body. Try to soften those areas while taking slow, deep breaths.

What specific worries are on your mind right now?""",

        "sad" to """I'm here to support you through these difficult feelings.

It's okay to feel sad. Sometimes acknowledging these emotions is the first step toward healing.

Would you like to talk about what's been weighing on you?""",

        "angry" to """I can sense you're feeling frustrated or angry. These are valid emotions.

Let's try to understand what's behind this anger. Often it's protecting us from other feelings.

What situation triggered these feelings?""",

        "default" to """Thank you for sharing. I'm here to help you work through this.

Let's explore what you're experiencing together. Can you tell me more about what's on your mind?"""
    )

    init {
        viewModelScope.launch {
            try {
                // Initialize the offline model
                initializeModel()
                // Initialize knowledge base after model is ready
                sessionManager.initializeKnowledgeBase()

                // Preload API service for faster CBT responses
                launch {
                    delay(1000) // Give settings time to load
                    if (shouldUseOnlineService()) {
                        try {
                            Timber.d("Preloading API service for CBT Coach")
                            if (shouldUseOpenAI()) {
                                // OpenAI service is already initialized if the API key is valid
                                Timber.d("Using OpenAI for CBT responses")
                            } else {
                                initializeApiServiceIfNeeded()
                                warmUpApiService()
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to preload API service for CBT Coach")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Gemma model or knowledge base")
                _sessionState.update {
                    it.copy(error = "Failed to initialize: ${e.message}")
                }
            }
        }
    }

    private suspend fun initializeModel() {
        if (gemmaService.isInitialized()) {
            isModelInitialized = true
            return
        }

        try {
            withContext(Dispatchers.IO) { gemmaService.initializeBestAvailable() }
            isModelInitialized = true
        } catch (_: CancellationException) {
            Timber.i("Gemma init cancelled (screen closed)")
        }
    }

    suspend fun startSession() {
        if (!isModelInitialized) {
            initializeModel()
            if (!isModelInitialized) {
                _sessionState.update {
                    it.copy(error = "Model initialization in progress. Please wait...")
                }
                return
            }
        }

        currentSessionId = UUID.randomUUID().toString()
        _sessionState.update {
            it.copy(
                isActive = true,
                conversation = listOf(
                    Message.AI("Hello! I'm here to support you. How are you feeling today?")
                ),
                currentEmotion = null,
                suggestedTechnique = null,
                thoughtRecord = null,
                currentStep = 0,
                error = null
            )
        }
    }

    fun endSession() {
        viewModelScope.launch {
            currentSessionId?.let { id ->
                sessionRepository.updateSessionEffectiveness(id, calculateSessionEffectiveness())
            }
        }
        _sessionState.update { it.copy(isActive = false) }
    }

    fun startRecording() {
        if (sessionState.value.isRecording) return

        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START_CAPTURE
            context.startService(this)
        }

        audioBuffer.clear()
        _sessionState.update { it.copy(userTypedInput = "", isRecording = true, error = null) }

        recordingJob = AudioCaptureService.audioDataFlow
            .filterNotNull()
            .onEach { chunk ->
                audioBuffer.add(chunk.clone())
                Timber.v("Collected audio chunk ${audioBuffer.size}, size: ${chunk.size}")
            }
            .catch { e ->
                Timber.e(e, "Audio collection error")
                _sessionState.update { it.copy(error = "Recording error: ${e.message}") }
            }
            .launchIn(viewModelScope + Dispatchers.IO)
    }

    fun stopRecording() {
        if (!sessionState.value.isRecording) return

        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP_CAPTURE
            context.startService(this)
        }

        recordingJob?.cancel()
        recordingJob = null
        _sessionState.update { it.copy(isRecording = false) }

        viewModelScope.launch {
            try {
                _sessionState.update { it.copy(userTypedInput = "Transcribing...") }

                if (audioBuffer.isEmpty()) {
                    _sessionState.update {
                        it.copy(
                            userTypedInput = "",
                            error = "No audio recorded. Please try again."
                        )
                    }
                    return@launch
                }

                val totalSize = audioBuffer.sumOf { it.size }
                val combinedAudio = FloatArray(totalSize)
                var offset = 0
                audioBuffer.forEach { chunk ->
                    chunk.copyInto(combinedAudio, offset)
                    offset += chunk.size
                }

                Timber.d("Transcribing ${audioBuffer.size} chunks, total size: $totalSize samples")

                val transcript = transcribeFullAudio(combinedAudio)

                if (transcript.isNotEmpty()) {
                    _sessionState.update {
                        it.copy(
                            userTypedInput = transcript,
                            error = null
                        )
                    }
                } else {
                    _sessionState.update {
                        it.copy(
                            userTypedInput = "",
                            error = "No speech detected. Please speak clearly into the microphone."
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Transcription failed")
                _sessionState.update {
                    it.copy(
                        userTypedInput = "",
                        error = "Transcription failed: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun transcribeFullAudio(audioData: FloatArray): String {
        if (!speechService.isInitialized) {
            throw IllegalStateException("Speech service not initialized")
        }

        val pcmData = ByteArray(audioData.size * 2)
        audioData.forEachIndexed { i, sample ->
            val value = (sample.coerceIn(-1f, 1f) * 32767).toInt()
            pcmData[i * 2] = (value and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }

        return speechService.transcribeAudioData(pcmData, "en-US")
    }

    // ═══════════════════════════════════════════════════════════════
    // MAIN FIX: Better text processing with fallbacks
    // ═══════════════════════════════════════════════════════════════

    suspend fun processTextInput(userInput: String) {
        if (!isModelInitialized) {
            _sessionState.update {
                it.copy(error = "Model not initialized. Please wait...")
            }
            return
        }

        if (userInput.trim().isEmpty()) return

        _isLoading.value = true
        
        // Update service mode indicator
        val usingOnline = shouldUseOnlineService()
        _sessionState.update { 
            it.copy(
                error = null,
                isUsingOnlineService = usingOnline
            ) 
        }

        try {
            // Add user message immediately for better UX
            _sessionState.update {
                it.copy(conversation = it.conversation + Message.User(userInput))
            }

            // First, try to match with fallback responses
            val fallbackResponse = getFallbackResponse(userInput)

            if (fallbackResponse != null) {
                // Use fallback response immediately
                _sessionState.update {
                    it.copy(
                        conversation = it.conversation + Message.AI(fallbackResponse)
                    )
                }

                // Try to detect emotion in background
                viewModelScope.launch {
                    try {
                        val emotion = emotionDetector.detectFromText(userInput)
                        _sessionState.update { it.copy(currentEmotion = emotion) }

                        // Suggest appropriate technique
                        val technique = cbtTechniques.getRecommendedTechnique(emotion)
                        _sessionState.update { it.copy(suggestedTechnique = technique) }
                    } catch (e: Exception) {
                        Timber.e(e, "Error detecting emotion")
                    }
                }
            } else {
                // Try to generate response with very short prompt
                val response = withTimeoutOrNull(15_000) { // Reduced timeout
                    generateOptimizedCBTResponse(userInput)
                }

                if (response != null && response.isNotBlank()) {
                    _sessionState.update {
                        it.copy(
                            conversation = it.conversation + Message.AI(response)
                        )
                    }
                } else {
                    // Use generic fallback
                    _sessionState.update {
                        it.copy(
                            conversation = it.conversation + Message.AI(FALLBACK_RESPONSES["default"]!!)
                        )
                    }
                }
            }

            // Save progress in background
            saveSessionProgress()

        } catch (e: Exception) {
            Timber.e(e, "Error processing text input")
            // Use appropriate fallback based on input
            val fallback = getFallbackResponse(userInput) ?: FALLBACK_RESPONSES["default"]!!
            _sessionState.update {
                it.copy(
                    conversation = it.conversation + Message.AI(fallback)
                )
            }
        } finally {
            _isLoading.value = false
        }
    }

    // Helper function to match user input with fallback responses
    private fun getFallbackResponse(userInput: String): String? {
        val lowerInput = userInput.lowercase()

        return when {
            lowerInput.contains("relax") || lowerInput.contains("calm") -> FALLBACK_RESPONSES["relax"]
            lowerInput.contains("panic") || lowerInput.contains("attack") -> FALLBACK_RESPONSES["panic"]
            lowerInput.contains("anxious") || lowerInput.contains("anxiety") || lowerInput.contains("worried") -> FALLBACK_RESPONSES["anxious"]
            lowerInput.contains("sad") || lowerInput.contains("depressed") || lowerInput.contains("down") -> FALLBACK_RESPONSES["sad"]
            lowerInput.contains("angry") || lowerInput.contains("mad") || lowerInput.contains("frustrated") -> FALLBACK_RESPONSES["angry"]
            else -> null
        }
    }

    // Enhanced response generation with online/offline support
    private suspend fun generateOptimizedCBTResponse(userInput: String): String {
        return if (shouldUseOnlineService()) {
            try {
                if (shouldUseOpenAI()) {
                    generateCBTResponseWithOpenAI(userInput)
                } else {
                    initializeApiServiceIfNeeded()
                    generateCBTResponseOnline(userInput)
                }
            } catch (e: Exception) {
                Timber.w(e, "Online CBT response failed, falling back to offline")
                generateCBTResponseOffline(userInput)
            }
        } else {
            generateCBTResponseOffline(userInput)
        }
    }

    private suspend fun generateCBTResponseWithOpenAI(userInput: String): String {
        // Update service indicator
        _sessionState.update { it.copy(isUsingOnlineService = true) }

        try {
            // Build conversation history with CBT-specific system prompt
            val chatHistory = convertToChatMessages(_sessionState.value.conversation)
            
            // Create custom system message for CBT context
            val systemMessage = com.mygemma3n.aiapp.feature.chat.ChatMessage.AI(
                """You are a compassionate, licensed CBT (Cognitive Behavioral Therapy) therapist. Your role is to:

1. Validate clients' feelings with empathy and warmth
2. Help them identify the connections between thoughts, feelings, and behaviors
3. Guide them through CBT techniques like thought challenging, grounding exercises, and behavioral activation
4. Ask thoughtful follow-up questions to deepen understanding
5. Suggest specific CBT interventions when appropriate
6. Keep responses concise but meaningful (under 150 words)
7. Maintain professional boundaries while being genuinely supportive

Focus on helping the client develop self-awareness and practical coping strategies."""
            )
            
            // Combine system message with conversation history
            val fullHistory = listOf(systemMessage) + chatHistory
            
            val response = withTimeoutOrNull(12_000) { // 12 second timeout for CBT responses
                openAIService.generateChatResponseOnline(
                    userMessage = userInput,
                    conversationHistory = fullHistory,
                    maxTokens = 512,
                    temperature = 0.7f
                )
            } ?: throw IllegalStateException("Response timeout")

            return if (response.isBlank()) {
                throw IllegalStateException("Empty response from OpenAI service")
            } else {
                response
            }

        } catch (e: Exception) {
            Timber.e(e, "Error generating OpenAI CBT response")
            throw e
        }
    }

    private suspend fun generateCBTResponseOnline(userInput: String): String {
        // Update service indicator
        _sessionState.update { it.copy(isUsingOnlineService = true) }

        val prompt = """You are a compassionate CBT (Cognitive Behavioral Therapy) therapist. A client has shared: "${userInput.take(200)}"

Respond with:
1. Validate their feelings empathetically
2. Ask a thoughtful follow-up question or suggest a CBT technique
3. Keep response under 100 words
4. Use a warm, professional tone

Focus on helping them understand the connection between thoughts, feelings, and behaviors."""

        try {
            val response = withTimeoutOrNull(12_000) { // 12 second timeout for CBT responses
                geminiApiService.generateTextComplete(prompt, "cbt")
            } ?: throw IllegalStateException("Response timeout")

            return if (response.isBlank()) {
                throw IllegalStateException("Empty response from online service")
            } else {
                response
            }

        } catch (e: Exception) {
            Timber.e(e, "Error generating online CBT response")
            throw e
        }
    }

    private suspend fun generateCBTResponseOffline(userInput: String): String {
        // Update service indicator
        _sessionState.update { it.copy(isUsingOnlineService = false) }

        // Check token limit
        if (gemmaService.wouldExceedLimit(userInput)) {
            Timber.w("Input too long, using fallback")
            return getFallbackResponse(userInput) ?: FALLBACK_RESPONSES["default"]!!
        }

        // Ensure offline model is ready
        if (!ensureModelInitialized()) {
            return getFallbackResponse(userInput) ?: FALLBACK_RESPONSES["default"]!!
        }

        // Very short, focused prompt optimized for token limits
        val prompt = """User: ${userInput.take(100)}

Respond as a CBT therapist in under 50 words. Be empathetic and helpful."""

        return try {
            gemmaService.generateTextAsync(
                prompt,
                UnifiedGemmaService.GenerationConfig(
                    maxTokens = 80,  // Very conservative
                    temperature = 0.6f
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Generation failed, using fallback")
            getFallbackResponse(userInput) ?: FALLBACK_RESPONSES["default"]!!
        }
    }

    // Keep voice processing separate as it can afford more time
    suspend fun processVoiceInput(audioData: FloatArray) {
        _isLoading.value = true

        try {
            val transcribedText = transcribeAudio(audioData)

            if (transcribedText.isEmpty()) {
                _sessionState.update {
                    it.copy(error = "Could not transcribe audio. Please try again.")
                }
                return
            }

            // For voice input, use the same optimized processing
            processTextInput(transcribedText)

        } catch (e: Exception) {
            Timber.e(e, "Error processing voice input")
            _sessionState.update {
                it.copy(error = "Voice processing failed: ${e.message}")
            }
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun transcribeAudio(audioData: FloatArray): String {
        if (!speechService.isInitialized) return ""

        val pcmData = ByteArray(audioData.size * 2)
        audioData.forEachIndexed { i, sample ->
            val value = (sample.coerceIn(-1f, 1f) * 32767).toInt()
            pcmData[i * 2] = (value and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }

        return speechService.transcribeAudioData(pcmData, "en-US")
    }

    // Helper function to convert CBT messages to chat messages for OpenAI service
    private fun convertToChatMessages(conversation: List<Message>): List<com.mygemma3n.aiapp.feature.chat.ChatMessage> {
        return conversation.takeLast(10).map { message -> // Limit to last 10 messages
            when (message) {
                is Message.User -> com.mygemma3n.aiapp.feature.chat.ChatMessage.User(message.content)
                is Message.AI -> com.mygemma3n.aiapp.feature.chat.ChatMessage.AI(message.content)
            }
        }
    }

    // Async background save - don't block UI
    private fun saveSessionProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = _sessionState.value
                currentSessionId?.let { sessionId ->
                    val session = CBTSession(
                        id         = sessionId,
                        timestamp  = System.currentTimeMillis(),
                        emotion    = state.currentEmotion ?: Emotion.NEUTRAL,
                        techniqueId= state.suggestedTechnique?.id,
                        transcript = state.conversation.joinToString("\n") { msg ->
                            when (msg) {
                                is Message.User -> "User: ${msg.content}"
                                is Message.AI   -> "AI: ${msg.content}"
                            }
                        },
                        duration   = state.sessionDuration,
                        completed  = false,
                        effectiveness = null
                    )
                    sessionRepository.saveSession(session)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error saving session progress")
            }
        }
    }

    fun clearSessionInsights() {
        _sessionState.update { it.copy(sessionInsights = null) }
    }

    fun createThoughtRecord(
        situation: String,
        automaticThought: String,
        emotionIntensity: Float
    ) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val emotion = _sessionState.value.currentEmotion ?: Emotion.NEUTRAL

                // Create thought record immediately with pre-crafted guidance
                val thoughtRecord = ThoughtRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    situation = situation,
                    automaticThought = automaticThought,
                    emotion = emotion,
                    emotionIntensity = emotionIntensity,
                    evidenceFor = listOf("What facts support this thought?"),
                    evidenceAgainst = listOf("What facts contradict this thought?"),
                    balancedThought = "Consider: Is there another way to look at this situation?",
                    newEmotionIntensity = emotionIntensity * 0.7f
                )

                _sessionState.update { it.copy(thoughtRecord = thoughtRecord) }

                // Add helpful message
                val message = """Great job creating a thought record! 

Let's examine the evidence for and against your thought: "$automaticThought"

This helps us find a more balanced perspective."""

                _sessionState.update {
                    it.copy(
                        conversation = it.conversation + Message.AI(message)
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Error creating thought record")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun progressToNextStep() {
        val currentTechnique = _sessionState.value.suggestedTechnique ?: return
        val nextStep = _sessionState.value.currentStep + 1

        if (nextStep < currentTechnique.steps.size) {
            _sessionState.update { it.copy(currentStep = nextStep) }

            viewModelScope.launch {
                try {
                    val response = """Excellent! Let's move to step ${nextStep + 1}:

${currentTechnique.steps[nextStep]}

Take your time with this step. How does it feel?"""

                    _sessionState.update {
                        it.copy(
                            conversation = it.conversation + Message.AI(
                                content     = response,
                                techniqueId = currentTechnique.id
                            )
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error progressing to next step")
                }
            }
        } else {
            // Completed all steps
            val completionMessage = """Wonderful! You've completed all steps of the ${currentTechnique.name} technique.

How are you feeling now compared to when we started? Remember, practice makes these techniques more effective."""

            _sessionState.update {
                it.copy(
                    conversation = it.conversation + Message.AI(
                        content = completionMessage,
                        techniqueId = currentTechnique.id
                    )
                )
            }
        }
    }

    private fun calculateSessionEffectiveness(): Float {
        val state = _sessionState.value
        var score = 0.5f

        if (state.thoughtRecord != null) score += 0.2f
        if (state.suggestedTechnique != null && state.currentStep > 0) {
            score += 0.1f * (state.currentStep.toFloat() / state.suggestedTechnique.steps.size)
        }
        if (state.conversation.size > 6) score += 0.1f

        return score.coerceIn(0f, 1f)
    }

    fun updateUserTypedInput(text: String) {
        _sessionState.update { it.copy(userTypedInput = text, error = null) }
    }

    override fun onCleared() {
        stopRecording()
        if (_sessionState.value.isActive) endSession()
        super.onCleared()
    }

    // Simplified recommendation function
    fun getPersonalizedRecommendations(currentIssue: String) {
        viewModelScope.launch {
            try {
                val emotion = _sessionState.value.currentEmotion ?: Emotion.NEUTRAL
                val technique = cbtTechniques.getRecommendedTechnique(emotion)

                val message = """Based on what you've shared, I recommend trying the ${technique.name} technique.

${technique.description}

This technique typically takes ${technique.duration} minutes and can be very effective for ${emotion.name.lowercase()} feelings.

Would you like to start with the first step?"""

                _sessionState.update {
                    it.copy(
                        conversation = it.conversation + Message.AI(
                            content     = message,
                            techniqueId = technique.id
                        ),
                        suggestedTechnique = technique
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting recommendations")
            }
        }
    }

    suspend fun generateSessionSummary() {
        val state = _sessionState.value
        if (state.conversation.isEmpty()) return

        try {
            val techniqueUsed = state.suggestedTechnique?.name ?: "general CBT techniques"
            val emotionText = state.currentEmotion?.name?.lowercase() ?: "your concerns"

            val summary = "You've made progress today working on $emotionText using $techniqueUsed. Remember, healing is a journey, not a destination."

            _sessionState.update {
                it.copy(
                    sessionInsights = CBTSessionManager.SessionInsights(
                        summary = summary,
                        keyInsights = listOf(
                            "You showed courage in sharing your feelings",
                            "You engaged with therapeutic techniques",
                            "You're building emotional awareness"
                        ),
                        progress = "Today's session focused on understanding and managing $emotionText",
                        homework = listOf(
                            "Practice the $techniqueUsed technique once daily",
                            "Notice and write down moments when you feel $emotionText",
                            "Use deep breathing when feeling overwhelmed"
                        ),
                        nextSteps = "Continue practicing these techniques and notice any patterns in your emotional responses"
                    )
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "Error generating session summary")
        }
    }

    fun analyzeEmotionTrajectory() {
        viewModelScope.launch {
            try {
                val currentEmotion = _sessionState.value.currentEmotion
                val message = when (currentEmotion) {
                    Emotion.ANXIOUS -> "I notice you're feeling anxious. That's completely understandable. Let's work on some calming techniques together."
                    Emotion.SAD -> "It sounds like you're experiencing sadness. It's okay to feel this way. I'm here to support you."
                    Emotion.ANGRY -> "I can sense some frustration or anger. These are valid emotions. Let's explore what's behind them."
                    Emotion.FEARFUL -> "Fear can be overwhelming. You're safe here. Let's work through this together."
                    Emotion.HAPPY -> "It's wonderful that you're feeling positive! Let's build on this momentum."
                    else -> "Let's take a moment to check in with how you're feeling right now. What emotions are you experiencing?"
                }

                _sessionState.update {
                    it.copy(
                        conversation = it.conversation + Message.AI(message)
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error analyzing emotion trajectory")
            }
        }
    }

    // Privacy functions for session data management
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                sessionManager.deleteSession(sessionId)
            } catch (e: Exception) {
                Timber.e(e, "Error deleting session")
                _sessionState.update {
                    it.copy(error = "Failed to delete session: ${e.message}")
                }
            }
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            try {
                sessionManager.deleteAllSessions()
                _sessionState.update {
                    it.copy(
                        conversation = emptyList(),
                        sessionInsights = null,
                        thoughtRecord = null
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting all sessions")
                _sessionState.update {
                    it.copy(error = "Failed to delete all sessions: ${e.message}")
                }
            }
        }
    }

    fun deleteOldSessions(daysOld: Int) {
        viewModelScope.launch {
            try {
                sessionManager.deleteOldSessions(daysOld)
            } catch (e: Exception) {
                Timber.e(e, "Error deleting old sessions")
                _sessionState.update {
                    it.copy(error = "Failed to delete old sessions: ${e.message}")
                }
            }
        }
    }

    fun clearAllCBTData() {
        viewModelScope.launch {
            try {
                sessionManager.clearAllData()
                _sessionState.update {
                    CBTSessionState() // Reset to empty state
                }
            } catch (e: Exception) {
                Timber.e(e, "Error clearing all CBT data")
                _sessionState.update {
                    it.copy(error = "Failed to clear all data: ${e.message}")
                }
            }
        }
    }
}