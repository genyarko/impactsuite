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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import org.json.JSONArray
import org.json.JSONObject
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Add audio recording properties
    private var recordingJob: Job? = null
    private val audioBuffer = mutableListOf<FloatArray>()

    private val _state = MutableStateFlow(TutorState())
    val state: StateFlow<TutorState> = _state.asStateFlow()

    private var currentStudent: StudentProfileEntity? = null
    private var currentSessionId: String? = null
    private var currentChatSessionId: String? = null
    private var conversationContext = mutableListOf<ConversationEntry>()
    private var attemptCount = mutableMapOf<String, Int>() // Track attempts per concept

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
        val lastUserQuestion: String = "", // Track original question for context
        val conversationTopic: String = "", // Current discussion topic (e.g., "forms of government")
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
        val sessionStartTime: Long? = null

    )

    data class PendingSession(
        val subject: OfflineRAG.Subject,
        val sessionType: TutorSessionType,
        val topic: String
    )

    enum class TeachingApproach {
        SOCRATIC,        // Ask guiding questions
        EXPLANATION,     // Direct teaching
        PROBLEM_SOLVING, // Step-by-step guidance
        ENCOURAGEMENT,   // Motivational support
        CORRECTION      // Fixing misconceptions
    }

    init {
        // Check and initialize speech service if needed
        viewModelScope.launch {
            initializeSpeechServiceIfNeeded()
            // Try to load the most recent student profile
            loadMostRecentStudent()
        }
    }

    fun toggleFloatingTopics() {
        _state.update { it.copy(showFloatingTopics = !it.showFloatingTopics) }
    }

    private suspend fun initializeSpeechServiceIfNeeded() {
        if (!speechService.isInitialized) {
            Timber.w("SpeechRecognitionService is not initialized - attempting to initialize")
            try {
                // Check if the service can be initialized
                _state.update {
                    it.copy(error = "Voice transcription is initializing... Please try again in a moment.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize SpeechRecognitionService")
                _state.update {
                    it.copy(error = "Voice transcription is not available. Please check your API settings in the app configuration.")
                }
            }
        } else {
            Timber.d("SpeechRecognitionService is already initialized and ready")
        }
    }

    fun startRecording() {
        if (_state.value.isRecording) return

        Timber.d("Starting audio recording for tutor")

        // Clear any previous errors
        _state.update { it.copy(error = null) }

        // Start audio capture service
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
                Timber.v("Tutor audio buffer size: ${audioBuffer.size}")
            }
            .launchIn(viewModelScope + Dispatchers.IO)

        Timber.d("Tutor recording started, collecting audio data...")
    }

    fun stopRecording() {
        if (!_state.value.isRecording) return

        Timber.d("Stopping tutor audio recording")

        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP_CAPTURE
            context.startService(this)
        }

        recordingJob?.cancel()
        recordingJob = null
        _state.update { it.copy(isRecording = false, isListening = false, isTranscribing = true) }

        viewModelScope.launch {
            try {
                Timber.d("Tutor audio buffer contains ${audioBuffer.size} chunks")

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
                    Timber.d("Tutor transcription successful: $transcript")
                    _state.update { it.copy(isTranscribing = false) }
                    // Process the transcribed text as user input
                    processUserInput(transcript)
                } else {
                    Timber.w("Tutor transcription returned empty result")
                    _state.update {
                        it.copy(
                            isTranscribing = false,
                            error = "Could not transcribe audio. Please try again or check your internet connection."
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
        }
    }

    private suspend fun transcribeAudio(): String {
        if (!speechService.isInitialized) {
            Timber.e("SpeechRecognitionService is not initialized for tutor")
            _state.update {
                it.copy(error = "Voice transcription is not available. Please check API settings.")
            }
            return ""
        }

        try {
            // Convert float audio data to PCM16 byte array
            val data = audioBuffer.flatMap { it.asList() }.toFloatArray()
            Timber.d("Processing ${data.size} audio samples for tutor")

            if (data.isEmpty()) {
                Timber.w("No audio data to transcribe for tutor")
                return ""
            }

            val pcm = ByteArray(data.size * 2)
            data.forEachIndexed { i, f ->
                val v = (f.coerceIn(-1f, 1f) * 32767).toInt()
                pcm[i * 2] = (v and 0xFF).toByte()
                pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }

            Timber.d("Sending ${pcm.size} bytes to speech service for tutor transcription")
            val result = speechService.transcribeAudioData(pcm, "en-US")
            Timber.d("Tutor transcription result: $result")

            return result
        } catch (e: Exception) {
            Timber.e(e, "Error in tutor transcribeAudio")
            throw e
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun speakText(text: String) {
        textToSpeechManager.speak(text)
    }

    override fun onCleared() {
        super.onCleared()
        // TextToSpeechManager is a singleton and will be cleaned up by Hilt
        // No need to manually call shutdown here
    }


    private fun getSuggestedTopics(
        allTopics: List<CurriculumTopic>,
        subject: String,
        grade: Int
    ): List<String> {
        Timber.d("Getting suggested topics for subject: $subject, grade: $grade")
        Timber.d("Total topics to filter: ${allTopics.size}")
        
        val filteredTopics = allTopics.filter {
            val subjectMatches = it.subject.name.equals(subject, ignoreCase = true)
            val gradeMatches = matchesGradeRange(it.gradeRange, grade)
            
            Timber.v("Topic '${it.title}': subject='${it.subject.name}' matches=$subjectMatches, grade='${it.gradeRange}' matches=$gradeMatches")
            
            subjectMatches && gradeMatches
        }
        
        Timber.d("Filtered ${filteredTopics.size} topics from ${allTopics.size} total")
        
        val topicTitles = filteredTopics.map { it.title }
        
        // Apply age-appropriate topic filtering and prioritization
        val ageAppropriateTopics = prioritizeTopicsForAge(topicTitles, grade)
        
        // If no topics found for specific grade, try to get some general topics from the subject
        if (ageAppropriateTopics.isEmpty() && allTopics.isNotEmpty()) {
            Timber.w("No topics found for grade $grade, falling back to general topics")
            val fallbackTopics = allTopics
                .filter { it.subject.name.equals(subject, ignoreCase = true) }
                .map { it.title }
                .let { prioritizeTopicsForAge(it, grade) }
                .take(5) // Get first 5 topics regardless of grade
            Timber.d("Fallback: found ${fallbackTopics.size} general topics")
            return fallbackTopics
        }
        
        return ageAppropriateTopics
    }

    private fun prioritizeTopicsForAge(topics: List<String>, grade: Int): List<String> {
        if (topics.isEmpty()) return topics
        
        // Apply topic simplification and prioritization for ALL grade levels
        // Different scoring strategies based on age groups
        return topics
            .sortedWith { topic1, topic2 ->
                // Always prioritize simpler, more focused topics for better AI explanations
                val score1 = calculateSimplicityScore(topic1, grade)
                val score2 = calculateSimplicityScore(topic2, grade)
                score2.compareTo(score1) // Higher score = simpler/more focused = appears first
            }
            .take(getMaxTopicsForGrade(grade)) // Age-appropriate topic limits
    }

    private fun getMaxTopicsForGrade(grade: Int): Int {
        return when {
            grade <= 3 -> 6   // Very young: fewer, simpler topics
            grade <= 5 -> 8   // Elementary: moderate number of topics
            grade == 6 -> 10  // Grade 6: Enhanced topic variety for transitional learners  
            grade <= 9 -> 10  // Middle school: more topics, still manageable
            grade <= 12 -> 12 // High school: maximum topics for variety
            else -> 10        // Default fallback
        }
    }

    private fun calculateSimplicityScore(topic: String, grade: Int): Int {
        var score = 0
        val lowerTopic = topic.lowercase()
        val wordCount = topic.split(" ").size
        
        // 1. LENGTH SCORING - Always favor concise topics for better AI explanations
        score += when {
            topic.length <= 8 -> 40   // Very short - excellent for floating bubbles
            topic.length <= 12 -> 30  // Short - good for bubbles
            topic.length <= 18 -> 20  // Medium - acceptable
            topic.length <= 25 -> 10  // Long - less ideal
            else -> -5                // Very long - avoid for bubbles
        }
        
        // 2. WORD COUNT SCORING - Favor focused, single-concept topics
        score += when {
            wordCount == 1 -> 35      // Single word - perfect focus
            wordCount == 2 -> 25      // Two words - good focus  
            wordCount == 3 -> 15      // Three words - acceptable
            wordCount == 4 -> 5       // Four words - getting complex
            else -> -10               // Too many words - hard to explain concisely
        }
        
        // 3. GRADE-APPROPRIATE VOCABULARY SCORING
        when {
            grade <= 5 -> {
                // Elementary: Prioritize basic, concrete concepts
                val elementaryWords = listOf("math", "science", "plants", "animals", "water", "food", 
                                           "home", "family", "colors", "shapes", "numbers", "letters",
                                           "reading", "writing", "art", "music", "sports", "games",
                                           "weather", "seasons", "counting", "adding", "rocks", "earth")
                score += elementaryWords.count { lowerTopic.contains(it) } * 15
                
                // Heavy penalty for complex terms
                val complexTerms = listOf("analysis", "synthesis", "theoretical", "paradigm", 
                                        "methodology", "infrastructure", "interdisciplinary")
                score += complexTerms.count { lowerTopic.contains(it) } * -25
            }
            
            grade == 6 -> {
                // Grade 6: Transition to more academic vocabulary while keeping accessibility
                val grade6Words = listOf("patterns", "systems", "energy", "matter", "equations", 
                                       "fractions", "geometry", "history", "culture", "government",
                                       "geography", "climate", "trade", "science", "math", "reading",
                                       "writing", "literature", "experiments", "forces", "planets")
                score += grade6Words.count { lowerTopic.contains(it) } * 12
                
                // Moderate penalty for overly complex terms, but more lenient than elementary
                val complexTerms = listOf("theoretical", "paradigm", "methodology", 
                                        "infrastructure", "epistemological", "philosophical")
                score += complexTerms.count { lowerTopic.contains(it) } * -15
            }
            
            grade <= 9 -> {
                // Middle School: Balance simple and academic concepts
                val middleSchoolWords = listOf("patterns", "systems", "energy", "forces", "matter",
                                             "equations", "fractions", "geometry", "history", "culture",
                                             "geography", "economy", "government", "literature")
                score += middleSchoolWords.count { lowerTopic.contains(it) } * 12
                
                // Moderate penalty for overly complex terms
                val complexTerms = listOf("philosophical", "epistemological", "phenomenological", 
                                        "interdisciplinary", "multidisciplinary")
                score += complexTerms.count { lowerTopic.contains(it) } * -15
            }
            
            else -> {
                // High School: Can handle more complex terms but still favor clarity
                val highSchoolWords = listOf("analysis", "theory", "concepts", "principles", 
                                           "mechanisms", "processes", "functions", "structures",
                                           "relationships", "applications", "methods")
                score += highSchoolWords.count { lowerTopic.contains(it) } * 8
                
                // Light penalty for extremely abstract terms
                val veryComplexTerms = listOf("epistemological", "phenomenological", "hermeneutical")
                score += veryComplexTerms.count { lowerTopic.contains(it) } * -8
            }
        }
        
        // 4. SUBJECT-SPECIFIC CLARITY BONUS
        // Favor topics that are clear, specific concepts rather than vague descriptions
        val scienceConcepts = listOf("photosynthesis", "gravity", "ecosystem", "cells", "atoms", 
                                   "energy", "matter", "forces", "light", "sound", "magnets")
        val mathConcepts = listOf("algebra", "fractions", "geometry", "numbers", "patterns", 
                                "measurement", "shapes", "graphs", "equations", "decimals")
        val englishConcepts = listOf("poetry", "grammar", "writing", "reading", "literature", 
                                   "spelling", "vocabulary", "stories", "essays", "sentences")
        val historyConcepts = listOf("democracy", "government", "culture", "civilization", 
                                   "timeline", "events", "leaders", "wars", "empires", "revolution")
        val geographyConcepts = listOf("maps", "climate", "mountains", "rivers", "countries", 
                                     "continents", "weather", "population", "cities", "environment")
        val economicsConcepts = listOf("trade", "money", "markets", "supply", "demand", "business", 
                                     "resources", "production", "consumption", "jobs", "income")
        
        val allClearConcepts = scienceConcepts + mathConcepts + englishConcepts + 
                              historyConcepts + geographyConcepts + economicsConcepts
        score += allClearConcepts.count { lowerTopic.contains(it) } * 10
        
        // 5. AVOID COMPOUND/MULTI-CONCEPT TOPICS (should be handled by parsing, but double-check)
        val compoundIndicators = listOf(" and ", " or ", " vs ", " versus ", ",", "&", "—", "–")
        val compoundPenalty = compoundIndicators.count { topic.contains(it, ignoreCase = true) } * -20
        score += compoundPenalty
        
        // 6. BONUS FOR ACTION/CONCRETE TOPICS (easier to explain)
        val actionWords = listOf("making", "building", "creating", "solving", "exploring", 
                               "measuring", "observing", "comparing", "classifying")
        score += actionWords.count { lowerTopic.contains(it) } * 8
        
        return score
    }

    private fun matchesGradeRange(phase: String, grade: Int): Boolean {
        Timber.d("Matching phase '$phase' with grade $grade")

        return when {
            // Handle simple single grade (e.g., "6", "7", "5")
            phase.matches(Regex("^\\d+$")) -> {
                val phaseGrade = phase.toInt()
                val matches = phaseGrade == grade
                Timber.d("Single digit grade match for '$phase': $matches")
                matches
            }

            // Handle grade ranges (e.g., "1-2", "3-4", "9-10", "11-12")
            phase.matches(Regex("^\\d+-\\d+$")) -> {
                val parts = phase.split("-")
                if (parts.size == 2) {
                    val startGrade = parts[0].toInt()
                    val endGrade = parts[1].toInt()
                    val matches = grade in startGrade..endGrade
                    Timber.d("Hyphenated grade range match for '$phase': $matches (range: $startGrade-$endGrade)")
                    matches
                } else {
                    false
                }
            }

            // Handle Kindergarten
            phase.equals("K", ignoreCase = true) && grade == 0 -> {
                Timber.d("Kindergarten match for grade $grade: true")
                true
            }

            // Handle grade ranges with parentheses like "Phase 4 (Grades 5–6)" or "Grades 5-6"
            phase.contains("Grades", ignoreCase = true) -> {
                // Enhanced regex to handle various dash types and formats
                val gradeRangeRegex = Regex("""Grades?\s*(\d+)[\s–\-~]+(\d+)""", RegexOption.IGNORE_CASE)
                val match = gradeRangeRegex.find(phase)
                
                if (match != null) {
                    val startGrade = match.groupValues[1].toInt()
                    val endGrade = match.groupValues[2].toInt()
                    val matches = grade in startGrade..endGrade
                    Timber.d("Grade range match for '$phase': $matches (range: $startGrade-$endGrade)")
                    matches
                } else {
                    // Fallback to original number extraction
                    val regex = Regex("""\d+""")
                    val numbers = regex.findAll(phase).map { it.value.toInt() }.toList()
                    val matches = when (numbers.size) {
                        0 -> false
                        1 -> numbers[0] == grade
                        else -> grade in numbers[0]..numbers[1]
                    }
                    Timber.d("Fallback grade range match for '$phase': $matches (numbers: $numbers)")
                    matches
                }
            }

            // Handle single grade mentions (e.g., "Grade 6", "Grade 6)")
            phase.contains("Grade $grade", ignoreCase = true) -> {
                Timber.d("Single grade match for '$phase': true")
                true
            }

            // Handle patterns like "(Grade 6)" or "Grade 6)"
            Regex("""\(?Grade\s*$grade\)?""", RegexOption.IGNORE_CASE).containsMatchIn(phase) -> {
                Timber.d("Pattern grade match for '$phase': true")
                true
            }

            // Handle KG (Kindergarten)
            phase.contains("KG", ignoreCase = true) && grade == 0 -> {
                Timber.d("KG match for grade $grade: true")
                true
            }

            // Handle MYP (Middle Years Programme) with specific grades
            phase.contains("MYP 1", ignoreCase = true) && grade == 6 -> {
                Timber.d("MYP 1 match for grade $grade: true")
                true
            }
            phase.contains("MYP 2", ignoreCase = true) && grade == 7 -> {
                Timber.d("MYP 2 match for grade $grade: true")
                true
            }
            phase.contains("MYP 3", ignoreCase = true) && grade == 8 -> {
                Timber.d("MYP 3 match for grade $grade: true")
                true
            }
            phase.contains("MYP 4", ignoreCase = true) && grade in 9..10 -> {
                Timber.d("MYP 4 match for grade $grade: true")
                true
            }
            phase.contains("MYP 5", ignoreCase = true) && grade == 10 -> {
                Timber.d("MYP 5 match for grade $grade: true")
                true
            }

            // Handle PYP (Primary Years Programme) phases
            phase.contains("Phase 1", ignoreCase = true) && grade in 1..2 -> {
                Timber.d("PYP Phase 1 match for grade $grade: true")
                true
            }
            phase.contains("Phase 2", ignoreCase = true) && grade in 2..3 -> {
                Timber.d("PYP Phase 2 match for grade $grade: true")
                true
            }
            phase.contains("Phase 3", ignoreCase = true) && grade in 3..4 -> {
                Timber.d("PYP Phase 3 match for grade $grade: true")
                true
            }
            phase.contains("Phase 4", ignoreCase = true) && grade in 5..6 -> {
                Timber.d("PYP Phase 4 match for grade $grade: true")
                true
            }

            // Handle DP subjects (Diploma Programme - Grades 11-12)
            (phase == "Biology" || phase == "Chemistry" || phase == "Physics" ||
                    phase == "ESS" || phase == "SEHS" || phase.contains("AA") || phase.contains("AI"))
                    && grade in 11..12 -> {
                Timber.d("DP subject match for grade $grade: true")
                true
            }

            else -> {
                Timber.d("No match for phase '$phase' with grade $grade")
                false
            }
        }
    }


    fun selectTopic(topic: String) {
        // Hide the floating topics
        _state.update { it.copy(showFloatingTopics = false) }

        // Process the topic as user input
        processUserInput("I want to learn about $topic")
    }



    fun processUserInput(input: String) = viewModelScope.launch {
        addTutorMessage(input, isUser = true)
        _state.update { it.copy(isLoading = true) }

        try {
            val student = currentStudent ?: throw IllegalStateException("No student profile")
            val subject = _state.value.currentSubject ?: throw IllegalStateException("No subject selected")
            val sessionType = _state.value.sessionType ?: throw IllegalStateException("No session type")

            // Context management
            val currentTopic = _state.value.conversationTopic
            val newTopic = extractTopicFromQuestion(input)
            val isFollowUp = isFollowUpQuestion(input)
            val isTopicChange = !isFollowUp && detectTopicChange(newTopic, currentTopic)

            // Enhanced context correlation - preserve conversation topic more aggressively
            val updatedTopic = when {
                isFollowUp -> {
                    Timber.d("Follow-up detected, preserving topic: $currentTopic")
                    currentTopic // Keep current topic for follow-ups
                }
                currentTopic.isEmpty() -> {
                    Timber.d("First question, setting topic: $newTopic")
                    newTopic // First question
                }
                isTopicChange -> {
                    Timber.d("Topic change detected: $currentTopic -> $newTopic")
                    newTopic // Explicit topic change
                }
                else -> {
                    Timber.d("Continuing current topic: $currentTopic")
                    currentTopic // Continue current topic
                }
            }

            // Update state with context information
            _state.update { 
                it.copy(
                    conversationTopic = updatedTopic,
                    lastUserQuestion = if (!isFollowUp) input else it.lastUserQuestion
                ) 
            }

            // Clear context if topic changed
            if (isTopicChange) {
                conversationContext.clear()
                attemptCount.clear()
                Timber.d("Topic changed from '$currentTopic' to '$newTopic' - clearing context")
            }

            // Analyze student input and context
            val studentNeed = analyzeStudentInput(input)
            val concept = extractMainConcept(input)
            val previousAttempts = attemptCount[concept] ?: 0

            // Determine teaching approach based on context
            val approach = determineTeachingApproach(
                studentNeed = studentNeed,
                previousAttempts = previousAttempts,
                recentHistory = conversationContext.takeLast(3)
            )

            // Get relevant content
            val relevantContent = offlineRAG.queryWithContext(
                query = input,
                subject = subject
            )

            // Generate response using appropriate prompt method
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
            val isContinuation = input.lowercase().let { 
                it.contains("continue") || it.contains("finish") || it.contains("complete") || 
                it.contains("go on") || (it.contains("more") && it.length < 20)
            }
            
            // Format and add response
            val baseResponse = formatTutorResponse(response, student, approach)
            val formattedResponse = ensureCompleteResponse(baseResponse, student.gradeLevel)
            
            // Handle continuation by concatenating with previous response
            val currentMessages = _state.value.messages
            if (isContinuation && currentMessages.isNotEmpty()) {
                val lastAiMessage = currentMessages.lastOrNull { !it.isUser }
                if (lastAiMessage != null) {
                    // Remove the last AI message and replace with concatenated version
                    val updatedMessages = currentMessages.toMutableList()
                    updatedMessages.removeAll { message: TutorMessage -> !message.isUser && message.id == lastAiMessage.id }
                    
                    val concatenatedResponse = lastAiMessage.content + " " + formattedResponse
                    addTutorMessage(concatenatedResponse, isUser = false, metadata = TutorMessage.MessageMetadata(
                        concept = concept,
                        explanationType = approach.name,
                        difficulty = getDifficultyForGrade(student.gradeLevel).name
                    ))
                    
                    // Update messages state with the new list
                    _state.update { it.copy(messages = updatedMessages) }
                } else {
                    addTutorMessage(formattedResponse, isUser = false, metadata = TutorMessage.MessageMetadata(
                        concept = concept,
                        explanationType = approach.name,
                        difficulty = getDifficultyForGrade(student.gradeLevel).name
                    ))
                }
            } else {
                addTutorMessage(formattedResponse, isUser = false, metadata = TutorMessage.MessageMetadata(
                    concept = concept,
                    explanationType = approach.name,
                    difficulty = getDifficultyForGrade(student.gradeLevel).name
                ))
            }

            // Track learning interaction for analytics
            val sessionDuration = System.currentTimeMillis() - (_state.value.sessionStartTime ?: System.currentTimeMillis())
            val interactionType = when {
                isFollowUp -> InteractionType.FOLLOW_UP_QUESTION
                studentNeed.type == NeedType.STEP_BY_STEP_HELP -> InteractionType.HELP_REQUESTED
                else -> InteractionType.QUESTION_ASKED
            }
            
            // Record interaction asynchronously
            viewModelScope.launch {
                try {
                    analyticsRepository.recordInteraction(
                        studentId = student.id,
                        subject = subject.name,
                        topic = updatedTopic,
                        concept = concept,
                        interactionType = interactionType,
                        sessionDurationMs = sessionDuration,
                        responseQuality = calculateResponseQuality(formattedResponse, approach),
                        difficultyLevel = getDifficultyForGrade(student.gradeLevel).name,
                        attemptsNeeded = previousAttempts + 1,
                        helpRequested = studentNeed.type == NeedType.STEP_BY_STEP_HELP,
                        followUpQuestions = if (isFollowUp) 1 else 0
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to record learning interaction")
                }
            }

            // Update tracking
            attemptCount[concept] = previousAttempts + 1
            conversationContext.add(ConversationEntry("Student", input, concept))
            conversationContext.add(ConversationEntry("Tutor", formattedResponse, concept, true))

            // Update state with current approach
            _state.update { it.copy(currentApproach = approach) }

        } catch (e: Exception) {
            Timber.e(e, "Error processing input")
            _state.update { it.copy(
                isLoading = false,
                error = "Error: ${e.message}"
            )}
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
    }

    // Add this method to generate properly structured responses:
    private fun structureResponseForApproach(
        content: String,
        approach: TeachingApproach,
        concept: String,
        student: StudentProfileEntity
    ): String {
        return when (approach) {
            TeachingApproach.PROBLEM_SOLVING -> {
                val steps = listOf(
                    "Understand what we're looking for",
                    "Identify what we know",
                    "Apply the right method",
                    "Check our answer"
                )
                TutorResponseFormatter.formatStepsResponse(
                    steps = steps,
                    intro = "Let's solve this step by step:",
                    student = student
                )
            }

            TeachingApproach.EXPLANATION -> {
                val keyPoints = extractKeyPoints(content)
                TutorResponseFormatter.formatListResponse(
                    items = keyPoints,
                    intro = "Here are the key points about $concept:",
                    explanations = null,
                    student = student
                )
            }

            else -> content.formatForTutor(student)
        }
    }

    // In getAdaptiveHintPrompt response processing:
    fun formatHintResponse(
        hint: String,
        hintLevel: Int,
        student: StudentProfileEntity
    ): String {
        return when (hintLevel) {
            in 3..5 -> {
                // Higher level hints often have steps
                val steps = hint.split("\n").filter { it.isNotBlank() }
                if (steps.size > 1) {
                    TutorResponseFormatter.formatStepsResponse(
                        steps = steps,
                        intro = "Here's how to approach this:",
                        student = student
                    )
                } else {
                    hint.formatForTutor(student)
                }
            }
            else -> hint.formatForTutor(student)
        }
    }

    /**
     * Generate an adaptive tutor response tailored to the student’s grade, need and chosen approach.
     */
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
        // 1. Extra guidance for very young learners (Grade 3 or below + EXPLANATION approach)
        val isYoungStudent = student.gradeLevel <= 3
        val gradeInstruction = when (student.gradeLevel) {
            in 1..3 -> {
                "\n!!CRITICAL!! GRADES 1-3: RESPONSE MUST START WITH \"[Topic] is...\" " +
                "FORBIDDEN: \"Okay!\", \"Great!\", \"Well\", \"So\", \"Let's\", \"Here's\" " +
                "Structure: Direct answer (1 sentence) → Simple explanation (2-3 sentences) → One concrete example from their world. " +
                "NO follow-up questions. Use very simple words. Max ${getMaxWordsForGrade(student.gradeLevel)} words."
            }
            in 4..6 -> {
                "\n!!CRITICAL!! GRADES 4-6: RESPONSE MUST START WITH \"[Topic] is...\" " +
                "FORBIDDEN: \"Okay!\", \"Great!\", \"Well\", \"So\", \"Let's\", \"Here's\" " +
                "Structure: Direct answer (1 sentence) → Clear explanation with 2 key points → One simple real-world example. " +
                "NO critical thinking questions. Be factual like a textbook. Max ${getMaxWordsForGrade(student.gradeLevel)} words."
            }
            in 7..8 -> {
                "\n!!CRITICAL!! GRADES 7-8: RESPONSE MUST START WITH \"[Topic] is...\" " +
                "FORBIDDEN: \"Okay!\", \"Great!\", \"Well\", \"So\", \"Let's\", \"Here's\" " +
                "Structure: Direct answer (1 sentence) → Structured explanation with 2-3 key points → One example with connection → " +
                "ONE simple follow-up question to encourage thinking. Max ${getMaxWordsForGrade(student.gradeLevel)} words."
            }
            else -> {
                "\n!!CRITICAL!! GRADES 9-12: RESPONSE MUST START WITH \"[Topic] is...\" " +
                "FORBIDDEN: \"Okay!\", \"Great!\", \"Well\", \"So\", \"Let's\", \"Here's\" " +
                "Structure: Direct answer (1 sentence) → Comprehensive explanation with multiple aspects → Real-world applications → " +
                "1-2 analytical questions for critical thinking. Max ${getMaxWordsForGrade(student.gradeLevel)} words."
            }
        }

        // 2. Build the core prompt for the chosen teaching approach
        val corePrompt = when (approach) {
            TeachingApproach.SOCRATIC -> promptManager.getSocraticPrompt(
                subject = subject,
                concept = concept,
                studentGrade = student.gradeLevel,
                studentQuestion = userInput
            )

            TeachingApproach.EXPLANATION -> promptManager.getConceptExplanationPrompt(
                subject = subject,
                concept = concept,
                studentGrade = student.gradeLevel,
                learningStyle = student.preferredLearningStyle,
                depth = getDepthForGrade(student.gradeLevel)
            )

            TeachingApproach.PROBLEM_SOLVING -> {
                val studentApproach = extractStudentApproach(userInput)
                promptManager.getProblemSolvingPrompt(
                    problemType = concept,
                    subject = subject,
                    difficulty = getDifficultyForGrade(student.gradeLevel),
                    studentApproach = studentApproach
                )
            }

            TeachingApproach.ENCOURAGEMENT -> {
                val struggleArea = identifyStruggleArea(conversationContext)
                promptManager.getEncouragementPrompt(
                    context = "Learning $concept in $subject",
                    struggleArea = struggleArea,
                    previousAttempts = attemptCount[concept] ?: 0
                )
            }

            TeachingApproach.CORRECTION -> {
                val misconception = extractMisconception(userInput, conversationContext)
                promptManager.getMisconceptionCorrectionPrompt(
                    misconception = misconception,
                    correctConcept = concept,
                    subject = com.example.mygemma3n.feature.quiz.Subject.valueOf(subject.name)
                )
            }
        }

        // 3. Combine any grade‑specific instruction with the core prompt
        val prompt = if (gradeInstruction.isNotEmpty()) {
            "$gradeInstruction\n\n$corePrompt"
        } else {
            corePrompt
        }

        // 4. Build context information (max 50 tokens)
        val contextInfo = buildString {
            if (conversationTopic.isNotEmpty()) {
                append("CURRENT TOPIC: $conversationTopic\n")
            }
            if (isFollowUp && originalQuestion.isNotEmpty()) {
                append("ORIGINAL QUESTION: ${originalQuestion.take(100)}\n")
                append("USER IS ASKING FOR: Examples/clarification about $conversationTopic\n")
            }
        }

        // 5. Assemble full prompt with context injection
        val fullPrompt = """
        $contextInfo$prompt

        Educational Context:
        $relevantContent

        Recent conversation:
        ${formatRecentHistory()}

        Student's current input: "$userInput"

        !!CRITICAL!! RESPONSE MUST START EXACTLY WITH: "[Topic] is..." or "[Topic] means..."
        
        ${if (isFollowUp) {
            "!!FOLLOW-UP DETECTED!! Continue discussing $conversationTopic. " +
            "Provide examples, clarification, or additional details about $conversationTopic ONLY. " +
            "DO NOT switch topics. Reference your previous explanation about $conversationTopic."
        } else ""}
        
        !!ABSOLUTELY FORBIDDEN!! DO NOT START WITH:
        - "Okay!" - "Great!" - "Well" - "So" - "Let's" - "Here's" - "To give you" - "I can definitely help"
        - "fascinating" - "amazing" - "wonderful" - "complex" - "structured overview"
        
        EXAMPLES:
        WRONG: "Okay! I can definitely help learn about different forms of government..."
        RIGHT: "Forms of government are different ways that countries organize their political systems..."
        
        Be direct and factual like a dictionary definition. ${getMaxWordsForGrade(student.gradeLevel)} words total.
    """.trimIndent()

        // 5. Generate and return Gemma output
        return gemmaService.generateTextAsync(
            fullPrompt,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = getMaxTokensForGrade(student.gradeLevel),
                temperature = when (approach) {
                    TeachingApproach.SOCRATIC     -> 0.6f
                    TeachingApproach.EXPLANATION  -> 0.4f
                    TeachingApproach.PROBLEM_SOLVING -> 0.3f
                    TeachingApproach.ENCOURAGEMENT -> 0.7f
                    TeachingApproach.CORRECTION   -> 0.5f
                }
            )
        )
    }

    private fun determineTeachingApproach(
        studentNeed: StudentNeed,
        previousAttempts: Int,
        recentHistory: List<ConversationEntry>
    ): TeachingApproach {
        val student = currentStudent
        val isYoungStudent = student?.gradeLevel?.let { it <= 3 } ?: false

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

    // Helper methods
    private fun extractMainConcept(input: String): String {
        // Extract concept from "what is X" questions
        val whatIsPattern = Regex("what\\s+(is|are|was|were)\\s+(?:a|an|the)?\\s*(.+?)\\??", RegexOption.IGNORE_CASE)
        whatIsPattern.find(input)?.let { match ->
            return match.groupValues[2].trim().lowercase()
        }

        // Existing concept extraction
        return when {
            input.contains("planet", ignoreCase = true) -> "planet"
            input.contains("balanced diet", ignoreCase = true) -> "balanced diet"
            input.contains("matter", ignoreCase = true) -> "matter"
            input.contains("trapezoid", ignoreCase = true) -> "area of trapezoid"
            input.contains("equation", ignoreCase = true) -> "equations"
            else -> "general concept"
        }
    }

    private fun extractStudentApproach(input: String): String? {
        return when {
            input.contains("I tried", ignoreCase = true) ->
                input.substringAfter("I tried").take(50)
            input.contains("my answer", ignoreCase = true) ->
                input.substringAfter("my answer").take(50)
            else -> null
        }
    }

    private fun identifyStruggleArea(history: List<ConversationEntry>): String? {
        val recentConcepts = history.mapNotNull { it.concept }.takeLast(3)
        return recentConcepts.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
    }

    private fun extractMisconception(input: String, history: List<ConversationEntry>): String {
        return when {
            input.contains("I thought", ignoreCase = true) ->
                input.substringAfter("I thought").take(50)
            input.contains("but", ignoreCase = true) ->
                input.substringAfter("but").take(50)
            else -> "general confusion about the concept"
        }
    }

    private fun extractTopicFromQuestion(input: String): String {
        val cleanInput = input.lowercase().trim()
        
        // Extract topic from common question patterns
        return when {
            cleanInput.startsWith("i want to learn about") -> 
                cleanInput.substringAfter("i want to learn about").trim()
            cleanInput.startsWith("what is") -> 
                cleanInput.substringAfter("what is").substringBefore("?").trim()
            cleanInput.startsWith("tell me about") -> 
                cleanInput.substringAfter("tell me about").trim()
            cleanInput.startsWith("explain") -> 
                cleanInput.substringAfter("explain").trim()
            cleanInput.contains("history of") -> 
                cleanInput.substringAfter("history of").substringBefore("?").trim()
            else -> {
                // Extract key nouns as potential topics
                val words = cleanInput.split(" ").filter { it.length > 3 }
                words.firstOrNull() ?: ""
            }
        }
    }

    private fun isFollowUpQuestion(input: String): Boolean {
        val cleanInput = input.lowercase().trim()
        
        // Enhanced follow-up detection patterns
        val followUpPhrases = listOf(
            "for example?", "for example", "what do you mean?", "can you elaborate?", 
            "tell me more", "explain more", "give me examples", "such as?",
            "like what?", "how so?", "in what way?", "continue", "go on", "more",
            "what else?", "and?", "then what?", "next?", "finish", "complete",
            "keep going", "more details", "expand on that", "tell me about"
        )
        
        // Check for short continuation requests
        val shortContinuationPattern = Regex("^(continue|more|go on|and\\?|next\\?|finish|complete|tell me more)\\s*\\??$", RegexOption.IGNORE_CASE)
        
        // Check for question words referring to previous context
        val contextReferencePattern = Regex("(what|how|why|when|where)\\s+(about|is|are|was|were)\\s+(that|this|it)\\b", RegexOption.IGNORE_CASE)
        
        return followUpPhrases.any { phrase -> cleanInput.contains(phrase) } ||
               shortContinuationPattern.containsMatchIn(cleanInput) ||
               contextReferencePattern.containsMatchIn(cleanInput) ||
               (cleanInput.length <= 15 && (cleanInput.contains("?") || cleanInput.contains("more")))
    }

    private fun detectTopicChange(newTopic: String, currentTopic: String): Boolean {
        if (currentTopic.isEmpty() || newTopic.isEmpty()) return false
        
        // Simple keyword overlap check
        val currentWords = currentTopic.lowercase().split(" ").filter { it.length > 3 }
        val newWords = newTopic.lowercase().split(" ").filter { it.length > 3 }
        
        val overlap = currentWords.intersect(newWords.toSet()).size
        val similarity = overlap.toFloat() / maxOf(currentWords.size, newWords.size, 1)
        
        // If similarity < 0.3, consider it a topic change
        return similarity < 0.3f
    }

    private fun formatRecentHistory(): String {
        return conversationContext.takeLast(3).joinToString("\n") { entry ->
            "${entry.role}: ${entry.content.take(50)}..."
        }
    }

    private fun getDepthForGrade(grade: Int): ExplanationDepth {
        return when (grade) {
            in 1..3 -> ExplanationDepth.SIMPLE
            in 4..8 -> ExplanationDepth.STANDARD
            else -> ExplanationDepth.DETAILED
        }
    }

    private fun getDifficultyForGrade(grade: Int): Difficulty {
        return when (grade) {
            in 1..3 -> Difficulty.EASY
            in 4..6 -> Difficulty.MEDIUM
            in 7..9 -> Difficulty.HARD
            else -> Difficulty.ADAPTIVE
        }
    }

    private fun getMaxWordsForGrade(grade: Int): Int {
        return when (grade) {
            in 1..3 -> 40   // Elementary Early: very simple responses
            in 4..6 -> 60   // Elementary Late: clear explanations
            in 7..8 -> 80   // Middle School: structured with one follow-up
            else -> 120     // High School: comprehensive with critical thinking
        }
    }

    private fun getMaxTokensForGrade(grade: Int): Int {
        return when (grade) {
            in 1..3 -> 150  // Elementary Early: simple responses
            in 4..6 -> 220  // Elementary Late: clear explanations  
            in 7..8 -> 300  // Middle School: structured with follow-up
            else -> 450     // High School: comprehensive with critical thinking (under 512 limit)
        }
    }

    private fun isResponseIncomplete(response: String): Boolean {
        val trimmed = response.trim()
        return when {
            trimmed.isEmpty() -> true
            // Ends mid-sentence without punctuation
            !trimmed.endsWith(".") && !trimmed.endsWith("!") && !trimmed.endsWith("?") -> true
            // Ends with common incomplete phrases
            trimmed.endsWith(" and") || trimmed.endsWith(" but") || trimmed.endsWith(" because") -> true
            trimmed.endsWith(" such as") || trimmed.endsWith(" like") || trimmed.endsWith(" for example") -> true
            // Has unmatched parentheses or quotes
            trimmed.count { it == '(' } != trimmed.count { it == ')' } -> true
            trimmed.count { it == '"' } % 2 != 0 -> true
            else -> false
        }
    }

    private fun ensureCompleteResponse(response: String, grade: Int): String {
        if (!isResponseIncomplete(response)) return response
        
        // Add appropriate completion based on grade level
        return when (grade) {
            in 1..3 -> {
                if (!response.trim().endsWith(".")) {
                    response.trim() + "."
                } else response
            }
            in 4..6 -> {
                when {
                    response.trim().endsWith(" and") -> response.trim() + " more."
                    response.trim().endsWith(" because") -> response.trim() + " it helps us understand better."
                    response.trim().endsWith(" such as") -> response.trim() + " these examples."
                    !response.trim().endsWith(".") -> response.trim() + "."
                    else -> response
                }
            }
            else -> {
                when {
                    response.trim().endsWith(" and") -> response.trim() + " many other related concepts."
                    response.trim().endsWith(" because") -> response.trim() + " it demonstrates important principles."
                    response.trim().endsWith(" such as") -> response.trim() + " various examples that illustrate this concept."
                    !response.trim().endsWith(".") -> response.trim() + "."
                    else -> response
                }
            }
        }
    }

    private fun buildContinuationPrompt(lastResponse: String, grade: Int): String {
        val gradeInstruction = when (grade) {
            in 1..3 -> "Continue with very simple words. Add 1-2 more simple sentences."
            in 4..6 -> "Continue with clear, age-appropriate language. Add more details or examples."
            in 7..8 -> "Continue with structured explanation. Add examples or connections."
            else -> "Continue with comprehensive details. Add analysis or broader context."
        }
        
        return """
        The student asked you to continue or finish your previous response. Here is what you said:
        
        "$lastResponse"
        
        TASK: Continue this response naturally. Complete any unfinished thoughts and add relevant details.
        
        REQUIREMENTS:
        - Continue from where you left off
        - Keep the same topic and tone
        - Do NOT restart or repeat what you already said
        - Complete any incomplete sentences
        - $gradeInstruction
        - Make it feel like a natural continuation
        
        CONTINUE HERE:
        """.trimIndent()
    }

    data class TutorMessage(
        val id: String = java.util.UUID.randomUUID().toString(),
        val content: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val status: MessageStatus = MessageStatus.SENT,
        val metadata: MessageMetadata? = null
    ) {
        data class MessageMetadata(
            val concept: String? = null,
            val explanationType: String? = null,
            val difficulty: String? = null,
            val responseTime: Long? = null,
            val tokensUsed: Int? = null
        )
        
        enum class MessageStatus {
            SENDING,
            SENT,
            DELIVERED,
            FAILED,
            TYPING
        }
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

    private fun addTutorMessage(
        content: String,
        isUser: Boolean,
        metadata: TutorMessage.MessageMetadata? = null
    ) {
        val message = TutorMessage(
            content = content,
            isUser = isUser,
            metadata = metadata
        )
        _state.update { currentState ->
            currentState.copy(messages = currentState.messages + message)
        }

        currentChatSessionId?.let { sid ->
            val chatMsg = if (isUser) ChatMessage.User(content) else ChatMessage.AI(content)
            viewModelScope.launch { chatRepository.addMessage(sid, chatMsg) }
        }
    }

    private fun analyzeStudentInput(input: String): StudentNeed {
        // Check for direct definition questions first
        val isDefinitionQuestion = input.lowercase().let { lowered ->
            lowered.startsWith("what is") ||
                    lowered.startsWith("what are") ||
                    lowered.startsWith("what's") ||
                    lowered.startsWith("what're")
        }

        if (isDefinitionQuestion) {
            return StudentNeed(NeedType.CONCEPT_EXPLANATION, 0.95f)
        }

        // Rest of the existing logic
        return when {
            input.contains("what", ignoreCase = true) ||
                    input.contains("why", ignoreCase = true) -> {
                StudentNeed(NeedType.CLARIFICATION, 0.8f)
            }
            input.contains("how do I", ignoreCase = true) ||
                    input.contains("steps", ignoreCase = true) -> {
                StudentNeed(NeedType.STEP_BY_STEP_HELP, 0.9f)
            }
            input.contains("explain", ignoreCase = true) ||
                    input.contains("don't understand", ignoreCase = true) -> {
                StudentNeed(NeedType.CONCEPT_EXPLANATION, 0.85f)
            }
            input.contains("practice", ignoreCase = true) ||
                    input.contains("more examples", ignoreCase = true) -> {
                StudentNeed(NeedType.PRACTICE, 0.7f)
            }
            input.contains("hard", ignoreCase = true) ||
                    input.contains("confused", ignoreCase = true) -> {
                StudentNeed(NeedType.ENCOURAGEMENT, 0.75f)
            }
            else -> StudentNeed(NeedType.CLARIFICATION, 0.5f)
        }
    }

    private suspend fun trackInteractionSuccess(
        variantId: String,
        response: String
    ) {
        // Simple success metric based on response quality
        val comprehensionScore = when {
            response.length > 100 && response.contains("example") -> 0.8f
            response.length > 50 -> 0.6f
            else -> 0.4f
        }

        variantManager.trackPerformance(
            variantId = variantId,
            metric = PromptVariantManager.PerformanceMetric.STUDENT_COMPREHENSION,
            value = comprehensionScore
        )
    }

    private fun parseTopicForFloatingBubbles(rawTitle: String): List<String> {
        // AGGRESSIVE TOPIC SPLITTING FOR ALL GRADE LEVELS
        // Goal: Create focused, single-concept topics that are easier for AI to explain
        
        // Phase 1: Primary splits on obvious delimiters
        val primarySplits = rawTitle
            .split(Regex("\\s+and\\s+|\\s*,\\s*|\\s*&\\s*|\\s*;\\s*|\\s*/\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val refinedTopics = mutableListOf<String>()

        primarySplits.forEach { topic ->
            // Phase 2: Advanced topic decomposition
            val furtherSplits = when {
                // Handle em-dashes and en-dashes (common in curriculum)
                topic.contains("—") || topic.contains("–") -> 
                    topic.split(Regex("[—–]")).map { it.trim() }
                
                // Handle parenthetical additions - split into main + detail
                topic.contains("(") -> {
                    val mainTopic = topic.substringBefore("(").trim()
                    val parenthetical = topic.substringAfter("(").substringBefore(")").trim()
                    
                    val results = mutableListOf<String>()
                    if (mainTopic.isNotBlank()) results.add(mainTopic)
                    if (parenthetical.isNotBlank() && parenthetical.length > 3) {
                        results.add(parenthetical)
                    }
                    results.ifEmpty { listOf(topic) }
                }
                
                // Split concepts joined by "vs", "versus", "or" 
                topic.contains(Regex("\\s+(vs|versus|or)\\s+", RegexOption.IGNORE_CASE)) -> {
                    topic.split(Regex("\\s+(vs|versus|or)\\s+", RegexOption.IGNORE_CASE))
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
                
                // Handle colon-separated topics (e.g., "Topic: Subtopic")
                topic.contains(":") && !topic.startsWith("http") -> {
                    val parts = topic.split(":")
                    if (parts.size == 2) {
                        listOf(parts[0].trim(), parts[1].trim()).filter { it.isNotBlank() }
                    } else {
                        listOf(topic)
                    }
                }
                
                // MORE AGGRESSIVE: Split long multi-concept topics
                topic.length > 20 && topic.contains(" ") -> {
                    val words = topic.split(" ").filter { it.isNotBlank() }
                    when {
                        // For very long topics (5+ words), try to split into meaningful chunks
                        words.size >= 5 -> {
                            splitLongTopicIntelligently(words)
                        }
                        // For medium topics (4 words), split in half
                        words.size == 4 -> {
                            listOf(
                                words.take(2).joinToString(" "),
                                words.drop(2).joinToString(" ")
                            )
                        }
                        else -> listOf(topic)
                    }
                }
                
                else -> listOf(topic)
            }
            
            // Phase 3: Clean and validate each split topic
            furtherSplits.forEach { split ->
                val cleaned = cleanTopicText(split)
                
                // Only add topics that are meaningful (3+ chars, not just numbers/symbols)
                if (cleaned.isNotBlank() && cleaned.length >= 3 && 
                    !cleaned.matches(Regex("^[0-9\\s\\-_.]+$"))) {
                    refinedTopics.add(cleaned)
                }
            }
        }

        // Fallback: if parsing fails, return original cleaned topic
        return refinedTopics.ifEmpty { 
            listOf(cleanTopicText(rawTitle))
        }.distinctBy { it.lowercase() } // Remove duplicates
    }
    
    private fun splitLongTopicIntelligently(words: List<String>): List<String> {
        // Try to split long topics at natural breakpoints
        val conjunctions = setOf("and", "or", "of", "in", "on", "for", "with", "by", "from", "to")
        
        // Find natural split points (conjunctions, prepositions)
        val splitPoints = words.mapIndexedNotNull { index, word ->
            if (word.lowercase() in conjunctions && index > 0 && index < words.size - 1) {
                index
            } else null
        }
        
        return if (splitPoints.isNotEmpty()) {
            // Split at the middle-most conjunction
            val splitPoint = splitPoints[splitPoints.size / 2]
            listOf(
                words.take(splitPoint).joinToString(" "),
                words.drop(splitPoint + 1).joinToString(" ") // Skip the conjunction
            )
        } else {
            // No natural split point, split in half
            val midPoint = words.size / 2
            listOf(
                words.take(midPoint).joinToString(" "),
                words.drop(midPoint).joinToString(" ")
            )
        }
    }
    
    private fun cleanTopicText(text: String): String {
        return text
            .trim()
            .removePrefix("-")
            .removePrefix("•")
            .removePrefix("*")
            .removePrefix("◦")
            .removeSuffix(",")
            .removeSuffix(";")
            .trim()
            .replaceFirstChar { it.uppercaseChar() }
    }

    private suspend fun loadCurriculumForSubject(context: Context, subject: OfflineRAG.Subject): List<CurriculumTopic> {
        return try {
            val cachedTopics = curriculumCache.getCurriculumTopics(context, subject)
            // Convert cache topics to viewmodel topics  
            cachedTopics.map { cached ->
                CurriculumTopic(
                    title = cached.title,
                    subject = cached.subject,
                    phase = cached.phase,
                    gradeRange = cached.gradeRange
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load curriculum from cache for subject: ${subject.name}")
            loadCurriculumForSubjectLegacy(context, subject)
        }
    }
    
    private suspend fun loadCurriculumForSubjectLegacy(context: Context, subject: OfflineRAG.Subject): List<CurriculumTopic> {
        val fileName = when (subject) {
            OfflineRAG.Subject.SCIENCE -> "curriculum/science_curriculum.json"
            OfflineRAG.Subject.MATHEMATICS -> "curriculum/mathematics_curriculum.json"
            OfflineRAG.Subject.ENGLISH -> "curriculum/english_curriculum.json"
            OfflineRAG.Subject.LANGUAGE_ARTS -> "curriculum/english_curriculum.json" // Use English curriculum for Language Arts
            OfflineRAG.Subject.HISTORY -> "curriculum/history_curriculum.json"
            OfflineRAG.Subject.GEOGRAPHY -> "curriculum/geography_curriculum.json"
            OfflineRAG.Subject.ECONOMICS -> "curriculum/economics_curriculum.json"
            OfflineRAG.Subject.GENERAL -> return emptyList() // No specific curriculum for general
            else -> return emptyList()
        }

        try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            Timber.d("Successfully loaded $fileName")

            val json = JSONObject(jsonString)
            val topics = mutableListOf<CurriculumTopic>()

            listOf("PYP", "MYP", "DP").forEach { program ->
                if (!json.has(program)) return@forEach
                val programObj = json.getJSONObject(program)

                programObj.keys().forEach { phaseOrSubject ->
                    val value = programObj.get(phaseOrSubject)
                    when (value) {
                        is JSONArray -> {
                            for (i in 0 until value.length()) {
                                val rawTitle = value.getString(i)

                                // Enhanced topic parsing for floating bubbles
                                val splitTitles = parseTopicForFloatingBubbles(rawTitle)

                                // Add each singular topic
                                splitTitles.forEach { single ->
                                    topics.add(
                                        CurriculumTopic(
                                            title = single,
                                            subject = subject,
                                            phase = program,
                                            gradeRange = phaseOrSubject
                                        )
                                    )
                                    Timber.d("Added topic: $single (program: $program, phase: $phaseOrSubject)")
                                }
                            }
                        }

                        is JSONObject -> {
                            // Handle nested structure
                            value.keys().forEach { topicGroup ->
                                when (val subValue = value.get(topicGroup)) {
                                    is JSONArray -> {
                                        for (i in 0 until subValue.length()) {
                                            val rawTitle = subValue.getString(i)

                                            val splitTitles = parseTopicForFloatingBubbles(rawTitle)

                                            splitTitles.forEach { single ->
                                                topics.add(
                                                    CurriculumTopic(
                                                        title = single,
                                                        subject = subject,
                                                        phase = program,
                                                        gradeRange = phaseOrSubject
                                                    )
                                                )
                                                Timber.d("Added topic: $single (program: $program, phase: $phaseOrSubject)")
                                            }
                                        }
                                    }
                                    is String -> {
                                        val splitTitles = parseTopicForFloatingBubbles(subValue)

                                        splitTitles.forEach { single ->
                                            topics.add(
                                                CurriculumTopic(
                                                    title = single,
                                                    subject = subject,
                                                    phase = program,
                                                    gradeRange = phaseOrSubject
                                                )
                                            )
                                            Timber.d("Added topic: $single (program: $program, phase: $phaseOrSubject)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Timber.d("Parsed ${topics.size} topics from $fileName")
            return topics
        } catch (e: Exception) {
            Timber.e(e, "Failed to load curriculum file: $fileName")
            return emptyList()
        }
    }



    private suspend fun loadMostRecentStudent() {
        try {
            val recentStudents = tutorRepository.getAllStudents()
            if (recentStudents.isNotEmpty()) {
                currentStudent = recentStudents.first()
                _state.update { it.copy(studentProfile = currentStudent) }
                Timber.d("Loaded recent student: ${currentStudent?.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load recent student - database may need rebuilding")
            // This is expected when the database schema has changed
            // The user will need to recreate their profile
        }
    }

    fun initializeStudent(name: String, gradeLevel: Int) = viewModelScope.launch {
        try {
            // Get or create the student profile
            val student = tutorRepository.createOrGetStudentProfile(name, gradeLevel)
            currentStudent = student

            // Load suggested topics from all subjects
            val subjects = OfflineRAG.Subject.entries.toTypedArray()
            val allSuggestedTopics = subjects.flatMap { subject ->
                val topics = loadCurriculumForSubject(context, subject)
                getSuggestedTopics(topics, subject.name, student.gradeLevel)
            }

            // Check if there's a pending session to start
            val currentState = _state.value
            val pendingSession = currentState.pendingSession
            
            // Update tutor state with student and topic suggestions
            _state.update {
                it.copy(
                    studentProfile = student,
                    suggestedTopics = allSuggestedTopics,
                    showStudentDialog = false,
                    pendingSession = null
                )
            }
            
            // Start pending session if exists
            if (pendingSession != null) {
                Timber.d("Starting pending tutor session: ${pendingSession.subject}")
                startTutorSession(
                    subject = pendingSession.subject,
                    sessionType = pendingSession.sessionType,
                    topic = pendingSession.topic
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize student")
            _state.update { it.copy(error = "Failed to create student profile") }
        }
    }

    fun showStudentProfileDialog() {
        _state.update { 
            it.copy(showStudentDialog = true) 
        }
    }

    fun dismissStudentDialog() {
        _state.update { 
            it.copy(
                showStudentDialog = false, 
                pendingSession = null
            ) 
        }
    }

    fun clearCurrentSubject() {
        Timber.d("Clearing current subject - returning to subject selection")
        _state.update { 
            it.copy(
                currentSubject = null,
                sessionType = null,
                currentTopic = "",
                messages = emptyList(),
                suggestedTopics = emptyList(),
                showFloatingTopics = false,
                conversationTopic = "",
                lastUserQuestion = "",
                isLoading = false,
                error = null
            ) 
        }
        Timber.d("Current subject cleared - should show subject selection")
    }

    fun startTutorSession(
        subject: OfflineRAG.Subject,
        sessionType: TutorSessionType,
        topic: String
    ) = viewModelScope.launch {
        try {
            val student = currentStudent
            if (student == null) {
                Timber.w("No student profile found, showing profile dialog")
                _state.update { 
                    it.copy(
                        showStudentDialog = true, 
                        pendingSession = PendingSession(subject, sessionType, topic)
                    ) 
                }
                return@launch
            }

            Timber.d("Starting tutor session for subject: $subject, grade: ${student.gradeLevel}")

            // Use startTutorSession instead of startSession
            val (tutorId, chatId) = tutorRepository.startTutorSession(
                studentId = student.id,
                subject = subject,
                sessionType = sessionType,
                topic = topic
            )
            currentSessionId = tutorId
            currentChatSessionId = chatId

            // Clear previous context
            conversationContext.clear()
            attemptCount.clear()

            // Load curriculum topics for this subject
            val curriculumTopics = loadCurriculumForSubject(context, subject)
            Timber.d("Loaded ${curriculumTopics.size} curriculum topics")

            val suggestedTopics = getSuggestedTopics(curriculumTopics, subject.name, student.gradeLevel)
            Timber.d("Found ${suggestedTopics.size} suggested topics for ${subject.name} grade ${student.gradeLevel}")
            Timber.d("Total curriculum topics loaded: ${curriculumTopics.size}")
            suggestedTopics.forEach { Timber.d("Topic: $it") }

            // Update state
            _state.update {
                it.copy(
                    currentSubject = subject,
                    sessionType = sessionType,
                    currentTopic = topic,
                    messages = emptyList(),
                    error = null,
                    suggestedTopics = suggestedTopics,
                    showFloatingTopics = suggestedTopics.isNotEmpty(), // Only show if we have topics
                    sessionStartTime = System.currentTimeMillis()
                )
            }

            // Send welcome message
            val welcomeMessage = generateWelcomeMessage(student, subject, sessionType)
            addTutorMessage(welcomeMessage, isUser = false)

        } catch (e: Exception) {
            Timber.e(e, "Failed to start tutor session")
            _state.update { it.copy(error = "Failed to start session: ${e.message}") }
        }
    }

    private fun generateWelcomeMessage(
        student: StudentProfileEntity,
        subject: OfflineRAG.Subject,
        sessionType: TutorSessionType
    ): String {
        return when (sessionType) {
            TutorSessionType.HOMEWORK_HELP ->
                "Hi ${student.name}! I'm here to help you with your ${subject.name.lowercase()} homework. What would you like to work on?"
            TutorSessionType.CONCEPT_EXPLANATION ->
                "Hello ${student.name}! Let's explore ${subject.name.lowercase()} concepts together. What topic interests you?"
            TutorSessionType.PRACTICE_PROBLEMS ->
                "Hi ${student.name}! Ready for some ${subject.name.lowercase()} practice? We can start with easy problems and work our way up!"
            TutorSessionType.EXAM_PREP ->
                "Hello ${student.name}! Let's prepare for your ${subject.name.lowercase()} exam. What topics do you need to review?"
        }
    }

    // Add these methods to TutorViewModel class:

    private fun formatTutorResponse(
        rawResponse: String,
        student: StudentProfileEntity,
        approach: TeachingApproach
    ): String {
        // Detect if the response contains lists or steps
        val hasNumberedSteps = rawResponse.contains(Regex("\\d+\\.\\s"))
        val hasBulletPoints = rawResponse.contains("•") || rawResponse.contains("-")

        return when {
            // Format step-by-step instructions
            hasNumberedSteps && approach == TeachingApproach.PROBLEM_SOLVING -> {
                val steps = extractSteps(rawResponse)
                val intro = extractIntro(rawResponse)
                TutorResponseFormatter.formatStepsResponse(steps, intro, student)
            }

            // Format lists
            hasBulletPoints && approach == TeachingApproach.EXPLANATION -> {
                val items = extractListItems(rawResponse)
                val intro = extractIntro(rawResponse)
                TutorResponseFormatter.formatListResponse(items, intro, null, student)
            }

            // Default formatting
            else -> rawResponse.formatForTutor(student)
        }
    }

    // Helper methods to extract content
    private fun extractSteps(response: String): List<String> {
        return response.split(Regex("\\d+\\.\\s"))
            .drop(1) // Skip content before first number
            .map { it.trim().takeWhile { char -> char != '\n' } }
            .filter { it.isNotBlank() }
    }

    private fun extractListItems(response: String): List<String> {
        return response.split(Regex("[•\\-]\\s"))
            .drop(1)
            .map { it.trim().takeWhile { char -> char != '\n' } }
            .filter { it.isNotBlank() }
    }

    private fun extractIntro(response: String): String {
        val firstListMarker = response.indexOfAny(listOf("1.", "•", "-"))
        return if (firstListMarker > 0) {
            response.substring(0, firstListMarker).trim()
        } else {
            "Here's what you need to know:"
        }
    }

    private fun extractKeyPoints(content: String): List<String> {
        // Extract key points from the content
        // This is a simple implementation - you can make it more sophisticated
        val sentences = content.split(Regex("[.!?]+")).filter { it.isNotBlank() }

        return sentences.take(5).map { sentence ->
            sentence.trim()
                .removePrefix("- ")
                .removePrefix("• ")
                .take(100) // Limit length of each point
        }.filter { it.length > 10 } // Filter out very short sentences
    }

    /**
     * Calculate response quality based on multiple factors
     */
    private fun calculateResponseQuality(response: String, approach: TeachingApproach): Float {
        var quality = 0.5f // Base quality score
        
        // Length factor - balanced responses are better
        val length = response.length
        quality += when {
            length < 50 -> -0.2f // Too short
            length in 50..200 -> 0.1f // Good length
            length in 200..500 -> 0.2f // Excellent length
            length in 500..800 -> 0.1f // Still good
            else -> -0.1f // Too long
        }
        
        // Structure factor - well-structured responses are better
        val hasQuestions = response.contains("?")
        val hasExamples = response.contains("example", ignoreCase = true) || 
                         response.contains("for instance", ignoreCase = true)
        val hasSteps = response.contains(Regex("\\d+\\.")) || 
                      response.contains("first", ignoreCase = true) ||
                      response.contains("next", ignoreCase = true)
        
        if (hasQuestions && approach == TeachingApproach.SOCRATIC) quality += 0.15f
        if (hasExamples) quality += 0.1f
        if (hasSteps && approach == TeachingApproach.PROBLEM_SOLVING) quality += 0.15f
        
        // Educational value - responses with educational keywords are better
        val educationalKeywords = listOf(
            "understand", "learn", "concept", "principle", "theory", "practice",
            "think", "analyze", "compare", "explain", "reason", "solve"
        )
        val keywordCount = educationalKeywords.count { 
            response.contains(it, ignoreCase = true) 
        }
        quality += (keywordCount * 0.05f).coerceAtMost(0.2f)
        
        // Engagement factor - interactive responses are better
        val engagementKeywords = listOf(
            "what do you think", "can you", "try to", "let's", "consider",
            "imagine", "suppose", "what if"
        )
        val engagementCount = engagementKeywords.count { 
            response.contains(it, ignoreCase = true) 
        }
        quality += (engagementCount * 0.1f).coerceAtMost(0.2f)
        
        // Clamp to valid range
        return quality.coerceIn(0.0f, 1.0f)
    }
}