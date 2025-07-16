package com.example.mygemma3n.feature.quiz

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.repository.SettingsRepository
import com.example.mygemma3n.shared_utilities.stripFences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    private val gemmaService: UnifiedGemmaService,  // Changed from GeminiApiService
    private val educationalContent: EducationalContentRepository,
    private val quizRepo: QuizRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    /* ───────── UI state ───────── */

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()

    data class QuizState(
        val isGenerating: Boolean = false,
        val currentQuiz: Quiz? = null,
        val subjects: List<Subject> = emptyList(),
        val difficulty: Difficulty = Difficulty.MEDIUM,
        val questionsGenerated: Int = 0,
        val userProgress: Map<Subject, Float> = emptyMap(),
        val error: String? = null,
        val isModelInitialized: Boolean = false  // Added to track model status
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
            } catch (e: Exception) {
                Timber.e(e, "Initialization failed")
                _state.update { it.copy(error = "Failed to initialize: ${e.message}") }
            }
        }
    }

    /* ───────── Model initialization ───────── */

    private suspend fun initializeModel() {
        try {
            // Try to initialize with the best available model
            val availableModels = gemmaService.getAvailableModels()
            if (availableModels.isEmpty()) {
                throw IllegalStateException("No Gemma models found in assets")
            }

            // Use FAST_2B if available, otherwise use whatever is available
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

    /* ───────── Public API ───────── */

    fun completeQuiz() =
        _state.update { it.copy(currentQuiz = null, questionsGenerated = 0, error = null) }

    /** Create a quiz, avoiding duplicates from previous _subject + topic_ quizzes. */
    /* ───────── Public API ───────── */

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
            val historyTexts = quizRepo
                .getQuizzesFor(subject, topic)
                .flatMap { it.questions }
                .map { it.questionText }

            val questions = mutableListOf<Question>()

            repeat(questionCount) { idx ->
                Timber.d("🔍 Calling Gemma for question ${idx + 1}...")          // 🔍 ADDED
                val q = generateQuestion(
                    difficulty        = _state.value.difficulty,
                    previousQuestions = historyTexts + questions.map { it.questionText },
                    questionType      = selectQuestionType(idx + 1)
                )
                Timber.d("✅ Generated Q${idx + 1}: ${q.questionText}")          // 🔍 ADDED
                questions += q
                _state.update { it.copy(questionsGenerated = questions.size) }
            }

            val quiz = Quiz(
                id         = java.util.UUID.randomUUID().toString(),
                subject    = subject,
                topic      = topic,
                questions  = questions,
                difficulty = _state.value.difficulty,
                createdAt  = System.currentTimeMillis()
            )
            quizRepo.saveQuiz(quiz)

            _state.update { it.copy(isGenerating = false, currentQuiz = quiz) }
            Timber.d("🎉 Quiz ready: ${quiz.questions.size} questions")          // 🔍 ADDED

        } catch (e: Exception) {
            Timber.e(e, "Quiz generation failed")
            _state.update { it.copy(isGenerating = false, error = e.message) }
        }
    }


    fun submitAnswer(qId: String, answer: String) {
        viewModelScope.launch {
            val quiz = _state.value.currentQuiz ?: return@launch
            val q = quiz.questions.find { it.id == qId } ?: return@launch
            val correct = answer == q.correctAnswer

            updateUserProgress(quiz.subject, q.difficulty, correct)

            val feedback = generateFeedback(q, answer, correct)
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

    /* ─────────────────────── Question generation ─────────────────────── */

    private suspend fun generateQuestion(
        difficulty: Difficulty,
        previousQuestions: List<String>,
        questionType: QuestionType
    ): Question = withContext(Dispatchers.IO) {

        val prompt = """
        Generate one **$questionType** quiz question in JSON with keys:
          question, options, correctAnswer, explanation.
        Difficulty: ${difficulty.name}.

        **Do NOT duplicate** any of these prior questions:
        ${previousQuestions.joinToString("\n")}
    """.trimIndent()

        Timber.d("⏱️  START Gemma call, qType=$questionType, diff=$difficulty")     // << BEFORE

        val t0 = System.currentTimeMillis()
        val raw = try {
            gemmaService.generateTextAsync(
                prompt,
                UnifiedGemmaService.GenerationConfig(maxTokens = 256, temperature = 0.7f, topK = 40)
            )
        } catch (e: Exception) {
            Timber.e(e, "Gemma crashed after ${System.currentTimeMillis() - t0} ms")
            return@withContext generateBasicQuestion(difficulty, questionType)
        }

        Timber.d("⏱️  END Gemma call (${System.currentTimeMillis() - t0} ms)\n$raw") // << AFTER

        parseQuestionFromJson(raw, questionType, difficulty)
    }

    private suspend fun generateFeedback(
        q: Question,
        answer: String,
        correct: Boolean
    ): String = withContext(Dispatchers.IO) {
        val prompt = if (correct)
            "Encourage the student for correctly answering: \"${q.questionText}\""
        else
            "Explain in ≤75 words why \"$answer\" is wrong and \"${q.correctAnswer}\" is right " +
                    "for the question \"${q.questionText}\"."
        try {
            // Use the offline model for feedback generation
            gemmaService.generateTextAsync(
                prompt,
                UnifiedGemmaService.GenerationConfig(
                    maxTokens = 100,
                    temperature = 0.7f
                )
            )
        } catch (_: Exception) {
            if (correct) "Great job!" else "Not quite. Correct is ${q.correctAnswer}."
        }
    }

    /* ───── JSON parsing & fallbacks ───── */

    private fun parseQuestionFromJson(
        raw: String,
        qt: QuestionType,
        diff: Difficulty
    ): Question = try {
        val obj  = org.json.JSONObject(stripFences(raw))
        val opts = obj.optJSONArray("options")?.let { arr ->
            List(arr.length()) { arr.getString(it) }
        } ?: emptyList()

        Question(
            questionText  = obj.getString("question"),
            questionType  = qt,
            options       = opts,
            correctAnswer = obj.getString("correctAnswer"),
            explanation   = obj.optString("explanation", ""),
            difficulty    = diff
        )
    } catch (e: Exception) {
        Timber.e(e, "JSON parse error – fallback")
        generateBasicQuestion(diff, qt)
    }

    private fun generateBasicQuestion(diff: Difficulty, qt: QuestionType): Question = when (qt) {
        QuestionType.MULTIPLE_CHOICE -> Question(
            questionText  = "What is 2 + 2?",
            questionType  = qt,
            options       = listOf("A) 3", "B) 4", "C) 5", "D) 6"),
            correctAnswer = "B",
            explanation   = "2 + 2 equals 4.",
            difficulty    = diff
        )
        QuestionType.TRUE_FALSE -> Question(
            questionText  = "The Earth revolves around the Sun.",
            questionType  = qt,
            options       = listOf("True", "False"),
            correctAnswer = "True",
            explanation   = "Basic astronomy fact.",
            difficulty    = diff
        )
        else -> Question(
            questionText  = "Complete: The capital of France is _____.",
            questionType  = QuestionType.FILL_IN_BLANK,
            options       = emptyList(),
            correctAnswer = "Paris",
            explanation   = "Paris is France's capital.",
            difficulty    = diff
        )
    }

    private fun selectQuestionType(i: Int): QuestionType =
        when (i % 3) { 0 -> QuestionType.MULTIPLE_CHOICE; 1 -> QuestionType.TRUE_FALSE; else -> QuestionType.FILL_IN_BLANK }

    private fun updateUserProgress(sub: Subject, diff: Difficulty, correct: Boolean) {
        _state.update { s ->
            val total = s.questionsGenerated.coerceAtLeast(1)
            val prev  = s.userProgress[sub] ?: 0f
            val new   = if (correct) (prev * (total - 1) + 1f) / total
            else         (prev * (total - 1))      / total
            s.copy(userProgress = s.userProgress + (sub to new))
        }
    }

    /* Optional helper: still available if ViewModel needs it elsewhere */
    private suspend fun previousQuestionsFor(subject: Subject, topic: String): List<String> =
        quizRepo.getQuizzesFor(subject, topic)
            .flatMap { it.questions }
            .map { it.questionText }

    /* Clean up when ViewModel is cleared */
    override fun onCleared() {
        super.onCleared()
        // The UnifiedGemmaService is a singleton, so we don't clean it up here
        // It will persist across ViewModels for better performance
    }
}