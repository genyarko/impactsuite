package com.example.mygemma3n.feature.cbt

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.feature.caption.SpeechRecognitionService
import com.example.mygemma3n.service.AudioCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class CBTCoachViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,  // Changed from GeminiApiService
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
    private val audioBuffer = mutableListOf<FloatArray>()  // Store all audio chunks
    private var isModelInitialized = false

    init {
        viewModelScope.launch {
            try {
                // Initialize the offline model
                initializeModel()
                // Initialize knowledge base after model is ready
                sessionManager.initializeKnowledgeBase()
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Gemma model or knowledge base")
                _sessionState.update {
                    it.copy(error = "Failed to initialize: ${e.message}")
                }
            }
        }
    }

    private suspend fun initializeModel() {
        // 1. Re‑use the already‑loaded engine
        if (gemmaService.isInitialized()) {
            isModelInitialized = true
            return
        }

        // 2. Ignore expected cancellation when user leaves the screen
        try {
            withContext(Dispatchers.IO) { gemmaService.initializeBestAvailable() }
            isModelInitialized = true
        } catch (ce: CancellationException) {        // expected when VM is cleared
            Timber.i("Gemma init cancelled (screen closed)")
        }
    }


    suspend fun startSession() {
        if (!isModelInitialized) {
            initializeModel()
            _sessionState.update {
                it.copy(error = "Model not initialized. Please wait...")
            }
            return
        }

        currentSessionId = UUID.randomUUID().toString()
        _sessionState.update {
            it.copy(
                isActive = true,
                conversation = emptyList(),
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

        /* ─── 1. Launch the microphone service ─── */
        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START_CAPTURE
            context.startService(this)
        }

        audioBuffer.clear()
        _sessionState.update { it.copy(userTypedInput = "", isRecording = true, error = null) }

        /* ─── 2. Collect audio chunks ─── */
        recordingJob = AudioCaptureService.audioDataFlow
            .filterNotNull()
            .onEach { chunk ->
                audioBuffer.add(chunk.clone())  // Store each chunk
                Timber.d("Collected audio chunk ${audioBuffer.size}, size: ${chunk.size}")
            }
            .catch { e ->
                Timber.e(e, "Audio collection error")
                _sessionState.update { it.copy(error = "Recording error: ${e.message}") }
            }
            .launchIn(viewModelScope + Dispatchers.IO)
    }

    fun stopRecording() {
        if (!sessionState.value.isRecording) return

        /* ─── 1. Tell AudioCaptureService to stop ─── */
        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP_CAPTURE
            context.startService(this)
        }

        /* ─── 2. Stop collecting audio ─── */
        recordingJob?.cancel()
        recordingJob = null

        /* ─── 3. Update UI state ─── */
        _sessionState.update { it.copy(isRecording = false) }

        /* ─── 4. Transcribe the collected audio ─── */
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

                // Combine all audio chunks into one array
                val totalSize = audioBuffer.sumOf { it.size }
                val combinedAudio = FloatArray(totalSize)
                var offset = 0
                audioBuffer.forEach { chunk ->
                    chunk.copyInto(combinedAudio, offset)
                    offset += chunk.size
                }

                Timber.d("Transcribing ${audioBuffer.size} chunks, total size: $totalSize samples")

                // Transcribe the entire audio at once
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

        // Convert to PCM bytes
        val pcmData = ByteArray(audioData.size * 2)
        audioData.forEachIndexed { i, sample ->
            val value = (sample.coerceIn(-1f, 1f) * 32767).toInt()
            pcmData[i * 2] = (value and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }

        // Call the speech service directly
        return speechService.transcribeAudioData(pcmData, "en-US")
    }

    /* ────────────── Conversation processing ─────────────────────────── */

    suspend fun processTextInput(userInput: String) {
        if (!isModelInitialized) {
            _sessionState.update {
                it.copy(error = "Model not initialized. Please wait...")
            }
            return
        }

        _isLoading.value = true
        try {
            _sessionState.update { it.copy(conversation = it.conversation + Message.User(userInput)) }
            val response  = generateCBTResponse(userInput)
            val technique = parseCBTTechnique(response)

            _sessionState.update {
                it.copy(
                    conversation = it.conversation + Message.AI(
                        content      = response,
                        techniqueId  = technique?.id
                    ),
                    suggestedTechnique = technique ?: it.suggestedTechnique
                )
            }
            saveSessionProgress()
        } catch (e: Exception) {
            Timber.e(e, "Error processing text input")
            _sessionState.update {
                it.copy(
                    conversation = it.conversation + Message.AI(
                        "I apologize, but I'm having trouble processing that. Could you please try again?"
                    )
                )
            }
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun parseCBTTechnique(response: String): CBTTechnique? {
        // Simple technique detection based on keywords in the response
        val lowercaseResponse = response.lowercase()

        return when {
            lowercaseResponse.contains("thought record") ||
                    lowercaseResponse.contains("examining evidence") -> {
                cbtTechniques.techniques.find { it.name.contains("Thought Record") }
            }
            lowercaseResponse.contains("breathing") ||
                    lowercaseResponse.contains("relaxation") -> {
                cbtTechniques.techniques.find { it.name.contains("Breathing") || it.name.contains("Relaxation") }
            }
            lowercaseResponse.contains("mindfulness") -> {
                cbtTechniques.techniques.find { it.name.contains("Mindfulness") }
            }
            lowercaseResponse.contains("behavioral activation") ||
                    lowercaseResponse.contains("activity") -> {
                cbtTechniques.techniques.find { it.name.contains("Behavioral Activation") }
            }
            else -> null
        }
    }

    suspend fun processVoiceInput(audioData: FloatArray) {
        _isLoading.value = true

        try {
            // Transcribe audio
            val transcribedText = transcribeAudio(audioData)

            // Detect emotion using multimodal approach
            val emotionResult = emotionDetector.detectFromMultimodal(
                audioData = audioData,
                text = transcribedText
            )

            _sessionState.update {
                it.copy(currentEmotion = emotionResult.emotion)
            }

            // Add user message
            _sessionState.update {
                it.copy(
                    conversation = it.conversation + Message.User(
                        content = transcribedText,
                        audioData = audioData
                    )
                )
            }

            // Find relevant CBT content
            val relevantContent = sessionManager.findRelevantContent(
                userInput = transcribedText,
                emotion = emotionResult.emotion
            )

            // Generate CBT response with emotion context and relevant content
            val response = generateCBTResponseWithContext(
                transcribedText,
                emotionResult,
                relevantContent
            )

            // Get recommended technique for detected emotion
            val recommendedTechnique = cbtTechniques.getRecommendedTechnique(emotionResult.emotion)

            // Add AI response
            _sessionState.update {
                it.copy(
                    conversation = it.conversation + Message.AI(
                        content     = response,
                        techniqueId = recommendedTechnique.id
                    ),
                    suggestedTechnique = recommendedTechnique
                )
            }

            // Save session
            saveSessionProgress()

        } catch (e: Exception) {
            Timber.e(e, "Error processing voice input")
        } finally {
            _isLoading.value = false
        }
    }

    fun clearSessionInsights() {
        _sessionState.update { it.copy(sessionInsights = null) }
    }

    private suspend fun generateCBTResponseWithContext(
        userInput: String,
        emotionResult: EmotionDetector.EmotionDetectionResult,
        relevantContent: List<CBTSessionManager.RelevantContent>
    ): String {
        val technique = cbtTechniques.getRecommendedTechnique(emotionResult.emotion)

        val prompt = buildString {
            appendLine("You are a compassionate CBT coach. Based on multimodal analysis:")
            appendLine("- Detected emotion: ${emotionResult.emotion.name} (confidence: ${emotionResult.confidence})")
            appendLine("- Reasoning: ${emotionResult.reasoning}")
            appendLine()
            appendLine("User says: $userInput")
            appendLine()

            if (relevantContent.isNotEmpty()) {
                appendLine("Relevant CBT knowledge:")
                relevantContent.take(2).forEach { content ->
                    appendLine("- ${content.content.take(100)}...")
                }
                appendLine()
            }

            appendLine("Recommended technique: ${technique.name}")
            appendLine("Description: ${technique.description}")
            appendLine()
            appendLine("Provide a warm, supportive response that:")
            appendLine("1. Acknowledges their ${emotionResult.emotion.name.lowercase()} feelings")
            appendLine("2. References relevant CBT concepts naturally")
            appendLine("3. Introduces the ${technique.name} technique")
            appendLine("4. Begins guiding them through the first step")
            appendLine("5. Keeps response under 150 words")
        }

        return gemmaService.generateTextAsync(
            prompt,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = 200,
                temperature = 0.7f
            )
        )
    }

    private suspend fun generateCBTResponse(userInput: String): String {
        val conversationHistory = _sessionState.value.conversation.takeLast(10)
            .joinToString("\n") { msg ->
                when (msg) {
                    is Message.User -> "User: ${msg.content}"
                    is Message.AI -> "Assistant: ${msg.content}"
                }
            }

        val currentTechnique = _sessionState.value.suggestedTechnique

        val prompt = buildString {
            appendLine("You are a compassionate and professional CBT (Cognitive Behavioral Therapy) coach.")
            appendLine("Your role is to guide users through evidence-based CBT techniques while being warm and supportive.")
            appendLine()

            if (conversationHistory.isNotEmpty()) {
                appendLine("Previous conversation:")
                appendLine(conversationHistory)
                appendLine()
            }

            if (currentTechnique != null) {
                appendLine("Currently working with technique: ${currentTechnique.name}")
                appendLine("Current step: ${_sessionState.value.currentStep + 1} of ${currentTechnique.steps.size}")
                appendLine()
            }

            appendLine("User says: $userInput")
            appendLine()
            appendLine("Provide a helpful CBT-based response that:")
            appendLine("1. Validates their feelings")
            appendLine("2. Identifies any cognitive distortions if present")
            appendLine("3. Guides them through appropriate CBT techniques")
            appendLine("4. Asks clarifying questions when needed")
            appendLine("5. Keeps the response conversational and under 150 words")

            if (currentTechnique != null && _sessionState.value.currentStep < currentTechnique.steps.size) {
                appendLine()
                appendLine("Guide them through: ${currentTechnique.steps[_sessionState.value.currentStep]}")
            }
        }

        return gemmaService.generateTextAsync(
            prompt,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = 200,
                temperature = 0.7f
            )
        )
    }

    private suspend fun generateCBTResponseWithEmotion(userInput: String, emotion: Emotion): String {
        val technique = cbtTechniques.getRecommendedTechnique(emotion)

        val prompt = buildString {
            appendLine("You are a compassionate CBT coach. The user is experiencing ${emotion.name.lowercase()} emotions.")
            appendLine()
            appendLine("User says: $userInput")
            appendLine()
            appendLine("Recommended technique: ${technique.name}")
            appendLine("Description: ${technique.description}")
            appendLine()
            appendLine("Steps to guide through:")
            technique.steps.forEachIndexed { index, step ->
                appendLine("${index + 1}. $step")
            }
            appendLine()
            appendLine("Provide a warm, supportive response that:")
            appendLine("1. Acknowledges their ${emotion.name.lowercase()} feelings")
            appendLine("2. Introduces the ${technique.name} technique")
            appendLine("3. Begins guiding them through the first step")
            appendLine("4. Keeps response under 150 words")
        }

        return gemmaService.generateTextAsync(
            prompt,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = 200,
                temperature = 0.7f
            )
        )
    }

    private suspend fun transcribeAudio(audioData: FloatArray): String {
        if (!speechService.isInitialized) return ""

        // Convert to PCM bytes
        val pcmData = ByteArray(audioData.size * 2)
        audioData.forEachIndexed { i, sample ->
            val value = (sample.coerceIn(-1f, 1f) * 32767).toInt()
            pcmData[i * 2] = (value and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }

        return speechService.transcribeAudioData(pcmData, "en-US")
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

                // Generate evidence analysis
                val analysisPrompt = """
                    Analyze this thought record:
                    Situation: $situation
                    Automatic thought: $automaticThought
                    Emotion: ${emotion.name} (intensity: ${(emotionIntensity * 100).toInt()}%)
                    
                    Provide:
                    1. Three pieces of evidence that might support this thought
                    2. Three pieces of evidence against this thought
                    3. A balanced, realistic alternative thought
                    
                    Format as JSON with keys: evidenceFor (array), evidenceAgainst (array), balancedThought (string)
                """.trimIndent()

                val analysisResponse = gemmaService.generateTextAsync(
                    analysisPrompt,
                    UnifiedGemmaService.GenerationConfig(
                        maxTokens = 300,
                        temperature = 0.7f
                    )
                )

                // Parse the response (simplified - in production use proper JSON parsing)
                val thoughtRecord = ThoughtRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    situation = situation,
                    automaticThought = automaticThought,
                    emotion = emotion,
                    emotionIntensity = emotionIntensity,
                    evidenceFor = extractEvidenceFor(analysisResponse),
                    evidenceAgainst = extractEvidenceAgainst(analysisResponse),
                    balancedThought = extractBalancedThought(analysisResponse),
                    newEmotionIntensity = emotionIntensity * 0.6f // Placeholder reduction
                )

                _sessionState.update { it.copy(thoughtRecord = thoughtRecord) }

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
            // advance the step index
            _sessionState.update { it.copy(currentStep = nextStep) }

            viewModelScope.launch {
                val prompt = """
                Guide the user through step ${nextStep + 1} of ${currentTechnique.name}:
                "${currentTechnique.steps[nextStep]}"
                
                Be encouraging and specific. Keep it under 100 words.
            """.trimIndent()

                val response = gemmaService.generateTextAsync(
                    prompt,
                    UnifiedGemmaService.GenerationConfig(
                        maxTokens = 150,
                        temperature = 0.7f
                    )
                )

                // append the AI message with techniqueId
                _sessionState.update {
                    it.copy(
                        conversation = it.conversation + Message.AI(
                            content     = response,
                            techniqueId = currentTechnique.id
                        )
                    )
                }
            }
        }
    }

    private suspend fun saveSessionProgress() {
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
    }

    private fun calculateSessionEffectiveness(): Float {
        val state = _sessionState.value
        var score = 0.5f // Base score

        // Factors that increase effectiveness
        if (state.thoughtRecord != null) score += 0.2f
        if (state.suggestedTechnique != null && state.currentStep > 0) {
            score += 0.1f * (state.currentStep.toFloat() / state.suggestedTechnique.steps.size)
        }
        if (state.conversation.size > 10) score += 0.1f

        return score.coerceIn(0f, 1f)
    }

    // Helper functions for parsing JSON response (simplified)
    private fun extractEvidenceFor(response: String): List<String> {
        // In production, use proper JSON parsing
        return listOf(
            "Evidence supporting the thought",
            "Another supporting point",
            "Third supporting evidence"
        )
    }

    private fun extractEvidenceAgainst(response: String): List<String> {
        return listOf(
            "Evidence against the thought",
            "Another contradicting point",
            "Third piece of contrary evidence"
        )
    }

    private fun extractBalancedThought(response: String): String {
        return "A more balanced perspective on the situation"
    }

    fun updateUserTypedInput(text: String) {
        _sessionState.update { it.copy(userTypedInput = text, error = null) }
    }

    // Add this test function to verify STT is working
    fun testSpeechRecognition() {
        viewModelScope.launch {
            try {
                _sessionState.update { it.copy(userTypedInput = "Testing speech recognition...") }

                // Generate a test audio signal (1 second of 440Hz tone)
                val testAudio = FloatArray(16000) { i ->
                    (kotlin.math.sin(2 * kotlin.math.PI * 440 * i / 16000) * 0.3).toFloat()
                }

                val result = transcribeFullAudio(testAudio)

                _sessionState.update {
                    it.copy(
                        userTypedInput = "",
                        error = "Test complete. Result: ${if (result.isEmpty()) "No transcription (expected for test tone)" else result}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "STT test failed")
                _sessionState.update {
                    it.copy(
                        userTypedInput = "",
                        error = "STT test failed: ${e.message}"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        stopRecording()
        if (_sessionState.value.isActive) endSession()
        super.onCleared()
    }

    fun getPersonalizedRecommendations(currentIssue: String) {
        viewModelScope.launch {
            val emotion = _sessionState.value.currentEmotion ?: Emotion.NEUTRAL
            try {
                val recommendations = sessionManager.getPersonalizedRecommendations(
                    currentEmotion = emotion,
                    recentIssue = currentIssue
                )

                // Store the recommendations in state
                _sessionState.update {
                    it.copy(personalizedRecommendations = recommendations)
                }

                // Build the recommendation message
                val recommendationMessage = """
                Based on your history and current ${emotion.name.lowercase()} feelings:

                ${recommendations.insight}

                I recommend trying the ${recommendations.recommendedTechnique.name} technique.
                Here's your action plan:
                ${recommendations.actionPlan.joinToString("\n") { "• $it" }}
            """.trimIndent()

                // Add it to the conversation and update suggestedTechnique
                _sessionState.update {
                    it.copy(
                        conversation = it.conversation + Message.AI(
                            content     = recommendationMessage,
                            techniqueId = recommendations.recommendedTechnique.id
                        ),
                        suggestedTechnique = recommendations.recommendedTechnique
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting personalized recommendations")
            }
        }
    }

    suspend fun generateSessionSummary() {
        val state = _sessionState.value
        if (state.conversation.isEmpty()) return

        try {
            // Generate insights
            val insights = sessionManager.generateSessionInsights(
                messages = state.conversation,
                emotion = state.currentEmotion ?: Emotion.NEUTRAL,
                techniqueUsed = state.suggestedTechnique
            )

            _sessionState.update {
                it.copy(sessionInsights = insights)
            }

            // Store session summary for future reference
            currentSessionId?.let { sessionId ->
                sessionManager.storeSessionSummary(
                    sessionId = sessionId,
                    emotion = state.currentEmotion ?: Emotion.NEUTRAL,
                    summary = insights.summary,
                    keyInsights = insights.keyInsights,
                    techniqueUsed = state.suggestedTechnique
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error generating session summary")
        }
    }

    fun analyzeEmotionTrajectory() {
        viewModelScope.launch {
            try {
                val analysis = emotionDetector.analyzeEmotionTrajectory(
                    _sessionState.value.conversation
                )

                val message = """
                    Emotional Journey Analysis:
                    
                    You started feeling ${analysis.startEmotion.name.lowercase()} and ended feeling ${analysis.endEmotion.name.lowercase()}.
                    
                    ${analysis.summary}
                    
                    ${if (analysis.improvement) "Great progress! You're moving in a positive direction." else "Let's continue working together on this."}
                """.trimIndent()

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
}