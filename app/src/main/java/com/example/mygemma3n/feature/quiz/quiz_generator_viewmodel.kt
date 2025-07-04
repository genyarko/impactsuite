/**
 * QuizGeneratorViewModel.kt
 *
 * _Cloud-only_ version — every reference to on-device Gemma, local model
 * shards, ModelDownloadManager, ModelRepository, Vector DB, and MediaPipe
 * embedder has been removed or replaced with Gemini API calls.
 */
package com.example.mygemma3n.feature.quiz

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.repository.SettingsRepository
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
import kotlin.math.ceil

@HiltViewModel
class QuizGeneratorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geminiApi: GeminiApiService,              // ← cloud only
    private val educationalContent: EducationalContentRepository,
    private val quizRepo: QuizRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    /* ─────────────────────────── UI state ────────────────────────── */

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()

    data class QuizState(
        val isGenerating: Boolean = false,
        val currentQuiz: Quiz? = null,
        val subjects: List<Subject> = emptyList(),
        val difficulty: Difficulty = Difficulty.MEDIUM,
        val questionsGenerated: Int = 0,
        val userProgress: Map<Subject, Float> = emptyMap(),
        val error: String? = null
    )

    /* ─────────────────────────── Init ────────────────────────── */

    init {
        viewModelScope.launch {
            try {
                educationalContent.prepopulateContent()
                val subj = educationalContent.getAllContent()
                    .map { it.subject }
                    .distinct()
                _state.update { it.copy(subjects = subj) }
            } catch (e: Exception) {
                Timber.e(e, "Content init failed")
            }
        }
    }

    /* ─────────────────────────── Public API ────────────────────────── */

    fun completeQuiz() =
        _state.update { it.copy(currentQuiz = null, questionsGenerated = 0, error = null) }

    fun generateAdaptiveQuiz(
        subject: Subject,
        topic: String,
        questionCount: Int = 10
    ) = viewModelScope.launch {
        _state.update { it.copy(isGenerating = true, questionsGenerated = 0, error = null) }

        try {
            val questions = mutableListOf<Question>()
            repeat(questionCount) { idx ->
                val q = generateQuestion(
                    difficulty = _state.value.difficulty,
                    previousQuestions = questions,
                    questionType = selectQuestionType(idx + 1)
                )
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
        previousQuestions: List<Question>,
        questionType: QuestionType
    ): Question = withContext(Dispatchers.IO) {
        val prompt = """
            Generate a $questionType quiz question in JSON with keys:
            question, options, correctAnswer, explanation.
            Difficulty: ${difficulty.name}. Previous: ${
            previousQuestions.joinToString { it.questionText }
        }
        """.trimIndent()

        try {
            val raw = geminiApi.generateTextComplete(prompt)
            parseQuestionFromJson(raw, questionType, difficulty)
        } catch (e: Exception) {
            Timber.e(e, "Gemini API failed, using basic question")
            generateBasicQuestion(difficulty, questionType)
        }
    }

    /* ─────────────────────── Feedback generation ─────────────────────── */

    private suspend fun generateFeedback(
        q: Question,
        answer: String,
        correct: Boolean
    ): String = withContext(Dispatchers.IO) {
        val prompt = if (correct) {
            "Encourage the student for correctly answering: \"${q.questionText}\""
        } else {
            "Explain in ≤75 words why \"$answer\" is wrong and \"${q.correctAnswer}\" is right " +
                    "for the question \"${q.questionText}\"."
        }
        try {
            geminiApi.generateTextComplete(prompt)
        } catch (_: Exception) {
            if (correct) "Great job!" else "Not quite. Correct is ${q.correctAnswer}."
        }
    }

    /* ─────────────────────── Utilities & fallbacks ─────────────────────── */

    private fun generateBasicQuestion(
        diff: Difficulty,
        qt: QuestionType
    ): Question = when (qt) {
        QuestionType.MULTIPLE_CHOICE -> Question(
            questionText   = "What is 2 + 2?",
            questionType   = qt,
            options        = listOf("A) 3", "B) 4", "C) 5", "D) 6"),
            correctAnswer  = "B",
            explanation    = "2 + 2 equals 4.",
            difficulty     = diff
        )

        QuestionType.TRUE_FALSE -> Question(
            questionText   = "The Earth revolves around the Sun.",
            questionType   = qt,
            options        = listOf("True", "False"),
            correctAnswer  = "True",
            explanation    = "Basic astronomy fact.",
            difficulty     = diff
        )

        else -> Question(
            questionText   = "Complete: The capital of France is _____.",
            questionType   = QuestionType.FILL_IN_BLANK,
            options        = emptyList(),
            correctAnswer  = "Paris",
            explanation    = "Paris is France's capital.",
            difficulty     = diff
        )
    }

    private fun parseQuestionFromJson(
        raw: String,
        qt: QuestionType,
        diff: Difficulty
    ): Question = try {
        val json  = org.json.JSONObject(raw.trim())
        val opts  = json.optJSONArray("options")?.let { arr ->
            List(arr.length()) { i -> arr.getString(i) }
        } ?: emptyList()

        Question(
            questionText   = json.getString("question"),
            questionType   = qt,
            options        = opts,
            correctAnswer  = json.getString("correctAnswer"),
            explanation    = json.optString("explanation", ""),
            difficulty     = diff
        )
    } catch (e: Exception) {
        Timber.e(e, "JSON parse error – falling back to basic question")
        generateBasicQuestion(diff, qt)
    }

    private fun selectQuestionType(i: Int): QuestionType =
        when (i % 3) {
            0  -> QuestionType.MULTIPLE_CHOICE
            1  -> QuestionType.TRUE_FALSE
            else -> QuestionType.FILL_IN_BLANK
        }

    private fun updateUserProgress(
        sub: Subject,
        diff: Difficulty,
        correct: Boolean
    ) {
        _state.update { s ->
            val total = s.questionsGenerated.coerceAtLeast(1)
            val prev  = s.userProgress[sub] ?: 0f
            val new   = if (correct) (prev * (total - 1) + 1f) / total
            else          (prev * (total - 1))      / total
            s.copy(userProgress = s.userProgress + (sub to new))
        }
    }

}

