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
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class CBTCoachViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,
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
        } catch (_: CancellationException) {
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
    // MAIN FIX: Simplified text processing for faster responses
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
        _sessionState.update { it.copy(error = null) }

        try {
            // Add user message immediately for better UX
            _sessionState.update {
                it.copy(conversation = it.conversation + Message.User(userInput))
            }

            // Generate response with timeout to prevent hanging
            val response = withTimeoutOrNull(30_000) { // 30 second timeout
                generateSimpleCBTResponse(userInput)
            }

            if (response != null) {
                _sessionState.update {
                    it.copy(
                        conversation = it.conversation + Message.AI(response)
                    )
                }

                // Save progress in background (don't block UI)
                saveSessionProgress()
            } else {
                // Timeout occurred
                _sessionState.update {
                    it.copy(
                        conversation = it.conversation + Message.AI(
                            "I apologize for the delay. Let me try to help you with a simpler approach. What specific issue would you like to work on right now?"
                        ),
                        error = "Response timeout - please try again with a shorter question"
                    )
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error processing text input")
            _sessionState.update {
                it.copy(
                    conversation = it.conversation + Message.AI(
                        "I'm having trouble processing that right now. Could you please try rephrasing your question more simply?"
                    ),
                    error = "Processing error: ${e.message}"
                )
            }
        } finally {
            _isLoading.value = false
        }
    }

    // Simplified response generation for faster performance
    private suspend fun generateSimpleCBTResponse(userInput: String): String {
        // Much shorter, focused prompt to reduce processing time
        val prompt = buildString {
            appendLine("You are a CBT coach. User says: $userInput")
            appendLine()
            appendLine("Provide a brief, helpful CBT response (under 100 words) that:")
            appendLine("1. Validates their feelings")
            appendLine("2. Offers one practical CBT technique or insight")
            appendLine("3. Asks a simple follow-up question")
        }

        return gemmaService.generateTextAsync(
            prompt,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = 100,  // Reduced from 200
                temperature = 0.6f  // Slightly lower for more focused responses
            )
        )
    }

    // Keep the complex processing for voice input only when needed
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

            // For voice input, we can afford more complex processing
            // since users expect it to take a bit longer
            val emotionResult = emotionDetector.detectFromMultimodal(
                audioData = audioData,
                text = transcribedText
            )

            _sessionState.update {
                it.copy(currentEmotion = emotionResult.emotion)
            }

            _sessionState.update {
                it.copy(
                    conversation = it.conversation + Message.User(
                        content = transcribedText,
                        audioData = audioData
                    )
                )
            }

            val relevantContent = sessionManager.findRelevantContent(
                userInput = transcribedText,
                emotion = emotionResult.emotion
            )

            val response = generateCBTResponseWithContext(
                transcribedText,
                emotionResult,
                relevantContent
            )

            val recommendedTechnique = cbtTechniques.getRecommendedTechnique(emotionResult.emotion)

            _sessionState.update {
                it.copy(
                    conversation = it.conversation + Message.AI(
                        content     = response,
                        techniqueId = recommendedTechnique.id
                    ),
                    suggestedTechnique = recommendedTechnique
                )
            }

            saveSessionProgress()

        } catch (e: Exception) {
            Timber.e(e, "Error processing voice input")
            _sessionState.update {
                it.copy(error = "Voice processing failed: ${e.message}")
            }
        } finally {
            _isLoading.value = false
        }
    }

    // Keep existing methods but with timeout protection
    private suspend fun generateCBTResponseWithContext(
        userInput: String,
        emotionResult: EmotionDetector.EmotionDetectionResult,
        relevantContent: List<CBTSessionManager.RelevantContent>
    ): String {
        val technique = cbtTechniques.getRecommendedTechnique(emotionResult.emotion)

        val prompt = buildString {
            appendLine("CBT coach response for ${emotionResult.emotion.name.lowercase()} user:")
            appendLine("User: $userInput")
            appendLine("Technique: ${technique.name}")
            appendLine("Provide supportive response under 120 words.")
        }

        return withTimeoutOrNull(20_000) {
            gemmaService.generateTextAsync(
                prompt,
                UnifiedGemmaService.GenerationConfig(
                    maxTokens = 150,
                    temperature = 0.7f
                )
            )
        } ?: "I understand you're feeling ${emotionResult.emotion.name.lowercase()}. Let's try the ${technique.name} technique. ${technique.description}"
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

                // Simplified thought record creation
                val thoughtRecord = ThoughtRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    situation = situation,
                    automaticThought = automaticThought,
                    emotion = emotion,
                    emotionIntensity = emotionIntensity,
                    evidenceFor = listOf("Consider evidence supporting this thought"),
                    evidenceAgainst = listOf("Consider evidence against this thought"),
                    balancedThought = "Let's explore a more balanced perspective",
                    newEmotionIntensity = emotionIntensity * 0.7f
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
            _sessionState.update { it.copy(currentStep = nextStep) }

            viewModelScope.launch {
                try {
                    val response = "Let's move to step ${nextStep + 1}: ${currentTechnique.steps[nextStep]}"

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

    // Simplified versions of complex methods for better performance
    fun getPersonalizedRecommendations(currentIssue: String) {
        viewModelScope.launch {
            try {
                val emotion = _sessionState.value.currentEmotion ?: Emotion.NEUTRAL
                val technique = cbtTechniques.getRecommendedTechnique(emotion)

                val message = """
                    Based on your ${emotion.name.lowercase()} feelings about "$currentIssue":

                    I recommend the ${technique.name} technique. ${technique.description}

                    Would you like to try this together?
                """.trimIndent()

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
            val summary = "Session completed. You worked on ${state.currentEmotion?.name?.lowercase() ?: "your concerns"} using ${state.suggestedTechnique?.name ?: "CBT techniques"}."

            _sessionState.update {
                it.copy(
                    sessionInsights = CBTSessionManager.SessionInsights(
                        summary = summary,
                        keyInsights = listOf(
                            "You engaged well with the process",
                            "Continue practicing these techniques"
                        ),
                        progress = "Completed a full session using ${state.suggestedTechnique?.name ?: "CBT"}",
                        homework = listOf(
                            "Practice the technique once daily",
                            "Journal how you feel before and after"
                        ),
                        nextSteps = "Review progress and adjust technique in the next session"
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
                val message = if (currentEmotion != null) {
                    "I notice you're feeling ${currentEmotion.name.lowercase()}. That's completely normal. Let's work through this together."
                } else {
                    "How are you feeling right now? Understanding your emotions is the first step in CBT."
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
}