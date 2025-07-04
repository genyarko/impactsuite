package com.example.mygemma3n.feature.cbt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.gemma.GemmaModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CBTCoachViewModel @Inject constructor(
    private val geminiApiService: GeminiApiService,
    private val modelManager: GemmaModelManager,
    private val emotionDetector: EmotionDetector,
    val cbtTechniques: CBTTechniques,
    private val sessionRepository: SessionRepository,
    private val sessionManager: CBTSessionManager
) : ViewModel() {

    private val _sessionState = MutableStateFlow(CBTSessionState())
    val sessionState: StateFlow<CBTSessionState> = _sessionState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentSessionId: String? = null

    data class CBTSessionState(
        val isActive: Boolean = false,
        val currentEmotion: Emotion? = null,
        val suggestedTechnique: CBTTechnique? = null,
        val conversation: List<Message> = emptyList(),
        val thoughtRecord: ThoughtRecord? = null,
        val isRecording: Boolean = false,
        val currentStep: Int = 0,
        val sessionDuration: Long = 0L,
        val personalizedRecommendations: CBTSessionManager.PersonalizedRecommendations? = null,
        val sessionInsights: CBTSessionManager.SessionInsights? = null
    )

    init {
        // Initialize the API if needed
        viewModelScope.launch {
            try {
                if (!geminiApiService.isInitialized()) {
                    val modelConfig = modelManager.selectOptimalModel(requireQuality = true)
                    modelManager.getModel(modelConfig)
                }
                // Initialize CBT knowledge base
                sessionManager.initializeKnowledgeBase()
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Gemini API or knowledge base")
            }
        }
    }

    fun startSession() {
        currentSessionId = UUID.randomUUID().toString()
        _sessionState.update {
            it.copy(
                isActive = true,
                conversation = emptyList(),
                currentEmotion = null,
                suggestedTechnique = null,
                thoughtRecord = null,
                currentStep = 0
            )
        }
    }

    fun endSession() {
        viewModelScope.launch {
            currentSessionId?.let { sessionId ->
                sessionRepository.updateSessionEffectiveness(
                    sessionId,
                    calculateSessionEffectiveness()
                )
            }
        }
        _sessionState.update {
            it.copy(isActive = false)
        }
    }

    suspend fun processTextInput(userInput: String) {
        _isLoading.value = true

        try {
            // Add user message
            _sessionState.update {
                it.copy(conversation = it.conversation + Message.User(userInput))
            }

            // Generate CBT response
            val response = generateCBTResponse(userInput)

            // Parse technique if mentioned
            val technique = parseCBTTechnique(response)

            // Add AI response
            _sessionState.update {
                it.copy(
                    conversation = it.conversation + Message.AI(
                        content     = response,
                        techniqueId = technique?.id
                    ),
                    suggestedTechnique = technique ?: it.suggestedTechnique
                )
            }

            // Save session progress
            saveSessionProgress()

        } catch (e: Exception) {
            Timber.e(e, "Error processing text input")
            _sessionState.update {
                it.copy(
                    conversation = it.conversation + Message.AI(
                        content = "I apologize, but I'm having trouble processing that. Could you please try again?"
                    )
                )
            }
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun processVoiceInput(audioData: FloatArray) {
        _isLoading.value = true

        try {
            // Transcribe audio (simplified - in production, use a proper speech-to-text service)
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

        return geminiApiService.generateTextComplete(prompt)
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

        return geminiApiService.generateTextComplete(prompt)
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

        return geminiApiService.generateTextComplete(prompt)
    }

    private suspend fun transcribeAudio(audioData: FloatArray): String {
        // Simplified transcription - in production, use a proper speech-to-text API
        // For now, generate a placeholder based on detected emotion
        val emotion = _sessionState.value.currentEmotion ?: Emotion.NEUTRAL

        val prompt = """
            Generate a realistic user statement for someone feeling ${emotion.name.lowercase()}.
            Make it brief (1-2 sentences) and authentic to what someone might say in a therapy session.
            Focus on expressing the emotion naturally.
        """.trimIndent()

        return geminiApiService.generateTextComplete(prompt)
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

                val analysisResponse = geminiApiService.generateTextComplete(analysisPrompt)

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

                val response = geminiApiService.generateTextComplete(prompt)

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

    override fun onCleared() {
        super.onCleared()
        if (_sessionState.value.isActive) {
            endSession()
        }
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