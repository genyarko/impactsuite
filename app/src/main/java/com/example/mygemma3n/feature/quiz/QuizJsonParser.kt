package com.example.mygemma3n.feature.quiz

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles JSON parsing and sanitization for quiz questions from AI responses.
 * Provides robust parsing that can handle malformed JSON from AI models.
 */
@Singleton
class QuizJsonParser @Inject constructor(
    private val gson: Gson
) {
    
    // Create a lenient Gson instance for parsing potentially malformed JSON
    private val lenientGson = gson.newBuilder()
        .setLenient()
        .create()
    
    /**
     * Parse OpenAI quiz response into Question objects
     */
    fun parseOpenAIQuizResponse(response: String): List<Question> {
        return try {
            if (response.isBlank()) {
                Timber.w("Empty response received from OpenAI")
                return emptyList()
            }
            
            Timber.d("Raw OpenAI response length: ${response.length}")
            val jsonString = extractJsonFromResponse(response)
            
            if (jsonString.isBlank()) {
                Timber.w("No JSON content extracted from response")
                Timber.w("Original response preview: ${response.take(300)}...")
                return emptyList()
            }
            
            Timber.d("Extracted JSON for parsing: ${jsonString.take(200)}...")
            
            // Try to parse as array first (preferred for multiple questions)
            val questions = parseAsJsonArray(jsonString) ?: parseAsJsonObject(jsonString)
            
            if (questions == null || questions.isEmpty()) {
                Timber.w("No questions parsed from OpenAI response")
                Timber.w("Extracted JSON: ${jsonString.take(500)}...")
                Timber.w("Original response preview: ${response.take(300)}...")
                return emptyList()
            }
            
            // Convert parsed data to Question objects
            val validQuestions = questions.mapNotNull { questionData ->
                parseOpenAIQuestionData(questionData)
            }
            
            Timber.d("Successfully parsed ${validQuestions.size} questions from OpenAI")
            validQuestions
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse OpenAI quiz response")
            Timber.e("Response preview: ${response.take(500)}...")
            emptyList()
        }
    }
    
    /**
     * Parse and validate a single question from JSON response
     */
    fun parseAndValidateQuestion(
        response: String, 
        expectedType: QuestionType, 
        expectedDifficulty: Difficulty
    ): Question? {
        return try {
            val cleanedJson = sanitizeJson(response)
            
            @Suppress("UNCHECKED_CAST")
            val questionData = lenientGson.fromJson(cleanedJson, Map::class.java) as Map<String, Any>
            
            val questionText = questionData["question"]?.toString()?.trim()
                ?: throw Exception("Missing question field")
            
            val correctAnswer = questionData["correctAnswer"]?.toString()?.trim()
                ?: throw Exception("Missing correctAnswer field")
            
            if (correctAnswer.isBlank()) {
                Timber.w("Empty correct answer detected for question: ${questionText.take(50)}...")
                throw Exception("Empty correct answer")
            }
            
            val explanation = questionData["explanation"]?.toString()?.trim() 
                ?: "No explanation provided"
            
            // Validate question type
            val typeStr = questionData["type"]?.toString() ?: expectedType.name
            val questionType = try {
                QuestionType.valueOf(typeStr)
            } catch (e: Exception) {
                Timber.w("Invalid question type: $typeStr, using expected: $expectedType")
                expectedType
            }
            
            // Handle options for multiple choice and true/false questions
            val options = when (questionType) {
                QuestionType.MULTIPLE_CHOICE -> {
                    @Suppress("UNCHECKED_CAST")
                    val rawOptions = questionData["options"] as? List<String>
                    rawOptions?.take(4) ?: listOf("A", "B", "C", "D")
                }
                QuestionType.TRUE_FALSE -> {
                    listOf("True", "False")
                }
                else -> emptyList()
            }
            
            // Validate the question has required content
            if (questionText.length < 10) {
                throw Exception("Question too short: $questionText")
            }
            
            Question(
                questionText = questionText,
                questionType = questionType,
                options = options,
                correctAnswer = correctAnswer,
                explanation = explanation,
                difficulty = expectedDifficulty
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse and validate question from response: ${response.take(200)}...")
            null
        }
    }
    
    /**
     * Sanitize JSON response to fix common issues
     */
    private fun sanitizeJson(raw: String): String {
        if (raw.isBlank()) return "{}"
        
        // Remove markdown code fences and common prefixes
        var cleaned = raw
            .replace("```json", "")
            .replace("```", "")
            .replace("**JSON:**", "")
            .replace("Here's the question:", "")
            .replace("Here is the question:", "")
            .replace("JSON:", "")
            .trim()
        
        // Find JSON boundaries
        val startBrace = cleaned.indexOf('{')
        val endBrace = cleaned.lastIndexOf('}')
        
        if (startBrace != -1 && endBrace != -1 && endBrace > startBrace) {
            cleaned = cleaned.substring(startBrace, endBrace + 1)
        }
        
        // Apply additional cleaning
        return sanitizeJsonSimple(cleaned)
    }
    
    /**
     * Simple JSON sanitization to fix common formatting issues
     */
    private fun sanitizeJsonSimple(raw: String): String {
        return raw
            // Remove trailing commas
            .replace(Regex(",\\s*}"), "}")
            .replace(Regex(",\\s*]"), "]")
            // Fix common quote issues
            .replace("\"\"", "\"")
            // Ensure proper spacing
            .replace(Regex(":\\s*([^\"\\[\\{])"), ": \"$1")
            .replace(Regex("([^\"\\]\\}])\\s*,"), "$1\",")
            // Fix missing quotes around values
            .replace(Regex(":\\s*([A-Za-z][A-Za-z0-9_\\s]*)\\s*[,}]")) { match ->
                val value = match.groupValues[1].trim()
                if (value !in listOf("true", "false", "null")) {
                    ": \"$value\"${match.value.last()}"
                } else {
                    match.value
                }
            }
            .trim()
    }
    
    /**
     * Extract JSON content from AI response
     */
    private fun extractJsonFromResponse(response: String): String {
        if (response.isBlank()) {
            Timber.w("Cannot extract JSON from blank response")
            return ""
        }
        
        val cleaned = cleanResponseString(response)
        
        if (cleaned.isBlank()) {
            Timber.w("Cleaned response is blank")
            return ""
        }
        
        // FIRST: Check for OpenAI-style wrapper object with "questions" key
        val objectStart = cleaned.indexOf('{')
        val objectEnd = cleaned.lastIndexOf('}')
        
        if (objectStart != -1 && objectEnd != -1 && objectEnd > objectStart) {
            val potentialObject = cleaned.substring(objectStart, objectEnd + 1)
            
            // Check if this object contains a "questions" key
            if (potentialObject.contains("\"questions\"")) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val parsedObject = lenientGson.fromJson(potentialObject, Map::class.java) as? Map<String, Any>
                    val questionsArray = parsedObject?.get("questions") as? List<*>
                    
                    if (questionsArray != null) {
                        // Extract the questions array and return it as JSON
                        val arrayJson = lenientGson.toJson(questionsArray)
                        Timber.d("Extracted questions array from wrapper object: ${arrayJson.take(100)}...")
                        return arrayJson
                    }
                } catch (e: Exception) {
                    Timber.d("Failed to parse wrapper object, continuing with normal extraction")
                }
            }
        }
        
        // SECOND: PRIORITIZE JSON array detection (for direct arrays)
        val arrayStart = cleaned.indexOf('[')
        val arrayEnd = cleaned.lastIndexOf(']')
        
        if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
            val jsonString = cleaned.substring(arrayStart, arrayEnd + 1)
            val finalJson = repairAndCleanJson(jsonString)
            Timber.d("Extracted JSON array: ${finalJson.take(100)}...")
            return finalJson
        }
        
        // THIRD: Fallback to single JSON object if no array is found
        val objectJson = extractSingleObjectJson(cleaned)
        if (objectJson.isNotBlank()) {
            Timber.d("Extracted JSON object: ${objectJson.take(100)}...")
        } else {
            Timber.w("No valid JSON found in response")
        }
        return objectJson
    }
    
    /**
     * Clean response string by removing common AI response prefixes
     */
    private fun cleanResponseString(response: String): String {
        return response
            .replace("```json", "")
            .replace("```", "")
            .replace("**JSON:**", "")
            .replace("Here's the question:", "")
            .replace("Here is the question:", "")
            .replace("Here are the questions:", "")
            .replace("Here's the quiz:", "")
            .replace("JSON:", "")
            .replace("Questions:", "")
            .replace("Quiz:", "")
            // Handle common OpenAI response prefixes
            .replace("I'll create", "")
            .replace("I'll generate", "")
            .replace("Here are", "")
            .replace("Based on", "")
            .trim()
    }
    
    /**
     * Extract single JSON object from cleaned response
     */
    private fun extractSingleObjectJson(cleaned: String): String {
        val startIndex = cleaned.indexOf('{')
        val endIndex = cleaned.lastIndexOf('}')
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            val jsonString = cleaned.substring(startIndex, endIndex + 1)
            val finalJson = cleanJsonString(jsonString)
            
            // Validate that we have meaningful content
            if (finalJson.length > 10 && finalJson.contains("question")) {
                return finalJson
            } else {
                Timber.w("Extracted JSON object seems invalid: ${finalJson.take(50)}...")
                return ""
            }
        }
        
        Timber.w("No valid JSON object boundaries found in: ${cleaned.take(100)}...")
        return ""
    }
    
    /**
     * Clean JSON string to fix common formatting issues
     */
    private fun cleanJsonString(jsonString: String): String {
        return jsonString
            .replace(",\\n]", "\\n]")    // Remove trailing commas before array end
            .replace(", ]", " ]")      // Remove trailing commas before array end
            .replace(",]", "]")        // Remove trailing commas before array end
            .replace(",\\n}", "\\n}")    // Remove trailing commas in objects
            .replace(", }", " }")      // Remove trailing commas in objects
            .replace(",}", "}")        // Remove trailing commas in objects
            .replace("\\\\n", "")        // Remove escaped newlines that might break parsing
            .let { sanitizeJsonQuotes(it) }  // Fix unescaped quotes
            .trim()
    }
    
    /**
     * Fix unescaped quotes in JSON strings
     */
    private fun sanitizeJsonQuotes(jsonString: String): String {
        return jsonString
            // Fix common contractions that break JSON
            .replace(" you\"re ", " you're ")
            .replace(" don\"t ", " don't ")
            .replace(" can\"t ", " can't ")
            .replace(" won\"t ", " won't ")
            .replace(" it\"s ", " it's ")
            .replace(" that\"s ", " that's ")
            .replace(" we\"ll ", " we'll ")
            .replace(" you\"ll ", " you'll ")
            .replace(" I\"m ", " I'm ")
            .replace(" we\"re ", " we're ")
            .replace(" they\"re ", " they're ")
            .replace(" I\"ve ", " I've ")
            .replace(" we\"ve ", " we've ")
            .replace(" I\"d ", " I'd ")
            .replace(" you\"d ", " you'd ")
            // Handle quotes at end of strings
            .replace("\"\"}", "\"}")
            .replace("\"\",", "\",")
    }
    
    /**
     * Repair and clean JSON with advanced fixes
     */
    private fun repairAndCleanJson(jsonString: String): String {
        // First apply basic cleaning
        val basicCleaned = cleanJsonString(jsonString)
        
        // Simple repairs without complex regex patterns
        return basicCleaned
            // Ensure proper closing brackets/braces
            .let { json ->
                // Count unclosed brackets and braces
                var braceCount = 0
                var bracketCount = 0
                var inString = false
                var escaped = false
                
                json.forEach { char ->
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' && !escaped -> inString = !inString
                        !inString -> when (char) {
                            '{' -> braceCount++
                            '}' -> braceCount--
                            '[' -> bracketCount++
                            ']' -> bracketCount--
                        }
                    }
                }
                
                val result = StringBuilder(json)
                // Add missing closing characters
                repeat(braceCount) { result.append('}') }
                repeat(bracketCount) { result.append(']') }
                result.toString()
            }
    }
    
    /**
     * Try to parse JSON as array of questions
     */
    private fun parseAsJsonArray(jsonString: String): List<Map<String, Any>>? {
        return try {
            if (jsonString.isBlank()) {
                Timber.w("Empty JSON string provided")
                return null
            }
            
            // Parse as List of Maps directly instead of Array
            @Suppress("UNCHECKED_CAST")
            val questionsList = lenientGson.fromJson(jsonString, List::class.java) as? List<*>
            
            if (questionsList == null) {
                Timber.w("Gson returned null for array parsing")
                return null
            }
            
            // Convert to List<Map<String, Any>> safely
            val mapList = questionsList.mapNotNull { item ->
                @Suppress("UNCHECKED_CAST")
                item as? Map<String, Any>
            }
            
            if (mapList.isEmpty()) {
                Timber.w("No valid map objects found in array")
                return null
            }
            
            mapList
        } catch (e: Exception) {
            Timber.d("Not a JSON array, trying as single object: ${e.message}")
            null
        }
    }
    
    /**
     * Try to parse JSON as single question object
     */
    private fun parseAsJsonObject(jsonString: String): List<Map<String, Any>>? {
        return try {
            if (jsonString.isBlank()) {
                Timber.w("Empty JSON string provided for object parsing")
                return null
            }
            
            @Suppress("UNCHECKED_CAST")
            val singleQuestion = lenientGson.fromJson(jsonString, Map::class.java) as? Map<String, Any>
            
            if (singleQuestion == null) {
                Timber.w("Gson returned null for object parsing")
                return null
            }
            
            listOf(singleQuestion)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse as both array and single object")
            null
        }
    }
    
    /**
     * Parse individual question data from OpenAI response
     */
    private fun parseOpenAIQuestionData(questionData: Map<String, Any>): Question? {
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
            
            // Try multiple field names for correct answer (OpenAI might use different names)
            val correctAnswer = (questionData["correctAnswer"]?.toString()?.trim()
                ?: questionData["correct_answer"]?.toString()?.trim()
                ?: questionData["answer"]?.toString()?.trim()).let { answer ->
                
                if (answer.isNullOrEmpty()) {
                    // For open-ended questions, provide a reasonable default
                    when (questionType) {
                        QuestionType.SHORT_ANSWER, QuestionType.FILL_IN_BLANK -> {
                            Timber.d("No correct answer provided for $questionType, using flexible default")
                            "Answers will vary"
                        }
                        QuestionType.TRUE_FALSE -> {
                            Timber.d("No correct answer for TRUE_FALSE, defaulting to True")
                            "True"
                        }
                        QuestionType.MULTIPLE_CHOICE -> {
                            // Try to get the first option as fallback
                            val options = questionData["options"] as? List<*>
                            val firstOption = options?.firstOrNull()?.toString()?.trim()
                            if (!firstOption.isNullOrEmpty()) {
                                Timber.d("No correct answer for MULTIPLE_CHOICE, using first option: $firstOption")
                                firstOption
                            } else {
                                Timber.w("Missing correct answer and no options available")
                                return null
                            }
                        }
                        else -> {
                            Timber.w("Missing correct answer for question type $questionType")
                            return null
                        }
                    }
                } else {
                    answer
                }
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
            
            Timber.d("Successfully parsed question: type=$questionType, hasAnswer=${correctAnswer.isNotEmpty()}")
            
            Question(
                questionText = questionText,
                questionType = questionType,
                options = options,
                correctAnswer = correctAnswer,
                explanation = explanation,
                difficulty = Difficulty.MEDIUM // Default difficulty
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse individual OpenAI question: $questionData")
            null
        }
    }
}