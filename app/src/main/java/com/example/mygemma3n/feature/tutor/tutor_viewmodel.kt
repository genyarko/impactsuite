package com.example.mygemma3n.feature.tutor

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.*
import com.example.mygemma3n.feature.caption.SpeechRecognitionService
import com.example.mygemma3n.feature.quiz.Difficulty
import com.example.mygemma3n.service.AudioCaptureService
import com.example.mygemma3n.shared_utilities.OfflineRAG
import com.example.mygemma3n.shared_utilities.TextToSpeechManager
import com.example.mygemma3n.feature.chat.ChatMessage
import com.example.mygemma3n.data.cache.CurriculumCache
import com.example.mygemma3n.feature.analytics.LearningAnalyticsRepository
import com.example.mygemma3n.feature.analytics.InteractionType
import com.example.mygemma3n.dataStore
import com.example.mygemma3n.SPEECH_API_KEY
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class TutorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tutorRepository: TutorRepository,
    private val chatRepository: ChatRepository,
    private val gemmaService: UnifiedGemmaService,
    private val offlineRAG: OfflineRAG,
    private val speechService: SpeechRecognitionService,
    private val promptManager: TutorPromptManager,
    private val variantManager: PromptVariantManager,
    private val textToSpeechManager: TextToSpeechManager,
    private val curriculumCache: CurriculumCache,
    private val analyticsRepository: LearningAnalyticsRepository,
    private val savedStateHandle: SavedStateHandle?
) : ViewModel() {

    // Performance: Topic cache
    private val topicCache = ConcurrentHashMap<String, List<String>>()
    
    // Performance: Input debouncing
    private val inputDebouncer = MutableSharedFlow<String>()
    
    // Memory management: Track all jobs
    private val activeJobs = mutableListOf<Job>()
    private var recordingJob: Job? = null
    private var transcriptionJob: Job? = null
    
    // Audio recording properties
    private val audioBuffer = mutableListOf<FloatArray>()

    private val _state = MutableStateFlow(TutorState())
    val state: StateFlow<TutorState> = _state.asStateFlow()

    private var currentStudent: StudentProfileEntity? = null
    private var currentSessionId: String? = null
    private var currentChatSessionId: String? = null
    private var conversationContext = mutableListOf<ConversationEntry>()
    private var attemptCount = mutableMapOf<String, Int>()

    // Retry configuration
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 1000L

    init {
        setupDebouncing()
        restoreStateIfNeeded(savedStateHandle)
        
        viewModelScope.launch {
            initializeSpeechServiceIfNeeded()
            loadMostRecentStudent()
        }
    }

    @OptIn(FlowPreview::class)
    private fun setupDebouncing() {
        inputDebouncer
            .debounce(300) // Wait 300ms after user stops typing
            .filter { it.isNotBlank() }
            .onEach { debouncedInput ->
                processUserInputInternal(debouncedInput)
            }
            .launchIn(viewModelScope)
    }

    private fun restoreStateIfNeeded(savedStateHandle: SavedStateHandle?) {
        savedStateHandle?.get<TutorConversationState>("conversation_state")?.let { saved ->
            _state.update { current ->
                current.copy(
                    messages = saved.messages,
                    currentTopic = saved.currentTopic,
                    conversationTopic = saved.conversationTopic,
                    conceptMastery = saved.conceptMastery
                )
            }
            
            // Restore conversation context
            conversationContext = saved.conversationContext.toMutableList()
            attemptCount = saved.attemptCount.toMutableMap()
        }
    }

    fun processUserInput(input: String) {
        viewModelScope.launch {
            inputDebouncer.emit(input)
        }
    }

    private fun addTutorMessage(
        content: String,
        isUser: Boolean,
        metadata: TutorMessage.MessageMetadata? = null
    ) {
        if (content.isBlank()) {
            Timber.w("Attempted to add blank message")
            return
        }

        val message = TutorMessage(
            content = content,
            isUser = isUser,
            metadata = metadata
        )

        _state.update { currentState ->
            currentState.copy(
                messages = currentState.messages + message
            )
        }

        Timber.d("Added message: ${content.take(50)}... (user=$isUser)")

        // Save to chat repository
        currentChatSessionId?.let { sid ->
            val chatMsg = if (isUser) ChatMessage.User(content) else ChatMessage.AI(content)
            viewModelScope.launch {
                try {
                    chatRepository.addMessage(sid, chatMsg)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to save message to repository")
                }
            }
        }
    }

    private suspend fun processUserInputInternal(input: String) {
        addTutorMessage(input, isUser = true)
        _state.update { it.copy(isLoading = true, error = null) }

        var retryCount = 0
        var lastError: Exception? = null

        while (retryCount < MAX_RETRIES) {
            try {
                processUserInputWithRetry(input)
                return
            } catch (e: Exception) {
                lastError = e
                retryCount++
                
                if (retryCount < MAX_RETRIES) {
                    _state.update { 
                        it.copy(
                            error = "Connection issue. Retrying... ($retryCount/$MAX_RETRIES)"
                        )
                    }
                    delay(RETRY_DELAY_MS * retryCount) // Exponential backoff
                } else {
                    Timber.e(e, "Failed after $MAX_RETRIES retries")
                }
            }
        }

        _state.update { 
            it.copy(
                isLoading = false,
                error = "Unable to process your request. Please try again."
            )
        }
    }

    private suspend fun processUserInputWithRetry(input: String) {
        val student = currentStudent ?: throw IllegalStateException("No student profile")
        val subject = _state.value.currentSubject ?: throw IllegalStateException("No subject selected")
        val sessionType = _state.value.sessionType ?: throw IllegalStateException("No session type")

        // Context management
        val currentTopic = _state.value.conversationTopic
        val newTopic = extractTopicFromQuestion(input)
        val isFollowUp = isFollowUpQuestion(input)
        val isTopicChange = !isFollowUp && detectTopicChange(newTopic, currentTopic)

        val updatedTopic = when {
            isFollowUp -> currentTopic
            currentTopic.isEmpty() -> newTopic
            isTopicChange -> newTopic
            else -> currentTopic
        }

        _state.update { 
            it.copy(
                conversationTopic = updatedTopic,
                lastUserQuestion = if (!isFollowUp) input else it.lastUserQuestion
            ) 
        }

        if (isTopicChange) {
            conversationContext.clear()
            attemptCount.clear()
        }



        // Analyze and process
        val studentNeed = analyzeStudentInput(input)
        val concept = extractMainConcept(input)
        val previousAttempts = attemptCount[concept] ?: 0

        val approach = determineTeachingApproach(
            studentNeed = studentNeed,
            previousAttempts = previousAttempts,
            recentHistory = conversationContext.takeLast(3)
        )

        val relevantContent = offlineRAG.queryWithContext(
            query = input,
            subject = subject
        )

        val response = generateAdaptiveResponse(
            student = student,
            userInput = input,
            approach = approach,
            studentNeed = studentNeed,
            relevantContent = relevantContent,
            concept = concept,
            subject = subject,
            sessionType = sessionType,
            conversationTopic = updatedTopic,
            originalQuestion = _state.value.lastUserQuestion,
            isFollowUp = isFollowUp
        )

        // Check if this is a continuation request
        val isContinuation = input.lowercase().trim().let {
            it == "continue" || it == "finish" || it == "complete" ||
                    it == "go on" || it == "more" || it == "next"
        }

        val formattedResponse = if (isContinuation) {
            // Find the last AI message to continue from
            val lastAiMessage = _state.value.messages.findLast { !it.isUser }

            if (lastAiMessage != null && lastAiMessage.content.isNotBlank()) {
                Timber.d("Continuing from previous message: ${lastAiMessage.content.take(50)}...")

                // Generate continuation prompt
                val continuationPrompt = """
                Continue your previous explanation about ${_state.value.conversationTopic}.
                Your previous response was: "${lastAiMessage.content}"
                
                Continue naturally from where you left off. Do not repeat what you already said.
                Add more details, examples, or complete any unfinished thoughts.
                Keep the same tone and style.
            """.trimIndent()

                val continuationResponse = gemmaService.generateTextAsync(
                    continuationPrompt,
                    UnifiedGemmaService.GenerationConfig(
                        maxTokens = getMaxTokensForGrade(student.gradeLevel),
                        temperature = 0.7f
                    )
                )

                val formattedContinuation = formatTutorResponse(continuationResponse, student, approach)

                // Replace the last AI message with extended content
                handleContinuationResponse(
                    formattedContinuation,
                    lastAiMessage.metadata?.concept ?: concept,
                    approach,
                    student
                )

                _state.update { it.copy(isLoading = false) }
                return
            } else {
                // No previous message to continue from, generate regular response
                val baseResponse = formatTutorResponse(response, student, approach)
                ensureCompleteResponse(baseResponse, student.gradeLevel)
            }
        } else {
            // Regular message processing
            val baseResponse = formatTutorResponse(response, student, approach)
            ensureCompleteResponse(baseResponse, student.gradeLevel)
        }

        // Add the message if not a continuation (since continuation is handled above)
        if (!isContinuation) {
            addTutorMessage(
                formattedResponse,
                isUser = false,
                metadata = TutorMessage.MessageMetadata(
                    concept = concept,
                    explanationType = approach.name,
                    difficulty = getDifficultyForGrade(student.gradeLevel).name
                )
            )
        }

        // Auto-speak if enabled
        if (_state.value.autoSpeak && !formattedResponse.isEmpty()) {
            textToSpeechManager.speak(formattedResponse)
        }

        // Track analytics
        trackLearningInteraction(
            student, subject, updatedTopic, concept, studentNeed, 
            previousAttempts, isFollowUp, formattedResponse, approach
        )

        // Update tracking
        attemptCount[concept] = previousAttempts + 1
        conversationContext.add(ConversationEntry("Student", input, concept))
        conversationContext.add(ConversationEntry("Tutor", formattedResponse, concept, true))

        _state.update { it.copy(currentApproach = approach, isLoading = false) }
    }

    private fun handleContinuationResponse(
        formattedResponse: String,
        concept: String,
        approach: TeachingApproach,
        student: StudentProfileEntity
    ) {
        val currentMessages = _state.value.messages
        val lastAiMessage = currentMessages.lastOrNull { !it.isUser }
        
        if (lastAiMessage != null) {
            // Create updated message with concatenated content
            val concatenatedResponse = "${lastAiMessage.content}\n\n$formattedResponse"
            val updatedMessage = lastAiMessage.copy(
                content = concatenatedResponse,
                metadata = lastAiMessage.metadata?.copy(
                    concept = concept,
                    explanationType = approach.name,
                    difficulty = getDifficultyForGrade(student.gradeLevel).name,
                    responseTime = System.currentTimeMillis() - lastAiMessage.timestamp
                ) ?: TutorMessage.MessageMetadata(
                    concept = concept,
                    explanationType = approach.name,
                    difficulty = getDifficultyForGrade(student.gradeLevel).name,
                    responseTime = System.currentTimeMillis() - lastAiMessage.timestamp
                )
            )
            
            // Replace the last AI message with the updated one
            val updatedMessages = currentMessages.map { message ->
                if (message.id == lastAiMessage.id) updatedMessage else message
            }
            
            _state.update { it.copy(messages = updatedMessages) }
            
            // Save extended content to chat repository
            currentChatSessionId?.let { sessionId ->
                viewModelScope.launch {
                    try {
                        val chatMsg = ChatMessage.AI(concatenatedResponse)
                        // Note: Since updateMessage doesn't exist, we rely on the UI state
                        // The message is already updated in the UI state above
                        Timber.d("Extended message content updated in UI state")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to process extended message")
                    }
                }
            }
        } else {
            // No previous message to extend, add as new
            addTutorMessage(
                formattedResponse, 
                isUser = false, 
                metadata = TutorMessage.MessageMetadata(
                    concept = concept,
                    explanationType = approach.name,
                    difficulty = getDifficultyForGrade(student.gradeLevel).name
                )
            )
        }
    }

// In TutorViewModel.kt - Update generateAdaptiveResponse
private suspend fun generateAdaptiveResponse(
    student: StudentProfileEntity,
    userInput: String,
    approach: TeachingApproach,
    studentNeed: StudentNeed,
    relevantContent: String,
    concept: String,
    subject: OfflineRAG.Subject,
    sessionType: TutorSessionType,
    conversationTopic: String = "",
    originalQuestion: String = "",
    isFollowUp: Boolean = false
): String {
    // Get appropriate max tokens for grade
    val maxTokens = getMaxTokensForGrade(student.gradeLevel)

    // Build comprehensive prompt for the AI tutor
    val fullPrompt = buildTutorPrompt(
        student = student,
        userInput = userInput,
        approach = approach,
        studentNeed = studentNeed,
        relevantContent = relevantContent,
        concept = concept,
        subject = subject,
        sessionType = sessionType,
        conversationTopic = conversationTopic,
        originalQuestion = originalQuestion,
        isFollowUp = isFollowUp
    )

    try {
        // Generate response with proper config
        val response = gemmaService.generateTextAsync(
            fullPrompt,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = maxTokens,
                temperature = when (approach) {
                    TeachingApproach.SOCRATIC -> 0.6f
                    TeachingApproach.EXPLANATION -> 0.4f
                    TeachingApproach.PROBLEM_SOLVING -> 0.3f
                    TeachingApproach.ENCOURAGEMENT -> 0.7f
                    TeachingApproach.CORRECTION -> 0.5f
                }
            )
        )

        // Check if response was truncated by the model
        if (response.length > maxTokens * 3) { // Rough estimate: 1 token ≈ 3 chars
            Timber.w("Response may have been truncated. Length: ${response.length}")

            // Find a good breaking point
            val sentences = response.split(Regex("(?<=[.!?])\\s+"))
            val completeResponse = if (sentences.size > 1) {
                // Take all but the last sentence if it seems incomplete
                sentences.dropLast(1).joinToString(" ")
            } else {
                response
            }

            return ensureCompleteResponse(completeResponse, student.gradeLevel)
        }

        return response

    } catch (e: Exception) {
        Timber.e(e, "Error generating response")
        throw e
    }
}

