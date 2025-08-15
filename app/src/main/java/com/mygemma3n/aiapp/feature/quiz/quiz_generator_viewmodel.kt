package com.mygemma3n.aiapp.feature.quiz

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mygemma3n.aiapp.data.TextEmbeddingServiceExtensions
import com.mygemma3n.aiapp.data.UnifiedGemmaService
import com.mygemma3n.aiapp.data.GeminiApiService
import com.mygemma3n.aiapp.data.GeminiApiConfig
import com.mygemma3n.aiapp.domain.repository.SettingsRepository
import com.mygemma3n.aiapp.feature.chat.OpenAIChatService
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mygemma3n.aiapp.feature.analytics.LearningAnalyticsRepository
import com.mygemma3n.aiapp.feature.analytics.InteractionType
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
import com.mygemma3n.aiapp.shared_utilities.QuizContentManager

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
    private val geminiApiService: GeminiApiService,
    private val openAIChatService: OpenAIChatService,
    // New refactored components
    private val networkManager: QuizNetworkManager,
    private val jsonParser: QuizJsonParser,
    private val promptGenerator: QuizPromptGenerator,
    private val studentManager: QuizStudentManager,
    private val progressTracker: QuizProgressTracker
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

            if (availableModels.isNotEmpty()) {
                val modelToUse = if (availableModels.contains(UnifiedGemmaService.ModelVariant.FAST_2B)) {
                    UnifiedGemmaService.ModelVariant.FAST_2B
                } else {
                    availableModels.first()
                }

                gemmaService.initialize(modelToUse)
                _state.update { it.copy(isModelInitialized = true) }
                Timber.d("Gemma model initialized successfully with ${modelToUse.displayName}")
            } else {
                throw IllegalStateException("No Gemma models available for quiz generation")
            }
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

    // Replaced with QuizNetworkManager methods
    private fun hasNetworkConnection(): Boolean = networkManager.hasNetworkConnection()
    
    private suspend fun shouldUseOnlineService(): Boolean = networkManager.shouldUseOnlineService()
    
    private suspend fun shouldUseOpenAI(): Boolean = networkManager.shouldUseOpenAI()

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
            // Use QuizStudentManager to load curriculum topics
            studentManager.loadCurriculumTopics(gradeLevel)
            
            // Observe the topics from studentManager
            studentManager.curriculumTopics.collect { topics ->
                _state.update {
                    it.copy(
                        curriculumTopics = topics,
                        isLoadingCurriculum = false
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load curriculum")
            _state.update { it.copy(isLoadingCurriculum = false) }
        }
    }

    /* ───────── Progress tracking ───────── */

    private suspend fun loadUserProgress() {
        try {
            // Use QuizProgressTracker to load comprehensive user progress
            progressTracker.loadUserProgress()
            
            // Observe progress data from the tracker
            viewModelScope.launch {
                progressTracker.userProgress.collect { progressMap ->
                    _state.update { it.copy(userProgress = progressMap) }
                }
            }
            
            viewModelScope.launch {
                progressTracker.reviewQuestionsAvailable.collect { reviewCount ->
                    _state.update { it.copy(reviewQuestionsAvailable = reviewCount) }
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
            
            // Record quiz completion in QuizProgressTracker for enhanced analytics
            progressTracker.recordQuizCompletion(completedQuiz)
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

    /**
     * Generate quiz from custom content (AI tutor/chat responses)
     */
    fun generateQuizFromCustomContent() = viewModelScope.launch {
        if (!QuizContentManager.hasContent()) {
            Timber.w("No custom content available for quiz generation")
            return@launch
        }

        val content = QuizContentManager.getContent() ?: return@launch
        val title = QuizContentManager.getTitle() ?: "Custom Content Quiz"
        
        try {
            _state.update { 
                it.copy(
                    isGenerating = true, 
                    error = null,
                    generationPhase = "Analyzing content..."
                ) 
            }
            
            // Direct content-based generation without curriculum application
            // Generate more questions based on content length
            val targetCount = when {
                content.length > 2000 -> 10  // Long content gets more questions
                content.length > 1000 -> 8   // Medium content 
                content.length > 500 -> 6    // Short content
                else -> 5                    // Minimum questions
            }
            val questions = generateDirectQuestionsFromContent(content, title, targetCount)
            
            if (questions.isNotEmpty()) {
                val quiz = Quiz(
                    id = java.util.UUID.randomUUID().toString(),
                    subject = Subject.GENERAL,
                    topic = title,
                    questions = questions,
                    difficulty = Difficulty.MEDIUM,
                    createdAt = System.currentTimeMillis()
                )
                
                // Save and set current quiz immediately
                quizRepo.saveQuiz(quiz)
                _state.update { 
                    it.copy(
                        currentQuiz = quiz,
                        isGenerating = false,
                        mode = QuizMode.NORMAL,
                        generationPhase = "Quiz ready!"
                    ) 
                }
                
                // Clear the custom content after use
                QuizContentManager.clearContent()
                Timber.i("Generated quiz from custom content: ${questions.size} questions on topic: $title")
            } else {
                _state.update { 
                    it.copy(
                        isGenerating = false,
                        error = "Unable to create quiz questions from the provided content. The content may not contain enough educational material.",
                        generationPhase = "Generation failed"
                    ) 
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error generating quiz from custom content")
            _state.update { 
                it.copy(
                    isGenerating = false,
                    error = "Sorry, I couldn't create a quiz from this content. Please try with different content or check your internet connection.",
                    generationPhase = "Error occurred"
                ) 
            }
        }
    }

    /**
     * Extract topics from custom content using simple keyword analysis
     */
    private fun extractTopicsFromContent(content: String): List<String> {
        val text = content.lowercase()
        val topics = mutableListOf<String>()
        
        // Simple keyword extraction - look for educational terms
        val keywordMap = mapOf(
            "mathematics" to listOf("math", "equation", "calculation", "number", "algebra", "geometry"),
            "science" to listOf("science", "experiment", "hypothesis", "biology", "chemistry", "physics"),
            "history" to listOf("history", "historical", "ancient", "century", "war", "civilization"),
            "english" to listOf("grammar", "literature", "writing", "reading", "essay", "language"),
            "geography" to listOf("geography", "continent", "country", "map", "climate", "region"),
            "programming" to listOf("code", "programming", "function", "algorithm", "software", "computer")
        )
        
        for ((topic, keywords) in keywordMap) {
            if (keywords.any { text.contains(it) }) {
                topics.add(topic.replaceFirstChar { it.uppercase() })
            }
        }
        
        return topics.ifEmpty { listOf("General Knowledge") }
    }

    /**
     * Generate questions from custom content using AI
     */
    private suspend fun generateQuestionsFromContent(content: String, topic: String): List<Question> {
        return try {
            // Check if we should use online service
            val useOnline = shouldUseOnlineService()
            
            if (useOnline) {
                generateQuestionsOnline(content, topic)
            } else {
                generateQuestionsOffline(content, topic)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate questions from content")
            emptyList()
        }
    }

    /**
     * Generate questions online using API
     */
    private suspend fun generateQuestionsOnline(content: String, topic: String): List<Question> {
        val prompt = """
        Based on the following content, create 5 multiple-choice questions to test understanding:
        
        Content: "${content.take(1000)}"
        
        Generate questions that test comprehension, application, and analysis of the key concepts discussed.
        
        Format each question as JSON with this structure:
        {
          "question": "Question text",
          "options": ["A", "B", "C", "D"],
          "correctAnswer": 0,
          "explanation": "Why this answer is correct"
        }
        
        Return a JSON array of 5 questions.
        """.trimIndent()

        return try {
            if (shouldUseOpenAI()) {
                val response = openAIChatService.generateTextResponseOnline(prompt)
                parseQuestionsFromJSON(response)
            } else {
                initializeApiServiceIfNeeded()
                val response = geminiApiService.generateTextComplete(prompt, "custom_quiz")
                parseQuestionsFromJSON(response)
            }
        } catch (e: Exception) {
            Timber.e(e, "Online question generation failed")
            emptyList()
        }
    }

    /**
     * Generate questions offline using Gemma
     */
    private suspend fun generateQuestionsOffline(content: String, topic: String): List<Question> {
        return try {
            if (!gemmaService.isInitialized()) {
                gemmaService.initializeBestAvailable()
            }

            val prompt = """
            Content: ${content.take(500)}
            
            Create 3 quiz questions about this content. Format: Question? A) option B) option C) option D) option. Answer: A
            """.trimIndent()

            val response = gemmaService.generateTextAsync(prompt)
            parseQuestionsFromText(response)
        } catch (e: Exception) {
            Timber.e(e, "Offline question generation failed")
            // Return basic fallback questions
            listOf(
                Question(
                    id = "fallback_1",
                    questionText = "What was the main topic discussed in this content?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Topic A", "Topic B", "Topic C", "The content shown"),
                    correctAnswer = "The content shown",
                    explanation = "This question tests basic comprehension of the content.",
                    difficulty = Difficulty.MEDIUM
                )
            )
        }
    }

    /**
     * Parse questions from JSON response
     */
    private fun parseQuestionsFromJSON(response: String): List<Question> {
        return try {
            // Clean the response to extract JSON array, handling markdown code blocks
            var cleanResponse = response.trim()
            
            // Remove markdown code block indicators
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.removePrefix("```json").trim()
            }
            if (cleanResponse.startsWith("```")) {
                cleanResponse = cleanResponse.removePrefix("```").trim()
            }
            if (cleanResponse.endsWith("```")) {
                cleanResponse = cleanResponse.removeSuffix("```").trim()
            }
            
            val jsonStart = cleanResponse.indexOf("[")
            val jsonEnd = cleanResponse.lastIndexOf("]") + 1
            
            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                Timber.w("No JSON array found in response: ${response.take(100)}")
                return emptyList()
            }
            
            val jsonString = cleanResponse.substring(jsonStart, jsonEnd)
            Timber.d("Parsing JSON: ${jsonString.take(200)}...")
            
            // Parse JSON array
            val jsonArray = org.json.JSONArray(jsonString)
            val questions = mutableListOf<Question>()
            
            for (i in 0 until jsonArray.length()) {
                val questionObj = jsonArray.getJSONObject(i)
                
                val questionText = questionObj.optString("question", "")
                val explanation = questionObj.optString("explanation", "Generated question")
                val correctAnswer = questionObj.optString("correctAnswer", "")
                
                // Parse options
                val optionsArray = questionObj.optJSONArray("options")
                val options = mutableListOf<String>()
                if (optionsArray != null) {
                    for (j in 0 until optionsArray.length()) {
                        options.add(optionsArray.getString(j))
                    }
                }
                
                // Only add valid questions
                if (questionText.isNotBlank() && options.size >= 2 && correctAnswer.isNotBlank()) {
                    questions.add(
                        Question(
                            id = generateQuestionId(),
                            questionText = questionText,
                            questionType = QuestionType.MULTIPLE_CHOICE,
                            options = options,
                            correctAnswer = correctAnswer,
                            explanation = explanation,
                            difficulty = Difficulty.MEDIUM
                        )
                    )
                } else {
                    Timber.w("Skipping invalid question: text='$questionText', options=${options.size}, answer='$correctAnswer'")
                }
            }
            
            Timber.i("Successfully parsed ${questions.size} questions from JSON")
            return questions
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse JSON response: ${response.take(200)}")
            return emptyList()
        }
    }

    /**
     * Parse questions from plain text response
     */
    private fun parseQuestionsFromText(response: String): List<Question> {
        // Implementation would parse text format and create Question objects
        // For now, return a simple fallback
        return listOf(
            Question(
                id = generateQuestionId(),
                questionText = "What can you conclude from this information?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Option A", "Option B", "Option C", "Need more information"),
                correctAnswer = "Option C",
                explanation = "This tests analytical thinking about the content.",
                difficulty = Difficulty.MEDIUM
            )
        )
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
                    val onlineQuestions = if (shouldUseOpenAI()) {
                        // Use OpenAI for quiz generation
                        _state.update { it.copy(generationPhase = "Generating quiz with OpenAI...") }
                        val response = openAIChatService.generateCurriculumAwareQuiz(
                            subject = subject.name,
                            gradeLevel = gradeLevel,
                            topic = topic,
                            count = questionCount,
                            country = _state.value.studentCountry,
                            studentName = _state.value.studentName,
                            previousQuestions = getRecentQuestionTexts(subject, topic)
                        )
                        parseOpenAIQuizResponse(response)
                    } else {
                        // Use Gemini for quiz generation
                        _state.update { it.copy(generationPhase = "Generating quiz with Gemini...") }
                        onlineQuizGenerator.generateCurriculumAwareOnlineQuiz(
                            subject = subject,
                            gradeLevel = gradeLevel,
                            topic = topic,
                            count = questionCount,
                            country = _state.value.studentCountry,
                            studentName = _state.value.studentName,
                            previousQuestions = getRecentQuestionTexts(subject, topic)
                        )
                    }
                    
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
                
                // Special handling for flexible/open-ended answers
                if (!correct) {
                    correct = isFlexibleAnswer(normalizedCorrectAnswer, normalizedUserAnswer)
                }
                
                correct = correct || checkAnswerVariations(normalizedUserAnswer, normalizedCorrectAnswer, q)
                
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
            
            // Also record in QuizProgressTracker for enhanced analytics
            viewModelScope.launch {
                progressTracker.recordQuestionAttempt(q, correct, 0L)
            }

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


    /**
     * Check if the expected answer indicates this is an open-ended question that should accept any reasonable response
     */
    private fun isFlexibleAnswer(expectedAnswer: String, userAnswer: String): Boolean {
        // Common patterns that indicate open-ended questions
        val flexibleAnswerPatterns = listOf(
            "answers will vary",
            "answers may vary",
            "various answers",
            "multiple answers",
            "depends on",
            "student answers",
            "open response",
            "personal opinion",
            "individual response",
            "varies",
            "different answers",
            "any reasonable",
            "sample answer",
            "example answer",
            "possible answer",
            "could include",
            "might include"
        )
        
        // Check if the expected answer contains any flexible patterns
        val isFlexibleExpected = flexibleAnswerPatterns.any { pattern ->
            expectedAnswer.contains(pattern, ignoreCase = true)
        }
        
        if (isFlexibleExpected) {
            // For flexible questions, accept any non-empty response that shows effort
            val trimmedUserAnswer = userAnswer.trim()
            
            // Reject obviously incomplete or non-effort responses
            val rejectedResponses = setOf(
                "", "i don't know", "dont know", "idk", "no idea", "nothing", 
                "not sure", "dunno", "?", "??", "???"
            )
            
            if (trimmedUserAnswer.lowercase() in rejectedResponses) {
                return false
            }
            
            // Accept if user provided any meaningful response (at least 2 characters)
            return trimmedUserAnswer.length >= 2
        }
        
        return false
    }

    // NOTE: checkAnswerVariations function has been moved to AnswerCheckingUtils class
    // This consolidates all answer checking logic in a single, reusable utility class

    // NOTE: checkSimpleKeywordMatch function has been moved to AnswerCheckingUtils class
    // This consolidates all answer checking logic in a single, reusable utility class

    // NOTE: checkGeographicConceptMatch function has been moved to AnswerCheckingUtils class
    // This consolidates all answer checking logic in a single, reusable utility class

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
                        val onlineQuestions = if (shouldUseOpenAI()) {
                            // Use OpenAI for individual question generation
                            val response = openAIChatService.generateQuizQuestionsOnline(
                                subject = subject.name,
                                topic = topic,
                                difficulty = difficulty.name,
                                questionTypes = listOf(questionType.name),
                                count = 1,
                                previousQuestions = previousQuestions,
                                studentName = _state.value.studentName,
                                gradeLevel = _state.value.studentGrade,
                                country = _state.value.studentCountry
                            )
                            parseOpenAIQuizResponse(response)
                        } else {
                            // Use Gemini for individual question generation
                            onlineQuizGenerator.generateQuestionsOnline(
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
                        }
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
        val prompt = promptGenerator.createStructuredPrompt(
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

    // NOTE: createStructuredPrompt function has been moved to QuizPromptGenerator class
    // for better separation of concerns and improved maintainability

    /* ─────────────────────── Enhanced Structured prompting ─────────────────────── */

    // NOTE: The createEnhancedStructuredPrompt function and its helper methods have been moved 
    // to the QuizPromptGenerator class for better separation of concerns and maintainability.
    // This reduces code duplication and centralizes all prompt generation logic.

    /* ─────────────────────── Parse and validate questions ─────────────────────── */



    private fun parseAndValidateQuestion(
        raw: String,
        expectedType: QuestionType,
        difficulty: Difficulty
    ): Question {
        return try {
            // Use our new QuizJsonParser to handle JSON parsing and validation
            val question = jsonParser.parseAndValidateQuestion(raw, expectedType, difficulty)
            if (question != null) {
                question
            } else {
                Timber.w("JSON parsing failed, using fallback")
                generateFallbackQuestionForType(expectedType, difficulty)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse question, using fallback")
            generateFallbackQuestionForType(expectedType, difficulty)
        }
    }

    private fun parseOpenAIQuizResponse(response: String): List<Question> {
        return try {
            // Use our new QuizJsonParser to handle OpenAI response parsing
            jsonParser.parseOpenAIQuizResponse(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse OpenAI quiz response")
            emptyList()
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
                val prompt = promptGenerator.createStructuredPrompt(
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

    /**
     * Generate questions directly from content without curriculum overhead
     */
    private suspend fun generateDirectQuestionsFromContent(
        content: String, 
        topic: String, 
        targetCount: Int = 5
    ): List<Question> {
        return try {
            // Simple content-based generation focusing on the provided material
            val prompt = """
            Based on this content, create exactly $targetCount multiple choice questions:
            
            "$content"
            
            Guidelines:
            - Questions should test understanding of the main concepts
            - Make questions educational and appropriate
            - Provide 4 options (A, B, C, D) for each question
            - Include clear explanations for correct answers
            - Focus on the key information presented
            
            Return a JSON array of questions with this format:
            [
              {
                "question": "Question text here?",
                "options": ["Option A", "Option B", "Option C", "Option D"],
                "correctAnswer": "Option B",
                "explanation": "Explanation of why this is correct"
              }
            ]
            """.trimIndent()

            val response = if (shouldUseOpenAI()) {
                openAIChatService.generateTextResponseOnline(prompt)
            } else {
                initializeApiServiceIfNeeded()
                geminiApiService.generateTextComplete(prompt, "content_quiz")
            }

            // Parse the JSON response or fall back to simple generation
            parseQuestionsFromJSON(response).ifEmpty {
                // Fallback: create basic questions from content analysis
                generateFallbackQuestions(content, topic, targetCount)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to generate questions directly, using fallback")
            generateFallbackQuestions(content, topic, targetCount)
        }
    }

    /**
     * Generate simple fallback questions when AI generation fails
     */
    private fun generateFallbackQuestions(content: String, topic: String, count: Int): List<Question> {
        val fallbackQuestions = listOf(
            Question(
                id = generateQuestionId(),
                questionText = "What is the main topic discussed in this content?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("$topic", "General information", "Technical details", "Other topic"),
                correctAnswer = topic,
                explanation = "This question tests basic comprehension of the main topic.",
                difficulty = Difficulty.EASY
            ),
            Question(
                id = generateQuestionId(),
                questionText = "Based on the content, which statement is most accurate?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf(
                    "The content provides detailed information",
                    "The content is unclear", 
                    "The content is too basic",
                    "The content is irrelevant"
                ),
                correctAnswer = "The content provides detailed information",
                explanation = "This tests understanding of the content quality and relevance.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                id = generateQuestionId(),
                questionText = "What can you learn from this content about $topic?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf(
                    "Key concepts and important details",
                    "Only basic information",
                    "Nothing useful",
                    "Confusing details"
                ),
                correctAnswer = "Key concepts and important details",
                explanation = "Educational content typically provides valuable learning opportunities.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                id = generateQuestionId(),
                questionText = "How would you describe the information presented?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf(
                    "Educational and informative",
                    "Too complex to understand",
                    "Not relevant to learning",
                    "Completely unclear"
                ),
                correctAnswer = "Educational and informative",
                explanation = "Well-presented content should be educational and help with understanding.",
                difficulty = Difficulty.EASY
            ),
            Question(
                id = generateQuestionId(),
                questionText = "What is the best way to use this information about $topic?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf(
                    "Study it further and apply the knowledge",
                    "Ignore it completely",
                    "Only memorize without understanding",
                    "Share it without reading"
                ),
                correctAnswer = "Study it further and apply the knowledge",
                explanation = "The best approach to learning is to understand and apply new knowledge.",
                difficulty = Difficulty.MEDIUM
            )
        )
        
        return fallbackQuestions.take(count)
    }

    /* ───── Helper functions ───── */

    private fun generateQuestionId(): String {
        return java.util.UUID.randomUUID().toString()
    }

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