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
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.Gson
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Add audio recording properties
    private var recordingJob: Job? = null
    private val audioBuffer = mutableListOf<FloatArray>()

    private val _state = MutableStateFlow(TutorState())
    val state: StateFlow<TutorState> = _state.asStateFlow()

    private var currentStudent: StudentProfileEntity? = null
    private var currentSessionId: String? = null
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
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val suggestedTopics: List<String> = emptyList(),
        val error: String? = null,
        val studentProfile: StudentProfileEntity? = null,
        val conceptMastery: Map<String, Float> = emptyMap(),
        val currentApproach: TeachingApproach = TeachingApproach.SOCRATIC,
        val showFloatingTopics: Boolean = false,
        val currentTopicIndex: Int = 0

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

    private suspend fun loadCurriculum(context: Context): List<CurriculumTopic> {
        val jsonString = context.assets.open("science_curriculum.json")
            .bufferedReader().use { it.readText() }

        val gson = Gson()
        val type = object : TypeToken<List<CurriculumTopic>>() {}.type
        return gson.fromJson(jsonString, type)
    }

    private fun getSuggestedTopics(
        allTopics: List<CurriculumTopic>,
        subject: String,
        grade: Int
    ): List<String> {
        return allTopics.filter {
            it.subject.name.equals(subject, ignoreCase = true) &&
                    matchesGradeRange(it.gradeRange, grade)
        }.map { it.title }
    }


    private fun matchesGradeRange(phase: String, grade: Int): Boolean {
        Timber.d("Matching phase '$phase' with grade $grade")

        return when {
            // Handle PYP phases with grade ranges in parentheses
            phase.contains("Grades", ignoreCase = true) -> {
                val regex = Regex("""\d+""")
                val numbers = regex.findAll(phase).map { it.value.toInt() }.toList()
                val matches = when (numbers.size) {
                    0 -> false
                    1 -> numbers[0] == grade
                    else -> grade in numbers[0]..numbers[1]  // 2 or more numbers
                }
                Timber.d("Grade range match for '$phase': $matches (numbers: $numbers)")
                matches
            }

            // Handle single grade mentions (e.g., "Grade 6")
            phase.contains("Grade $grade", ignoreCase = true) -> {
                Timber.d("Single grade match for '$phase': true")
                true
            }

            // Handle KG
            phase.contains("KG", ignoreCase = true) && grade == 0 -> true

            // Handle MYP with specific grades
            phase.contains("MYP 1", ignoreCase = true) && grade == 6 -> true
            phase.contains("MYP 2", ignoreCase = true) && grade == 7 -> true
            phase.contains("MYP 3", ignoreCase = true) && grade == 8 -> true
            phase.contains("MYP 4", ignoreCase = true) && grade in 9..10 -> true
            phase.contains("MYP 5", ignoreCase = true) && grade == 10 -> true

            // Handle DP subjects (Grades 11-12)
            (phase == "Biology" || phase == "Chemistry" || phase == "Physics" ||
                    phase == "ESS" || phase == "SEHS" || phase.contains("AA") || phase.contains("AI"))
                    && grade in 11..12 -> true

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



    suspend fun handleStudentQuestion(
        question: String,
        student: StudentProfileEntity,
        subject: OfflineRAG.Subject
    ) {
        // 1. Analyze the question and student's history
        val concept = extractMainConcept(question)
        val attempts = attemptCount[concept] ?: 0
        val recentHistory = conversationContext.takeLast(5)

        // 2. Get adaptive prompt - automatically switches strategies
        val basePrompt = promptManager.getAdaptivePrompt(
            subject = subject,
            concept = concept,
            studentGrade = student.gradeLevel,
            studentQuestion = question,
            attemptNumber = attempts,
            previousResponses = recentHistory.map { it.content }
        )

        // 3. Apply educator customizations
        val customizedPrompt = promptManager.getCustomizedPrompt(
            basePrompt = basePrompt,
            grade = student.gradeLevel,
            subject = subject
        )

        // 4. Apply A/B test variant (if enabled)
        val finalPrompt = variantManager.applyVariantToPrompt(customizedPrompt)

        // 5. Generate response
        val response = gemmaService.generateTextAsync(
            prompt = finalPrompt,
            config = UnifiedGemmaService.GenerationConfig(
                maxTokens = 200,
                temperature = 0.5f
            )
        )

        // 6. Track performance (for A/B testing)
        val variant = variantManager.getCurrentVariant()
        trackInteractionSuccess(variant.id, response)
    }
    fun processUserInput(input: String) = viewModelScope.launch {
        addTutorMessage(input, isUser = true)
        _state.update { it.copy(isLoading = true) }

        try {
            val student = currentStudent ?: throw IllegalStateException("No student profile")
            val subject = _state.value.currentSubject ?: throw IllegalStateException("No subject selected")
            val sessionType = _state.value.sessionType ?: throw IllegalStateException("No session type")

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
                sessionType = sessionType
            )

            // Format and add response
            val formattedResponse = formatTutorResponse(response, student, approach)
            addTutorMessage(formattedResponse, isUser = false, metadata = TutorMessage.MessageMetadata(
                concept = concept,
                explanationType = approach.name,
                difficulty = getDifficultyForGrade(student.gradeLevel).name // Convert to string here
            ))

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
        sessionType: TutorSessionType
    ): String {
        // 1. Extra guidance for very young learners (Grade 3 or below + EXPLANATION approach)
        val isYoungStudent = student.gradeLevel <= 3
        val gradeInstruction = if (isYoungStudent && approach == TeachingApproach.EXPLANATION) {
            "\nIMPORTANT: This is a grade ${student.gradeLevel} student. " +
                    "Give a DIRECT, SIMPLE answer first in one sentence. " +
                    "Then add exactly one follow‑up question to encourage thinking."
        } else ""

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

        // 4. Assemble full prompt with extra context, history and length limits
        val fullPrompt = """
        $prompt

        Educational Context:
        $relevantContent

        Recent conversation:
        ${formatRecentHistory()}

        Student's current input: "$userInput"

        Remember: Keep response concise (under ${getMaxWordsForGrade(student.gradeLevel)} words).
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
            in 1..3 -> 30
            in 4..6 -> 50
            in 7..9 -> 75
            else -> 100
        }
    }

    private fun getMaxTokensForGrade(grade: Int): Int {
        return when (grade) {
            in 1..3 -> 150
            in 4..6 -> 200
            in 7..9 -> 300
            else -> 400
        }
    }

    data class TutorMessage(
        val content: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val metadata: MessageMetadata? = null
    ) {
        data class MessageMetadata(
            val concept: String? = null,
            val explanationType: String? = null,
            val difficulty: String? = null
        )
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

    private suspend fun loadCurriculumForSubject(context: Context, subject: OfflineRAG.Subject): List<CurriculumTopic> {
        val fileName = when (subject) {
            OfflineRAG.Subject.SCIENCE -> "curriculum/science_curriculum.json"  // Add "curriculum/" prefix back
            OfflineRAG.Subject.MATHEMATICS -> "curriculum/mathematics_curriculum.json"
            OfflineRAG.Subject.ENGLISH -> "curriculum/english_curriculum.json"
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
                                val title = value.getString(i)
                                topics.add(CurriculumTopic(title, subject, program, phaseOrSubject))
                                Timber.d("Added topic: $title (program: $program, phase: $phaseOrSubject)")
                            }
                        }
                        is JSONObject -> {
                            // Handle nested structure
                            value.keys().forEach { topicGroup ->
                                when (val subValue = value.get(topicGroup)) {
                                    is JSONArray -> {
                                        for (i in 0 until subValue.length()) {
                                            val title = subValue.getString(i)
                                            topics.add(CurriculumTopic(title, subject, program, phaseOrSubject))
                                            Timber.d("Added topic: $title (program: $program, phase: $phaseOrSubject)")
                                        }
                                    }
                                    is String -> {
                                        // Handle single string values if any
                                        topics.add(CurriculumTopic(subValue, subject, program, phaseOrSubject))
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

            // Update tutor state with student and topic suggestions
            _state.update {
                it.copy(
                    studentProfile = student,
                    suggestedTopics = allSuggestedTopics
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize student")
            _state.update { it.copy(error = "Failed to create student profile") }
        }
    }


    fun startTutorSession(
        subject: OfflineRAG.Subject,
        sessionType: TutorSessionType,
        topic: String
    ) = viewModelScope.launch {
        try {
            val student = currentStudent ?: throw IllegalStateException("No student profile")

            Timber.d("Starting tutor session for subject: $subject, grade: ${student.gradeLevel}")

            // Use startTutorSession instead of startSession
            currentSessionId = tutorRepository.startTutorSession(
                studentId = student.id,
                subject = subject,
                sessionType = sessionType,
                topic = topic
            )

            // Clear previous context
            conversationContext.clear()
            attemptCount.clear()

            // Load curriculum topics for this subject
            val curriculumTopics = loadCurriculumForSubject(context, subject)
            Timber.d("Loaded ${curriculumTopics.size} curriculum topics")

            val suggestedTopics = getSuggestedTopics(curriculumTopics, subject.name, student.gradeLevel)
            Timber.d("Found ${suggestedTopics.size} suggested topics for grade ${student.gradeLevel}")
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
                    showFloatingTopics = suggestedTopics.isNotEmpty() // Only show if we have topics
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
}