private fun ensureCompleteResponse(response: String, grade: Int): String {
    val trimmed = response.trim()

    // Check if response seems complete
    if (trimmed.isEmpty()) return trimmed

    // Check for obvious truncation patterns
    val lastSentence = trimmed.split(".").lastOrNull()?.trim() ?: ""
    val endsWithPunctuation = trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")

    // If the response doesn't end with punctuation, try to complete it
    if (!endsWithPunctuation) {
        return when {
            // If it ends mid-word or with incomplete thought
            trimmed.endsWith(" and") || trimmed.endsWith(" but") ||
                    trimmed.endsWith(" because") || trimmed.endsWith(" such as") -> {
                // Add a generic completion
                "$trimmed and more."
            }

            // If the last sentence is very short (likely truncated)
            lastSentence.length < 20 && lastSentence.isNotEmpty() -> {
                // Remove the incomplete sentence
                val withoutLast = trimmed.substring(0, trimmed.length - lastSentence.length).trim()
                if (withoutLast.endsWith(".") || withoutLast.endsWith("!") || withoutLast.endsWith("?")) {
                    withoutLast
                } else {
                    "$withoutLast."
                }
            }

            // Otherwise just add a period
            else -> "$trimmed."
        }
    }

    return trimmed
}

    private fun determineTeachingApproach(
        studentNeed: StudentNeed,
        previousAttempts: Int,
        recentHistory: List<ConversationEntry>
    ): TeachingApproach {
        // Fix: Use state instead of currentStudent directly
        val student = _state.value.studentProfile
        val isYoungStudent = student?.gradeLevel?.let { it <= 3 } ?: false

        Timber.d("Student grade: ${student?.gradeLevel}, isYoungStudent: $isYoungStudent")

        // For young students asking "what is" questions, always explain first
        if (isYoungStudent && studentNeed.type == NeedType.CONCEPT_EXPLANATION) {
            return TeachingApproach.EXPLANATION
        }

        // Check if student has been struggling
        val recentFailures = recentHistory.count { it.success == false }
        val isStruggling = recentFailures >= 2 || previousAttempts >= 3

        // Check for misconceptions
        val hasMisconception = recentHistory.any { entry ->
            entry.content.contains("but I thought", ignoreCase = true) ||
                    entry.content.contains("isn't it", ignoreCase = true)
        }

        return when {
            // Prioritize encouragement if struggling
            isStruggling && previousAttempts > 2 -> TeachingApproach.ENCOURAGEMENT

            // Correct misconceptions immediately
            hasMisconception -> TeachingApproach.CORRECTION

            // For young students, prefer explanation over Socratic for initial attempts
            isYoungStudent && previousAttempts == 0 -> TeachingApproach.EXPLANATION

            // Otherwise, match to student need
            studentNeed.type == NeedType.CLARIFICATION && previousAttempts == 0 ->
                if (isYoungStudent) TeachingApproach.EXPLANATION else TeachingApproach.SOCRATIC

            studentNeed.type == NeedType.CLARIFICATION && previousAttempts > 0 ->
                TeachingApproach.EXPLANATION

            studentNeed.type == NeedType.STEP_BY_STEP_HELP ->
                TeachingApproach.PROBLEM_SOLVING

            studentNeed.type == NeedType.CONCEPT_EXPLANATION ->
                TeachingApproach.EXPLANATION

            studentNeed.type == NeedType.PRACTICE ->
                TeachingApproach.PROBLEM_SOLVING

            else -> if (isYoungStudent) TeachingApproach.EXPLANATION else TeachingApproach.SOCRATIC
        }
    }

    fun startRecording() {
        if (_state.value.isRecording) return

        Timber.d("Starting audio recording for tutor")
        _state.update { it.copy(error = null) }

        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START_CAPTURE
            context.startService(this)
        }

        audioBuffer.clear()
        _state.update { it.copy(isRecording = true, isListening = true) }

        recordingJob = AudioCaptureService.audioDataFlow
            .filterNotNull()
            .onEach { chunk ->
                audioBuffer.add(chunk.clone())
            }
            .launchIn(viewModelScope + Dispatchers.IO)
            .also { activeJobs.add(it) }
    }

    fun stopRecording() {
        if (!_state.value.isRecording) return

        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP_CAPTURE
            context.startService(this)
        }

        recordingJob?.cancel()
        recordingJob = null
        _state.update { it.copy(isRecording = false, isListening = false, isTranscribing = true) }

        transcriptionJob = viewModelScope.launch {
            try {
                if (audioBuffer.isEmpty()) {
                    _state.update {
                        it.copy(
                            isTranscribing = false,
                            error = "No audio recorded. Please try again."
                        )
                    }
                    return@launch
                }

                val transcript = transcribeAudio()
                if (transcript.isNotBlank()) {
                    _state.update { it.copy(isTranscribing = false) }
                    processUserInput(transcript)
                } else {
                    _state.update {
                        it.copy(
                            isTranscribing = false,
                            error = "Could not transcribe audio. Please try again."
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during tutor transcription")
                _state.update {
                    it.copy(
                        isTranscribing = false,
                        error = "Transcription failed: ${e.message}"
                    )
                }
            }
        }.also { activeJobs.add(it) }
    }

    private suspend fun getSuggestedTopicsWithCache(
        subject: OfflineRAG.Subject,
        grade: Int
    ): List<String> = withContext(Dispatchers.Default) {
        val cacheKey = "${subject.name}-$grade"
        
        Timber.d("Requesting topics for cache key: $cacheKey")
        
        // Check cache first
        topicCache[cacheKey]?.let { cachedTopics ->
            Timber.d("Cache HIT: Returning ${cachedTopics.size} cached topics for $cacheKey")
            return@withContext cachedTopics 
        }
        
        Timber.d("Cache MISS: Loading topics for $cacheKey")
        
        // Load and process topics on background thread
        val allTopics = loadCurriculumForSubject(context, subject)
        val filteredTopics = getSuggestedTopics(allTopics, subject.name, grade)
        
        // Cache the result
        topicCache[cacheKey] = filteredTopics
        Timber.d("CACHED: Stored ${filteredTopics.size} topics for key '$cacheKey'")
        
        filteredTopics
    }

    fun startTutorSession(
        subject: OfflineRAG.Subject,
        sessionType: TutorSessionType,
        topic: String
    ) = viewModelScope.launch {
        try {
            val student = currentStudent
            if (student == null) {
                _state.update { 
                    it.copy(
                        showStudentDialog = true, 
                        pendingSession = PendingSession(subject, sessionType, topic)
                    ) 
                }
                return@launch
            }

            val (tutorId, chatId) = tutorRepository.startTutorSession(
                studentId = student.id,
                subject = subject,
                sessionType = sessionType,
                topic = topic
            )
            currentSessionId = tutorId
            currentChatSessionId = chatId

            conversationContext.clear()
            attemptCount.clear()

            // Use cached topic loading
            Timber.d("Starting tutor session for ${student.name} (Grade ${student.gradeLevel}) - ${subject.name}")
            val suggestedTopics = getSuggestedTopicsWithCache(subject, student.gradeLevel)
            Timber.d("Loaded ${suggestedTopics.size} suggested topics for session")

            _state.update {
                it.copy(
                    currentSubject = subject,
                    sessionType = sessionType,
                    currentTopic = topic,
                    messages = emptyList(),
                    error = null,
                    suggestedTopics = suggestedTopics,
                    showFloatingTopics = suggestedTopics.isNotEmpty(),
                    sessionStartTime = System.currentTimeMillis()
                )
            }
            
            if (suggestedTopics.isNotEmpty()) {
                Timber.d("Floating topics enabled with ${suggestedTopics.size} topics: ${suggestedTopics.take(3).joinToString(", ")}${if (suggestedTopics.size > 3) "..." else ""}")
            } else {
                Timber.w("No suggested topics available - floating topics will be hidden")
            }

            val welcomeMessage = generateWelcomeMessage(student, subject, sessionType)
            addTutorMessage(welcomeMessage, isUser = false)

        } catch (e: Exception) {
            Timber.e(e, "Failed to start tutor session")
            _state.update { it.copy(error = "Failed to start session: ${e.message}") }
        }
    }

    fun bookmarkMessage(messageId: String, tags: List<String> = emptyList()) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(isBookmarked = true, tags = tags)
                    } else msg
                }
            )
        }
        
        viewModelScope.launch {
            try {
                // Bookmark saved in UI state above
                // Repository bookmark functionality not yet implemented
                Timber.d("Message bookmarked: $messageId with tags: $tags")
            } catch (e: Exception) {
                Timber.e(e, "Failed to process bookmark")
            }
        }
    }

    fun toggleAutoSpeak() {
        _state.update { it.copy(autoSpeak = !it.autoSpeak) }
    }

    fun setSpeechRate(rate: Float) {
        _state.update { it.copy(speechRate = rate.coerceIn(0.5f, 2.0f)) }
    }

    fun clearCurrentSubject() {
        _state.update { 
            it.copy(
                currentSubject = null,
                sessionType = null,
                messages = emptyList(),
                currentTopic = "",
                conversationTopic = "",
                conceptMastery = emptyMap(),
                suggestedTopics = emptyList(),
                showFloatingTopics = false,
                error = null
            )
        }
        conversationContext.clear()
        attemptCount.clear()
        currentSessionId = null
        currentChatSessionId = null
    }

    fun dismissStudentDialog() {
        _state.update { it.copy(showStudentDialog = false, pendingSession = null) }
    }

    fun showStudentProfileDialog() {
        _state.update { it.copy(showStudentDialog = true) }
    }

    fun initializeStudent(name: String, grade: Int) {
        viewModelScope.launch {
            try {
                val student = tutorRepository.createOrGetStudentProfile(name, grade)
                currentStudent = student
                _state.update { 
                    it.copy(
                        studentProfile = student,
                        showStudentDialog = false
                    )
                }
                
                // Start pending session if any
                _state.value.pendingSession?.let { pending ->
                    startTutorSession(pending.subject, pending.sessionType, pending.topic)
                    _state.update { it.copy(pendingSession = null) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize student")
                _state.update { 
                    it.copy(
                        error = "Failed to save student profile: ${e.message}",
                        showStudentDialog = false
                    )
                }
            }
        }
    }

    fun toggleFloatingTopics() {
        _state.update { it.copy(showFloatingTopics = !it.showFloatingTopics) }
    }

    fun speakText(text: String) {
        if (text.isNotBlank()) {
            textToSpeechManager.speak(text)
        }
    }

    fun selectTopic(topic: String) {
        Timber.d("Topic selected: '$topic' - hiding floating topics and processing input")
        _state.update { it.copy(showFloatingTopics = false) }
        processUserInput("Tell me about $topic")
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun saveConversationState() {
        // Save conversation state for persistence across app restarts
        // Note: Implementation depends on available state persistence mechanism
        Timber.d("Saving conversation state with ${_state.value.messages.size} messages")
    }

    override fun onCleared() {
        super.onCleared()
        
        // Cancel all active jobs
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        
        recordingJob?.cancel()
        transcriptionJob?.cancel()
        
        // Clear audio buffer
        audioBuffer.clear()
        
        // Save state
        saveConversationState()
        
        // Clear caches
        topicCache.clear()
        
        Timber.d("TutorViewModel cleaned up")
    }

    // Data classes and helper methods...
    data class ConversationEntry(
        val role: String,
        val content: String,
        val concept: String? = null,
        val success: Boolean? = null
    )

    data class CurriculumTopic(
        val title: String,
        val subject: OfflineRAG.Subject,
        val phase: String,
        val gradeRange: String
    )

    data class TutorState(
        val isLoading: Boolean = false,
        val currentSubject: OfflineRAG.Subject? = null,
        val sessionType: TutorSessionType? = null,
        val messages: List<TutorMessage> = emptyList(),
        val isListening: Boolean = false,
        val currentTopic: String = "",
        val lastUserQuestion: String = "",
        val conversationTopic: String = "",
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val suggestedTopics: List<String> = emptyList(),
        val error: String? = null,
        val studentProfile: StudentProfileEntity? = null,
        val conceptMastery: Map<String, Float> = emptyMap(),
        val currentApproach: TeachingApproach = TeachingApproach.SOCRATIC,
        val showFloatingTopics: Boolean = false,
        val currentTopicIndex: Int = 0,
        val showStudentDialog: Boolean = false,
        val pendingSession: PendingSession? = null,
        val sessionStartTime: Long? = null,
        val autoSpeak: Boolean = false,
        val speechRate: Float = 1.0f
    )

    data class TutorMessage(
        val id: String = java.util.UUID.randomUUID().toString(),
        val content: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val status: MessageStatus = MessageStatus.SENT,
        val metadata: MessageMetadata? = null,
        val isBookmarked: Boolean = false,
        val tags: List<String> = emptyList()
    ) {
        data class MessageMetadata(
            val concept: String? = null,
            val explanationType: String? = null,
            val difficulty: String? = null,
            val responseTime: Long? = null,
            val tokensUsed: Int? = null
        )
        
        enum class MessageStatus {
            SENDING, SENT, DELIVERED, FAILED, TYPING
        }
    }

    data class PendingSession(
        val subject: OfflineRAG.Subject,
        val sessionType: TutorSessionType,
        val topic: String
    )

    enum class TeachingApproach {
        SOCRATIC,
        EXPLANATION,
        PROBLEM_SOLVING,
        ENCOURAGEMENT,
        CORRECTION
    }

    data class StudentNeed(
        val type: NeedType,
        val confidence: Float,
        val specificArea: String? = null
    )

    enum class NeedType {
        CLARIFICATION,
        STEP_BY_STEP_HELP,
        CONCEPT_EXPLANATION,
        PRACTICE,
        ENCOURAGEMENT
    }

    // Helper method implementations
    private suspend fun loadMostRecentStudent() {
        try {
            val allStudents = tutorRepository.getAllStudents()
            currentStudent = allStudents.maxByOrNull { it.lastActiveAt }
            _state.update { it.copy(studentProfile = currentStudent) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load recent student")
        }
    }

    private suspend fun initializeSpeechServiceIfNeeded() {
        try {
            // Speech service initialization is optional for tutor functionality
            if (!speechService.isInitialized) {
                // Try to get API key from settings
                val apiKey = context.dataStore.data
                    .map { preferences -> preferences[SPEECH_API_KEY] }
                    .first()
                
                if (!apiKey.isNullOrBlank()) {
                    Timber.d("Initializing speech service with API key from settings")
                    speechService.initializeWithApiKey(apiKey)
                } else {
                    Timber.d("No speech API key found in settings - voice input will be disabled")
                }
            }
        } catch (e: Exception) {
            Timber.w("Speech service initialization failed: ${e.message} - voice input will be disabled")
        }
    }

    private suspend fun transcribeAudio(): String {
        return try {
            if (!speechService.isInitialized) {
                Timber.w("Speech service not initialized - cannot transcribe audio")
                return "Speech recognition not available"
            }
            
            if (audioBuffer.isEmpty()) {
                ""
            } else {
                // Combine audio chunks and transcribe
                val combinedAudio = audioBuffer.flatMap { it.toList() }.toFloatArray()
                // Convert float audio to byte array (PCM format)
                val pcmData = ByteArray(combinedAudio.size * 2)
                for (i in combinedAudio.indices) {
                    val sample = (combinedAudio[i] * 32767).toInt().coerceIn(-32768, 32767)
                    pcmData[i * 2] = (sample and 0xFF).toByte()
                    pcmData[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                }
                speechService.transcribeAudioData(pcmData, "en-US")
            }
        } catch (e: Exception) {
            Timber.e(e, "Transcription failed")
            "Voice input failed - please type your message"
        }
    }

    private suspend fun generateWelcomeMessage(
        student: StudentProfileEntity,
        subject: OfflineRAG.Subject,
        sessionType: TutorSessionType
    ): String {
        val prompt = """
            Generate a warm, encouraging welcome message for ${student.name}, a grade ${student.gradeLevel} student, 
            starting a ${sessionType.name.lowercase().replace('_', ' ')} session in ${subject.name}.
            Keep it brief, age-appropriate, and motivating. Ask what they'd like to learn about.
        """.trimIndent()

        return try {
            gemmaService.generateTextAsync(
                prompt,
                UnifiedGemmaService.GenerationConfig(
                    maxTokens = 150,
                    temperature = 0.7f
                )
            )
        } catch (e: Exception) {
            "Hello ${student.name}! I'm excited to help you learn ${subject.name} today. What would you like to explore?"
        }
    }

    // Helper methods for missing functionality
    private fun extractTopicFromQuestion(input: String): String {
        return input.take(50).trim()
    }

    private fun isFollowUpQuestion(input: String): Boolean {
        val followUpPatterns = listOf("continue", "more", "explain", "what about", "how about", "tell me more")
        return followUpPatterns.any { input.lowercase().contains(it) }
    }

    private fun detectTopicChange(newTopic: String, currentTopic: String): Boolean {
        return newTopic.isNotBlank() && currentTopic.isNotBlank() && newTopic != currentTopic
    }

    private fun analyzeStudentInput(input: String): StudentNeed {
        return when {
            input.lowercase().contains("what is") || input.lowercase().contains("define") ->
                StudentNeed(NeedType.CONCEPT_EXPLANATION, 0.9f)
            input.lowercase().contains("how to") || input.lowercase().contains("steps") ->
                StudentNeed(NeedType.STEP_BY_STEP_HELP, 0.8f)
            input.lowercase().contains("practice") || input.lowercase().contains("examples") ->
                StudentNeed(NeedType.PRACTICE, 0.8f)
            input.contains("?") ->
                StudentNeed(NeedType.CLARIFICATION, 0.7f)
            else ->
                StudentNeed(NeedType.CONCEPT_EXPLANATION, 0.6f)
        }
    }

    private fun extractMainConcept(input: String): String {
        return input.split(" ").take(3).joinToString(" ").trim()
    }

    private fun formatTutorResponse(response: String, student: StudentProfileEntity, approach: TeachingApproach): String {
        return response.trim()
    }

    private fun getMaxTokensForGrade(grade: Int): Int {
        return when {
            grade <= 3 -> 200
            grade <= 6 -> 300
            grade <= 9 -> 400
            else -> 500
        }
    }

    private fun getDifficultyForGrade(grade: Int): Difficulty {
        return when {
            grade <= 3 -> Difficulty.EASY
            grade <= 6 -> Difficulty.MEDIUM
            grade <= 9 -> Difficulty.HARD
            else -> Difficulty.HARD
        }
    }

    private suspend fun trackLearningInteraction(
        student: StudentProfileEntity,
        subject: OfflineRAG.Subject,
        topic: String,
        concept: String,
        studentNeed: StudentNeed,
        previousAttempts: Int,
        isFollowUp: Boolean,
        response: String,
        approach: TeachingApproach
    ) {
        try {
            analyticsRepository.recordInteraction(
                studentId = student.id,
                subject = subject.name,
                topic = topic,
                concept = concept,
                interactionType = InteractionType.QUESTION_ASKED,
                sessionDurationMs = System.currentTimeMillis() - (_state.value.sessionStartTime ?: System.currentTimeMillis()),
                wasCorrect = true,
                attemptsNeeded = previousAttempts + 1,
                difficultyLevel = approach.name
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to track learning interaction")
        }
    }

    private fun buildTutorPrompt(
        student: StudentProfileEntity,
        userInput: String,
        approach: TeachingApproach,
        studentNeed: StudentNeed,
        relevantContent: String,
        concept: String,
        subject: OfflineRAG.Subject,
        sessionType: TutorSessionType,
        conversationTopic: String,
        originalQuestion: String,
        isFollowUp: Boolean
    ): String {
        val gradeLevel = student.gradeLevel
        val approachInstruction = when (approach) {
            TeachingApproach.SOCRATIC -> "Ask guiding questions to help the student discover the answer themselves."
            TeachingApproach.EXPLANATION -> "Provide a clear, direct explanation appropriate for grade $gradeLevel."
            TeachingApproach.PROBLEM_SOLVING -> "Break down the problem into manageable steps."
            TeachingApproach.ENCOURAGEMENT -> "Be supportive and encouraging while gently correcting any misconceptions."
            TeachingApproach.CORRECTION -> "Gently correct the misconception and provide the accurate information."
        }

        return """
            You are an AI tutor helping ${student.name}, a grade $gradeLevel student, with ${subject.name}.
            
            Teaching approach: $approachInstruction
            Student's question: "$userInput"
            ${if (conversationTopic.isNotBlank()) "Current topic: $conversationTopic" else ""}
            ${if (isFollowUp) "This is a follow-up to: $originalQuestion" else ""}
            
            Relevant knowledge:
            $relevantContent
            
            Instructions:
            - Keep responses appropriate for grade $gradeLevel (age ${gradeLevel + 5})
            - Use simple, clear language
            - Include examples when helpful
            - Be encouraging and patient
            - Focus on understanding, not just answers
            
            Respond now:
        """.trimIndent()
    }

    private fun loadCurriculumForSubject(context: Context, subject: OfflineRAG.Subject): List<String> {
        return try {
            Timber.d("Loading curriculum for subject: ${subject.name}")
            
            // Load from assets or cached curriculum
            val defaultTopics = when (subject) {
                OfflineRAG.Subject.MATHEMATICS -> listOf(
                    "Addition and Subtraction", "Multiplication and Division", "Fractions", 
                    "Decimals", "Geometry", "Algebra", "Statistics"
                )
                OfflineRAG.Subject.SCIENCE -> listOf(
                    "Life Science", "Physical Science", "Earth Science", "Chemistry", 
                    "Physics", "Biology", "Environmental Science"
                )
                OfflineRAG.Subject.ENGLISH -> listOf(
                    "Reading Comprehension", "Grammar", "Writing", "Literature", 
                    "Poetry", "Essays", "Vocabulary"
                )
                OfflineRAG.Subject.HISTORY -> listOf(
                    "Ancient History", "World History", "American History", "European History",
                    "Modern History", "Cultural Studies", "Government"
                )
                OfflineRAG.Subject.GEOGRAPHY -> listOf(
                    "Physical Geography", "Human Geography", "World Regions", "Climate",
                    "Natural Resources", "Maps and Navigation", "Cultural Geography"
                )
                OfflineRAG.Subject.ECONOMICS -> listOf(
                    "Basic Economics", "Supply and Demand", "Markets", "Money and Banking",
                    "International Trade", "Government Economics", "Personal Finance"
                )
                else -> listOf("General Topics", "Basic Concepts", "Advanced Topics")
            }
            
            Timber.d("Loaded ${defaultTopics.size} curriculum topics for ${subject.name}: ${defaultTopics.joinToString(", ")}")
            defaultTopics
        } catch (e: Exception) {
            Timber.e(e, "Failed to load curriculum for ${subject.name}")
            emptyList()
        }
    }

    private fun getSuggestedTopics(allTopics: List<String>, subjectName: String, grade: Int): List<String> {
        Timber.d("Filtering topics for $subjectName, grade $grade from ${allTopics.size} available topics")
        
        val filteredTopics = allTopics.take(10) // Return top 10 topics for the grade level
        
        Timber.d("Suggested topics for grade $grade $subjectName: ${filteredTopics.joinToString(", ")}")
        return filteredTopics
    }
}

// Add this data class for state persistence
data class TutorConversationState(
    val messages: List<TutorViewModel.TutorMessage>,
    val currentTopic: String,
    val conversationTopic: String,
    val conceptMastery: Map<String, Float>,
    val conversationContext: List<TutorViewModel.ConversationEntry>,
    val attemptCount: Map<String, Int>
)