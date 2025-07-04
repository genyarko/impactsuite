package com.example.mygemma3n.feature.quiz

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.ModelDownloadManager
import com.example.mygemma3n.data.ModelRepository
import com.example.mygemma3n.data.local.VectorDatabase
import com.example.mygemma3n.di.EmbedderManager
import com.example.mygemma3n.feature.toList
import com.example.mygemma3n.gemma.GemmaEngine
import com.example.mygemma3n.gemma.GemmaModelManager
import com.example.mygemma3n.models.EmbedderModel
import com.example.mygemma3n.repository.SettingsRepository
import com.example.mygemma3n.shared_utilities.OfflineRAG
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Random
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * ViewModel responsible for generating adaptive quizzes using an on‑device Gemma model.
 *
 * ‑ Uses the single‑file model bundled in **assets/gemma-3n-E2B-it-int4.task** by default.
 * ‑ Falls back to downloading an updated model via [ModelDownloadManager] and reloads Gemma afterwards.
 * ‑ Expensive disk and CPU work is off‑loaded to IO / Default dispatchers to keep the UI responsive.
 */
@HiltViewModel
class QuizGeneratorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaEngine: GemmaEngine,
    private val vectorDB: VectorDatabase,
    private val modelManager: GemmaModelManager,
    private val modelRepository: ModelRepository,
    private val modelDownloadManager: ModelDownloadManager,
    private val educationalContent: EducationalContentRepository,
    private val quizRepository: QuizRepository,
    private val settingsRepo: SettingsRepository,
    private val embedderManager: EmbedderManager       // wrapper around MediaPipe Text-Embedder

) : ViewModel() {
    /*──────────────────────────────────────────────────────────────────────────*/
    /* ── State ── */

    private val _quizState = MutableStateFlow(QuizState())
    val quizState: StateFlow<QuizState> = _quizState.asStateFlow()

    data class QuizState(
        val isGenerating: Boolean = false,
        val currentQuiz: Quiz? = null,
        val subjects: List<Subject> = emptyList(),
        val difficulty: Difficulty = Difficulty.MEDIUM,
        val questionsGenerated: Int = 0,
        val userProgress: Map<Subject, Float> = emptyMap(),
        val error: String? = null,
        val modelStatus: ModelStatus = ModelStatus.CHECKING
    )

    enum class ModelStatus {
        CHECKING,
        READY,
        DOWNLOADING,
        ERROR
    }

    /*──────────────────────────────────────────────────────────────────────────*/
    /* ── Init ── */

    init {
        viewModelScope.launch {
            // 1) Check model availability first
            checkModelAvailability(context) { progress, ready, preparing, error ->
                _quizState.update { state ->
                    state.copy(
                        modelStatus   = when {
                            error != null    -> ModelStatus.ERROR
                            !ready && preparing -> ModelStatus.CHECKING
                            ready           -> ModelStatus.READY
                            else            -> ModelStatus.ERROR
                        },
                        // optionally track progress and error if you want
                        // modelProgress = progress,
                        // modelError    = error
                    )
                }
            }

            // 2) Then initialize educational content
            try {
                educationalContent.prepopulateContent()
                initializeEducationalContent()
            } catch (e: Exception) {
                _quizState.update {
                    it.copy(subjects = OfflineRAG.Subject.entries)
                }
                Timber.e(e, "Error initializing educational content")
            }
        }
    }


    private val REQUIRED_SHARDS = listOf(
        "TF_LITE_EMBEDDER.tflite.tflite",
        "TF_LITE_PER_LAYER_EMBEDDER.tflite.tflite",
        "TF_LITE_PREFILL_DECODE.tflite.tflite",
        "TF_LITE_VISION_ADAPTER.tflite.tflite",
        "TF_LITE_VISION_ENCODER.tflite.tflite",
        "TOKENIZER_MODEL.tflite.tflite",
        "METADATA"
    )

    fun checkModelAvailability(
        ctx: Context,
        onStatusUpdate: (progress: Float,
                         ready: Boolean,
                         preparing: Boolean,
                         error: String?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val available = ctx.assets.list("models")?.toSet() ?: emptySet()
            val missing   = REQUIRED_SHARDS.filterNot { it in available }

            if (missing.isEmpty()) {
                onStatusUpdate(100f, true, false, null)
            } else {
                onStatusUpdate(0f, false, false, "Missing files: ${missing.joinToString()}")
            }
        }
    }



    /** Call once after reading the saved preference. */
    fun initEmbedder(pref: EmbedderManager.Model) = viewModelScope.launch {
        embedderManager.load(pref)
    }



    private suspend fun initializeEducationalContent() {
        val contents = educationalContent.getAllContent()
        if (contents.isEmpty()) {
            _quizState.update { it.copy(subjects = OfflineRAG.Subject.entries) }
            return
        }

        withContext(Dispatchers.Default) {
            contents.forEach { content ->
                try {
                    val embedding = generateEmbedding(content.text)
                    vectorDB.insert(
                        VectorDatabase.VectorDocument(
                            id = content.id,
                            content = content.text,
                            embedding = embedding,
                            metadata = mapOf(
                                "subject" to content.subject.name,
                                "grade" to content.gradeLevel,
                                "topic" to content.topic,
                            )
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error embedding content ${content.id}")
                }
            }
        }

        _quizState.update { it.copy(subjects = contents.map { it.subject }.distinct()) }
    }



    /*──────────────────────────────────────────────────────────────────────────*/
    /* ── Public API ── */

    fun completeQuiz() {
        _quizState.update { it.copy(currentQuiz = null, questionsGenerated = 0, error = null) }
    }

    fun generateAdaptiveQuiz(
        subject: Subject,
        topic: String,
        questionCount: Int = 10,
    ) {
        viewModelScope.launch {
            _quizState.update { it.copy(isGenerating = true, questionsGenerated = 0, error = null) }

            try {
                initializeGemmaEngine()
                if (shouldDownloadUpdatedModel() && downloadUpdatedModel()) {
                    initializeGemmaEngine(force = true)
                }

                val relevantContent = withContext(Dispatchers.IO) {
                    retrieveRelevantContent(subject, topic, questionCount * 2)
                }

                val userLevel = getUserProficiencyLevel(subject, quizRepository.progressDao())
                val questions = mutableListOf<Question>()

                for (i in 1..questionCount) {
                    val diff = calculateAdaptiveDifficulty(userLevel, questions)
                    val q = withContext(Dispatchers.Default) {
                        generateQuestion(
                            context = relevantContent,
                            difficulty = diff,
                            previousQuestions = questions,
                            questionType = selectQuestionType(i),
                        )
                    }
                    questions.add(q)
                    _quizState.update { it.copy(questionsGenerated = questions.size) }
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

                _quizState.update { it.copy(isGenerating = false, currentQuiz = quiz) }
            } catch (e: Exception) {
                Timber.e(e, "Error generating quiz")
                _quizState.update {
                    it.copy(isGenerating = false, error = "Failed to generate quiz: ${e.message}")
                }
            }
        }
    }

    fun submitAnswer(questionId: String, answer: String) {
        viewModelScope.launch {
            val quiz = _quizState.value.currentQuiz ?: return@launch
            val question = quiz.questions.find { it.id == questionId } ?: return@launch
            val correct = answer == question.correctAnswer

            updateUserProgress(quiz.subject, question.difficulty, correct)
            quizRepository.recordProgress(
                quiz.subject, question.difficulty, correct, 1_000L
            )

            val feedback = generatePersonalizedFeedback(question, answer, correct)
            _quizState.update {
                it.copy(
                    currentQuiz = quiz.copy(
                        questions = quiz.questions.map { q ->
                            if (q.id == questionId) q.copy(
                                userAnswer = answer,
                                feedback = feedback,
                                isAnswered = true
                            ) else q
                        }
                    )
                )
            }
        }
    }

    /* ─────────────────────── Gemma init ─────────────────────── */

    private val modelInitMutex = Mutex()
    private var gemmaInitialized = false

    private suspend fun initializeGemmaEngine(force: Boolean = false) {
        if (gemmaInitialized && !force) return

        modelInitMutex.withLock {
            if (gemmaInitialized && !force) return@withLock

            try {
                val modelPath = getValidModelPath() ?: run {
                    Timber.w("No valid model – fallback mode")
                    _quizState.update { it.copy(modelStatus = ModelStatus.ERROR) }
                    return@withLock
                }

                val cfg = GemmaEngine.InferenceConfig(
                    modelPath = modelPath,
                    numThreads = 4,
                    temperature = 0.7f
                )

                gemmaEngine.initialize(cfg)
                gemmaInitialized = true
                _quizState.update { it.copy(modelStatus = ModelStatus.READY) }

            } catch (e: Exception) {
                Timber.e(e, "Gemma init failed")
                _quizState.update { it.copy(modelStatus = ModelStatus.ERROR) }
            }
        }
    }

    private suspend fun getValidModelPath(): String? = withContext(Dispatchers.IO) {
        File(context.filesDir, "models")
            .listFiles { f -> f.extension in setOf("tflite", "task") && f.length() > 0 }
            ?.firstOrNull()
            ?.absolutePath
            ?: copyBundledAsset("models/gemma-3n-E2B-it-int4.task")
    }

    private fun copyBundledAsset(name: String): String {
        val out = File(context.filesDir, "models/$name")
        if (!out.exists()) context.assets.open(name).use { it.copyTo(out.outputStream()) }
        return out.absolutePath
    }



    private suspend fun getModelPath(): String? = withContext(Dispatchers.IO) {
        // Use ModelRepository's unified model loading
        modelRepository.getGemmaModelPath() ?: run {
            // Fallback: ensure we have a model by copying from assets
            val modelNames = listOf("models/gemma-3n-E2B-it-int4.task", "gemma-3n-E4B-it-int4.task")

            for (modelName in modelNames) {
                val cacheFile = File(context.cacheDir, modelName)

                if (!cacheFile.exists()) {
                    try {
                        context.assets.open(modelName).use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Timber.d("Copied model from assets: $modelName")
                        return@withContext cacheFile.absolutePath
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to copy model from assets: $modelName")
                        continue
                    }
                }
            }

            Timber.e("No model available in assets or cache")
            null
        }
    }

    private suspend fun shouldDownloadUpdatedModel(): Boolean {
        // Check if we already have a downloaded model
        val hasDownloadedModel = modelRepository.getAvailableModels().isNotEmpty()
        if (hasDownloadedModel) return false

        // Check if we're connected to wifi (to avoid mobile data usage)
        // This is a simplified check - in production you'd use ConnectivityManager
        return false // For now, don't auto-download
    }

    private suspend fun downloadUpdatedModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            _quizState.update { it.copy(modelStatus = ModelStatus.DOWNLOADING) }

            val request = ModelDownloadManager.DownloadRequest(
                url = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-2b-it-fast.tflite",
                name = "gemma-2b-it-fast",
                type = "gemma-3n-2b"
            )

            val downloadState = ensureModelDownloaded(request)

            when (downloadState) {
                is ModelDownloadManager.DownloadState.Success -> {
                    Timber.d("Model downloaded successfully: ${downloadState.modelPath}")
                    _quizState.update { it.copy(modelStatus = ModelStatus.READY) }
                    true
                }
                is ModelDownloadManager.DownloadState.Error -> {
                    Timber.e("Model download failed: ${downloadState.message}")
                    _quizState.update { it.copy(modelStatus = ModelStatus.ERROR) }
                    false
                }
                else -> {
                    _quizState.update { it.copy(modelStatus = ModelStatus.ERROR) }
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during model download")
            _quizState.update { it.copy(modelStatus = ModelStatus.ERROR) }
            false
        }
    }

    private suspend fun ensureModelDownloaded(
        request: ModelDownloadManager.DownloadRequest
    ): ModelDownloadManager.DownloadState {
        if (modelDownloadManager.isModelAvailable(request.name)) {
            val model = modelDownloadManager.getAvailableModels().first { it.name == request.name }
            return ModelDownloadManager.DownloadState.Success(model.path, model.size)
        }

        var result: ModelDownloadManager.DownloadState = ModelDownloadManager.DownloadState.Idle
        modelDownloadManager.downloadModel(request).collect { state ->
            result = state
            // Update UI with download progress
            if (state is ModelDownloadManager.DownloadState.Downloading) {
                Timber.d("Download progress: ${state.progress}%")
            }
        }
        return result
    }

    /*──────────────────────────────────────────────────────────────────────────*/
    /* ── Question generation helpers ── */

    private suspend fun generateQuestionWithModel(
        context: List<String>,
        difficulty: Difficulty,
        previousQuestions: List<Question>,
        questionType: QuestionType,
    ): Question {
        if (context.isEmpty()) return generateBasicQuestion(difficulty, questionType)

        val contextText = context.joinToString("\n\n")
        val previousQuestionsText = previousQuestions.joinToString("\n") { "- ${it.questionText}" }

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
            val generationConfig = GemmaEngine.GenerationConfig(
                maxNewTokens = 300,
                temperature = 0.7f,
                topK = 40,
                topP = 0.95f,
            )

            val raw = gemmaEngine.generateText(prompt, generationConfig)
                .toList()
                .joinToString("")

            parseQuestionFromJson(raw, questionType, difficulty)
        } catch (e: Exception) {
            Timber.e(e, "Error generating question")
            generateBasicQuestion(difficulty, questionType)
        }
    }


    private suspend fun generateQuestion(
        context: List<String>,
        difficulty: Difficulty,
        previousQuestions: List<Question>,
        questionType: QuestionType,
    ): Question {
        // Check if model is available
        if (_quizState.value.modelStatus != ModelStatus.READY || !gemmaInitialized) {
            // Use rule-based generation as fallback
            return generateQuestionWithRules(context, difficulty, questionType, previousQuestions)
        }

        // Original model-based generation code...
        return try {
            generateQuestionWithModel(
                context          = context,
                difficulty       = difficulty,
                previousQuestions = previousQuestions,   // 3rd
                questionType     = questionType         // 4th
            )
        } catch (e: Exception) {
            Timber.e(e, "Model generation failed, using fallback")
            generateQuestionWithRules(context, difficulty, questionType, previousQuestions)
        }

    }

    // Rule-based question generation as fallback
    private fun generateQuestionWithRules(
        context: List<String>,
        difficulty: Difficulty,
        questionType: QuestionType,
        previousQuestions: List<Question>
    ): Question {
        // This is a simplified rule-based generator
        // You can expand this with more sophisticated rules

        val questionTemplates = when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> listOf(
                "Which of the following best describes %s?",
                "What is the main characteristic of %s?",
                "According to the content, %s is:",
                "Which statement about %s is correct?"
            )
            QuestionType.TRUE_FALSE -> listOf(
                "%s is an example of %s.",
                "The primary function of %s is %s.",
                "%s can be classified as %s.",
                "It is true that %s %s."
            )
            QuestionType.FILL_IN_BLANK -> listOf(
                "The process of %s involves _____.",
                "_____ is the term used to describe %s.",
                "%s is characterized by _____.",
                "The main component of %s is _____."
            )
            else -> listOf("Explain the concept of %s.")
        }

        // Extract key terms from context
        val keyTerms = extractKeyTerms(context)
        val template = questionTemplates.random()
        val term = keyTerms.randomOrNull() ?: "this concept"

        val questionText = template.replace("%s", term)

        return when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> {
                val correctAnswer = generateCorrectAnswer(term, context)
                val distractors = generateDistractors(correctAnswer, term, 3)
                val options = (listOf(correctAnswer) + distractors).shuffled()
                val correctIndex = options.indexOf(correctAnswer)

                Question(
                    questionText = questionText,
                    questionType = questionType,
                    options = options.mapIndexed { index, option ->
                        "${('A' + index)}) $option"
                    },
                    correctAnswer = "${('A' + correctIndex)}",
                    explanation = "The correct answer is based on the educational content provided.",
                    difficulty = difficulty
                )
            }
            QuestionType.TRUE_FALSE -> {
                val isTrue = kotlin.random.Random.nextBoolean()
                Question(
                    questionText = questionText,
                    questionType = questionType,
                    options = listOf("True", "False"),
                    correctAnswer = if (isTrue) "True" else "False",
                    explanation = "This statement is ${if (isTrue) "true" else "false"} based on the content.",
                    difficulty = difficulty
                )
            }
            else -> {
                Question(
                    questionText = questionText,
                    questionType = QuestionType.FILL_IN_BLANK,
                    correctAnswer = generateCorrectAnswer(term, context),
                    explanation = "The answer can be found in the educational content.",
                    difficulty = difficulty
                )
            }
        }
    }

    private fun extractKeyTerms(context: List<String>): List<String> {
        // Simple keyword extraction
        val allText = context.joinToString(" ")
        val words = allText.split(Regex("\\s+"))
            .filter { it.length > 4 }
            .map { it.lowercase().trim(',', '.', '!', '?') }

        // Get most frequent terms
        return words.groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }

    private fun generateCorrectAnswer(term: String, context: List<String>): String {
        // Generate a plausible answer based on the term and context
        val contextText = context.joinToString(" ").lowercase()

        return when {
            "definition" in contextText -> "A clear and precise explanation"
            "process" in contextText -> "A series of steps or actions"
            "function" in contextText -> "The purpose or role it serves"
            "example" in contextText -> "A specific instance or case"
            else -> "An important concept in this subject"
        }
    }

    private fun generateDistractors(correctAnswer: String, term: String, count: Int): List<String> {
        val distractorTemplates = listOf(
            "An outdated understanding",
            "A common misconception",
            "A related but different concept",
            "An incomplete explanation",
            "A superficial description",
            "An alternative theory",
            "A historical perspective"
        )

        return distractorTemplates.shuffled().take(count)
    }
    /* ─────────────────────── Basic fallback question ─────────────────────── */

    private fun generateBasicQuestion(
        difficulty: Difficulty,
        questionType: QuestionType
    ): Question = when (questionType) {
        QuestionType.MULTIPLE_CHOICE -> Question(
            questionText = "What is 2 + 2?",
            questionType = questionType,
            options = listOf("A) 3", "B) 4", "C) 5", "D) 6"),
            correctAnswer = "B",
            explanation = "2 + 2 equals 4.",
            difficulty = difficulty
        )

        QuestionType.TRUE_FALSE -> Question(
            questionText = "The Earth revolves around the Sun.",
            questionType = questionType,
            options = listOf("True", "False"),
            correctAnswer = "True",
            explanation = "The Earth completes one orbit every year.",
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

    private suspend fun retrieveRelevantContent(
        subject: Subject,
        topic: String,
        k: Int,
    ): List<String> = try {
        val queryEmbedding = generateEmbedding("$subject $topic educational content")
        val results = vectorDB.search(
            embedding = queryEmbedding,
            k = k,
            filter = mapOf("subject" to subject.name),
        )
        results.map { it.document.content }
    } catch (e: Exception) {
        Timber.e(e, "Error retrieving content")
        emptyList()
    }
     // cached interpreter

    // Safe embedding generation with fallback
    private suspend fun generateEmbedding(text: String): FloatArray {
        return try {
            // Try to load the embedder if not already loaded
            if (!embedderManager.isLoaded()) {
                // Check available models
                val availableModels = embedderManager.getAvailableModels()
                if (availableModels.isEmpty()) {
                    Timber.e("No embedding models available in assets")
                    return generateFallbackEmbedding(text)
                }

                // Try to load the smallest model first (AVG_WORD)
                val modelToLoad = if (availableModels.contains(EmbedderManager.Model.AVG_WORD)) {
                    EmbedderManager.Model.AVG_WORD
                } else {
                    availableModels.first()
                }

                try {
                    embedderManager.load(modelToLoad, useGpu = false) // Start with CPU
                    Timber.d("Successfully loaded embedder: $modelToLoad")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load embedder model")
                    return generateFallbackEmbedding(text)
                }
            }

            // Generate embedding
            embedderManager.embed(text)

        } catch (e: Exception) {
            Timber.e(e, "Embedding generation failed, using fallback")
            generateFallbackEmbedding(text)
        }
    }


    /*──────────────────────────────────────────────────────────────────────────*/
    /* ── Progress & feedback helpers ── */

    private fun updateUserProgress(subject: Subject, difficulty: Difficulty, isCorrect: Boolean) {
        _quizState.update { state ->
            val currentAcc = state.userProgress[subject] ?: 0f
            val count = state.questionsGenerated.coerceAtLeast(1)
            val newAcc = if (isCorrect) (currentAcc * (count - 1) + 1f) / count else (currentAcc * (count - 1)) / count
            state.copy(userProgress = state.userProgress + (subject to newAcc))
        }
    }

    private suspend fun generatePersonalizedFeedback(
        question: Question,
        userAnswer: String,
        isCorrect: Boolean,
    ): String = try {
        val prompt = if (isCorrect) {
            """
                The user correctly answered: \"${question.questionText}\"
                Correct answer: ${question.correctAnswer}

                Provide encouraging feedback and add an interesting fact about ${question.conceptsCovered.joinToString()} (max 50 words).
            """.trimIndent()
        } else {
            """
                Question: \"${question.questionText}\"
                User answered: $userAnswer
                Correct answer: ${question.correctAnswer}

                Provide constructive feedback (max 75 words) explaining why their answer was incorrect and why the correct answer is right. Reference the concepts: ${question.conceptsCovered.joinToString()}.
            """.trimIndent()
        }

        val generationConfig = GemmaEngine.GenerationConfig(maxNewTokens = 100, temperature = 0.7f)
        gemmaEngine.generateText(prompt, generationConfig).toList().joinToString("")
    } catch (e: Exception) {
        if (isCorrect) "Great job! You got it right." else "Not quite. The correct answer is ${question.correctAnswer}. ${question.explanation}"
    }


    // Fallback embedding generation (simple hash-based approach)
    private fun generateFallbackEmbedding(text: String): FloatArray {
        val dimension = 512
        val embedding = FloatArray(dimension)

        // Deterministic seed
        val random = java.util.Random(text.hashCode().toLong())

        // Fill with pseudo-random values
        for (i in embedding.indices) {
            embedding[i] = random.nextGaussian().toFloat()
        }

        // L2-normalise
        var norm = 0f
        for (v in embedding) norm += v * v
        if (norm > 0f) {
            val scale = 1f / sqrt(norm)
            for (i in embedding.indices) embedding[i] *= scale
        }
        return embedding
    }


    // Safe similarity computation
    private fun computeSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.isEmpty() || embedding2.isEmpty()) return 0f
        if (embedding1.size != embedding2.size) {
            Timber.w("Embedding dimension mismatch: ${embedding1.size} vs ${embedding2.size}")
            return 0f
        }

        var dotProduct = 0f
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
        }

        // Clamp to valid cosine similarity range
        return dotProduct.coerceIn(-1f, 1f)
    }

}