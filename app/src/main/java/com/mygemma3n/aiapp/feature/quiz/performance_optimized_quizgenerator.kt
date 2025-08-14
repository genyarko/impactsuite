package com.mygemma3n.aiapp.feature.quiz

import com.mygemma3n.aiapp.data.UnifiedGemmaService
import com.mygemma3n.aiapp.shared_utilities.PerformanceMonitor
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import kotlin.system.measureTimeMillis

/**
 * Performance-optimized wrapper for quiz generation
 * Add this to your QuizGeneratorViewModel
 */
class PerformanceOptimizedQuizGenerator @Inject constructor(
    private val gemmaService: UnifiedGemmaService,
    private val performanceMonitor: PerformanceMonitor,
    private val promptManager: EnhancedPromptManager
) {

    /**
     * Generate multiple questions in parallel with timeout protection
     */
    suspend fun generateQuestionsParallel(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        questionTypes: List<QuestionType>,
        count: Int,
        previousQuestions: List<String> = emptyList()
    ): List<Question> = withContext(Dispatchers.Default) {

        lateinit var questions: List<Question>
        val generationTime = measureTimeMillis {
            questions = coroutineScope {
                questionTypes.take(count).mapIndexed { index, questionType ->
                    async {
                        // Add timeout to prevent hanging
                        withTimeoutOrNull(60_000) { // 30 second timeout per question
                            generateSingleQuestion(
                                subject = subject,
                                topic = topic,
                                difficulty = difficulty,
                                questionType = questionType,
                                previousQuestions = previousQuestions,
                                attemptNumber = index
                            )
                        }
                    }
                }.awaitAll().filterNotNull() // Remove any that timed out
            }
        }

        // Log performance metrics after generationTime is known
        performanceMonitor.trackEvent("quiz_generation_parallel", mapOf(
            "subject" to subject.name,
            "count" to count.toString(),
            "success_count" to questions.size.toString(),
            "duration_ms" to generationTime.toString()
        ))

        Timber.d("Generated $count questions in ${generationTime}ms")
        return@withContext questions
    }



    /**
     * Pre-warm the model before heavy usage
     */
    suspend fun prewarmModel() = withContext(Dispatchers.IO) {
        try {
            // Generate a simple prompt to warm up the model
            gemmaService.generateTextAsync(
                "Hello",
                UnifiedGemmaService.GenerationConfig(maxTokens = 1, temperature = 0.1f)
            )
            Timber.d("Model pre-warmed successfully")
        } catch (e: kotlinx.coroutines.CancellationException) {
            Timber.d("Model pre-warming cancelled")
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to pre-warm model")
        }
    }

    /**
     * Generate with retry and exponential backoff
     */
    private suspend fun generateSingleQuestion(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        questionType: QuestionType,
        previousQuestions: List<String>,
        attemptNumber: Int,
        maxRetries: Int = 3
    ): Question? {
        var lastException: Exception? = null
        var delay = 100L

        repeat(maxRetries) { retryCount ->
            try {
                val prompt = createOptimizedPrompt(
                    subject, topic, difficulty, questionType,
                    previousQuestions, attemptNumber
                )

                val response = gemmaService.generateTextAsync(
                    prompt,
                    UnifiedGemmaService.GenerationConfig(
                        maxTokens = 300,
                        temperature = 0.7f + (retryCount * 0.1f),
                        topK = 40,
                        randomSeed = (System.currentTimeMillis() + attemptNumber).toInt() % 1000
                    )
                )

                return parseQuestionResponse(response, questionType, difficulty)

            } catch (e: Exception) {
                lastException = e
                Timber.w("Question generation attempt ${retryCount + 1} failed: ${e.message}")

                if (retryCount < maxRetries - 1) {
                    delay(delay)
                    delay *= 2 // Exponential backoff
                }
            }
        }

        Timber.e(lastException, "All generation attempts failed")
        return null
    }

    private fun createOptimizedPrompt(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        questionType: QuestionType,
        previousQuestions: List<String>,
        attemptNumber: Int
    ): String {
        // Add variety elements
        val angles = listOf(
            "practical application",
            "theoretical understanding",
            "problem-solving",
            "real-world scenario",
            "comparative analysis"
        )

        val focusAngle = angles[attemptNumber % angles.size]

        return """
        Create ONE unique ${questionType.name} question.
        Subject: $subject
        Topic: $topic
        Difficulty: $difficulty
        Focus: $focusAngle
        
        Requirements:
        - Be creative and original (variation #${attemptNumber + 1})
        - Approach from a ${focusAngle} perspective
        - Return ONLY valid JSON
        - Format: ${getQuestionFormat(questionType)}
        
        Make it different from: "${previousQuestions.lastOrNull()?.take(40) ?: "N/A"}..."
        
        Generate now:
    """.trimIndent()
    }

    private fun getQuestionFormat(questionType: QuestionType): String {
        return when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> """
                {
                  "question": "Your question here?",
                  "options": ["A", "B", "C", "D"],
                  "correctAnswer": "A",
                  "explanation": "Brief explanation"
                }
            """.trimIndent()

            QuestionType.TRUE_FALSE -> """
                {
                  "question": "Statement here.",
                  "options": ["True", "False"],
                  "correctAnswer": "True",
                  "explanation": "Brief explanation"
                }
            """.trimIndent()

            else -> """
                {
                  "question": "Your question here?",
                  "correctAnswer": "Answer",
                  "explanation": "Brief explanation"
                }
            """.trimIndent()
        }
    }

    private fun parseQuestionResponse(
        response: String,
        questionType: QuestionType,
        difficulty: Difficulty
    ): Question? {
        return try {
            // Your existing parsing logic here
            // This is just a placeholder
            Question(
                questionText = "Parsed question",
                questionType = questionType,
                options = listOf(),
                correctAnswer = "Answer",
                explanation = "Explanation",
                difficulty = difficulty
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse question response")
            null
        }
    }
}