package com.example.mygemma3n.feature

// ===== Feature: Offline Quiz Generator with RAG =====

// QuizGeneratorViewModel.kt


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.gemma.GemmaEngine
import dagger.hilt.android.lifecycle.HiltViewModel

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.security.auth.Subject

@HiltViewModel
class QuizGeneratorViewModel @Inject constructor(
    private val gemmaEngine: GemmaEngine,
    private val vectorDB: VectorDatabase,
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
                    Vectorocument(
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

    suspend fun generateAdaptiveQuiz(
        subject: Subject,
        topic: String,
        questionCount: Int = 10
    ) {
        _quizState.update { it.copy(isGenerating = true) }

        val questions = mutableListOf<Question>()
        val userLevel = getUserProficiencyLevel(subject)

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
            modelConfig = ModelConfig.Balanced3B
        ).toList().joinToString("")

        return parseQuestionFromJson(response, questionType, difficulty)
    }

    private suspend fun retrieveRelevantContent(
        subject: Subject,
        topic: String,
        k: Int
    ): List<String> {
        // Generate query embedding
        val queryEmbedding = generateEmbedding("$subject $topic educational content")

        // Retrieve from vector database with metadata filtering
        val results = vectorDB.search(
            queryEmbedding = queryEmbedding,
            k = k,
            filter = mapOf(
                "subject" to subject.name,
                "topic" to topic
            )
        )

        return results.map { it.content }
    }

    private suspend fun generateEmbedding(text: String): FloatArray {
        // Use Gemma's encoder for generating embeddings
        val prompt = """
            Generate a semantic embedding for the following text:
            "$text"
            
            [EMBEDDING]:
        """.trimIndent()

        // In practice, you'd use a dedicated embedding model or Gemma's encoder
        // This is a simplified representation
        return gemmaEngine.generateEmbedding(text)
    }

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

// QuizScreen.kt - Compose UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: QuizGeneratorViewModel = hiltViewModel()
) {
    val state by viewModel.quizState.collectAsState()
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var selectedTopic by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adaptive Quiz Generator") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        when {
            state.currentQuiz == null && !state.isGenerating -> {
                // Quiz Setup Screen
                QuizSetupScreen(
                    subjects = state.subjects,
                    onGenerateQuiz = { subject, topic, count ->
                        viewModel.generateAdaptiveQuiz(subject, topic, count)
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            state.isGenerating -> {
                // Generation Progress
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Generating adaptive quiz...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Questions created: ${state.questionsGenerated}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            state.currentQuiz != null -> {
                // Quiz Taking Screen
                QuizTakingScreen(
                    quiz = state.currentQuiz!!,
                    onAnswerSubmit = { questionId, answer ->
                        viewModel.submitAnswer(questionId, answer)
                    },
                    onQuizComplete = {
                        viewModel.completeQuiz()
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun QuizTakingScreen(
    quiz: Quiz,
    onAnswerSubmit: (String, String) -> Unit,
    onQuizComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentQuestionIndex by remember { mutableStateOf(0) }
    val currentQuestion = quiz.questions[currentQuestionIndex]
    var selectedAnswer by remember(currentQuestion.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = (currentQuestionIndex + 1) / quiz.questions.size.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Question counter
        Text(
            text = "Question ${currentQuestionIndex + 1} of ${quiz.questions.size}",
            style = MaterialTheme.typography.labelLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Question
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = currentQuestion.questionText,
                    style = MaterialTheme.typography.headlineSmall
                )

                if (currentQuestion.hint != null && !currentQuestion.isAnswered) {
                    Spacer(modifier = Modifier.height(8.dp))

                    var showHint by remember { mutableStateOf(false) }

                    TextButton(
                        onClick = { showHint = !showHint }
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showHint) "Hide Hint" else "Show Hint")
                    }

                    if (showHint) {
                        Text(
                            text = currentQuestion.hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Answer options
        when (currentQuestion.type) {
            QuestionType.MULTIPLE_CHOICE -> {
                currentQuestion.options.forEach { option ->
                    AnswerOption(
                        text = option,
                        isSelected = selectedAnswer == option,
                        isCorrect = currentQuestion.isAnswered && option == currentQuestion.correctAnswer,
                        isWrong = currentQuestion.isAnswered && option == currentQuestion.userAnswer && option != currentQuestion.correctAnswer,
                        enabled = !currentQuestion.isAnswered,
                        onClick = { selectedAnswer = option }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            QuestionType.TRUE_FALSE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("True", "False").forEach { option ->
                        ElevatedButton(
                            onClick = { selectedAnswer = option },
                            enabled = !currentQuestion.isAnswered,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = when {
                                    currentQuestion.isAnswered && option == currentQuestion.correctAnswer ->
                                        MaterialTheme.colorScheme.primary
                                    currentQuestion.isAnswered && option == currentQuestion.userAnswer && option != currentQuestion.correctAnswer ->
                                        MaterialTheme.colorScheme.error
                                    selectedAnswer == option ->
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Text(option)
                        }
                    }
                }
            }

            QuestionType.FILL_IN_BLANK -> {
                var textAnswer by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = textAnswer,
                    onValueChange = {
                        textAnswer = it
                        selectedAnswer = it
                    },
                    label = { Text("Your answer") },
                    enabled = !currentQuestion.isAnswered,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Feedback section
        if (currentQuestion.isAnswered && currentQuestion.feedback != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (currentQuestion.userAnswer == currentQuestion.correctAnswer)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (currentQuestion.userAnswer == currentQuestion.correctAnswer)
                                Icons.Default.CheckCircle
                            else
                                Icons.Default.Cancel,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentQuestion.userAnswer == currentQuestion.correctAnswer)
                                "Correct!" else "Incorrect",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentQuestion.feedback,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (currentQuestion.explanation != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Explanation: ${currentQuestion.explanation}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentQuestionIndex > 0) {
                OutlinedButton(
                    onClick = { currentQuestionIndex-- }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Previous")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            if (!currentQuestion.isAnswered && selectedAnswer != null) {
                Button(
                    onClick = {
                        onAnswerSubmit(currentQuestion.id, selectedAnswer!!)
                    }
                ) {
                    Text("Submit Answer")
                }
            } else if (currentQuestion.isAnswered) {
                if (currentQuestionIndex < quiz.questions.size - 1) {
                    Button(
                        onClick = {
                            currentQuestionIndex++
                            selectedAnswer = null
                        }
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = onQuizComplete
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Complete Quiz")
                    }
                }
            }
        }
    }
}

// ===== Vector Database Implementation =====

// VectorDatabase.kt
package com.impactsuite.data.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VectorDatabase @Inject constructor(
    private val roomDatabase: AppDatabase
) {

    data class VectorDocument(
        val id: String = UUID.randomUUID().toString(),
        val content: String,
        val embedding: FloatArray,
        val metadata: Map<String, String> = emptyMap()
    )

    suspend fun insert(document: VectorDocument) = withContext(Dispatchers.IO) {
        val entity = VectorEntity(
            id = document.id,
            content = document.content,
            embedding = document.embedding.toList(),
            metadata = document.metadata
        )
        roomDatabase.vectorDao().insert(entity)
    }

    suspend fun search(
        queryEmbedding: FloatArray,
        k: Int = 5,
        filter: Map<String, String>? = null
    ): List<VectorDocument> = withContext(Dispatchers.IO) {
        val allDocuments = if (filter != null) {
            roomDatabase.vectorDao().getByMetadata(filter)
        } else {
            roomDatabase.vectorDao().getAll()
        }

        // Calculate cosine similarity
        val scoredDocs = allDocuments.map { entity ->
            val similarity = cosineSimilarity(
                queryEmbedding,
                entity.embedding.toFloatArray()
            )
            entity to similarity
        }

        // Return top-k results
        scoredDocs
            .sortedByDescending { it.second }
            .take(k)
            .map { (entity, _) ->
                VectorDocument(
                    id = entity.id,
                    content = entity.content,
                    embedding = entity.embedding.toFloatArray(),
                    metadata = entity.metadata
                )
            }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }
}