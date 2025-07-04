/**
 * QuizGeneratorViewModel.kt
 *
 * Cloud-only version — no on-device models.
 */
package com.example.mygemma3n.feature.quiz

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.GeminiApiService
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
    private val geminiApi: GeminiApiService,
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
        val error: String? = null
    )

    /* ───────── Init ───────── */

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

    /* ───────── Public API ───────── */

    fun completeQuiz() =
        _state.update { it.copy(currentQuiz = null, questionsGenerated = 0, error = null) }

    /** Create a quiz, avoiding duplicates from previous _subject + topic_ quizzes. */
    fun generateAdaptiveQuiz(
        subject: Subject,
        topic: String,
        questionCount: Int = 10
    ) = viewModelScope.launch {

        // reset UI
        _state.update { it.copy(isGenerating = true, questionsGenerated = 0, error = null) }

        try {
            /* ① collect every previous question for this subject + topic */
            val historyTexts: List<String> = quizRepo
                .getQuizzesFor(subject, topic)           // <- DAO helper
                .flatMap { it.questions }
                .map { it.questionText }                 // List<String>

            val questions = mutableListOf<Question>()

            /* ② generate new, non-duplicate questions */
            repeat(questionCount) { idx ->
                val q = generateQuestion(
                    difficulty        = _state.value.difficulty,
                    previousQuestions = historyTexts +              // all past text
                            questions.map { it.questionText },  // plus ones just made
                    questionType      = selectQuestionType(idx + 1)
                )
                questions += q
                _state.update { it.copy(questionsGenerated = questions.size) }
            }

            /* ③ persist the quiz and expose to UI */
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


    /* ───── Question generation (now takes List<String>) ───── */
    /* ─────────────────────── Question generation ─────────────────────── */

    private suspend fun generateQuestion(
        difficulty: Difficulty,
        previousQuestions: List<String>,        // <-- strings only
        questionType: QuestionType
    ): Question = withContext(Dispatchers.IO) {

        val prompt = """
        Generate one **$questionType** quiz question in JSON with keys:
          question, options, correctAnswer, explanation.
        Difficulty: ${difficulty.name}.

        **Do NOT duplicate** any of these prior questions:
        ${previousQuestions.joinToString("\n")}
    """.trimIndent()

        try {
            val raw = geminiApi.generateTextComplete(prompt)
            parseQuestionFromJson(raw, questionType, difficulty)
        } catch (e: Exception) {
            Timber.e(e, "Gemini API failed – falling back")
            generateBasicQuestion(difficulty, questionType)
        }
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
            geminiApi.generateTextComplete(prompt)
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
        quizRepo.getQuizzesFor(subject, topic)   // DAO layer; see Room docs :contentReference[oaicite:1]{index=1}
            .flatMap { it.questions }
            .map { it.questionText }
}
