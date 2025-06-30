package com.example.mygemma3n.feature.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.local.VectorDatabase
import com.example.mygemma3n.gemma.GemmaEngine
import com.example.mygemma3n.gemma.GemmaModelManager
import com.example.mygemma3n.shared_utilities.OfflineRAG
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
        // Initialize with prepopulated content
        viewModelScope.launch {
            try {
                // First, prepopulate the educational content
                educationalContent.prepopulateContent()

                // Then initialize
                initializeEducationalContent()
            } catch (e: Exception) {
                // If content loading fails, at least show available subjects
                _quizState.update {
                    it.copy(subjects = Subject.values().toList())
                }
                println("Error initializing educational content: ${e.message}")
            }
        }
    }

    private suspend fun initializeEducationalContent() {
        try {
            // Load pre-embedded educational content
            val contents = educationalContent.getAllContent()

            if (contents.isEmpty()) {
                // If no content, just show available subjects
                _quizState.update {
                    it.copy(subjects = Subject.values().toList())
                }
                return
            }

            contents.forEach { content ->
                try {
                    // Generate embeddings using the embedding model
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
                } catch (e: Exception) {
                    println("Error embedding content ${content.id}: ${e.message}")
                }
            }

            _quizState.update {
                it.copy(subjects = contents.map { it.subject }.distinct())
            }
        } catch (e: Exception) {
            println("Error in initializeEducationalContent: ${e.message}")
            // Fallback to showing all subjects
            _quizState.update {
                it.copy(subjects = Subject.values().toList())
            }
        }
    }

    /** Called when the user finishes the quiz to clean up or reset state. */
    fun completeQuiz() {
        _quizState.update { it.copy(currentQuiz = null, questionsGenerated = 0) }
    }

    /**
     * Generate an adaptive quiz in the background.
     */
    fun generateAdaptiveQuiz(
        subject: Subject,
        topic: String,
        questionCount: Int = 10
    ) {
        viewModelScope.launch {
            _quizState.update { it.copy(isGenerating = true, questionsGenerated = 0) }

            try {
                // Initialize GemmaEngine if needed
                if (!isGemmaInitialized()) {
                    initializeGemmaEngine()
                }

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
            } catch (e: Exception) {
                println("Error generating quiz: ${e.message}")
                _quizState.update {
                    it.copy(isGenerating = false)
                }
            }
        }
    }

    fun submitAnswer(questionId: String, answer: String) {
        viewModelScope.launch {
            val quiz = _quizState.value.currentQuiz ?: return@launch
            val question = quiz.questions.find { it.id == questionId } ?: return@launch

            val isCorrect = answer == question.correctAnswer

            // Update user progress
            updateUserProgress(quiz.subject, question.difficulty, isCorrect)

            // Record progress in database
            quizRepository.recordProgress(
                subject = quiz.subject,
                difficulty = question.difficulty,
                correct = isCorrect,
                responseTimeMs = 1000L // You might want to track actual response time
            )

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

    private var gemmaInitialized = false

    private fun isGemmaInitialized(): Boolean = gemmaInitialized

    private suspend fun initializeGemmaEngine() {
        // Initialize with a default configuration
        val config = GemmaEngine.InferenceConfig(
            modelPath = getModelPath(),
            useGpu = true,
            useNnapi = true,
            numThreads = 4,
            temperature = 0.7f
        )
        gemmaEngine.initialize(config)
        gemmaInitialized = true
    }

    private suspend fun getModelPath(): String {
        // Try to get a model path from the model manager or repository
        return modelManager.getModelStats().let { stats ->
            stats.availableModels.firstOrNull()?.path
                ?: throw IllegalStateException("No Gemma model found. Please download a model first.")
        }
    }

    private suspend fun generateQuestion(
        context: List<String>,
        difficulty: Difficulty,
        previousQuestions: List<Question>,
        questionType: QuestionType
    ): Question {
        // If no context available, generate a basic question
        if (context.isEmpty()) {
            return generateBasicQuestion(difficulty, questionType)
        }

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

        return try {
            // Use the correct GemmaEngine method signature
            val generationConfig = GemmaEngine.GenerationConfig(
                maxNewTokens = 300,
                temperature = 0.7f,
                topK = 40,
                topP = 0.95f
            )

            val response = gemmaEngine.generateText(prompt, generationConfig)
                .toList()
                .joinToString("")

            parseQuestionFromJson(response, questionType, difficulty)
        } catch (e: Exception) {
            println("Error generating question: ${e.message}")
            generateBasicQuestion(difficulty, questionType)
        }
    }

    private fun generateBasicQuestion(difficulty: Difficulty, questionType: QuestionType): Question {
        // Fallback questions when generation fails
        return when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> Question(
                questionText = "What is 2 + 2?",
                questionType = questionType,
                options = listOf("A) 3", "B) 4", "C) 5", "D) 6"),
                correctAnswer = "B",
                explanation = "2 + 2 equals 4",
                difficulty = difficulty
            )
            QuestionType.TRUE_FALSE -> Question(
                questionText = "The Earth revolves around the Sun.",
                questionType = questionType,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "The Earth orbits the Sun once per year.",
                difficulty = difficulty
            )
            else -> Question(
                questionText = "Complete: The capital of France is _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                correctAnswer = "Paris",
                explanation = "Paris is the capital city of France.",
                difficulty = difficulty
            )
        }
    }

    private suspend fun retrieveRelevantContent(
        subject: Subject,
        topic: String,
        k: Int
    ): List<String> {
        return try {
            // Generate embedding for the query
            val queryEmbedding = generateEmbedding("$subject $topic educational content")

            // Vector search
            val results = vectorDB.search(
                embedding = queryEmbedding,
                k = k,
                filter = mapOf("subject" to subject.name)
            )

            // Extract actual text
            results.map { it.document.content }
        } catch (e: Exception) {
            println("Error retrieving content: ${e.message}")
            emptyList()
        }
    }

    /** Builds an embedding using the dedicated on-device model. */
    private suspend fun generateEmbedding(text: String): FloatArray {
        return try {
            val embeddingModel = modelManager.getEmbeddingModel()
            val tokens = modelManager.tokenize(text, maxLength = 512)

            // Create input buffer
            val inputBuffer = java.nio.ByteBuffer.allocateDirect(tokens.size * 4)
                .order(java.nio.ByteOrder.nativeOrder())
            tokens.forEach { inputBuffer.putInt(it) }
            inputBuffer.rewind()

            // Create output buffer
            val outputBuffer = java.nio.ByteBuffer.allocateDirect(GemmaModelManager.EMBEDDING_DIM * 4)
                .order(java.nio.ByteOrder.nativeOrder())

            // Run the model
            embeddingModel.run(inputBuffer, outputBuffer)

            // Extract embeddings
            outputBuffer.rewind()
            val embeddings = FloatArray(GemmaModelManager.EMBEDDING_DIM)
            outputBuffer.asFloatBuffer().get(embeddings)

            // L2-normalize
            var sum = 0f
            for (v in embeddings) sum += v * v
            val norm = kotlin.math.sqrt(sum)
            if (norm > 0) {
                FloatArray(embeddings.size) { i -> embeddings[i] / norm }
            } else {
                embeddings
            }
        } catch (e: Exception) {
            println("Error generating embedding: ${e.message}")
            // Return a dummy embedding
            FloatArray(GemmaModelManager.EMBEDDING_DIM) { 0f }
        }
    }

    private fun updateUserProgress(
        subject: Subject,
        difficulty: Difficulty,
        isCorrect: Boolean
    ) {
        _quizState.update { state ->
            val current = state.userProgress[subject] ?: 0f
            val count = state.questionsGenerated.coerceAtLeast(1)
            val newAcc = if (isCorrect)
                (current * (count - 1) + 1f) / count
            else
                (current * (count - 1)) / count

            state.copy(
                userProgress = state.userProgress + (subject to newAcc)
            )
        }
    }

    // Add this at the bottom of QuizGeneratorViewModel.kt, outside the class
    private suspend fun GemmaEngine.generateText(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        modelConfig: GemmaModelManager.ModelConfig
    ): Flow<String> {
        val generationConfig = GemmaEngine.GenerationConfig(
            maxNewTokens = maxTokens,
            temperature = temperature
        )
        return generateText(prompt, generationConfig)
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

        return try {
            if (!isGemmaInitialized()) {
                initializeGemmaEngine()
            }

            val generationConfig = GemmaEngine.GenerationConfig(
                maxNewTokens = 100,
                temperature = 0.7f
            )

            gemmaEngine.generateText(prompt, generationConfig)
                .toList()
                .joinToString("")
        } catch (e: Exception) {
            if (isCorrect) {
                "Great job! You got it right."
            } else {
                "Not quite. The correct answer is ${question.correctAnswer}. ${question.explanation}"
            }
        }
    }
}