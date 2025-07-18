package com.example.mygemma3n.feature.quiz

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.repository.SettingsRepository
import com.example.mygemma3n.shared_utilities.stripFences
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class QuizGeneratorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,
    private val educationalContent: EducationalContentRepository,
    private val quizRepo: QuizRepository,
    private val settingsRepo: SettingsRepository,
    private val gson: Gson
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
        val isModelInitialized: Boolean = false
    )

    /* ───────── Init ───────── */

    init {
        viewModelScope.launch {
            try {
                // Initialize the offline model first
                initializeModel()

                // Then load educational content
                educationalContent.prepopulateContent()
                val subj = educationalContent.getAllContent()
                    .map { it.subject }
                    .distinct()
                _state.update { it.copy(subjects = subj) }

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
        _state.update { it.copy(currentQuiz = null, questionsGenerated = 0, error = null) }
        loadUserProgress() // Reload progress after quiz completion
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

        _state.update { it.copy(isGenerating = true, questionsGenerated = 0, error = null) }

        try {
            // 1. Adaptive difficulty based on performance
            val adaptedDifficulty = quizRepo.getAdaptiveDifficulty(subject, _state.value.difficulty)
            _state.update { it.copy(difficulty = adaptedDifficulty) }

            // 2. Get learner profile for better question generation
            val profile = quizRepo.getLearnerProfile(subject)
            _state.update { it.copy(learnerProfile = profile) }

            // 3. Get previous questions for deduplication
            val historyTexts = quizRepo
                .getQuizzesFor(subject, topic)
                .flatMap { it.questions }
                .map { it.questionText }

            val questions = mutableListOf<Question>()
            val conceptsCovered = mutableMapOf<String, Int>()

            // 4. Generate questions based on mode
            when (_state.value.mode) {
                QuizMode.REVIEW -> {
                    // Include some review questions from spaced repetition
                    val reviewQuestions = quizRepo.getQuestionsForSpacedReview(subject, limit = questionCount / 2)
                    reviewQuestions.forEach { history ->
                        val q = recreateQuestionFromHistory(history)
                        questions.add(q)
                        updateConceptCoverage(conceptsCovered, q.conceptsCovered)
                    }

                    // Generate remaining new questions
                    val remaining = questionCount - questions.size
                    repeat(remaining) { idx ->
                        val questionType = selectQuestionType(idx + 1)
                        val q = generateQuestionWithRetry(
                            subject = subject,
                            topic = topic,
                            difficulty = adaptedDifficulty,
                            questionType = questionType,
                            previousQuestions = historyTexts + questions.map { it.questionText },
                            learnerProfile = profile
                        )
                        val validatedQuestion = validateQuestion(q)
                        questions += validatedQuestion
                        updateConceptCoverage(conceptsCovered, validatedQuestion.conceptsCovered)
                        _state.update { it.copy(questionsGenerated = questions.size) }
                    }
                }

                else -> {
                    // Normal or adaptive mode
                    repeat(questionCount) { idx ->
                        val questionType = selectQuestionType(idx + 1)
                        val q = generateQuestionWithRetry(
                            subject = subject,
                            topic = topic,
                            difficulty = adaptedDifficulty,
                            questionType = questionType,
                            previousQuestions = historyTexts + questions.map { it.questionText },
                            learnerProfile = profile
                        )
                        val validatedQuestion = validateQuestion(q)
                        questions += validatedQuestion
                        updateConceptCoverage(conceptsCovered, validatedQuestion.conceptsCovered)
                        _state.update { it.copy(questionsGenerated = questions.size) }
                    }
                }
            }

            val quiz = Quiz(
                id         = java.util.UUID.randomUUID().toString(),
                subject    = subject,
                topic      = topic,
                questions  = questions,
                difficulty = adaptedDifficulty,
                mode       = _state.value.mode,
                createdAt  = System.currentTimeMillis()
            )
            quizRepo.saveQuiz(quiz)

            _state.update {
                it.copy(
                    isGenerating = false,
                    currentQuiz = quiz,
                    conceptCoverage = conceptsCovered
                )
            }
            Timber.d("🎉 Quiz ready: ${quiz.questions.size} questions covering ${conceptsCovered.size} concepts")

        } catch (e: Exception) {
            Timber.e(e, "Quiz generation failed")
            _state.update { it.copy(isGenerating = false, error = e.message) }
        }
    }

    fun submitAnswer(qId: String, answer: String) {
        viewModelScope.launch {
            val quiz = _state.value.currentQuiz ?: return@launch
            val q = quiz.questions.find { it.id == qId } ?: return@launch
            val correct = answer.trim().equals(q.correctAnswer.trim(), ignoreCase = true)

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
                val prompt = createStructuredPrompt(
                    questionType = questionType,
                    subject = subject,
                    topic = topic,
                    difficulty = difficulty,
                    learnerProfile = learnerProfile,
                    previousQuestions = previousQuestions
                )

                val response = gemmaService.generateTextAsync(
                    prompt,
                    UnifiedGemmaService.GenerationConfig(
                        maxTokens = 300,
                        temperature = 0.6f + (attempt * 0.1f), // Increase temperature on retries
                        topK = 30
                    )
                )

                return@withContext parseAndValidateQuestion(response, questionType, difficulty)
            } catch (e: Exception) {
                lastError = e
                Timber.w("Question generation attempt ${attempt + 1} failed: ${e.message}")
                delay(100L * (attempt + 1)) // Brief delay between retries
            }
        }

        Timber.e(lastError, "All generation attempts failed, using fallback")
        return@withContext generateFallbackQuestionForType(questionType, difficulty)
    }

    /* ─────────────────────── Structured prompting for Gemma ─────────────────────── */

    private fun createStructuredPrompt(
        questionType: QuestionType,
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        learnerProfile: LearnerProfile,
        previousQuestions: List<String>
    ): String {
        // Get type-specific instructions and example
        val (instructions, exampleJson) = getQuestionTypeInstructions(questionType)

        val weakConcepts = learnerProfile.weaknessesByConcept.keys.take(3)
        val masteredConcepts = learnerProfile.masteredConcepts.take(5)

        return """
        You are a quiz generator. Create exactly ONE ${questionType.name} question.
        
        Subject: $subject
        Topic: $topic
        Difficulty: $difficulty
        
        ${if (weakConcepts.isNotEmpty()) "Focus on weak areas: ${weakConcepts.joinToString()}" else ""}
        ${if (masteredConcepts.isNotEmpty()) "Student has mastered: ${masteredConcepts.joinToString()}" else ""}
        
        INSTRUCTIONS:
        $instructions
        
        EXAMPLE of correct format:
        $exampleJson
        
        RULES:
        1. Return ONLY valid JSON, no other text
        2. Question must be about $topic in $subject
        3. Difficulty should match $difficulty level
        4. Follow the EXACT format of the example
        5. Do NOT duplicate these previous questions:
        ${previousQuestions.takeLast(10).joinToString("\n") { "- $it" }}
        
        Generate your question now:
    """.trimIndent()
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
                
                Example: "The heart pumps blood throughout the body." (Answer: True)
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

    private fun parseAndValidateQuestion(
        raw: String,
        expectedType: QuestionType,
        difficulty: Difficulty
    ): Question {
        return try {
            val obj = org.json.JSONObject(stripFences(raw))

            val questionText = obj.getString("question")
            val correctAnswer = obj.getString("correctAnswer")

            // Validate and fix common issues
            val (validatedQuestion, validatedOptions) = when (expectedType) {
                QuestionType.MULTIPLE_CHOICE -> {
                    val opts = obj.optJSONArray("options")?.let { arr ->
                        List(arr.length()) { arr.getString(it) }
                    } ?: listOf("Option A", "Option B", "Option C", "Option D")

                    // Ensure we have exactly 4 options and the correct answer is among them
                    val finalOptions = if (opts.size != 4 || !opts.contains(correctAnswer)) {
                        // Create new options including the correct answer
                        val newOpts = mutableListOf(correctAnswer)
                        // Add other options, avoiding duplicates
                        opts.filter { it != correctAnswer }.take(3).forEach { newOpts.add(it) }
                        // Fill remaining slots with generic options if needed
                        while (newOpts.size < 4) {
                            newOpts.add("Option ${('A'..'D').toList()[newOpts.size]}")
                        }
                        newOpts.shuffled()
                    } else {
                        opts
                    }

                    // Fix question phrasing if needed
                    val fixedQuestion = if (questionText.contains("True or False") ||
                        questionText.contains("true/false")) {
                        questionText.replace("True or False:", "").trim()
                    } else {
                        questionText
                    }

                    Pair(fixedQuestion, finalOptions)
                }

                QuestionType.TRUE_FALSE -> {
                    // Force True/False options
                    val fixedQuestion = questionText
                        .replace("Which of the following", "")
                        .replace("?", ".")
                        .trim()
                        .let { q ->
                            if (q.endsWith(".")) q else "$q."
                        }

                    Pair(fixedQuestion, listOf("True", "False"))
                }

                QuestionType.FILL_IN_BLANK -> {
                    // Ensure question has a blank
                    val fixedQuestion = if (!questionText.contains("_____")) {
                        "$questionText _____."
                    } else {
                        questionText
                    }

                    Pair(fixedQuestion, emptyList())
                }

                else -> {
                    Pair(questionText, emptyList())
                }
            }

            val concepts = obj.optJSONArray("conceptsCovered")?.let { arr ->
                List(arr.length()) { arr.getString(it) }
            } ?: listOf("general")

            Question(
                questionText = validatedQuestion,
                questionType = expectedType,
                options = validatedOptions,
                correctAnswer = correctAnswer,
                explanation = obj.optString("explanation", ""),
                hint = obj.optString("hint", null),
                conceptsCovered = concepts,
                difficulty = difficulty
            )

        } catch (e: Exception) {
            Timber.e(e, "JSON parse error – generating fallback")
            generateFallbackQuestionForType(expectedType, difficulty)
        }
    }

    /* ─────────────────────── Validate questions before adding ─────────────────────── */

    private fun validateQuestion(question: Question): Question {
        return when (question.questionType) {
            QuestionType.MULTIPLE_CHOICE -> {
                // Ensure we have exactly 4 options
                val validOptions = if (question.options.size == 4) {
                    question.options
                } else {
                    val opts = question.options.toMutableList()
                    // Ensure correct answer is in options
                    if (!opts.contains(question.correctAnswer)) {
                        opts.add(0, question.correctAnswer)
                    }
                    // Pad or trim to 4 options
                    while (opts.size < 4) {
                        opts.add("Option ${opts.size + 1}")
                    }
                    opts.take(4).shuffled()
                }

                question.copy(options = validOptions)
            }

            QuestionType.TRUE_FALSE -> {
                // Ensure only True/False options
                question.copy(options = listOf("True", "False"))
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

    override fun onCleared() {
        super.onCleared()
        // The UnifiedGemmaService is a singleton, so we don't clean it up here
    }
}