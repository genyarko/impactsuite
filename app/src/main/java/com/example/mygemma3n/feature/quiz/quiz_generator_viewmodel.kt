package com.example.mygemma3n.feature.quiz

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.TextEmbeddingServiceExtensions
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.GeminiApiConfig
import com.example.mygemma3n.domain.repository.SettingsRepository
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.mygemma3n.feature.analytics.LearningAnalyticsRepository
import com.example.mygemma3n.feature.analytics.InteractionType
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class QuizGeneratorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,
    private val educationalContent: EducationalContentRepository,
    private val quizRepo: QuizRepository,
    private val settingsRepo: SettingsRepository,
    private val gson: Gson,
    private val optimizedGenerator: PerformanceOptimizedQuizGenerator,
    private val embeddingExtensions: TextEmbeddingServiceExtensions,
    private val enhancedPromptManager: EnhancedPromptManager,
    private val curriculumQuizGenerator: CurriculumAwareQuizGenerator,
    private val studentIntegration: QuizStudentIntegration,
    private val analyticsRepository: LearningAnalyticsRepository,
    private val onlineQuizGenerator: OnlineQuizGenerator,
    private val geminiApiService: GeminiApiService
) : ViewModel() {

    /* ───────── UI state ───────── */

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()

    data class QuizState(
        val isGenerating: Boolean = false,
        val currentQuiz: Quiz? = null,
        val subjects: List<Subject> = emptyList(),
        val difficulty: Difficulty = Difficulty.MEDIUM,
        val mode: QuizMode = QuizMode.NORMAL,
        val questionsGenerated: Int = 0,
        val userProgress: Map<Subject, Float> = emptyMap(),
        val learnerProfile: LearnerProfile? = null,
        val conceptCoverage: Map<String, Int> = emptyMap(),
        val reviewQuestionsAvailable: Int = 0,
        val error: String? = null,
        val isModelInitialized: Boolean = false,
        val generationPhase: String = "Starting...", // For animation variety
        val studentName: String? = null,
        val studentGrade: Int? = null,
        val studentCountry: String? = null,
        val curriculumTopics: List<String> = emptyList(),
        val isLoadingCurriculum: Boolean = false
    )

    /* ───────── Init ───────── */

    init {
        viewModelScope.launch {
            try {
                // Initialize the offline model first
                initializeModel()
                optimizedGenerator.prewarmModel()

                // Load educational content
                educationalContent.prepopulateContent()

                // Always use all available subjects instead of loading from content
                _state.update { it.copy(subjects = Subject.entries) }

                // Load initial progress data
                loadUserProgress()

                // Preload API service for faster quiz generation
                launch {
                    delay(1000) // Give settings time to load
                    if (shouldUseOnlineService()) {
                        try {
                            Timber.d("Preloading API service for Quiz Generator")
                            initializeApiServiceIfNeeded()
                            warmUpApiService()
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to preload API service for Quiz Generator")
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Timber.d("Quiz initialization cancelled")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Initialization failed")
                _state.update { it.copy(error = "Failed to initialize: ${e.message}") }
            }
        }
    }

    /* ───────── Model initialization ───────── */

    private suspend fun initializeModel() {
        try {
            val availableModels = gemmaService.getAvailableModels()
            if (availableModels.isEmpty()) {
                throw IllegalStateException("No Gemma models found in assets")
            }

            val modelToUse = if (availableModels.contains(UnifiedGemmaService.ModelVariant.FAST_2B)) {
                UnifiedGemmaService.ModelVariant.FAST_2B
            } else {
                availableModels.first()
            }

            gemmaService.initialize(modelToUse)
            _state.update { it.copy(isModelInitialized = true) }
            Timber.d("Gemma model initialized successfully with ${modelToUse.displayName}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Gemma model")
            _state.update {
                it.copy(
                    isModelInitialized = false,
                    error = "Failed to initialize offline model: ${e.message}"
                )
            }
            throw e
        }
    }

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
            val useOnlineService = settingsRepo.useOnlineServiceFlow.first()
            val hasApiKey = settingsRepo.apiKeyFlow.first().isNotBlank()
            val hasNetwork = hasNetworkConnection()
            
            useOnlineService && hasApiKey && hasNetwork
        } catch (e: Exception) {
            Timber.w(e, "Error checking service preference, defaulting to offline")
            false
        }
    }

    private suspend fun initializeApiServiceIfNeeded() {
        if (!geminiApiService.isInitialized()) {
            val apiKey = settingsRepo.apiKeyFlow.first()
            if (apiKey.isNotBlank()) {
                try {
                    geminiApiService.initialize(GeminiApiConfig.forQuiz(apiKey))
                    Timber.d("GeminiApiService initialized for Quiz Generator")
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
            geminiApiService.generateTextComplete(warmupPrompt, "quiz_warmup")
            Timber.d("Quiz API service warmed up successfully")
        } catch (e: Exception) {
            Timber.w(e, "Quiz API warmup failed, but service should still work")
        }
    }

    /* ───────── Student initialization ───────── */

    /**
     * Initialize quiz with student info
     */
    fun initializeQuizWithStudent(name: String, gradeLevel: Int, country: String = "") = viewModelScope.launch {
        try {
            // Check if country has changed and clear history if needed
            val previousCountry = _state.value.studentCountry
            val newCountry = country.takeIf { it.isNotBlank() }
            
            if (previousCountry != newCountry && newCountry != null) {
                Timber.i("Student country changed from $previousCountry to $newCountry - clearing quiz history")
                try {
                    quizRepo.clearAllQuizzes()
                    Timber.i("Quiz history cleared successfully for country change")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to clear quiz history during country change")
                }
            }
            
            val student = studentIntegration.getOrCreateStudent(name, gradeLevel)

            _state.update {
                it.copy(
                    studentName = name,
                    studentGrade = gradeLevel,
                    studentCountry = newCountry,
                    difficulty = studentIntegration.getSuggestedDifficulty(student, Subject.GENERAL)
                )
            }

            // Load curriculum topics for grade
            loadCurriculumTopics(gradeLevel)

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize student for quiz")
            _state.update { it.copy(error = "Failed to load student profile") }
        }
    }

    /**
     * Load curriculum topics for the grade
     */
     fun loadCurriculumTopics(gradeLevel: Int) = viewModelScope.launch {
        _state.update { it.copy(isLoadingCurriculum = true) }

        try {
            // This would load topics from curriculum files
            // You can expand this to actually parse the curriculum
            val topics = listOf(
                "Numbers and Operations",
                "Geometry and Shapes",
                "Measurement",
                "Data and Graphs"
            )

            _state.update {
                it.copy(
                    curriculumTopics = topics,
                    isLoadingCurriculum = false
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load curriculum")
            _state.update { it.copy(isLoadingCurriculum = false) }
        }
    }

    /* ───────── Progress tracking ───────── */

    private suspend fun loadUserProgress() {
        try {
            val subjects = _state.value.subjects
            val progressMap = mutableMapOf<Subject, Float>()

            subjects.forEach { subject ->
                val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                val accuracy = quizRepo.progressDao().recentAccuracy(subject, oneWeekAgo)
                progressMap[subject] = accuracy

                // Check for review questions
                val reviewQuestions = quizRepo.getQuestionsForSpacedReview(subject)
                _state.update {
                    it.copy(
                        userProgress = progressMap,
                        reviewQuestionsAvailable = reviewQuestions.size
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load user progress")
        }
    }

    /* ───────── Public API ───────── */

    fun setQuizMode(mode: QuizMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun completeQuiz() = viewModelScope.launch {
        val quiz = _state.value.currentQuiz
        if (quiz != null) {
            val score = calculateQuizScore(quiz)
            val completedQuiz = quiz.copy(
                completedAt = System.currentTimeMillis(),
                score = score
            )
            quizRepo.saveQuiz(completedQuiz)
        }

        // Clear current quiz state
        _state.update { it.copy(currentQuiz = null, questionsGenerated = 0, error = null) }

        // Reload progress
        loadUserProgress()

        // Always ensure all subjects are available
        _state.update { it.copy(subjects = Subject.entries) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
    
    /**
     * Clear quiz history to reset country context influence
     * This can be called when switching student countries or to refresh the question pool
     */
    fun clearQuizHistory() = viewModelScope.launch {
        try {
            Timber.i("Clearing quiz history to reset country context influence")
            quizRepo.clearAllQuizzes()
            Timber.i("Quiz history cleared successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear quiz history")
        }
    }

    // Also update loadSubjects method
    fun loadSubjects() = viewModelScope.launch {
        // Always use all available subjects
        _state.update { it.copy(subjects = Subject.entries) }

        // Load progress for all subjects
        loadUserProgress()
    }

    /**
     * Generate curriculum-aware adaptive quiz
     */
    fun generateCurriculumAwareQuiz(
        subject: Subject,
        topic: String,
        questionCount: Int = 10
    ) = viewModelScope.launch {
        val gradeLevel = _state.value.studentGrade
            ?: throw IllegalStateException("No student grade set")

        _state.update { it.copy(
            isGenerating = true,
            questionsGenerated = 0,
            error = null,
            generationPhase = "Loading curriculum..."
        )}

        try {
            // Check if we should use online or offline generation
            val useOnline = shouldUseOnlineService()
            val questions = if (useOnline) {
                _state.update { it.copy(generationPhase = "Generating online quiz...") }
                try {
                    initializeApiServiceIfNeeded()
                    val onlineQuestions = onlineQuizGenerator.generateCurriculumAwareOnlineQuiz(
                        subject = subject,
                        gradeLevel = gradeLevel,
                        topic = topic,
                        count = questionCount,
                        country = _state.value.studentCountry,
                        studentName = _state.value.studentName,
                        previousQuestions = getRecentQuestionTexts(subject, topic)
                    )
                    
                    // Check if we got any questions from online generation
                    if (onlineQuestions.isNotEmpty()) {
                        onlineQuestions
                    } else {
                        Timber.w("Online generation returned empty list, falling back to offline")
                        _state.update { it.copy(generationPhase = "Falling back to offline...") }
                        // Fallback to offline generation
                        curriculumQuizGenerator.generateCurriculumBasedQuestions(
                            subject = subject,
                            gradeLevel = gradeLevel,
                            topic = topic,
                            count = questionCount,
                            difficulty = _state.value.difficulty,
                            country = _state.value.studentCountry,
                            previousQuestions = getRecentQuestionTexts(subject, topic)
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Online generation failed, falling back to offline")
                    _state.update { it.copy(generationPhase = "Falling back to offline...") }
                    // Fallback to offline generation
                    curriculumQuizGenerator.generateCurriculumBasedQuestions(
                        subject = subject,
                        gradeLevel = gradeLevel,
                        topic = topic,
                        count = questionCount,
                        difficulty = _state.value.difficulty,
                        country = _state.value.studentCountry,
                        previousQuestions = getRecentQuestionTexts(subject, topic)
                    )
                }
            } else {
                _state.update { it.copy(generationPhase = "Generating offline quiz...") }
                // Use offline generation
                curriculumQuizGenerator.generateCurriculumBasedQuestions(
                    subject = subject,
                    gradeLevel = gradeLevel,
                    topic = topic,
                    count = questionCount,
                    difficulty = _state.value.difficulty,
                    country = _state.value.studentCountry,
                    previousQuestions = getRecentQuestionTexts(subject, topic)
                )
            }

            // Final safety check - ensure we have questions
            if (questions.isEmpty()) {
                throw Exception("No questions were generated for $subject - $topic")
            }

            // Create quiz
            val quiz = Quiz(
                id = java.util.UUID.randomUUID().toString(),
                subject = subject,
                topic = topic,
                questions = questions,
                difficulty = _state.value.difficulty,
                mode = _state.value.mode,
                createdAt = System.currentTimeMillis()
            )

            quizRepo.saveQuiz(quiz)

            _state.update {
                it.copy(
                    isGenerating = false,
                    currentQuiz = quiz,
                    questionsGenerated = questions.size,
                    generationPhase = "Complete!"
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "Curriculum quiz generation failed")
            _state.update { it.copy(isGenerating = false, error = e.message) }
        }
    }

    /** Generate adaptive quiz with enhanced features */
    fun generateAdaptiveQuiz(
        subject: Subject,
        topic: String,
        questionCount: Int = 10
    ) = viewModelScope.launch {
        // If student grade is set, use curriculum-aware generation
        if (_state.value.studentGrade != null) {
            generateCurriculumAwareQuiz(subject, topic, questionCount)
            return@launch
        }

        if (!_state.value.isModelInitialized) {
            _state.update { it.copy(error = "Model not initialized. Please wait...") }
            return@launch
        }

        _state.update { it.copy(
            isGenerating = true,
            questionsGenerated = 0,
            error = null,
            generationPhase = "Preparing..."
        )}

        try {
            // 1. Adaptive difficulty based on performance
            _state.update { it.copy(generationPhase = "🎯 Analyzing your performance...") }
            delay(800) // Small delay for UI feedback
            val adaptedDifficulty = quizRepo.getAdaptiveDifficulty(subject, _state.value.difficulty)
            _state.update { it.copy(difficulty = adaptedDifficulty) }

            // 2. Get learner profile for better question generation
            _state.update { it.copy(generationPhase = "📊 Building your learning profile...") }
            delay(600)
            val profile = quizRepo.getLearnerProfile(subject)
            _state.update { it.copy(learnerProfile = profile) }

            // 3. Get previous questions for deduplication
            _state.update { it.copy(generationPhase = "🔍 Ensuring fresh content...") }
            val historyTexts = quizRepo
                .getQuizzesFor(subject, topic)
                .flatMap { it.questions }
                .map { it.questionText }

            val questions = mutableListOf<Question>()
            val conceptsCovered = mutableMapOf<String, Int>()

            // 4. Generate questions based on mode
            when (_state.value.mode) {
                QuizMode.REVIEW -> {
                    _state.update { it.copy(generationPhase = "📚 Gathering your review questions...") }
                    delay(600)

                    // Include some review questions from spaced repetition
                    val reviewQuestions = quizRepo.getQuestionsForSpacedReview(
                        subject,
                        limit = minOf(questionCount / 2, questionCount)
                    )

                    reviewQuestions.forEach { history ->
                        val q = recreateQuestionFromHistory(history)
                        questions.add(q)
                        updateConceptCoverage(conceptsCovered, q.conceptsCovered)
                    }

                    // Generate remaining new questions
                    val remaining = questionCount - questions.size
                    if (remaining > 0) {
                        _state.update { it.copy(generationPhase = "✨ Creating new challenges...") }
                        val newQuestions = generateQuestionsSequentially(
                            subject = subject,
                            topic = topic,
                            difficulty = adaptedDifficulty,
                            count = remaining,
                            previousQuestions = historyTexts + questions.map { it.questionText },
                            learnerProfile = profile
                        )
                        questions.addAll(newQuestions)
                        newQuestions.forEach { q ->
                            updateConceptCoverage(conceptsCovered, q.conceptsCovered)
                        }
                    }
                }

                else -> {
                    // Normal or adaptive mode - generate all questions
                    _state.update { it.copy(generationPhase = "🎨 Crafting your perfect quiz...") }
                    val newQuestions = generateQuestionsSequentially(
                        subject = subject,
                        topic = topic,
                        difficulty = adaptedDifficulty,
                        count = questionCount,
                        previousQuestions = historyTexts,
                        learnerProfile = profile
                    )
                    questions.addAll(newQuestions)
                    newQuestions.forEach { q ->
                        updateConceptCoverage(conceptsCovered, q.conceptsCovered)
                    }
                }
            }

            // Ensure we don't exceed the requested count
            val finalQuestions = questions.take(questionCount)

            _state.update { it.copy(generationPhase = "🎉 Finalizing your quiz experience...") }
            delay(400)

            val quiz = Quiz(
                id = java.util.UUID.randomUUID().toString(),
                subject = subject,
                topic = topic,
                questions = finalQuestions,
                difficulty = adaptedDifficulty,
                mode = _state.value.mode,
                createdAt = System.currentTimeMillis()
            )

            quizRepo.saveQuiz(quiz)

            _state.update {
                it.copy(
                    isGenerating = false,
                    currentQuiz = quiz,
                    conceptCoverage = conceptsCovered,
                    questionsGenerated = finalQuestions.size,
                    generationPhase = "Complete!"
                )
            }

            Timber.d("🎉 Quiz ready: ${finalQuestions.size} questions covering ${conceptsCovered.size} concepts")

        } catch (e: Exception) {
            Timber.e(e, "Quiz generation failed")
            _state.update { it.copy(isGenerating = false, error = e.message) }
        }
    }

    // Add this new sequential generation function:
    private suspend fun generateQuestionsSequentially(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        count: Int,
        previousQuestions: List<String>,
        learnerProfile: LearnerProfile
    ): List<Question> = withContext(Dispatchers.IO) {
        val questions = mutableListOf<Question>()
        val allPreviousQuestions = previousQuestions.toMutableList()

        // Update generation phases dynamically
        val phases = listOf(
            "Analyzing your learning style...",
            "Crafting personalized questions...",
            "Adding educational variety...",
            "Polishing question quality...",
            "Finalizing your quiz experience...",
            "Almost ready for you!"
        )

        repeat(count) { idx ->
            try {
                val questionType = selectQuestionType(idx + 1)
                Timber.d("Generating question ${idx + 1}/$count of type: $questionType")

                // Update phase
                _state.update { it.copy(
                    generationPhase = phases[idx % phases.size]
                )}

                val q = generateQuestionWithRetry(
                    subject = subject,
                    topic = topic,
                    difficulty = difficulty,
                    questionType = questionType,
                    previousQuestions = allPreviousQuestions,
                    learnerProfile = learnerProfile
                )

                val validatedQuestion = validateQuestion(q)

                // Log the validated question details
                Timber.d("Generated ${validatedQuestion.questionType} question with ${validatedQuestion.options.size} options")

                questions.add(validatedQuestion)
                allPreviousQuestions.add(validatedQuestion.questionText)

                // Update UI state
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(questionsGenerated = questions.size) }
                }

                // Small delay between questions
                if (idx < count - 1) {
                    delay(200)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate question ${idx + 1}")
            }
        }

        // Log final question types
        Timber.d("Generated questions summary: ${questions.map { it.questionType }}")
        questions
    }

    fun submitAnswer(qId: String, answer: String) {
        viewModelScope.launch {
            val quiz = _state.value.currentQuiz ?: return@launch
            val q = quiz.questions.find { it.id == qId } ?: return@launch

            // Enhanced answer checking to handle common variations
            val normalizedUserAnswer = normalizeAnswer(answer.trim())
            val normalizedCorrectAnswer = normalizeAnswer(q.correctAnswer.trim())

            // Check for exact match first
            var correct = normalizedUserAnswer.equals(normalizedCorrectAnswer, ignoreCase = true)

            // Special handling for True/False questions
            if (!correct && q.questionType == QuestionType.TRUE_FALSE) {
                // Normalize True/False answers
                val userTrueFalse = when (normalizedUserAnswer) {
                    "true", "t", "yes", "y", "1" -> "true"
                    "false", "f", "no", "n", "0" -> "false"
                    else -> normalizedUserAnswer
                }
                val correctTrueFalse = when (normalizedCorrectAnswer) {
                    "true", "t", "yes", "y", "1" -> "true"
                    "false", "f", "no", "n", "0" -> "false"
                    else -> normalizedCorrectAnswer
                }
                correct = userTrueFalse == correctTrueFalse
                
                // Log potential question type mismatch
                if (!correct && !listOf("true", "false").contains(normalizedCorrectAnswer)) {
                    Timber.w("Question type mismatch detected - TRUE_FALSE question with non-boolean answer: '${q.correctAnswer}'")
                }
            }

            // For multiple choice, check if user answer matches any option
            if (!correct && q.questionType == QuestionType.MULTIPLE_CHOICE) {
                // Check if user selected the correct option by letter (a, b, c, d)
                if (normalizedUserAnswer in listOf("a", "b", "c", "d")) {
                    correct = normalizedUserAnswer == normalizedCorrectAnswer
                } else {
                    // User selected full text - find which option index it matches
                    val normalizedOptions = q.options.map { normalizeAnswer(it) }
                    val userOptionIndex = normalizedOptions.indexOf(normalizedUserAnswer)
                    
                    if (userOptionIndex != -1) {
                        // Convert option index to letter (0->a, 1->b, 2->c, 3->d)
                        val userSelectionLetter = ('a' + userOptionIndex).toString()
                        correct = userSelectionLetter == normalizedCorrectAnswer
                        Timber.d("Multiple choice - User selected option $userOptionIndex ('$userSelectionLetter'), correct is '$normalizedCorrectAnswer'")
                    } else {
                        // User typed something not in options - check semantic similarity
                        correct = checkAnswerVariations(normalizedUserAnswer, normalizedCorrectAnswer, q)
                    }
                }
            }
            
            // For fill-in-blank and short answer, check for acceptable variations
            if (!correct && (q.questionType == QuestionType.FILL_IN_BLANK ||
                        q.questionType == QuestionType.SHORT_ANSWER)) {
                correct = checkAnswerVariations(normalizedUserAnswer, normalizedCorrectAnswer, q)
                
                // Additional lenient check for 6th graders - accept if user mentions any key concept
                if (!correct) {
                    correct = checkSimpleKeywordMatch(normalizedUserAnswer, normalizedCorrectAnswer)
                }
                
                // Extra lenient check for geographic/demographic questions
                if (!correct) {
                    correct = checkGeographicConceptMatch(normalizedUserAnswer, normalizedCorrectAnswer, q)
                }
            }

            // Log for debugging with question type information
            Timber.d("Answer check - Type: ${q.questionType}, User: '$normalizedUserAnswer', Expected: '$normalizedCorrectAnswer', Correct: $correct")

            // Record the attempt
            quizRepo.recordQuestionAttempt(q, correct)
            if (!correct) {
                quizRepo.recordWrongAnswer(q, answer)
            }

            // Update progress with concepts
            quizRepo.recordProgress(
                quiz.subject,
                q.difficulty,
                correct,
                0L,
                q.conceptsCovered
            )

            // Track quiz interaction for analytics
            viewModelScope.launch {
                try {
                    // Get student ID - in real implementation, get from UserRepository/StudentIntegration
                    val studentId = "student_001" // Simplified for now
                    
                    analyticsRepository.recordInteraction(
                        studentId = studentId,
                        subject = quiz.subject.toString(),
                        topic = q.conceptsCovered.firstOrNull() ?: "General",
                        concept = q.questionText.take(50), // Use first part of question as concept
                        interactionType = InteractionType.QUIZ_COMPLETED,
                        sessionDurationMs = 0L, // Can be enhanced to track actual time
                        responseQuality = if (correct) 1.0f else 0.0f,
                        difficultyLevel = q.difficulty.name,
                        wasCorrect = correct,
                        attemptsNeeded = 1, // Can be enhanced to track multiple attempts
                        helpRequested = false,
                        followUpQuestions = 0
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to record quiz interaction for analytics")
                }
            }

            // Generate feedback that matches the correctness
            val feedback = if (correct) {
                generatePositiveFeedback(q, answer)
            } else {
                generateCorrectiveFeedback(q, answer)
            }

            val updated = quiz.copy(
                questions = quiz.questions.map {
                    if (it.id == qId) it.copy(
                        userAnswer = answer,
                        feedback = feedback,
                        isAnswered = true,
                        isCorrect = correct
                    ) else it
                }
            )
            _state.update { it.copy(currentQuiz = updated) }
        }
    }

    private suspend fun generatePositiveFeedback(q: Question, answer: String): String {
        val encouragements = listOf(
            "Great job! You nailed it!",
            "Excellent! You've mastered this concept.",
            "Perfect! Your understanding is solid.",
            "Wonderful! Keep up the great work.",
            "Fantastic! You really know this material."
        )

        val base = encouragements.random()
        return "$base ${q.explanation}"
    }

    private suspend fun generateCorrectiveFeedback(q: Question, answer: String): String {
        return "Not quite. The correct answer is '${q.correctAnswer}'. ${q.explanation}"
    }

    // Helper function to normalize answers
    private fun normalizeAnswer(answer: String): String {
        return answer
            .trim()
            .lowercase()
            // Remove common articles at the beginning
            .replace(Regex("^(the|a|an)\\s+"), "")
            // Remove punctuation
            .replace(Regex("[.,!?;:'\"-]"), "")
            // Normalize whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // 4. ENHANCED ANSWER VARIATIONS CHECK
    private fun checkAnswerVariations(userAnswer: String, correctAnswer: String, question: Question): Boolean {
        // Check for compound answers with parenthetical explanations
        val compoundAnswerPattern = """(.+?)\s*\([^)]*\)\s*(or|and)\s*(.+?)\s*\([^)]*\)""".toRegex()
        val singleAnswerPattern = """(.+?)\s*\([^)]*\)""".toRegex()
        
        // Extract main terms from compound answers
        val correctAnswerParts = when {
            compoundAnswerPattern.containsMatchIn(correctAnswer) -> {
                // Handle "term1 (explanation) or term2 (explanation)" format
                compoundAnswerPattern.find(correctAnswer)?.let { match ->
                    listOf(
                        normalizeAnswer(match.groupValues[1].trim()),
                        normalizeAnswer(match.groupValues[3].trim())
                    )
                } ?: listOf(normalizeAnswer(correctAnswer))
            }
            singleAnswerPattern.containsMatchIn(correctAnswer) -> {
                // Handle "term (explanation)" format
                singleAnswerPattern.find(correctAnswer)?.let { match ->
                    listOf(normalizeAnswer(match.groupValues[1].trim()))
                } ?: listOf(normalizeAnswer(correctAnswer))
            }
            correctAnswer.contains(" or ") -> {
                // Handle simple "term1 or term2" format
                correctAnswer.split(" or ").map { normalizeAnswer(it.trim()) }
            }
            correctAnswer.contains(" and ") -> {
                // Handle simple "term1 and term2" format
                correctAnswer.split(" and ").map { normalizeAnswer(it.trim()) }
            }
            else -> listOf(normalizeAnswer(correctAnswer))
        }
        
        // Check if user answer matches any of the acceptable parts
        if (correctAnswerParts.any { it == userAnswer || it.contains(userAnswer) || userAnswer.contains(it) }) {
            return true
        }

        // Direct variations mapping
        val answerVariations = mapOf(
            "industrial revolution" to listOf(
                "industrial revolution",
                "the industrial revolution",
                "industrialization",
                "industrial age",
                "industrial era"
            ),
            "photosynthesis" to listOf(
                "photosynthesis",
                "photo synthesis",
                "photosynthetic process",
                "the process of photosynthesis"
            ),
            "evaporation" to listOf(
                "evaporation",
                "evaporating",
                "water evaporation",
                "the evaporation process"
            ),
            "mitochondria" to listOf(
                "mitochondria",
                "mitochondrion",
                "the mitochondria",
                "mitochondrial"
            ),
            "karma" to listOf(
                "karma",
                "good deeds and bad deeds",
                "actions and consequences",
                "law of karma"
            ),
            "dharma" to listOf(
                "dharma",
                "righteous duty",
                "moral law",
                "religious duty"
            )
        )

        // Check if the user answer or any correct answer part has known variations
        val allAnswerParts = correctAnswerParts + listOf(normalizeAnswer(correctAnswer))
        for (answerPart in allAnswerParts) {
            val variations = answerVariations[answerPart] ?: emptyList()
            if (variations.any { normalizeAnswer(it) == userAnswer }) {
                return true
            }
        }

        // Enhanced semantic matching for better coverage
        val userWords = userAnswer.split(" ").filter { it.length > 2 }
        val correctWords = correctAnswerParts.flatMap { it.split(" ").filter { word -> word.length > 2 } }

        if (userWords.isEmpty() || correctWords.isEmpty()) {
            return false
        }

        // Semantic word mappings for better matching
        val semanticMappings = mapOf(
            "water" to listOf("water", "drinking", "irrigation", "hydration"),
            "food" to listOf("food", "farming", "agriculture", "crops", "harvest"),
            "transport" to listOf("transport", "transportation", "trade", "travel", "movement"),
            "provided" to listOf("provided", "gave", "supplied", "offered", "made"),
            "easier" to listOf("easier", "better", "improved", "facilitated"),
            "climate" to listOf("climate", "weather", "environment", "conditions"),
            "egyptian" to listOf("egyptian", "egypt", "ancient"),
            "nile" to listOf("nile", "river")
        )

        val matchingWords = userWords.count { userWord ->
            correctWords.any { correctWord ->
                // Direct match
                userWord == correctWord ||
                // Substring matching
                (userWord.length > 3 && correctWord.contains(userWord)) ||
                (correctWord.length > 3 && userWord.contains(correctWord)) ||
                // Prefix matching
                (userWord.length > 4 && correctWord.startsWith(userWord)) ||
                (correctWord.length > 4 && userWord.startsWith(correctWord)) ||
                // Semantic mapping check
                semanticMappings[userWord]?.contains(correctWord) == true ||
                semanticMappings[correctWord]?.contains(userWord) == true
            }
        }

        // Expanded key concepts for better coverage (more lenient for 6th graders)
        val keyConceptsSet = setOf(
            "water", "food", "transport", "transportation", "trade", "farming", "agriculture",
            "leader", "leadership", "ruler", "king", "queen", "government", "rule", "control",
            "egypt", "egyptian", "nile", "river", "flood", "flooding", "harvest", "planting",
            "ancient", "civilization", "empire", "kingdom", "city", "culture", "religion",
            "democracy", "republic", "monarchy", "organize", "organization", "skill", "skills",
            "communication", "roads", "canals", "harbors", "travel", "commerce", "goods",
            "agreement", "exchange", "infrastructure", "customers", "business", "economy"
        )
        
        val keyConceptsInUser = userWords.intersect(keyConceptsSet)
        val keyConceptsInCorrect = correctWords.intersect(keyConceptsSet)
        val conceptCoverage = if (keyConceptsInCorrect.isNotEmpty()) {
            keyConceptsInUser.size.toFloat() / keyConceptsInCorrect.size
        } else 0f

        val similarity = matchingWords.toFloat() / maxOf(userWords.size, correctWords.size)
        
        // More lenient thresholds for 6th graders:
        // - Accept if user mentions ANY key concept (even just one)
        // - Lower word similarity requirement
        // - Accept if user answer contains at least one important word
        val hasKeyWords = keyConceptsInUser.isNotEmpty()
        val hasReasonableSimilarity = similarity >= 0.3f  // Lowered from 0.6f
        val hasGoodConceptCoverage = conceptCoverage >= 0.5f  // Lowered from 0.7f
        
        return hasKeyWords || hasReasonableSimilarity || hasGoodConceptCoverage
    }

    /**
     * Enhanced keyword matching - accept if user demonstrates understanding of key concepts
     */
    private fun checkSimpleKeywordMatch(userAnswer: String, correctAnswer: String): Boolean {
        val userWords = userAnswer.split(" ").filter { it.length > 2 }.map { it.lowercase() }
        val correctWords = correctAnswer.split(" ").filter { it.length > 2 }.map { it.lowercase() }
        
        // Expanded key educational concepts across all subjects
        val importantConcepts = setOf(
            // History & Government
            "trade", "trading", "leader", "leadership", "ruler", "rule", "government", 
            "skill", "skills", "organization", "communicate", "communication",
            "roads", "infrastructure", "agriculture", "farming", "water", "nile",
            "egypt", "egyptian", "ancient", "civilization", "democracy", "republic",
            
            // Geography & Demographics
            "population", "people", "density", "coastal", "coast", "cities", "city",
            "urban", "rural", "mountain", "mountains", "plains", "rivers", "river",
            "fertile", "land", "resources", "climate", "migrate", "move", "settlement",
            "region", "area", "location", "northern", "southern", "eastern", "western",
            
            // Science & Nature
            "climate", "weather", "temperature", "precipitation", "ecosystem", "habitat",
            "species", "adaptation", "environment", "natural", "resources", "energy",
            
            // Economics & Social
            "economy", "economic", "jobs", "employment", "industry", "services",
            "culture", "cultural", "society", "social", "community", "family"
        )
        
        // Check for semantic word overlap (more lenient)
        val userConcepts = userWords.intersect(importantConcepts)
        val correctConcepts = correctWords.intersect(importantConcepts)
        
        // Accept if user mentions relevant concepts
        if (userConcepts.isNotEmpty() && correctConcepts.isNotEmpty()) {
            val hasOverlap = userConcepts.intersect(correctConcepts).isNotEmpty()
            val hasRelevantConcept = userConcepts.size >= 1
            return hasOverlap || hasRelevantConcept
        }
        
        // Additional lenient check: partial word matching for key terms
        val keyTermsInCorrect = correctWords.filter { word ->
            importantConcepts.any { concept -> word.contains(concept) || concept.contains(word) }
        }
        val keyTermsInUser = userWords.filter { word ->
            importantConcepts.any { concept -> word.contains(concept) || concept.contains(word) }
        }
        
        // Accept if user mentions any key geographic/demographic terms for population questions
        val isPopulationQuestion = correctWords.any { it in setOf("population", "density", "people", "coastal", "cities") }
        val userMentionsPopulationConcepts = userWords.any { it in setOf("people", "population", "cities", "coastal", "move", "southern", "northern") }
        
        return (keyTermsInCorrect.isNotEmpty() && keyTermsInUser.isNotEmpty()) ||
               (isPopulationQuestion && userMentionsPopulationConcepts)
    }

    /**
     * Special lenient checking for geographic and demographic questions
     */
    private fun checkGeographicConceptMatch(userAnswer: String, correctAnswer: String, question: Question): Boolean {
        val userWords = userAnswer.lowercase().split(" ").filter { it.length > 2 }
        val correctWords = correctAnswer.lowercase().split(" ").filter { it.length > 2 }
        
        // Check if this is a population/demographic question
        val isPopulationQuestion = correctWords.any { 
            it in setOf("population", "density", "people", "coastal", "cities", "plains", "rivers", "fertile", "mountainous") 
        }
        
        if (isPopulationQuestion) {
            // For population questions, accept if user shows understanding of:
            // 1. Where people live (coastal, cities, south, north, etc.)
            // 2. Why people live there (resources, fertile, etc.)
            val populationConcepts = setOf(
                "people", "population", "live", "move", "cities", "city", "urban",
                "coastal", "coast", "southern", "northern", "eastern", "western",
                "plains", "rivers", "fertile", "resources", "farming", "mountains",
                "density", "higher", "lower", "areas", "regions"
            )
            
            val userPopulationConcepts = userWords.intersect(populationConcepts)
            val correctPopulationConcepts = correctWords.intersect(populationConcepts)
            
            // Accept if user mentions relevant population concepts
            if (userPopulationConcepts.isNotEmpty() && correctPopulationConcepts.isNotEmpty()) {
                return true
            }
            
            // Specific pattern matching for population distribution answers
            val userMentionsLocation = userWords.any { it in setOf("southern", "coastal", "cities", "plains") }
            val correctMentionsLocation = correctWords.any { it in setOf("southern", "coastal", "cities", "plains") }
            
            if (userMentionsLocation && correctMentionsLocation) {
                return true
            }
        }
        
        // Check for climate/geography questions
        val isGeographyQuestion = correctWords.any {
            it in setOf("climate", "weather", "temperature", "rainfall", "desert", "forest", "mountain", "ocean")
        }
        
        if (isGeographyQuestion) {
            val geographyConcepts = setOf(
                "climate", "weather", "hot", "cold", "dry", "wet", "rain", "rainfall",
                "desert", "forest", "mountain", "ocean", "temperature", "season"
            )
            
            val userGeoConcepts = userWords.intersect(geographyConcepts)
            val correctGeoConcepts = correctWords.intersect(geographyConcepts)
            
            return userGeoConcepts.isNotEmpty() && correctGeoConcepts.isNotEmpty()
        }
        
        return false
    }

    /* ─────────────────────── Enhanced Question generation with retry ─────────────────────── */

    private suspend fun generateQuestionWithRetry(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        questionType: QuestionType,
        previousQuestions: List<String>,
        learnerProfile: LearnerProfile,
        maxRetries: Int = 3
    ): Question = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                // Check if we should use online or offline generation
                val useOnline = shouldUseOnlineService()
                val question = if (useOnline) {
                    try {
                        initializeApiServiceIfNeeded()
                        val onlineQuestions = onlineQuizGenerator.generateQuestionsOnline(
                            subject = subject,
                            topic = topic,
                            difficulty = difficulty,
                            questionTypes = listOf(questionType),
                            count = 1,
                            previousQuestions = previousQuestions,
                            studentName = _state.value.studentName,
                            gradeLevel = _state.value.studentGrade,
                            country = _state.value.studentCountry
                        )
                        onlineQuestions.firstOrNull() ?: throw Exception("No question generated online")
                    } catch (e: Exception) {
                        Timber.w(e, "Online generation failed, falling back to offline")
                        // Fallback to offline generation
                        generateOfflineQuestion(subject, topic, difficulty, questionType, learnerProfile, previousQuestions, attempt)
                    }
                } else {
                    generateOfflineQuestion(subject, topic, difficulty, questionType, learnerProfile, previousQuestions, attempt)
                }

                // Validate question quality
                if (question.questionText.lowercase().contains("sample") ||
                    question.questionText.length < 10 ||
                    question.correctAnswer.lowercase().contains("sample")) {
                    Timber.w("Low quality question detected, retrying...")
                    delay(200)
                    lastError = Exception("Low quality question")
                } else {
                    // Check similarity
                    val similarities = previousQuestions.map { prev ->
                        prev to calculateEnhancedSimilarity(question.questionText.lowercase(), prev.lowercase())
                    }
                    val maxSimilarity = similarities.maxOfOrNull { it.second } ?: 0f
                    val isTooSimilar = maxSimilarity > 0.7f

                    if (!isTooSimilar) {
                        Timber.d("Question accepted, max similarity: $maxSimilarity")
                        return@withContext question
                    } else {
                        val mostSimilar = similarities.maxByOrNull { it.second }
                        Timber.w("Question too similar (${maxSimilarity}): '${question.questionText.take(50)}...' vs '${mostSimilar?.first?.take(50)}...'")
                        lastError = Exception("Question too similar")
                    }
                }

                Timber.w("Question too similar, retrying with more variety...")

            } catch (e: Exception) {
                lastError = e
                Timber.w("Generation attempt ${attempt + 1} failed: ${e.message}")
            }

            delay(100L * (attempt + 1))
        }

        Timber.e(lastError, "All attempts failed, using quality fallback")
        return@withContext generateFallbackQuestionForType(questionType, difficulty)
    }

    private suspend fun generateOfflineQuestion(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        questionType: QuestionType,
        learnerProfile: LearnerProfile,
        previousQuestions: List<String>,
        attempt: Int
    ): Question = withContext(Dispatchers.IO) {
        val prompt = createStructuredPrompt(
            questionType = questionType,
            subject = subject,
            topic = topic,
            difficulty = difficulty,
            learnerProfile = learnerProfile,
            previousQuestions = previousQuestions,
            attemptNumber = attempt
        )

        // Use higher token limit to avoid truncation
        val response = gemmaService.generateTextAsync(
            prompt,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = 400, // Increased from 350
                temperature = 0.8f + (attempt * 0.05f),
                topK = 50,
                randomSeed = (System.currentTimeMillis() + attempt * 1000).toInt()
            )
        )

        // Validate response isn't truncated
        if (response.trim().endsWith("...") || !response.contains("}")) {
            throw Exception("Response truncated")
        }

        return@withContext parseAndValidateQuestion(response, questionType, difficulty)
    }

    private fun generateVariedFallback(
        questionType: QuestionType,
        difficulty: Difficulty,
        subject: Subject,
        previousQuestions: List<String>
    ): Question {
        val timestamp = System.currentTimeMillis()
        val randomElement = (timestamp % 100).toInt()

        return when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> {
                val templates = listOf(
                    "In the context of $subject, what happens when condition $randomElement occurs?",
                    "Considering concept ${randomElement + 10}, which statement is most accurate?",
                    "When analyzing element $randomElement in $subject, what is the primary characteristic?"
                )
                Question(
                    questionText = templates[randomElement % templates.size],
                    questionType = questionType,
                    options = listOf("Option A", "Option B", "Option C", "Option D"),
                    correctAnswer = "Option A",
                    explanation = "This is a fallback question.",
                    difficulty = difficulty
                )
            }
            else -> enhancedPromptManager.getFallbackQuestion(questionType, subject, difficulty)
        }
    }

    /* ─────────────────────── Improved similarity detection ─────────────────────── */

    private fun calculateEnhancedSimilarity(text1: String, text2: String): Float {
        val clean1 = text1.lowercase().replace(Regex("[^a-z0-9\\s]"), "")
        val clean2 = text2.lowercase().replace(Regex("[^a-z0-9\\s]"), "")

        // Don't compare very short strings
        if (clean1.length < 20 || clean2.length < 20) {
            return 0f
        }

        // Check exact substring match (very similar)
        if (clean1.contains(clean2) || clean2.contains(clean1)) {
            return 0.9f
        }

        // Word-based similarity
        val words1 = clean1.split("\\s+".toRegex()).filter { it.length > 3 }.toSet() // Changed from > 2 to > 3
        val words2 = clean2.split("\\s+".toRegex()).filter { it.length > 3 }.toSet()

        if (words1.isEmpty() || words2.isEmpty()) return 0f

        // Jaccard similarity
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        val jaccard = intersection.toFloat() / union

        // Also check for n-gram similarity (trigrams instead of bigrams)
        val trigrams1 = getTrigrams(clean1)
        val trigrams2 = getTrigrams(clean2)

        val trigramIntersection = trigrams1.intersect(trigrams2).size
        val trigramUnion = trigrams1.union(trigrams2).size
        val trigramSimilarity = if (trigramUnion > 0) {
            trigramIntersection.toFloat() / trigramUnion
        } else 0f

        // Weighted combination - slightly less aggressive
        return (jaccard * 0.5f + trigramSimilarity * 0.5f)
    }

    private fun getTrigrams(text: String): Set<String> {
        return if (text.length >= 3) {
            text.windowed(3, 1).toSet()
        } else {
            emptySet()
        }
    }

    private fun getBigrams(text: String): Set<String> {
        return text.windowed(2, 1).toSet()
    }

    /* ─────────────────────── Structured prompting for Gemma ─────────────────────── */
    private val questionVariationSeeds = listOf(
        "Imagine a scenario where",
        "Consider the case when",
        "In a practical situation",
        "From a different perspective",
        "Thinking critically about",
        "Analyzing the concept of",
        "Exploring the relationship between",
        "In the context of",
        "When applying",
        "Understanding how"
    )

    private val recentQuestionHashes = mutableSetOf<Int>()

    private fun createStructuredPrompt(
        questionType: QuestionType,
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        learnerProfile: LearnerProfile,
        previousQuestions: List<String>,
        attemptNumber: Int = 0
    ): String {
        val typeInstructions = when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> """
            Create a multiple-choice question with these EXACT requirements:
            1. "question": A clear, specific question (NOT "Sample question")
            2. "options": Array of EXACTLY 4 different answer choices
            3. "correctAnswer": Must be one of the 4 options
            4. "explanation": 1-2 sentences explaining why the answer is correct
            5. "hint": Optional helpful clue
            6. "conceptsCovered": Array of 1-3 concept tags
        """.trimIndent()

            QuestionType.TRUE_FALSE -> """
            Create a true/false question with these EXACT requirements:
            1. "question": A clear statement that is definitively true or false
            2. "options": ["True", "False"] 
            3. "correctAnswer": Either "True" or "False"
            4. "explanation": Why the statement is true/false
            5. "hint": Optional clue
            6. "conceptsCovered": Array of concept tags
        """.trimIndent()

            QuestionType.FILL_IN_BLANK -> """
            Create a fill-in-the-blank question with these EXACT requirements:
            1. "question": Sentence with ONE blank shown as _____ 
            2. "options": [] (empty array)
            3. "correctAnswer": The word/phrase that fills the blank (1-3 words)
            4. "explanation": Brief explanation
            5. "hint": Optional clue about the answer
            6. "conceptsCovered": Array of concept tags
        """.trimIndent()

            QuestionType.SHORT_ANSWER -> """
            Create a short-answer question with these EXACT requirements:
            1. "question": Open-ended question requiring brief explanation
            2. "options": [] (empty array)
            3. "correctAnswer": Concise 1-2 sentence answer
            4. "explanation": Additional context
            5. "hint": Optional guidance
            6. "conceptsCovered": Array of concept tags
        """.trimIndent()

            else -> ""
        }

        val examplesByType = mapOf(
            QuestionType.MULTIPLE_CHOICE to """
            {
              "question": "What causes tides in Earth's oceans?",
              "options": ["The Moon's gravity", "Earth's rotation", "Ocean currents", "Wind patterns"],
              "correctAnswer": "The Moon's gravity",
              "explanation": "The Moon's gravitational pull creates bulges in Earth's oceans.",
              "hint": "Think about what celestial body is closest to Earth.",
              "conceptsCovered": ["tides", "gravity", "moon"]
            }
        """.trimIndent(),

            QuestionType.TRUE_FALSE to """
            {
              "question": "Diamond is the hardest naturally occurring substance on Earth.",
              "options": ["True", "False"],
              "correctAnswer": "True",
              "explanation": "Diamond ranks 10 on the Mohs hardness scale.",
              "hint": "This substance is used in cutting tools.",
              "conceptsCovered": ["minerals", "hardness"]
            }
        """.trimIndent(),

            QuestionType.FILL_IN_BLANK to """
            {
              "question": "The smallest unit of life is the _____.",
              "options": [],
              "correctAnswer": "cell",
              "explanation": "Cells are the basic building blocks of all living things.",
              "hint": "Robert Hooke discovered these in cork.",
              "conceptsCovered": ["biology", "cells"]
            }
        """.trimIndent(),

            QuestionType.SHORT_ANSWER to """
            {
              "question": "What causes earthquakes?",
              "options": [],
              "correctAnswer": "Earthquakes are caused by the movement of tectonic plates releasing energy",
              "explanation": "Most occur along plate boundaries where plates interact.",
              "hint": "Think about the Earth's crust structure.",
              "conceptsCovered": ["geology", "earthquakes", "plate-tectonics"]
            }
        """.trimIndent()
        )

        // Create variation prompts
        val variations = listOf(
            "Focus on practical applications",
            "Test conceptual understanding",
            "Use a real-world scenario",
            "Challenge common misconceptions",
            "Connect to everyday experiences"
        )

        val selectedVariation = variations[attemptNumber % variations.size]

        return """
        TASK: Generate ONE $questionType question about $topic in $subject.
        
        $typeInstructions
        
        IMPORTANT RULES:
        - Make the question unique and interesting
        - $selectedVariation
        - Difficulty level: $difficulty
        - Do NOT use generic placeholders like "Sample question"
        - Do NOT use "Question text here" or similar
        - Create actual, meaningful content
        - Avoid these previous questions: ${previousQuestions.takeLast(2).joinToString("; ") { "\"${it.take(30)}...\"" }}
        
        EXAMPLE of correct format:
        ${examplesByType[questionType]}
        
        Now generate a completely different question following this exact JSON format:
    """.trimIndent()
    }

    /* ─────────────────────── Enhanced Structured prompting ─────────────────────── */

    private fun createEnhancedStructuredPrompt(
        questionType: QuestionType,
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        learnerProfile: LearnerProfile,
        previousQuestions: List<String>,
        attemptNumber: Int
    ): String {
        // Use the enhanced prompt manager for varied instructions
        val (instructions, exampleJson) = enhancedPromptManager.getVariedQuestionPrompt(
            questionType = questionType,
            subject = subject,
            topic = topic,
            difficulty = difficulty,
            attemptNumber = attemptNumber
        )

        val weakConcepts = learnerProfile.weaknessesByConcept.keys.take(3)
        val masteredConcepts = learnerProfile.masteredConcepts.take(5)

        // Add more variety with different prompt structures
        val promptStructures = listOf(
            // Structure 1: Story-based
            """
            Create a $questionType question for $subject about $topic.
            
            Frame it as: ${getScenarioContext(subject, topic, attemptNumber)}
            
            Requirements:
            - Difficulty: $difficulty
            - Make it practical and engaging
            - Test understanding, not memorization
            
            $instructions
            
            Example format (DO NOT copy content):
            $exampleJson
            
            Previous questions to avoid:
            ${previousQuestions.takeLast(5).joinToString("\n") { "- ${it.take(50)}..." }}
            
            Generate a unique question now:
            """.trimIndent(),

            // Structure 2: Problem-solving
            """
            Design a $difficulty $questionType problem for $subject.
            Topic: $topic
            
            Focus: ${getProblemFocus(subject, topic, attemptNumber)}
            
            Student profile:
            - Weak areas: ${if (weakConcepts.isNotEmpty()) weakConcepts.joinToString() else "none identified"}
            - Strong areas: ${if (masteredConcepts.isNotEmpty()) masteredConcepts.take(3).joinToString() else "developing"}
            
            $instructions
            
            Format example:
            $exampleJson
            
            Create an original question that's different from these:
            ${previousQuestions.takeLast(3).joinToString("\n") { "- ${it.take(40)}..." }}
            """.trimIndent(),

            // Structure 3: Conceptual
            """
            Generate ONE $questionType question.
            
            Subject: $subject - $topic
            Level: $difficulty
            Angle: ${getConceptualAngle(questionType, attemptNumber)}
            
            Guidelines:
            $instructions
            
            ${if (weakConcepts.isNotEmpty()) "Reinforce: ${weakConcepts.first()}" else ""}
            
            JSON format required:
            $exampleJson
            
            Make it unique - avoid similarity to:
            ${previousQuestions.takeLast(5).joinToString("\n") { "- \"${it.take(30)}...\"" }}
            """.trimIndent()
        )

        // Select a structure based on attempt number for variety
        return promptStructures[attemptNumber % promptStructures.size]
    }

    private fun getScenarioContext(subject: Subject, topic: String, attempt: Int): String {
        val scenarios = when (subject) {
            Subject.MATHEMATICS -> listOf(
                "A student solving a real-world problem",
                "A scientist analyzing data",
                "A game designer creating mechanics",
                "An architect planning a structure",
                "A chef adjusting a recipe",
                "A sports analyst reviewing statistics"
            )
            Subject.SCIENCE -> listOf(
                "A researcher conducting an experiment",
                "A doctor diagnosing a patient",
                "An engineer solving a problem",
                "A naturalist observing wildlife",
                "A weather forecaster analyzing patterns",
                "An inventor creating something new"
            )
            Subject.HISTORY -> listOf(
                "A historian analyzing primary sources",
                "A museum curator explaining an artifact",
                "A journalist reporting on past events",
                "An archaeologist making a discovery",
                "A diplomat learning from history",
                "A filmmaker researching a period"
            )
            else -> listOf(
                "Someone applying this knowledge",
                "A professional using this concept",
                "A student discovering something new",
                "A teacher explaining to others"
            )
        }

        return scenarios[attempt % scenarios.size] + " involving $topic"
    }

    private fun getProblemFocus(subject: Subject, topic: String, attempt: Int): String {
        val focuses = listOf(
            "practical application",
            "conceptual understanding",
            "problem-solving skills",
            "critical analysis",
            "creative thinking",
            "connecting ideas",
            "real-world relevance"
        )
        return focuses[attempt % focuses.size]
    }

    private fun getConceptualAngle(questionType: QuestionType, attempt: Int): String {
        val angles = when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> listOf(
                "best answer among similar options",
                "identifying the exception",
                "analyzing cause and effect",
                "comparing and contrasting",
                "applying knowledge to new situation"
            )
            QuestionType.TRUE_FALSE -> listOf(
                "common misconception",
                "subtle distinction",
                "general principle",
                "specific exception",
                "relationship between concepts"
            )
            else -> listOf(
                "explanation of concept",
                "application of knowledge",
                "analysis of situation",
                "synthesis of ideas"
            )
        }
        return angles[attempt % angles.size]
    }

    // Helper methods for variety
    private fun getRandomAngle(attempt: Int): String {
        val angles = listOf(
            "identifying the key difference",
            "finding the best solution",
            "analyzing the outcome",
            "determining the cause",
            "evaluating the method",
            "comparing alternatives"
        )
        return angles[attempt % angles.size]
    }

    private fun getRandomStyle(attempt: Int): String {
        val styles = listOf(
            "analytical",
            "practical problem",
            "conceptual understanding",
            "application-based",
            "scenario-driven",
            "comparative"
        )
        return styles[attempt % styles.size]
    }

    private fun getRandomContext(subject: Subject, attempt: Int): String {
        val contexts = when (subject) {
            Subject.MATHEMATICS -> listOf("real-world calculation", "pattern analysis", "problem-solving", "measurement")
            Subject.SCIENCE -> listOf("experimental", "observational", "hypothesis-testing", "analytical")
            else -> listOf("practical", "theoretical", "analytical", "contextual")
        }
        return contexts[attempt % contexts.size]
    }

    private fun getAvoidancePatterns(previousQuestions: List<String>): String {
        val patterns = previousQuestions
            .takeLast(3)
            .mapNotNull { q ->
                when {
                    q.contains("What is") -> "\"What is...\""
                    q.contains("Which") -> "\"Which...\""
                    q.contains("How many") -> "\"How many...\""
                    else -> null
                }
            }
            .distinct()
            .joinToString(", ")

        return patterns.ifEmpty { "common phrasings" }
    }

    private fun getTrueFalseFocus(attempt: Int): String {
        val focuses = listOf(
            "a specific property or characteristic",
            "a cause-and-effect relationship",
            "a general principle with exceptions",
            "a comparison between concepts",
            "a definition or classification"
        )
        return focuses[attempt % focuses.size]
    }

    private fun getFillBlankContext(attempt: Int): String {
        val contexts = listOf(
            "definition completion",
            "process description",
            "concept application",
            "relationship identification",
            "characteristic naming"
        )
        return contexts[attempt % contexts.size]
    }

    private fun getTestingFocus(attempt: Int): String {
        val focuses = listOf(
            "key terminology",
            "critical concept",
            "important relationship",
            "specific value or quantity",
            "process or method name"
        )
        return focuses[attempt % focuses.size]
    }

    private fun getRandomApproach(attempt: Int): String {
        val approaches = listOf(
            "explanation-focused",
            "comparison-based",
            "application-oriented",
            "analysis-driven",
            "synthesis-focused"
        )
        return approaches[attempt % approaches.size]
    }

    private fun getComplexityDescriptor(difficulty: Difficulty): String {
        return when (difficulty) {
            Difficulty.EASY -> "straightforward and clear"
            Difficulty.MEDIUM -> "moderately challenging with some nuance"
            Difficulty.HARD -> "complex with subtle distinctions"
            Difficulty.ADAPTIVE -> "appropriately challenging"
        }
    }

    private fun getQuestionTypeInstructions(questionType: QuestionType): Pair<String, String> {
        return when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> Pair(
                """
                Create a multiple choice question with:
                - A clear question stem
                - EXACTLY 4 answer options
                - Only ONE correct answer
                - 3 plausible distractors (wrong answers)
                
                Format: The question should naturally lead to choosing from options.
                Example: "Which organ is responsible for...?" or "What is the primary function of...?"
                """.trimIndent(),
                """
                {
                    "question": "What is the powerhouse of the cell?",
                    "options": ["Nucleus", "Mitochondria", "Ribosome", "Cell membrane"],
                    "correctAnswer": "Mitochondria",
                    "explanation": "Mitochondria produce ATP through cellular respiration.",
                    "hint": "This organelle is involved in energy production.",
                    "conceptsCovered": ["cell-biology", "organelles"]
                }
                """.trimIndent()
            )

            QuestionType.TRUE_FALSE -> Pair(
                """
                Create a TRUE/FALSE question with:
                - A simple, clear declarative statement
                - The statement must be definitively true or false
                - NO "which of the following" phrasing
                - options array should be ["True", "False"]
                """.trimIndent(),
                """
                {
                    "question": "All mammals lay eggs.",
                    "options": ["True", "False"],
                    "correctAnswer": "False",
                    "explanation": "Most mammals give birth to live young. Only monotremes like platypuses lay eggs.",
                    "hint": "Think about how most mammals reproduce.",
                    "conceptsCovered": ["mammal-reproduction", "animal-classification"]
                }
                """.trimIndent()
            )

            QuestionType.FILL_IN_BLANK -> Pair(
                """
                Create a fill-in-the-blank question with:
                - A sentence with ONE blank indicated by _____
                - The blank should be a key term or concept
                - The answer should be 1-3 words maximum
                - NO multiple choice phrasing
                - options array should be empty []
                - CRITICAL: correctAnswer MUST be the exact word/phrase that fills the blank
                
                Example: "The process by which plants make food using sunlight is called _____." (Answer: photosynthesis)
                """.trimIndent(),
                """
                {
                    "question": "The process of water changing from liquid to gas is called _____.",
                    "options": [],
                    "correctAnswer": "evaporation",
                    "explanation": "Evaporation occurs when water molecules gain enough energy to escape as vapor.",
                    "hint": "This happens when water is heated or exposed to air.",
                    "conceptsCovered": ["states-of-matter", "water-cycle"]
                }
                """.trimIndent()
            )

            QuestionType.SHORT_ANSWER -> Pair(
                """
                Create a short answer question with:
                - An open-ended question requiring a brief explanation
                - Answer should be 1-2 sentences
                - NO "which of the following" phrasing
                - options array should be empty []
                - correctAnswer should directly answer the question
                
                Example: "Explain the main function of red blood cells."
                """.trimIndent(),
                """
                {
                    "question": "Describe the water cycle.",
                    "options": [],
                    "correctAnswer": "The water cycle is the continuous movement of water through evaporation, condensation, and precipitation",
                    "explanation": "Water evaporates from bodies of water, forms clouds, and returns as rain or snow.",
                    "hint": "Think about how water moves between earth and atmosphere.",
                    "conceptsCovered": ["water-cycle", "earth-science"]
                }
                """.trimIndent()
            )

            else -> Pair(
                "Create a basic question appropriate for the topic.",
                """
                {
                    "question": "Sample question",
                    "options": [],
                    "correctAnswer": "Sample answer",
                    "explanation": "Sample explanation",
                    "hint": "Sample hint",
                    "conceptsCovered": ["general"]
                }
                """.trimIndent()
            )
        }
    }

    /* ─────────────────────── Parse and validate questions ─────────────────────── */

    private fun sanitizeJson(raw: String): String {
        var cleaned = raw
            .trim()
            .replace(Regex("^```(?:json)?\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\s*```$", RegexOption.MULTILINE), "")
            .trim()

        // Remove any text before the first {
        val jsonStart = cleaned.indexOf('{')
        if (jsonStart > 0) {
            cleaned = cleaned.substring(jsonStart)
        }

        // Remove any text after the last }
        val jsonEnd = cleaned.lastIndexOf('}')
        if (jsonEnd != -1 && jsonEnd < cleaned.length - 1) {
            cleaned = cleaned.substring(0, jsonEnd + 1)
        }

        // Fix common JSON issues - using simple string replacements to avoid regex issues
        // Remove trailing commas before closing braces
        cleaned = cleaned.replace(Regex(",\\s*}"), "}")
        // Remove trailing commas before closing brackets
        cleaned = cleaned.replace(Regex(",\\s*]"), "]")

        // Fix escaped quotes
        cleaned = cleaned.replace("\\\\\"", "\\\"")

        // Remove newlines within quoted strings (more complex, needs careful handling)
        val sb = StringBuilder()
        var inString = false
        var escapeNext = false
        var prevChar = ' '

        for (char in cleaned) {
            when {
                escapeNext -> {
                    sb.append(char)
                    escapeNext = false
                }
                char == '\\' -> {
                    sb.append(char)
                    escapeNext = true
                }
                char == '"' && prevChar != '\\' -> {
                    sb.append(char)
                    inString = !inString
                }
                char == '\n' && inString -> {
                    sb.append(' ') // Replace newline with space inside strings
                }
                else -> {
                    sb.append(char)
                }
            }
            prevChar = char
        }
        cleaned = sb.toString()

        // Balance braces if needed
        val openBraces = cleaned.count { it == '{' }
        val closeBraces = cleaned.count { it == '}' }
        if (openBraces > closeBraces) {
            cleaned += "}".repeat(openBraces - closeBraces)
        }

        return cleaned
    }

    private fun sanitizeJsonSimple(raw: String): String {
        // Step 1: Extract JSON content
        var content = raw.trim()

        // Remove markdown code blocks
        if (content.startsWith("```")) {
            val startIdx = content.indexOf('\n')
            if (startIdx != -1) {
                content = content.substring(startIdx + 1)
            }
        }
        if (content.endsWith("```")) {
            val endIdx = content.lastIndexOf("```")
            if (endIdx != -1) {
                content = content.substring(0, endIdx)
            }
        }

        // Find the JSON object boundaries
        val firstBrace = content.indexOf('{')
        val lastBrace = content.lastIndexOf('}')

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            content = content.substring(firstBrace, lastBrace + 1)
        }

        // Simple cleanup without regex
        val result = StringBuilder()
        var inString = false
        var escapeNext = false
        var lastNonWhitespace = ' '

        for (i in content.indices) {
            val char = content[i]
            val nextChar = if (i < content.length - 1) content[i + 1] else ' '

            when {
                escapeNext -> {
                    result.append(char)
                    escapeNext = false
                }
                char == '\\' && inString -> {
                    result.append(char)
                    escapeNext = true
                }
                char == '"' && !escapeNext -> {
                    result.append(char)
                    inString = !inString
                }
                char == '\n' && inString -> {
                    result.append(' ') // Replace newlines in strings with space
                }
                char == ',' && !inString && (nextChar == '}' || nextChar == ']') -> {
                    // Skip trailing commas
                }
                else -> {
                    result.append(char)
                }
            }

            if (!char.isWhitespace()) {
                lastNonWhitespace = char
            }
        }

        return result.toString()
    }

    private fun parseAndValidateQuestion(
        raw: String,
        expectedType: QuestionType,
        difficulty: Difficulty
    ): Question {
        return try {
            Timber.d("Raw response length: ${raw.length} characters")

            val preprocessed = preprocessModelResponse(raw)
            val cleaned = sanitizeJsonSimple(preprocessed) // Use simple version

            // Log cleaned JSON for debugging
            Timber.d("Cleaned JSON: ${cleaned.take(200)}...")

            // Check if JSON was truncated
            if (cleaned.contains("...") && !cleaned.contains("explanation")) {
                Timber.w("JSON appears truncated, using fallback")
                return generateFallbackQuestionForType(expectedType, difficulty)
            }

            // Additional validation
            if (!cleaned.trim().startsWith("{") || !cleaned.trim().endsWith("}")) {
                Timber.e("Invalid JSON structure: starts with '${cleaned.take(10)}', ends with '${cleaned.takeLast(10)}'")
                return generateFallbackQuestionForType(expectedType, difficulty)
            }

            val obj = org.json.JSONObject(cleaned)

            // Extract fields with proper validation
            val questionText = obj.optString("question", "").trim()
            if (questionText.isEmpty() || questionText == "Sample question" ||
                questionText.length < 10 || questionText.contains("question here")) {
                Timber.e("Invalid question text: '$questionText'")
                return generateFallbackQuestionForType(expectedType, difficulty)
            }

            val correctAnswer = obj.optString("correctAnswer", "").trim()
            if (correctAnswer.isEmpty() || correctAnswer == "Sample answer" ||
                correctAnswer.contains("answer here")) {
                Timber.e("Invalid correct answer: '$correctAnswer'")
                return generateFallbackQuestionForType(expectedType, difficulty)
            }

            // For multiple choice, ensure we have valid options
            val options = when (expectedType) {
                QuestionType.MULTIPLE_CHOICE -> {
                    val opts = try {
                        obj.optJSONArray("options")?.let { arr ->
                            List(arr.length()) { arr.getString(it).trim() }
                                .filter { it.isNotBlank() && !it.startsWith("Option") }
                        } ?: emptyList()
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing options array")
                        emptyList()
                    }

                    if (opts.size < 4) {
                        Timber.w("Insufficient options (${opts.size}), using fallback")
                        return generateFallbackQuestionForType(expectedType, difficulty)
                    }

                    // Ensure correct answer is in options
                    if (!opts.contains(correctAnswer)) {
                        Timber.w("Correct answer '$correctAnswer' not in options: $opts")
                        val adjustedOpts = listOf(correctAnswer) + opts.filter { it != correctAnswer }.take(3)
                        adjustedOpts.shuffled()
                    } else {
                        opts.take(4)
                    }
                }
                QuestionType.TRUE_FALSE -> listOf("True", "False")
                else -> emptyList()
            }

            // Build the question
            val question = Question(
                questionText = questionText,
                questionType = expectedType,
                options = options,
                correctAnswer = correctAnswer,
                explanation = obj.optString("explanation", "").takeIf { it.isNotBlank() }
                    ?: "No explanation provided.",
                hint = obj.optString("hint").takeIf { it.isNotBlank() },
                conceptsCovered = try {
                    obj.optJSONArray("conceptsCovered")?.let { arr ->
                        List(arr.length()) { arr.getString(it) }
                    } ?: listOf("general")
                } catch (e: Exception) {
                    listOf("general")
                },
                difficulty = difficulty
            )

            Timber.d("Successfully parsed ${expectedType} question: '${question.questionText.take(50)}...'")
            question

        } catch (e: Exception) {
            Timber.e(e, "Failed to parse question JSON, using fallback")
            generateFallbackQuestionForType(expectedType, difficulty)
        }
    }

    private fun tryExtractFieldsFromPartialJson(
        raw: String,
        expectedType: QuestionType,
        difficulty: Difficulty
    ): Question? {
        return try {
            // Try regex extraction as last resort
            val questionPattern = Regex(""""question"\s*:\s*"([^"]+)"""")
            val answerPattern = Regex(""""correctAnswer"\s*:\s*"([^"]+)"""")

            val questionMatch = questionPattern.find(raw)
            val answerMatch = answerPattern.find(raw)

            if (questionMatch != null && answerMatch != null) {
                val questionText = questionMatch.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\n", " ")
                    .trim()

                val correctAnswer = answerMatch.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\n", " ")
                    .trim()

                // Don't accept placeholder values
                if (questionText.contains("Sample") || correctAnswer.contains("Sample") ||
                    questionText.contains("question here") || correctAnswer.contains("answer here")) {
                    return null
                }

                Question(
                    questionText = questionText,
                    questionType = expectedType,
                    options = when (expectedType) {
                        QuestionType.MULTIPLE_CHOICE -> {
                            // Try to extract options
                            val optionsPattern = Regex(""""options"\s*:\s*\[([^\]]+)\]""")
                            optionsPattern.find(raw)?.let { match ->
                                match.groupValues[1]
                                    .split(",")
                                    .mapNotNull { opt ->
                                        Regex(""""([^"]+)"""").find(opt)?.groupValues?.get(1)
                                    }
                                    .filter { it.isNotBlank() }
                                    .take(4)
                            } ?: listOf("A", "B", "C", "D")
                        }
                        QuestionType.TRUE_FALSE -> listOf("True", "False")
                        else -> emptyList()
                    },
                    correctAnswer = correctAnswer,
                    explanation = "Generated question",
                    difficulty = difficulty
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // Add helper to check if JSON is complete
    private fun isCompleteJson(json: String): Boolean {
        if (json.isBlank()) return false

        var depth = 0
        var inString = false
        var escapeNext = false

        for (char in json) {
            when {
                escapeNext -> escapeNext = false
                char == '\\' && inString -> escapeNext = true
                char == '"' && !escapeNext -> inString = !inString
                !inString -> {
                    when (char) {
                        '{', '[' -> depth++
                        '}', ']' -> depth--
                    }
                }
            }

            if (depth < 0) return false
        }

        return depth == 0 && !inString
    }

    private fun tryExtractFromRawText(
        raw: String,
        expectedType: QuestionType,
        difficulty: Difficulty
    ): Question? {
        // Try to find question and answer patterns in the raw text
        val questionPattern = Regex("\"question\":\"(.*?)\"")
        val answerPattern = Regex("\"correctAnswer\":\"(.*?)\"")

        val questionMatch = questionPattern.find(raw)
        val answerMatch = answerPattern.find(raw)

        if (questionMatch != null && answerMatch != null) {
            val questionText = questionMatch.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\n", " ")
                .trim()

            val correctAnswer = answerMatch.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\n", " ")
                .trim()

            // Create a minimal valid question
            return Question(
                questionText = questionText,
                questionType = expectedType,
                options = when (expectedType) {
                    QuestionType.MULTIPLE_CHOICE -> listOf("A", "B", "C", "D")
                    QuestionType.TRUE_FALSE -> listOf("True", "False")
                    else -> emptyList()
                },
                correctAnswer = correctAnswer,
                explanation = "Generated from raw text due to JSON parsing error",
                hint = null,
                conceptsCovered = listOf("general"),
                difficulty = difficulty
            )
        }
        return null
    }

    /* ─────────────────────── Validate questions before adding ─────────────────────── */

    /**
     * Generate contextually relevant fallback options for multiple choice questions
     */
    private fun generateFallbackOptions(correctAnswer: String, questionText: String): List<String> {
        val fallbackOptions = mutableListOf(correctAnswer)
        val questionLower = questionText.lowercase()
        
        // Generate subject-specific wrong answers based on question context
        when {
            questionLower.contains("year") || questionLower.contains("date") || questionLower.contains("century") -> {
                // For time-related questions
                fallbackOptions.addAll(listOf(
                    "1500 BCE", "500 CE", "1200 CE", "1800 CE"
                ).filter { it != correctAnswer }.take(3))
            }
            questionLower.contains("river") || questionLower.contains("nile") -> {
                // For geography/river questions  
                fallbackOptions.addAll(listOf(
                    "Amazon River", "Mississippi River", "Yangtze River", "Nile River"
                ).filter { it != correctAnswer }.take(3))
            }
            questionLower.contains("egypt") || questionLower.contains("pharaoh") -> {
                // For Egyptian history
                fallbackOptions.addAll(listOf(
                    "Mesopotamia", "Ancient Greece", "Roman Empire", "Persian Empire"
                ).filter { it != correctAnswer }.take(3))
            }
            questionLower.contains("government") || questionLower.contains("democracy") -> {
                // For government questions
                fallbackOptions.addAll(listOf(
                    "Monarchy", "Democracy", "Republic", "Theocracy"
                ).filter { it != correctAnswer }.take(3))
            }
            else -> {
                // Generic fallbacks
                fallbackOptions.addAll(listOf(
                    "Option A", "Option B", "Option C"
                ))
            }
        }
        
        // Ensure we have exactly 4 options
        while (fallbackOptions.size < 4) {
            fallbackOptions.add("Additional Option ${fallbackOptions.size}")
        }
        
        return fallbackOptions.take(4).shuffled()
    }

    /**
     * Detect the actual question type based on the content to prevent mismatches
     */
    private fun detectActualQuestionType(question: Question): QuestionType {
        val normalizedAnswer = normalizeAnswer(question.correctAnswer)
        val questionText = question.questionText.lowercase()
        
        // Check for "which of the following" format questions (should be multiple choice)
        if (questionText.contains("which of the following") || 
            questionText.contains("which one of the following") ||
            questionText.contains("which among the following") ||
            questionText.contains("select all that apply") ||
            questionText.contains("choose the correct option") ||
            questionText.contains("choose the best answer")) {
            return QuestionType.MULTIPLE_CHOICE
        }
        
        // Check for True/False patterns
        if (listOf("true", "false", "t", "f", "yes", "no", "y", "n").contains(normalizedAnswer)) {
            return QuestionType.TRUE_FALSE
        }
        
        // Check for fill-in-the-blank patterns (short single answers)
        if (question.questionText.contains("_____") || question.questionText.contains("___")) {
            return QuestionType.FILL_IN_BLANK
        }
        
        // Check for multiple choice (has options)
        if (question.options.size >= 2) {
            return QuestionType.MULTIPLE_CHOICE
        }
        
        // Check for long answers (likely short answer)
        if (question.correctAnswer.split(" ").size > 3) {
            return QuestionType.SHORT_ANSWER
        }
        
        // Default to original type if can't determine
        return question.questionType
    }

    private fun validateQuestion(question: Question): Question {
        // First, detect and fix potential question type mismatches
        val actualQuestionType = detectActualQuestionType(question)
        val correctedQuestion = if (actualQuestionType != question.questionType) {
            Timber.w("Question type mismatch detected: expected ${question.questionType}, but content suggests ${actualQuestionType}. Auto-correcting.")
            question.copy(questionType = actualQuestionType)
        } else {
            question
        }
        
        return when (correctedQuestion.questionType) {
            QuestionType.MULTIPLE_CHOICE -> {
                // For multiple choice, we MUST have 4 valid options
                val validOptions = when {
                    correctedQuestion.options.isEmpty() -> {
                        // Generate contextually relevant options if none provided
                        Timber.w("Multiple choice question has no options, generating contextual fallbacks")
                        generateFallbackOptions(correctedQuestion.correctAnswer, correctedQuestion.questionText)
                    }
                    correctedQuestion.options.size < 4 -> {
                        // Pad options to 4
                        val opts = correctedQuestion.options.toMutableList()
                        if (!opts.contains(correctedQuestion.correctAnswer)) {
                            opts.add(0, correctedQuestion.correctAnswer)
                        }
                        while (opts.size < 4) {
                            opts.add("Option ${('A' + opts.size)}")
                        }
                        opts.shuffled()
                    }
                    correctedQuestion.options.size > 4 -> {
                        // Trim to 4 options, ensuring correct answer is included
                        val opts = if (correctedQuestion.options.contains(correctedQuestion.correctAnswer)) {
                            correctedQuestion.options.take(4)
                        } else {
                            listOf(correctedQuestion.correctAnswer) + correctedQuestion.options.take(3)
                        }.shuffled()
                        opts
                    }
                    else -> {
                        // Ensure correct answer is in options
                        if (!correctedQuestion.options.contains(correctedQuestion.correctAnswer)) {
                            (listOf(correctedQuestion.correctAnswer) + correctedQuestion.options.take(3)).shuffled()
                        } else {
                            correctedQuestion.options
                        }
                    }
                }

                correctedQuestion.copy(options = validOptions)
            }

            QuestionType.TRUE_FALSE -> {
                // Ensure only True/False options and validate correctAnswer
                val normalizedCorrect = normalizeAnswer(correctedQuestion.correctAnswer)
                val validTrueFalseAnswer = when {
                    normalizedCorrect == "true" || normalizedCorrect == "t" || normalizedCorrect == "yes" || normalizedCorrect == "y" -> "True"
                    normalizedCorrect == "false" || normalizedCorrect == "f" || normalizedCorrect == "no" || normalizedCorrect == "n" -> "False"
                    else -> {
                        // Log warning for potential data quality issue
                        Timber.w("TRUE_FALSE question with non-boolean answer: '${correctedQuestion.correctAnswer}'. Defaulting to 'True'")
                        "True"
                    }
                }
                
                correctedQuestion.copy(
                    options = listOf("True", "False"),
                    correctAnswer = validTrueFalseAnswer
                )
            }

            QuestionType.FILL_IN_BLANK,
            QuestionType.SHORT_ANSWER -> {
                // Ensure no options for text input questions
                correctedQuestion.copy(options = emptyList())
            }

            else -> correctedQuestion
        }
    }


    /* ─────────────────────── Enhanced feedback generation ─────────────────────── */

    private suspend fun generateEnhancedFeedback(
        q: Question,
        answer: String,
        correct: Boolean
    ): String = withContext(Dispatchers.IO) {

        // Get past wrong answers for this concept
        val pastMistakes = if (!correct && q.conceptsCovered.isNotEmpty()) {
            val concept = q.conceptsCovered.first()
            quizRepo.wrongAnswerDao().getRecentWrongAnswersByConcept("%$concept%", 3)
        } else emptyList()

        val prompt = if (correct) {
            """Encourage the student for correctly answering: "${q.questionText}"
            Make it personal and motivating in 50 words or less."""
        } else {
            """The student answered "$answer" but the correct answer is "${q.correctAnswer}" 
            for the question "${q.questionText}".
            
            ${if (pastMistakes.isNotEmpty()) {
                "Note: Student has made similar mistakes before:\n" +
                        pastMistakes.take(2).joinToString("\n") {
                            "- Answered '${it.userAnswer}' instead of '${it.correctAnswer}'"
                        }
            } else ""}
            
            Provide a clear, encouraging explanation in 75 words or less that:
            1. Explains why their answer is incorrect
            2. Clarifies the correct answer
            3. ${if (pastMistakes.isNotEmpty()) "Addresses the pattern of mistakes" else "Gives a memory tip"}
            """
        }

        try {
            gemmaService.generateTextAsync(
                prompt,
                UnifiedGemmaService.GenerationConfig(
                    maxTokens = 150,
                    temperature = 0.7f
                )
            )
        } catch (_: Exception) {
            if (correct) {
                "Great job! You're mastering ${q.conceptsCovered.firstOrNull() ?: "this concept"}!"
            } else {
                "Not quite. The correct answer is ${q.correctAnswer}. " +
                        if (pastMistakes.isNotEmpty()) {
                            "This is a common mistake - try to remember: ${q.explanation}"
                        } else {
                            q.explanation
                        }
            }
        }
    }

    /* ─────────────────────── Better question type selection ─────────────────────── */

    private fun selectQuestionTypeWithVariety(index: Int, previousTypes: List<QuestionType>): QuestionType {
        // Avoid repeating the same type too often
        val recentTypes = previousTypes.takeLast(3)

        // Weighted distribution
        val baseWeights = mapOf(
            QuestionType.MULTIPLE_CHOICE to 35,
            QuestionType.TRUE_FALSE to 25,
            QuestionType.FILL_IN_BLANK to 25,
            QuestionType.SHORT_ANSWER to 15
        )

        // Adjust weights based on recent usage
        val adjustedWeights = baseWeights.mapValues { (type, weight) ->
            val recentCount = recentTypes.count { it == type }
            when (recentCount) {
                0 -> weight + 10  // Boost if not used recently
                1 -> weight       // Normal weight
                2 -> weight / 2   // Reduce if used twice
                else -> weight / 4 // Strongly reduce if used 3 times
            }
        }

        // Add some randomness
        val totalWeight = adjustedWeights.values.sum()
        var random = Random.nextInt(totalWeight)

        for ((type, weight) in adjustedWeights) {
            random -= weight
            if (random < 0) {
                return type
            }
        }

        // Fallback with forced variety
        return QuestionType.entries.first { it !in recentTypes }
    }

    /* ─────────────────────── Parallel generation with diversity ─────────────────────── */

    suspend fun generateDiverseQuestionsParallel(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        count: Int,
        previousQuestions: List<String>,
        learnerProfile: LearnerProfile
    ): List<Question> = withContext(Dispatchers.Default) {
        val questions = mutableListOf<Question>()
        val usedTypes = mutableListOf<QuestionType>()

        // Generate in smaller batches for better variety
        val batchSize = 3
        val batches = (count + batchSize - 1) / batchSize

        for (batch in 0 until batches) {
            val batchQuestions = coroutineScope {
                (0 until minOf(batchSize, count - batch * batchSize)).map { indexInBatch ->
                    val globalIndex = batch * batchSize + indexInBatch
                    async {
                        val questionType = selectQuestionTypeWithVariety(globalIndex, usedTypes)
                        usedTypes.add(questionType)

                        // Add delay between questions in same batch for variety
                        delay(indexInBatch * 100L)

                        generateSingleQuestionWithDiversity(
                            subject = subject,
                            topic = topic,
                            difficulty = difficulty,
                            questionType = questionType,
                            previousQuestions = previousQuestions + questions.map { it.questionText },
                            learnerProfile = learnerProfile,
                            attemptNumber = globalIndex,
                            diversitySeed = System.currentTimeMillis() + globalIndex * 1000
                        )
                    }
                }.awaitAll().filterNotNull()
            }

            questions.addAll(batchQuestions)

            // Update state after each batch
            _state.update { it.copy(questionsGenerated = questions.size) }

            // Small delay between batches
            if (batch < batches - 1) {
                delay(200)
            }
        }

        return@withContext questions
    }

    private suspend fun generateSingleQuestionWithDiversity(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        questionType: QuestionType,
        previousQuestions: List<String>,
        learnerProfile: LearnerProfile,
        attemptNumber: Int,
        diversitySeed: Long
    ): Question? {
        var lastError: Exception? = null

        repeat(3) { retryCount ->
            try {
                // Use enhanced prompt with more variety
                val prompt = createEnhancedStructuredPrompt(
                    questionType = questionType,
                    subject = subject,
                    topic = topic,
                    difficulty = difficulty,
                    learnerProfile = learnerProfile,
                    previousQuestions = previousQuestions,
                    attemptNumber = attemptNumber + retryCount // Vary prompt on retry
                )

                // Vary generation parameters more
                val temperature = when (retryCount) {
                    0 -> 0.7f + (attemptNumber % 3) * 0.1f  // 0.7-0.9
                    1 -> 0.8f + (attemptNumber % 4) * 0.05f // 0.8-0.95
                    else -> 0.9f + Random.nextFloat() * 0.1f // 0.9-1.0
                }

                val response = gemmaService.generateTextAsync(
                    prompt,
                    UnifiedGemmaService.GenerationConfig(
                        maxTokens = 350,
                        temperature = temperature,
                        topK = 40 + (attemptNumber % 20), // Vary topK
                        randomSeed = (diversitySeed + retryCount * 1000).toInt()
                    )
                )

                val question = parseAndValidateQuestion(response, questionType, difficulty)

                // Enhanced similarity check
                val isTooSimilar = previousQuestions.any { prev ->
                    calculateEnhancedSimilarity(question.questionText, prev) > 0.7f
                }

                if (!isTooSimilar) {
                    return question
                }

                Timber.w("Question too similar (attempt ${retryCount + 1}), regenerating...")

            } catch (e: Exception) {
                lastError = e
                Timber.w("Generation attempt ${retryCount + 1} failed: ${e.message}")
            }

            // Exponential backoff
            delay(100L * (retryCount + 1))
        }

        Timber.e(lastError, "All generation attempts failed")
        return null
    }

    /* ───── Helper functions ───── */

    private fun recreateQuestionFromHistory(history: QuestionHistory): Question {
        val concepts = try {
            gson.fromJson(history.conceptsCovered, Array<String>::class.java).toList()
        } catch (_: Exception) {
            listOf("review")
        }

        return Question(
            id = history.questionId,
            questionText = history.questionText + " (Review)",
            questionType = QuestionType.SHORT_ANSWER, // Could be stored in history
            correctAnswer = "Review question - check original",
            explanation = "This is a review question you've seen before.",
            conceptsCovered = concepts,
            difficulty = history.difficulty,
            lastSeenAt = history.lastAttemptedAt
        )
    }

    private fun updateConceptCoverage(
        coverage: MutableMap<String, Int>,
        concepts: List<String>
    ) {
        concepts.forEach { concept ->
            coverage[concept] = (coverage[concept] ?: 0) + 1
        }
    }

    private fun calculateQuizScore(quiz: Quiz): Float {
        val answered = quiz.questions.filter { it.isAnswered }
        if (answered.isEmpty()) return 0f

        val correct = answered.count { it.userAnswer == it.correctAnswer }
        return (correct.toFloat() / answered.size) * 100f
    }

    // Better question type selection with weighted distribution
    private fun selectQuestionType(index: Int): QuestionType {
        // Check if student grade is set for grade-appropriate selection
        val gradeLevel = _state.value.studentGrade

        return if (gradeLevel != null) {
            // Grade-appropriate distribution
            when (gradeLevel) {
                in 0..2 -> {
                    // Very young: mostly multiple choice and true/false
                    val weights = mapOf(
                        QuestionType.MULTIPLE_CHOICE to 60,
                        QuestionType.TRUE_FALSE to 40
                    )
                    selectFromWeightedDistribution(weights)
                }
                in 3..5 -> {
                    // Elementary: add fill in blank
                    val weights = mapOf(
                        QuestionType.MULTIPLE_CHOICE to 50,
                        QuestionType.TRUE_FALSE to 30,
                        QuestionType.FILL_IN_BLANK to 20
                    )
                    selectFromWeightedDistribution(weights)
                }
                in 6..8 -> {
                    // Middle school: balanced
                    val weights = mapOf(
                        QuestionType.MULTIPLE_CHOICE to 40,
                        QuestionType.TRUE_FALSE to 25,
                        QuestionType.FILL_IN_BLANK to 20,
                        QuestionType.SHORT_ANSWER to 15
                    )
                    selectFromWeightedDistribution(weights)
                }
                else -> {
                    // High school: all types
                    val weights = mapOf(
                        QuestionType.MULTIPLE_CHOICE to 40,
                        QuestionType.TRUE_FALSE to 25,
                        QuestionType.FILL_IN_BLANK to 20,
                        QuestionType.SHORT_ANSWER to 15
                    )
                    selectFromWeightedDistribution(weights)
                }
            }
        } else {
            // Default weighted distribution if no grade set
            val weights = mapOf(
                QuestionType.MULTIPLE_CHOICE to 40,  // 40% chance
                QuestionType.TRUE_FALSE to 25,       // 25% chance
                QuestionType.FILL_IN_BLANK to 20,    // 20% chance
                QuestionType.SHORT_ANSWER to 15      // 15% chance
            )
            selectFromWeightedDistribution(weights)
        }
    }

    private fun selectFromWeightedDistribution(weights: Map<QuestionType, Int>): QuestionType {
        val random = (0..99).random()
        var cumulative = 0

        for ((type, weight) in weights) {
            cumulative += weight
            if (random < cumulative) {
                return type
            }
        }

        // Fallback
        return QuestionType.MULTIPLE_CHOICE
    }

    // Updated generateFallbackQuestionForType to use curriculum fallbacks
    private fun generateFallbackQuestionForType(
        questionType: QuestionType,
        difficulty: Difficulty
    ): Question {
        val subject = _state.value.currentQuiz?.subject ?: Subject.GENERAL
        val gradeLevel = _state.value.studentGrade ?: 5

        // Use curriculum-aware fallback
        return curriculumQuizGenerator.generateCurriculumFallback(
            subject = subject,
            topic = CurriculumAwareQuizGenerator.CurriculumTopic(
                title = "General Knowledge",
                gradeRange = "Grade $gradeLevel",
                phase = ""
            ),
            gradeLevel = gradeLevel,
            questionType = questionType,
            difficulty = difficulty
        )
    }

    // Helper to get recent questions
    private suspend fun getRecentQuestionTexts(subject: Subject, topic: String): List<String> {
        val studentCountry = _state.value.studentCountry
        
        // If we have country context, be more selective about historical questions
        if (studentCountry != null && (subject == Subject.HISTORY || subject == Subject.GEOGRAPHY)) {
            return getCountryFilteredQuestionTexts(subject, topic, studentCountry)
        }
        // Get questions from the last week across ALL topics for this subject
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

        // Get all recent quizzes for this subject (not just this topic)
        val recentQuizzes = quizRepo.getAllQuizzes()
            .first() // Get the first emission
            .filter { quiz ->
                quiz.subject == subject &&
                        quiz.createdAt > oneWeekAgo
            }
            .sortedByDescending { it.createdAt }
            .take(10) // Last 10 quizzes

        // Extract all question texts
        val allRecentQuestions = recentQuizzes
            .flatMap { it.questions }
            .map { it.questionText }
            .distinct()

        // Also get questions specifically from this topic
        val topicQuestions = if (topic.isNotBlank()) {
            quizRepo.getQuizzesFor(subject, topic)
                .flatMap { it.questions }
                .map { it.questionText }
        } else {
            emptyList()
        }

        // Combine and deduplicate
        val finalQuestions = (allRecentQuestions + topicQuestions)
            .distinct()
            .takeLast(50) // Keep more questions for better deduplication
            
        // Debug logging
        Timber.d("Recent question deduplication: Found ${finalQuestions.size} recent questions for $subject/$topic")
        finalQuestions.take(3).forEach { q ->
            Timber.d("Recent question sample: ${q.take(50)}...")
        }
        
        return finalQuestions
    }
    
    /**
     * Get recent questions filtered to be more relevant for country-specific context
     */
    private suspend fun getCountryFilteredQuestionTexts(
        subject: Subject, 
        topic: String, 
        country: String
    ): List<String> {
        // Get recent questions but be more selective
        val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L) // Shorter time window
        
        val recentQuizzes = quizRepo.getAllQuizzes()
            .first()
            .filter { quiz ->
                quiz.subject == subject &&
                quiz.createdAt > threeDaysAgo // More recent questions only
            }
            .sortedByDescending { it.createdAt }
            .take(5) // Fewer historical questions
        
        val allRecentQuestions = recentQuizzes
            .flatMap { it.questions }
            .map { it.questionText }
            .distinct()
        
        // Filter out questions that seem unrelated to the country context
        val countryFilteredQuestions = allRecentQuestions.filter { questionText ->
            isQuestionRelevantForCountry(questionText, country, subject)
        }
        
        Timber.d("Country-filtered recent questions: Found ${countryFilteredQuestions.size} relevant questions for $country in $subject")
        countryFilteredQuestions.take(2).forEach { q ->
            Timber.d("Country-filtered question sample: ${q.take(50)}...")
        }
        
        return countryFilteredQuestions.takeLast(10) // Keep fewer for better diversity
    }
    
    /**
     * Check if a question seems relevant for the student's country context
     */
    private fun isQuestionRelevantForCountry(
        questionText: String, 
        country: String, 
        subject: Subject
    ): Boolean {
        val question = questionText.lowercase()
        
        // Always include questions that mention the student's country
        if (question.contains(country.lowercase())) {
            return true
        }
        
        // For history and geography, filter out obviously unrelated content
        if (subject == Subject.HISTORY || subject == Subject.GEOGRAPHY) {
            // Filter out questions about other specific countries/regions that aren't relevant
            val irrelevantKeywords = when (country) {
                "Ghana" -> listOf(
                    "china", "japan", "europe", "america", "india", "russia", 
                    "world war", "napoleon", "roman", "viking", "confucius", 
                    "hinduism", "buddhism", "christianity"
                )
                else -> listOf(
                    "specific country names that don't relate", // This could be expanded per country
                )
            }
            
            // If question contains irrelevant keywords, exclude it
            val hasIrrelevantContent = irrelevantKeywords.any { keyword ->
                question.contains(keyword)
            }
            
            if (hasIrrelevantContent) {
                Timber.d("Filtering out irrelevant question for $country: ${questionText.take(50)}...")
                return false
            }
        }
        
        // Include questions about general concepts, basic historical periods, etc.
        return true
    }

    private fun isBalancedJson(json: String): Boolean {
        var depth = 0
        var inString = false
        var escapeNext = false

        for (char in json) {
            when {
                escapeNext -> escapeNext = false
                char == '\\' -> escapeNext = true
                char == '"' -> inString = !inString
                !inString && char == '{' -> depth++
                !inString && char == '}' -> depth--
            }
        }
        return depth == 0
    }

    private fun preprocessModelResponse(rawResponse: String): String {
        var processed = rawResponse

        // 1. Remove any markdown code block markers
        processed = processed.replace(Regex("^```(?:json)?\\s*"), "")
        processed = processed.replace(Regex("\\s*```$"), "")

        // 2. Try to remove any text before or after the JSON
        val jsonStart = processed.indexOf('{')
        val jsonEnd = processed.lastIndexOf('}')

        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            processed = processed.substring(jsonStart, jsonEnd + 1)
        }

        // 3. Try to fix unbalanced quotes
        var quoteCount = processed.count { it == '"' }
        if (quoteCount % 2 != 0) {
            // If odd number of quotes, try adding a closing quote at the end
            processed += '"'
            quoteCount++
        }

        // 4. Try to balance braces if needed
        val openBraces = processed.count { it == '{' }
        val closeBraces = processed.count { it == '}' }

        if (openBraces > closeBraces) {
            processed += "}".repeat(openBraces - closeBraces)
        } else if (closeBraces > openBraces) {
            // If more closing braces, try removing extras from the end
            processed = processed.dropLast(closeBraces - openBraces)
        }

        return processed.trim()
    }

    override fun onCleared() {
        super.onCleared()
        // The UnifiedGemmaService is a singleton, so we don't clean it up here
    }
}