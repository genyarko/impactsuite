package com.example.mygemma3n.feature.quiz

// QuizGeneratorViewModel.kt


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.local.VectorDatabase
import com.example.mygemma3n.gemma.GemmaEngine
import com.example.mygemma3n.gemma.GemmaModelManager
import com.example.mygemma3n.shared_utilities.OfflineRAG
import com.example.mygemma3n.shared_utilities.generateEmbedding
import com.example.mygemma3n.shared_utilities.generateText
import dagger.hilt.android.lifecycle.HiltViewModel



import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuizGeneratorViewModel @Inject constructor(
    private val gemmaEngine: GemmaEngine,
    private val vectorDB: VectorDatabase,
    private val modelManager: GemmaModelManager,
    private val educationalContent: EducationalContentRepository,
    private val quizRepository: QuizRepository
) : ViewModel() {

    private val _quizState = MutableStateFlow(QuizState())
    val quizState: StateFlow<QuizState> = _quizState.asStateFlow()

    data class QuizState(
        val isGenerating: Boolean = false,
        val currentQuiz: Quiz? = null,
        val subjects: List<Subject> = emptyList(),
        val difficulty: Difficulty = Difficulty.MEDIUM,
        val questionsGenerated: Int = 0,
        val userProgress: Map<Subject, Float> = emptyMap()
    )

    init {
        initializeEducationalContent()
    }

    private fun initializeEducationalContent() {
        viewModelScope.launch {
            // Load pre-embedded educational content
            val contents = educationalContent.getAllContent()

            contents.forEach { content ->
                // Generate embeddings using Gemma's encoder
                val embedding = generateEmbedding(content.text)

                vectorDB.insert(
                    VectorDatabase.VectorDocument(
                        id = content.id,
                        content = content.text,
                        embedding = embedding,
                        metadata = mapOf(
                            "subject" to content.subject.name,
                            "grade" to content.gradeLevel,
                            "topic" to content.topic
                        )
                    )
                )
            }

            _quizState.update {
                it.copy(subjects = contents.map { it.subject }.distinct())
            }
        }
    }

    /** Called when the user finishes the quiz to clean up or reset state. */
    fun completeQuiz() {
        _quizState.update { it.copy(currentQuiz = null) }
        // Optional: record completed quiz in history, analytics, etc.
    }


    /**
     * Generate an adaptive quiz in the background.
     * This version is NOT suspend—starts its own coroutine.
     */
    fun generateAdaptiveQuiz(
        subject: OfflineRAG.Subject,
        topic: String,
        questionCount: Int = 10
    ) {
        viewModelScope.launch {
            _quizState.update { it.copy(isGenerating = true) }

            val questions = mutableListOf<Question>()
            val userLevel = getUserProficiencyLevel(
                subject,
                quizRepository.progressDao()
            )

            // Retrieve relevant content using RAG
            val relevantContent = retrieveRelevantContent(subject, topic, questionCount * 2)

            // Generate questions adaptively
            for (i in 1..questionCount) {
                val difficulty = calculateAdaptiveDifficulty(userLevel, questions)

                val question = generateQuestion(
                    context = relevantContent,
                    difficulty = difficulty,
                    previousQuestions = questions,
                    questionType = selectQuestionType(i)
                )

                questions.add(question)

                _quizState.update {
                    it.copy(questionsGenerated = questions.size)
                }
            }

            val quiz = Quiz(
                id = generateQuizId(),
                subject = subject,
                topic = topic,
                questions = questions,
                difficulty = _quizState.value.difficulty,
                createdAt = System.currentTimeMillis()
            )

            quizRepository.saveQuiz(quiz)

            _quizState.update {
                it.copy(
                    isGenerating = false,
                    currentQuiz = quiz
                )
            }
        }
    }


    private suspend fun generateQuestion(
        context: List<String>,
        difficulty: Difficulty,
        previousQuestions: List<Question>,
        questionType: QuestionType
    ): Question {
        val contextText = context.joinToString("\n\n")
        val previousQuestionsText = previousQuestions.joinToString("\n") {
            "- ${it.questionText}"
        }

        val prompt = """
            Based on the following educational content, generate a ${questionType.name} question.
            
            CONTENT:
            $contextText
            
            REQUIREMENTS:
            - Difficulty level: ${difficulty.name}
            - Question type: ${questionType.name}
            - Make it different from these previous questions:
            $previousQuestionsText
            
            FORMAT YOUR RESPONSE AS JSON:
            {
                "question": "...",
                "options": ["A) ...", "B) ...", "C) ...", "D) ..."],
                "correctAnswer": "A",
                "explanation": "...",
                "hint": "...",
                "conceptsCovered": ["concept1", "concept2"]
            }
            
            For TRUE/FALSE questions, use options ["True", "False"].
            For FILL_IN_BLANK, use a single blank marked as _____.
        """.trimIndent()

        val response = gemmaEngine.generateText(
            prompt = prompt,
            maxTokens = 300,
            temperature = 0.7f,
            modelConfig = GemmaModelManager.ModelConfig.BALANCED_3B
        ).toList().joinToString("")

        return parseQuestionFromJson(response, questionType, difficulty)
    }

    private suspend fun retrieveRelevantContent(
        subject: Subject,
        topic: String,
        k: Int
    ): List<String> {
        // 1️⃣ Embed the query
        val queryEmbedding = generateEmbedding("$subject $topic educational content")

        // 2️⃣ Vector search (note: parameter name is embedding)
        val results = vectorDB.search(
            embedding = queryEmbedding,
            k = k,
            filter = mapOf("subject" to subject.name, "topic" to topic)
        )

        // 3️⃣ Extract actual text
        return results.map { it.document.content }   // ← field path corrected
    }


    // helper at the bottom of the file (NEW)
    /** Builds an embedding using the dedicated on-device model. */
    private suspend fun generateEmbedding(text: String): FloatArray {
        val embeddingModel = modelManager.getEmbeddingModel()
        val tokens = modelManager.tokenize(text, maxLength = 512)
        val input = mapOf("input_ids" to tokens)
        val output = mutableMapOf<String, Any>(
            "embeddings" to FloatArray(GemmaModelManager.EMBEDDING_DIM)
        )
        embeddingModel.runSignature(input, output)
        val vec = output["embeddings"] as FloatArray
        // L2-normalise
        var sum = 0f; for (v in vec) sum += v*v
        val norm = kotlin.math.sqrt(sum)
        return if (norm > 0) FloatArray(vec.size) { i -> vec[i]/norm } else vec
    }

    // ─────────────────────────────────────────────────────────────
// Place this inside QuizGeneratorViewModel (private section) ──
    private fun updateUserProgress(
        subject: Subject,
        difficulty: Difficulty,
        isCorrect: Boolean
    ) {
        _quizState.update { state ->
            val current = state.userProgress[subject] ?: 0f       // stored accuracy 0-1
            val count   = state.questionsGenerated.coerceAtLeast(1)
            val newAcc  = if (isCorrect)
                (current * (count - 1) + 1f) / count              // correct ➜ boost accuracy
            else
                (current * (count - 1)) / count                   // wrong ➜ average unchanged

            state.copy(
                userProgress = state.userProgress + (subject to newAcc)
            )
        }
    }
// ─────────────────────────────────────────────────────────────



    fun submitAnswer(questionId: String, answer: String) {
        viewModelScope.launch {
            val quiz = _quizState.value.currentQuiz ?: return@launch
            val question = quiz.questions.find { it.id == questionId } ?: return@launch

            val isCorrect = answer == question.correctAnswer

            // Update user progress
            updateUserProgress(quiz.subject, question.difficulty, isCorrect)

            // Generate personalized feedback
            val feedback = generatePersonalizedFeedback(
                question = question,
                userAnswer = answer,
                isCorrect = isCorrect
            )

            _quizState.update { state ->
                state.copy(
                    currentQuiz = quiz.copy(
                        questions = quiz.questions.map {
                            if (it.id == questionId) {
                                it.copy(
                                    userAnswer = answer,
                                    feedback = feedback,
                                    isAnswered = true
                                )
                            } else it
                        }
                    )
                )
            }
        }
    }

    private suspend fun generatePersonalizedFeedback(
        question: Question,
        userAnswer: String,
        isCorrect: Boolean
    ): String {
        val prompt = if (isCorrect) {
            """
                The user correctly answered: "${question.questionText}"
                Correct answer: ${question.correctAnswer}
                
                Provide encouraging feedback and add an interesting fact about ${question.conceptsCovered.joinToString()}.
                Keep it under 50 words.
            """.trimIndent()
        } else {
            """
                Question: "${question.questionText}"
                User answered: $userAnswer
                Correct answer: ${question.correctAnswer}
                
                Provide constructive feedback explaining why their answer was incorrect and why the correct answer is right.
                Reference the concepts: ${question.conceptsCovered.joinToString()}
                Keep it supportive and under 75 words.
            """.trimIndent()
        }

        return gemmaEngine.generateText(
            prompt = prompt,
            maxTokens = 100,
            temperature = 0.7f
        ).toList().joinToString("")
    }



}

