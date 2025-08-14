package com.example.mygemma3n.feature.quiz

import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.GeminiApiConfig
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class OnlineQuizGenerator @Inject constructor(
    private val geminiApiService: GeminiApiService,
    private val gson: Gson
) {
    
    // Create a lenient Gson instance for parsing potentially malformed JSON
    private val lenientGson = gson.newBuilder()
        .setLenient()
        .create()

    suspend fun generateQuestionsOnline(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        questionTypes: List<QuestionType>,
        count: Int,
        previousQuestions: List<String> = emptyList(),
        studentName: String? = null,
        gradeLevel: Int? = null,
        country: String? = null
    ): List<Question> = withContext(Dispatchers.IO) {
        
        require(geminiApiService.isInitialized()) { "GeminiApiService not initialized" }
        
        return@withContext coroutineScope {
            val questionJobs = questionTypes.take(count).mapIndexed { index, questionType ->
                async {
                    withTimeoutOrNull(45_000) { // 45 second timeout per question
                        generateSingleQuestionOnline(
                            subject = subject,
                            topic = topic,
                            difficulty = difficulty,
                            questionType = questionType,
                            previousQuestions = previousQuestions,
                            attemptNumber = index,
                            studentName = studentName,
                            gradeLevel = gradeLevel,
                            country = country
                        )
                    }
                }
            }
            
            val questions = questionJobs.awaitAll().filterNotNull()
            Timber.d("Online generation completed: ${questions.size}/$count questions generated")
            questions
        }
    }

    private suspend fun generateSingleQuestionOnline(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        questionType: QuestionType,
        previousQuestions: List<String>,
        attemptNumber: Int,
        studentName: String? = null,
        gradeLevel: Int? = null,
        country: String? = null,
        maxRetries: Int = 3
    ): Question? {
        var lastException: Exception? = null
        var delay = 200L // Start with 200ms delay

        repeat(maxRetries) { retryCount ->
            try {
                val prompt = createOnlinePrompt(
                    subject = subject,
                    topic = topic,
                    difficulty = difficulty,
                    questionType = questionType,
                    previousQuestions = previousQuestions,
                    attemptNumber = attemptNumber,
                    studentName = studentName,
                    gradeLevel = gradeLevel,
                    country = country
                )

                val response = geminiApiService.generateTextComplete(prompt, "quiz")
                return parseQuestionResponse(response, questionType, difficulty)

            } catch (e: Exception) {
                lastException = e
                Timber.w("Online question generation attempt ${retryCount + 1} failed: ${e.message}")

                if (retryCount < maxRetries - 1) {
                    delay(delay)
                    delay = (delay * 1.5).toLong() // Exponential backoff
                }
            }
        }

        Timber.e(lastException, "All online generation attempts failed")
        return null
    }

    private fun createOnlinePrompt(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        questionType: QuestionType,
        previousQuestions: List<String>,
        attemptNumber: Int,
        studentName: String? = null,
        gradeLevel: Int? = null,
        country: String? = null
    ): String {
        val difficultyContext = when (difficulty) {
            Difficulty.EASY -> "basic understanding, simple concepts"
            Difficulty.MEDIUM -> "intermediate application, moderate complexity"
            Difficulty.HARD -> "advanced analysis, complex problem-solving"
            Difficulty.ADAPTIVE -> "dynamic difficulty based on student performance"
        }

        val studentContext = buildString {
            if (studentName != null) append("Student: $studentName\n")
            if (gradeLevel != null) append("Grade Level: $gradeLevel\n")
            if (country != null) append("Educational Context: $country curriculum standards\n")
        }

        val focusAngles = listOf(
            "practical real-world application",
            "conceptual understanding",
            "problem-solving approach",
            "analytical thinking",
            "critical evaluation"
        )
        val focusAngle = focusAngles[attemptNumber % focusAngles.size]

        val avoidanceContext = if (previousQuestions.isNotEmpty()) {
            val lastFew = previousQuestions.takeLast(3).joinToString("; ") { it.take(50) + "..." }
            "Make this question distinctly different from recent questions: $lastFew"
        } else ""

        return """
        You are an expert educational content creator. Generate ONE high-quality ${questionType.name.lowercase().replace('_', ' ')} question.
        
        REQUIREMENTS:
        Subject: ${subject.name}
        Topic: $topic
        Difficulty: $difficulty ($difficultyContext)
        Focus: $focusAngle
        Question Type: ${questionType.name}
        
        $studentContext
        
        CONSTRAINTS:
        - Create original, engaging content appropriate for the difficulty level
        - $avoidanceContext
        - Return ONLY valid JSON in the exact format specified below
        - Ensure the question tests $focusAngle skills
        - Make explanations clear and educational
        
        OUTPUT FORMAT:
        ${getOnlineQuestionFormat(questionType)}
        
        Generate the question now:
        """.trimIndent()
    }

    private fun getOnlineQuestionFormat(questionType: QuestionType): String {
        return when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> """
                {
                  "question": "Your complete question here?",
                  "options": ["Option A", "Option B", "Option C", "Option D"],
                  "correctAnswer": "Option A",
                  "explanation": "Clear explanation of why this is correct and others are wrong"
                }
            """.trimIndent()

            QuestionType.TRUE_FALSE -> """
                {
                  "question": "Your true/false statement here.",
                  "options": ["True", "False"],
                  "correctAnswer": "True",
                  "explanation": "Explanation of why this statement is true or false"
                }
            """.trimIndent()

            QuestionType.FILL_IN_BLANK -> """
                {
                  "question": "Complete sentence with _____ for the blank.",
                  "correctAnswer": "word or phrase that fills the blank",
                  "explanation": "Why this answer is correct"
                }
            """.trimIndent()

            QuestionType.SHORT_ANSWER -> """
                {
                  "question": "Your open-ended question here?",
                  "correctAnswer": "Sample correct answer",
                  "explanation": "What makes a good answer to this question"
                }
            """.trimIndent()

            QuestionType.MATCHING -> """
                {
                  "question": "Match the following items.",
                  "options": ["Item 1", "Item 2", "Item 3", "Match A", "Match B", "Match C"],
                  "correctAnswer": "1-A, 2-B, 3-C",
                  "explanation": "Explanation of the correct matches"
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
            // This method is specifically for single question responses from online Gemini API
            val jsonString = extractSingleQuestionJson(response)
            @Suppress("UNCHECKED_CAST")
            val jsonData = lenientGson.fromJson(jsonString, Map::class.java) as Map<String, Any>

            val questionText = jsonData["question"]?.toString()
                ?: throw Exception("Missing question field")

            val correctAnswer = jsonData["correctAnswer"]?.toString()
                ?: throw Exception("Missing correctAnswer field")

            val explanation = jsonData["explanation"]?.toString()
                ?: "No explanation provided"

            val options = when (questionType) {
                QuestionType.MULTIPLE_CHOICE, QuestionType.TRUE_FALSE -> {
                    @Suppress("UNCHECKED_CAST")
                    (jsonData["options"] as? List<String>) ?: listOf()
                }
                else -> listOf()
            }

            Question(
                questionText = questionText,
                questionType = questionType,
                options = options,
                correctAnswer = correctAnswer,
                explanation = explanation,
                difficulty = difficulty
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to parse online question response: $response")
            null
        }
    }

    /**
     * Extract JSON for curriculum quiz responses - handles both arrays and single objects
     * Used for Gemini 2.5 Flash responses which may return arrays of multiple questions
     */
    private fun extractJsonFromResponse(response: String): String {
        val cleaned = cleanResponseString(response)

        // PRIORITIZE JSON array detection first (for multiple questions from Gemini)
        val arrayStart = cleaned.indexOf('[')
        val arrayEnd = cleaned.lastIndexOf(']')
        
        if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
            val jsonString = cleaned.substring(arrayStart, arrayEnd + 1)
            return cleanJsonString(jsonString)
        }

        // Fallback to single JSON object if no array is found
        return extractSingleObjectJson(cleaned)
    }

    /**
     * Extract JSON for single question responses 
     * Used for individual question generation from both Gemini and Gemma models
     */
    private fun extractSingleQuestionJson(response: String): String {
        val cleaned = cleanResponseString(response)
        return extractSingleObjectJson(cleaned)
    }

    private fun cleanResponseString(response: String): String {
        return response
            .replace("```json", "")
            .replace("```", "")
            .replace("**JSON:**", "")
            .replace("Here's the question:", "")
            .replace("Here is the question:", "")
            .replace("JSON:", "")
            .trim()
    }

    private fun extractSingleObjectJson(cleaned: String): String {
        val startIndex = cleaned.indexOf('{')
        val endIndex = cleaned.lastIndexOf('}')

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            val jsonString = cleaned.substring(startIndex, endIndex + 1)
            return cleanJsonString(jsonString)
        }

        return cleaned
    }

    private fun cleanJsonString(jsonString: String): String {
        return jsonString
            .replace(",\n]", "\n]")    // Remove trailing commas before array end
            .replace(", ]", " ]")      // Remove trailing commas before array end
            .replace(",]", "]")        // Remove trailing commas before array end
            .replace(",\n}", "\n}")    // Remove trailing commas in objects
            .replace(", }", " }")      // Remove trailing commas in objects
            .replace(",}", "}")        // Remove trailing commas in objects
            .replace("\\n", "")        // Remove escaped newlines that might break parsing
            .trim()
    }

    suspend fun generateCurriculumAwareOnlineQuiz(
        subject: Subject,
        gradeLevel: Int,
        topic: String,
        count: Int,
        country: String? = null,
        studentName: String? = null,
        previousQuestions: List<String> = emptyList()
    ): List<Question> = withContext(Dispatchers.IO) {
        
        require(geminiApiService.isInitialized()) { "GeminiApiService not initialized" }
        
        // Create avoidance context for duplicate prevention
        val avoidanceContext = if (previousQuestions.isNotEmpty()) {
            val lastFew = previousQuestions.takeLast(5).joinToString("; ") { 
                it.take(50) + "..." 
            }
            "- IMPORTANT: Make ALL questions distinctly different from these recent questions: $lastFew"
        } else ""
        
        val curriculumPrompt = """
        Generate $count educational quiz questions for a Grade $gradeLevel student.
        
        Subject: ${subject.name}
        Topic: $topic
        ${if (country != null) "Curriculum Context: $country educational standards" else ""}
        ${if (studentName != null) "Student: $studentName" else ""}
        
        Requirements:
        - Questions should be appropriate for Grade $gradeLevel level
        - Mix different question types (multiple choice, true/false)
        - Ensure educational value and curriculum alignment
        - Include clear explanations for learning
        $avoidanceContext
        
        Return as a JSON array of question objects in this format:
        [
          {
            "question": "Question text here?",
            "type": "MULTIPLE_CHOICE",
            "options": ["A", "B", "C", "D"],
            "correctAnswer": "A",
            "explanation": "Why this is correct"
          }
        ]

        
        Generate the questions:
        """.trimIndent()

        try {
            val response = geminiApiService.generateTextComplete(curriculumPrompt, "quiz")
            return@withContext parseCurriculumQuestionsResponse(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate curriculum-aware online quiz")
            // Return a basic fallback question to avoid empty list
            listOf(createFallbackQuestion(subject, topic, gradeLevel))
        }
    }

    private fun parseCurriculumQuestionsResponse(response: String): List<Question> {
        Timber.d("Parsing Gemini 2.5 Flash curriculum response, length: ${response.length}")

        return try {
            val jsonString = extractJsonFromResponse(response)
            Timber.d("Extracted JSON for parsing: ${jsonString.take(200)}...")

            // Try to parse as array first (preferred for multiple questions from Gemini)
            val questions = parseAsJsonArray(jsonString) ?: parseAsJsonObject(jsonString)

            if (questions == null || questions.isEmpty()) {
                Timber.w("No questions parsed from response, response preview: ${response.take(300)}...")
                return emptyList()
            }

            val validQuestions = questions.mapNotNull { questionData ->
                parseGeminiQuestionData(questionData)
            }

            Timber.d("Successfully parsed ${validQuestions.size}/${questions.size} questions from Gemini")
            validQuestions

        } catch (e: Exception) {
            Timber.e(e, "Failed to parse Gemini curriculum questions response")
            Timber.e("Response preview: ${response.take(500)}...")
            emptyList()
        }
    }

    private fun parseAsJsonArray(jsonString: String): List<Map<String, Any>>? {
        return try {
            // Parse as List of Maps directly instead of Array
            @Suppress("UNCHECKED_CAST")
            val questionsList = lenientGson.fromJson(jsonString, List::class.java) as List<Map<String, Any>>
            questionsList
        } catch (e: Exception) {
            Timber.d("Not a JSON array, trying as single object: ${e.message}")
            null
        }
    }

    private fun parseAsJsonObject(jsonString: String): List<Map<String, Any>>? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val singleQuestion = lenientGson.fromJson(jsonString, Map::class.java) as Map<String, Any>
            listOf(singleQuestion)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse as both array and single object")
            null
        }
    }

    private fun parseGeminiQuestionData(questionData: Map<String, Any>): Question? {
        return try {
            val questionText = questionData["question"]?.toString()?.trim()
            if (questionText.isNullOrEmpty()) {
                Timber.w("Missing or empty question text")
                return null
            }

            val typeStr = questionData["type"]?.toString() ?: "MULTIPLE_CHOICE"
            val questionType = try {
                QuestionType.valueOf(typeStr)
            } catch (e: Exception) {
                Timber.w("Unknown question type: $typeStr, defaulting to MULTIPLE_CHOICE")
                QuestionType.MULTIPLE_CHOICE
            }

            val correctAnswer = questionData["correctAnswer"]?.toString()?.trim()
            if (correctAnswer.isNullOrEmpty()) {
                Timber.w("Missing or empty correct answer")
                return null
            }

            val explanation = questionData["explanation"]?.toString()?.trim() ?: ""

            // Handle options for multiple choice and true/false questions
            val options = when (questionType) {
                QuestionType.MULTIPLE_CHOICE, QuestionType.TRUE_FALSE -> {
                    @Suppress("UNCHECKED_CAST")
                    val rawOptions = questionData["options"] as? List<Any>
                    rawOptions?.map { it.toString() } ?: run {
                        // For TRUE_FALSE, provide default options if missing
                        if (questionType == QuestionType.TRUE_FALSE) {
                            listOf("True", "False")
                        } else {
                            Timber.w("Missing options for $questionType question")
                            emptyList()
                        }
                    }
                }
                else -> emptyList()
            }

            Question(
                questionText = questionText,
                questionType = questionType,
                options = options,
                correctAnswer = correctAnswer,
                explanation = explanation,
                difficulty = Difficulty.MEDIUM
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse individual Gemini question: $questionData")
            null
        }
    }

    private fun createFallbackQuestion(subject: Subject, topic: String, gradeLevel: Int?): Question {
        val gradeLevelText = gradeLevel?.let { "Grade $it" } ?: "student"
        
        return when (subject) {
            Subject.MATHEMATICS -> Question(
                questionText = "What is the result of 2 + 2?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("3", "4", "5", "6"),
                correctAnswer = "4",
                explanation = "2 + 2 equals 4, which is a basic addition fact.",
                difficulty = Difficulty.EASY
            )
            Subject.SCIENCE -> Question(
                questionText = "What do plants need to grow?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Only water", "Only sunlight", "Water, sunlight, and nutrients", "Only air"),
                correctAnswer = "Water, sunlight, and nutrients",
                explanation = "Plants need water, sunlight, and nutrients from soil to grow properly.",
                difficulty = Difficulty.EASY
            )
            Subject.HISTORY -> Question(
                questionText = "What is a primary source in history?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("A textbook", "A firsthand account", "A movie", "A summary"),
                correctAnswer = "A firsthand account",
                explanation = "A primary source is a firsthand account or original document from the time period being studied.",
                difficulty = Difficulty.MEDIUM
            )
            Subject.LANGUAGE_ARTS -> Question(
                questionText = "What is a noun?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("An action word", "A describing word", "A person, place, or thing", "A connecting word"),
                correctAnswer = "A person, place, or thing",
                explanation = "A noun is a word that names a person, place, or thing.",
                difficulty = Difficulty.EASY
            )
            else -> Question(
                questionText = "What is an important skill for a $gradeLevelText to develop in $topic?",
                questionType = QuestionType.SHORT_ANSWER,
                options = emptyList(),
                correctAnswer = "Critical thinking and problem-solving",
                explanation = "Critical thinking and problem-solving are essential skills for learning in any subject.",
                difficulty = Difficulty.MEDIUM
            )
        }
    }
}