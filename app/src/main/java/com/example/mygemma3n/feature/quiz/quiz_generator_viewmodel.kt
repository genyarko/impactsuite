package com.example.mygemma3n.feature.quiz

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.TextEmbeddingServiceExtensions
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.domain.repository.SettingsRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val enhancedPromptManager: EnhancedPromptManager
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
        val generationPhase: String = "Starting..." // For animation variety
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

    // Also update loadSubjects method
    fun loadSubjects() = viewModelScope.launch {
        // Always use all available subjects
        _state.update { it.copy(subjects = Subject.entries) }

        // Load progress for all subjects
        loadUserProgress()
    }

    /** Generate adaptive quiz with enhanced features */

    fun generateAdaptiveQuiz(
        subject: Subject,
        topic: String,
        questionCount: Int = 10
    ) = viewModelScope.launch {
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
            _state.update { it.copy(generationPhase = "Analyzing your performance...") }
            delay(500) // Small delay for UI feedback
            val adaptedDifficulty = quizRepo.getAdaptiveDifficulty(subject, _state.value.difficulty)
            _state.update { it.copy(difficulty = adaptedDifficulty) }

            // 2. Get learner profile for better question generation
            _state.update { it.copy(generationPhase = "Loading your profile...") }
            delay(300)
            val profile = quizRepo.getLearnerProfile(subject)
            _state.update { it.copy(learnerProfile = profile) }

            // 3. Get previous questions for deduplication
            _state.update { it.copy(generationPhase = "Checking question history...") }
            val historyTexts = quizRepo
                .getQuizzesFor(subject, topic)
                .flatMap { it.questions }
                .map { it.questionText }

            val questions = mutableListOf<Question>()
            val conceptsCovered = mutableMapOf<String, Int>()

            // 4. Generate questions based on mode
            when (_state.value.mode) {
                QuizMode.REVIEW -> {
                    _state.update { it.copy(generationPhase = "Gathering review questions...") }
                    delay(400)

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
                        _state.update { it.copy(generationPhase = "Creating new questions...") }
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
                    _state.update { it.copy(generationPhase = "Crafting questions...") }
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

            _state.update { it.copy(generationPhase = "Finalizing quiz...") }
            delay(300)

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
            "Thinking of creative questions...",
            "Making it challenging...",
            "Adding variety...",
            "Ensuring uniqueness...",
            "Almost there..."
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

            // For fill-in-blank and short answer, check for acceptable variations
            if (!correct && (q.questionType == QuestionType.FILL_IN_BLANK ||
                        q.questionType == QuestionType.SHORT_ANSWER)) {
                correct = checkAnswerVariations(normalizedUserAnswer, normalizedCorrectAnswer, q)
            }

            // Log for debugging
            if (!correct) {
                Timber.d("Answer mismatch - User: '$normalizedUserAnswer', Expected: '$normalizedCorrectAnswer'")
            }

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
                0L, // You could track actual time spent
                q.conceptsCovered
            )

            // Generate enhanced feedback
            val feedback = generateEnhancedFeedback(q, answer, correct)

            val updated = quiz.copy(
                questions = quiz.questions.map {
                    if (it.id == qId) it.copy(
                        userAnswer = answer,
                        feedback   = feedback,
                        isAnswered = true
                    ) else it
                }
            )
            _state.update { it.copy(currentQuiz = updated) }
        }
    }

    // Helper function to normalize answers
    private fun normalizeAnswer(answer: String): String {
        return answer
            .lowercase()
            .trim()
            // Remove common articles
            .replace(Regex("^(the|a|an)\\s+"), "")
            // Remove punctuation
            .replace(Regex("[.,!?;:]"), "")
            // Normalize whitespace
            .replace(Regex("\\s+"), " ")
    }

    // Check for acceptable answer variations
    private fun checkAnswerVariations(userAnswer: String, correctAnswer: String, question: Question): Boolean {
        // Common variations for specific question types
        val variations = when {
            // Photosynthesis variations
            correctAnswer.contains("photosynthesis") -> {
                listOf("photosynthesis", "photo synthesis", "photosynthetic process")
            }
            // Geography variations
            correctAnswer.contains("geography") -> {
                listOf("geography", "physical geography", "earth science", "geoscience")
            }
            // Add more subject-specific variations as needed
            else -> emptyList()
        }

        return variations.any { variant ->
            normalizeAnswer(variant).equals(userAnswer, ignoreCase = true)
        }
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
        val attemptedPromptHashes = mutableSetOf<Int>()

        repeat(maxRetries) { attempt ->
            try {
                // Create prompt with increasing variety on each attempt
                var prompt = createStructuredPrompt(
                    questionType = questionType,
                    subject = subject,
                    topic = topic,
                    difficulty = difficulty,
                    learnerProfile = learnerProfile,
                    previousQuestions = previousQuestions,
                    attemptNumber = attempt + (System.currentTimeMillis() % 10).toInt()
                )

                // Check if we've used this exact prompt before
                val promptHash = prompt.hashCode()
                if (promptHash in attemptedPromptHashes) {
                    Timber.w("Duplicate prompt detected, adding more randomness")
                    prompt += "\nUnique seed: ${System.currentTimeMillis()}"
                }
                attemptedPromptHashes.add(promptHash)

                // Increase temperature and randomness with each retry
                val temperature = 0.7f + (attempt * 0.15f) + (Random.nextFloat() * 0.1f)
                val topK = 40 + (attempt * 10) + Random.nextInt(20)

                val response = gemmaService.generateTextAsync(
                    prompt,
                    UnifiedGemmaService.GenerationConfig(
                        maxTokens = 512,
                        temperature = temperature.coerceIn(0.7f, 1.0f),
                        topK = topK,
                        randomSeed = (System.currentTimeMillis() + attempt * 1000).toInt()
                    )
                )

                val question = parseAndValidateQuestion(response, questionType, difficulty)

                // More aggressive similarity check
                val questionLower = question.questionText.lowercase()
                val isTooSimilar = previousQuestions.any { prev ->
                    val prevLower = prev.lowercase()
                    // Check for substring matches
                    (questionLower.contains(prevLower.take(20)) ||
                            prevLower.contains(questionLower.take(20))) ||
                            // Check word overlap
                            calculateEnhancedSimilarity(questionLower, prevLower) > 0.6f
                }

                if (!isTooSimilar) {
                    // Add to recent questions tracking
                    val questionHash = question.questionText.hashCode()
                    if (questionHash !in recentQuestionHashes) {
                        recentQuestionHashes.add(questionHash)
                        if (recentQuestionHashes.size > 100) {
                            // Remove oldest entries
                            recentQuestionHashes.clear()
                        }
                        return@withContext question
                    }
                }

                Timber.w("Question too similar or duplicate, retrying with more variety...")
                delay(200L * (attempt + 1))

            } catch (e: Exception) {
                lastError = e
                Timber.w("Question generation attempt ${attempt + 1} failed: ${e.message}")
                delay(100L * (attempt + 1))
            }
        }

        Timber.e(lastError, "All generation attempts failed, using fallback")
        // Generate a more varied fallback
        return@withContext generateVariedFallback(questionType, difficulty, subject, previousQuestions)
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

        // Check exact substring match (very similar)
        if (clean1.contains(clean2) || clean2.contains(clean1)) {
            return 0.9f
        }

        // Word-based similarity
        val words1 = clean1.split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
        val words2 = clean2.split("\\s+".toRegex()).filter { it.length > 2 }.toSet()

        if (words1.isEmpty() || words2.isEmpty()) return 0f

        // Jaccard similarity
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        val jaccard = intersection.toFloat() / union

        // Also check for n-gram similarity (bigrams)
        val bigrams1 = getBigrams(clean1)
        val bigrams2 = getBigrams(clean2)

        val bigramIntersection = bigrams1.intersect(bigrams2).size
        val bigramUnion = bigrams1.union(bigrams2).size
        val bigramSimilarity = if (bigramUnion > 0) {
            bigramIntersection.toFloat() / bigramUnion
        } else 0f

        // Weighted combination
        return (jaccard * 0.6f + bigramSimilarity * 0.4f)
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
        val (baseInstructions, exampleJson) = getQuestionTypeInstructions(questionType)

        // Enhanced instructions for specific question types to ensure correct answers
        val typeSpecificInstructions = when (questionType) {
            QuestionType.FILL_IN_BLANK -> """
                CRITICAL: The correctAnswer field MUST contain the exact word or phrase that fills the blank.
                For example, if the question is "_____ is the process of light being converted into chemical energy",
                then correctAnswer MUST be "Photosynthesis" (not something else).
            """.trimIndent()

            QuestionType.SHORT_ANSWER -> """
                CRITICAL: The correctAnswer should be a concise, accurate answer to the question.
                It should directly answer what is being asked.
            """.trimIndent()

            else -> ""
        }

        return """
        Create ONE $questionType question about $topic.
        Subject: $subject
        Difficulty: $difficulty
        
        IMPORTANT CONSTRAINTS:
        - Keep question text under 100 characters
        - Keep options under 50 characters each
        - Keep explanation under 100 characters
        - Return ONLY the JSON, no other text
        
        $baseInstructions
        
        $typeSpecificInstructions
        
        Example format:
        $exampleJson
        
        Make it unique from these previous questions:
        ${previousQuestions.takeLast(3).joinToString("\n") { "- ${it.take(40)}..." }}
        
        JSON:
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
            .replace(Regex("^```(?:json)?\\s*"), "")   // remove starting ```
            .replace(Regex("\\s*```$"), "")            // remove ending ```
            .trim()

        // Unwrap outer quotes
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length - 1)
                .replace("\\n", "")
                .replace("\\\"", "\"")
        }

        // Escape unescaped quotes inside JSON string values (only inside question/answer fields)
        // Safe way: use regex to find "question": "...", and replace unescaped " inside values
        val quoteFixRegex = Regex("\"(question|correctAnswer)\"\\s*:\\s*\"([^\"]*?)\"")
        cleaned = quoteFixRegex.replace(cleaned) { match ->
            val field = match.groupValues[1]
            val value = match.groupValues[2].replace("\"", "\\\"")
            "\"$field\":\"$value\""
        }

        val openBraces = cleaned.count { it == '{' }
        val closeBraces = cleaned.count { it == '}' }
        if (openBraces > closeBraces) {
            cleaned += "}".repeat(openBraces - closeBraces)
        }

        return cleaned
    }
    private fun parseAndValidateQuestion(
        raw: String,
        expectedType: QuestionType,
        difficulty: Difficulty
    ): Question {
        return try {
            Timber.d("Raw response length: ${raw.length} characters")

            val preprocessed = preprocessModelResponse(raw)
            val cleaned = sanitizeJson(preprocessed)

            // Check if JSON was truncated
            if (cleaned.contains("...") || !isCompleteJson(cleaned)) {
                Timber.w("JSON appears truncated, using fallback")
                return generateFallbackQuestionForType(expectedType, difficulty)
            }

            // Additional validation
            if (!cleaned.trim().startsWith("{") || !cleaned.trim().endsWith("}")) {
                Timber.e("Invalid JSON structure")
                return generateFallbackQuestionForType(expectedType, difficulty)
            }

            val obj = org.json.JSONObject(cleaned)

            // Extract fields with proper validation
            val questionText = obj.optString("question", "").takeIf { it.isNotBlank() }
                ?: return generateFallbackQuestionForType(expectedType, difficulty)

            val correctAnswer = obj.optString("correctAnswer", "").takeIf { it.isNotBlank() }
                ?: return generateFallbackQuestionForType(expectedType, difficulty)

            // For multiple choice, ensure we have valid options
            val options = when (expectedType) {
                QuestionType.MULTIPLE_CHOICE -> {
                    val opts = obj.optJSONArray("options")?.let { arr ->
                        List(arr.length()) { arr.getString(it) }.filter { it.isNotBlank() }
                    } ?: emptyList()

                    if (opts.size < 4) {
                        Timber.w("Insufficient options for multiple choice, using fallback")
                        return generateFallbackQuestionForType(expectedType, difficulty)
                    }
                    opts.take(4)
                }
                QuestionType.TRUE_FALSE -> listOf("True", "False")
                else -> emptyList()
            }

            // Build the question
            Question(
                questionText = questionText.trim(),
                questionType = expectedType,
                options = options,
                correctAnswer = correctAnswer.trim(),
                explanation = obj.optString("explanation", "No explanation provided."),
                hint = obj.optString("hint"),
                conceptsCovered = try {
                    obj.optJSONArray("conceptsCovered")?.let { arr ->
                        List(arr.length()) { arr.getString(it) }
                    } ?: listOf("general")
                } catch (e: Exception) {
                    listOf("general")
                },
                difficulty = difficulty
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to parse question, using fallback")
            generateFallbackQuestionForType(expectedType, difficulty)
        }
    }

    // Add helper to check if JSON is complete
    private fun isCompleteJson(json: String): Boolean {
        return try {
            val trimmed = json.trim()
            val openBraces = trimmed.count { it == '{' }
            val closeBraces = trimmed.count { it == '}' }
            val openBrackets = trimmed.count { it == '[' }
            val closeBrackets = trimmed.count { it == ']' }

            openBraces == closeBraces && openBrackets == closeBrackets
        } catch (e: Exception) {
            false
        }
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

    private fun validateQuestion(question: Question): Question {
        return when (question.questionType) {
            QuestionType.MULTIPLE_CHOICE -> {
                // For multiple choice, we MUST have 4 valid options
                val validOptions = when {
                    question.options.isEmpty() -> {
                        // Generate default options if none provided
                        Timber.w("Multiple choice question has no options, generating defaults")
                        listOf(
                            question.correctAnswer,
                            "Incorrect Option 1",
                            "Incorrect Option 2",
                            "Incorrect Option 3"
                        ).shuffled()
                    }
                    question.options.size < 4 -> {
                        // Pad options to 4
                        val opts = question.options.toMutableList()
                        if (!opts.contains(question.correctAnswer)) {
                            opts.add(0, question.correctAnswer)
                        }
                        while (opts.size < 4) {
                            opts.add("Option ${('A' + opts.size)}")
                        }
                        opts.shuffled()
                    }
                    question.options.size > 4 -> {
                        // Trim to 4 options, ensuring correct answer is included
                        val opts = if (question.options.contains(question.correctAnswer)) {
                            question.options.take(4)
                        } else {
                            listOf(question.correctAnswer) + question.options.take(3)
                        }.shuffled()
                        opts
                    }
                    else -> {
                        // Ensure correct answer is in options
                        if (!question.options.contains(question.correctAnswer)) {
                            (listOf(question.correctAnswer) + question.options.take(3)).shuffled()
                        } else {
                            question.options
                        }
                    }
                }

                question.copy(options = validOptions)
            }

            QuestionType.TRUE_FALSE -> {
                // Ensure only True/False options
                question.copy(
                    options = listOf("True", "False"),
                    correctAnswer = if (question.correctAnswer.equals("true", ignoreCase = true)) "True" else "False"
                )
            }

            QuestionType.FILL_IN_BLANK,
            QuestionType.SHORT_ANSWER -> {
                // Ensure no options for text input questions
                question.copy(options = emptyList())
            }

            else -> question
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
        // Weighted distribution for better variety
        val weights = mapOf(
            QuestionType.MULTIPLE_CHOICE to 40,  // 40% chance
            QuestionType.TRUE_FALSE to 25,       // 25% chance
            QuestionType.FILL_IN_BLANK to 20,    // 20% chance
            QuestionType.SHORT_ANSWER to 15      // 15% chance
        )

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

    private fun generateFallbackQuestionForType(
        questionType: QuestionType,
        difficulty: Difficulty
    ): Question = when (questionType) {
        QuestionType.MULTIPLE_CHOICE -> Question(
            questionText = "What is the result of 5 + 3?",
            questionType = questionType,
            options = listOf("6", "7", "8", "9"),
            correctAnswer = "8",
            explanation = "5 + 3 equals 8.",
            hint = "Count up from 5 by 3.",
            conceptsCovered = listOf("basic-arithmetic"),
            difficulty = difficulty
        )

        QuestionType.TRUE_FALSE -> Question(
            questionText = "Water boils at 100 degrees Celsius at sea level.",
            questionType = questionType,
            options = listOf("True", "False"),
            correctAnswer = "True",
            explanation = "At standard atmospheric pressure (sea level), water boils at 100°C or 212°F.",
            hint = "Think about the temperature scale used in science.",
            conceptsCovered = listOf("states-of-matter", "temperature"),
            difficulty = difficulty
        )

        QuestionType.FILL_IN_BLANK -> Question(
            questionText = "The capital of France is _____.",
            questionType = questionType,
            options = emptyList(),
            correctAnswer = "Paris",
            explanation = "Paris is the capital and largest city of France.",
            hint = "It's known as the City of Light.",
            conceptsCovered = listOf("geography-capitals"),
            difficulty = difficulty
        )

        QuestionType.SHORT_ANSWER -> Question(
            questionText = "What is photosynthesis?",
            questionType = questionType,
            options = emptyList(),
            correctAnswer = "The process by which plants use sunlight to make food from carbon dioxide and water",
            explanation = "Photosynthesis converts light energy into chemical energy stored in glucose.",
            hint = "Think about how plants make their own food.",
            conceptsCovered = listOf("plant-biology", "photosynthesis"),
            difficulty = difficulty
        )

        else -> Question(
            questionText = "Sample question for ${questionType.name}",
            questionType = questionType,
            options = emptyList(),
            correctAnswer = "Sample answer",
            explanation = "This is a fallback question.",
            conceptsCovered = listOf("general"),
            difficulty = difficulty
        )
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