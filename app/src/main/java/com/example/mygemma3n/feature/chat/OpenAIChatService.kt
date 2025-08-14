package com.example.mygemma3n.feature.chat

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.mygemma3n.OPENAI_API_KEY
import com.example.mygemma3n.config.ApiConfiguration
import com.example.mygemma3n.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.JsonSyntaxException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import com.example.mygemma3n.feature.chat.ChatMessage

data class OpenAIMessage(
    val role: String,
    val content: Any // Can be String or List<ContentPart> for vision
)

data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String,
    val detail: String = "auto" // "low", "high", or "auto"
)

data class DALLEResponse(
    val created: Long,
    val data: List<DALLEImageData>
)

data class DALLEImageData(
    val b64_json: String? = null,
    val url: String? = null,
    val revised_prompt: String? = null
)

data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Float? = null,  // GPT-5-mini only supports default temperature (1.0)
    @SerializedName("max_completion_tokens") val maxCompletionTokens: Int = 1024,
    @SerializedName("top_p") val topP: Float? = null,  // Use defaults for GPT-5-mini
    @SerializedName("frequency_penalty") val frequencyPenalty: Float? = null,
    @SerializedName("presence_penalty") val presencePenalty: Float? = null,
    @SerializedName("response_format") val responseFormat: Map<String, String>? = null,
    val stream: Boolean = false
)

data class OpenAIChoice(
    val message: OpenAIMessage? = null,
    val delta: OpenAIMessage? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class OpenAIUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0
)

data class OpenAIResponse(
    val id: String? = null,
    val choices: List<OpenAIChoice> = emptyList(),
    val usage: OpenAIUsage? = null,
    val error: OpenAIError? = null
)

data class OpenAIError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

@Singleton
class OpenAIChatService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenUsageRepository: com.example.mygemma3n.data.repository.TokenUsageRepository,
    private val costPredictionUtils: com.example.mygemma3n.util.CostPredictionUtils,
    private val spendingLimitService: com.example.mygemma3n.service.SpendingLimitService
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Longer timeout client for story generation
    private val storyHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)  // 3 minutes for story generation
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun isInitialized(): Boolean {
        return getApiKey().isNotBlank()
    }

    private suspend fun getApiKey(): String {
        return context.dataStore.data
            .map { it[OPENAI_API_KEY] ?: "" }
            .first()
    }

    suspend fun generateChatResponseOnline(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        maxTokens: Int = 3000,  // Increased from 2048 to prevent truncation
        temperature: Float = 0.7f
    ): String = withContext(Dispatchers.IO) {
        
        val apiKey = getApiKey()
        require(apiKey.isNotBlank()) { "OpenAI API key not configured" }
        
        // Predict cost before making the request
        val historyText = conversationHistory.map { it.content }.take(10) // Last 10 messages for context
        val costPrediction = costPredictionUtils.predictRequestCost(
            modelName = ApiConfiguration.OpenAI.GPT_5_MINI,
            inputText = userMessage,
            expectedOutputTokens = maxTokens,
            conversationHistory = historyText
        )
        
        if (costPrediction.isSuccess) {
            Timber.i("Chat request cost prediction: ~${costPrediction.estimatedTotalTokens} tokens ≈ $${String.format("%.4f", costPrediction.estimatedCost)}")
            
            // Check spending limits before proceeding
            val spendingViolation = spendingLimitService.wouldExceedSpendingLimits(costPrediction.estimatedCost)
            if (spendingViolation != null) {
                Timber.w("Chat request blocked due to spending limit: ${spendingViolation.warningMessage}")
                throw IllegalStateException("Request blocked: ${spendingViolation.warningMessage}")
            }
            
            // Warn if cost seems high (>$0.01 per request)
            if (costPrediction.estimatedCost > 0.01) {
                Timber.w("High cost chat request: $${String.format("%.4f", costPrediction.estimatedCost)} for ${costPrediction.estimatedTotalTokens} tokens")
            }
        }
        
        val messages = buildMessageHistory(userMessage, conversationHistory)
        
        val request = OpenAIRequest(
            model = ApiConfiguration.OpenAI.GPT_5_MINI,
            messages = messages,
            maxCompletionTokens = maxTokens,
            stream = false
        )
        
        try {
            val response = makeApiCall(apiKey, request)
            val content = extractMessageContent(response)
            
            // Record token usage asynchronously - don't let it block the response
            launch {
                try {
                    recordTokenUsage(response, "chat", request.model)
                } catch (e: Exception) {
                    Timber.w(e, "Token usage recording failed (non-blocking)")
                }
            }
            
            return@withContext content
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate OpenAI chat response")
            throw e
        }
    }

    suspend fun generateTextResponseOnline(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        maxTokens: Int = 2048,
        temperature: Float = 0.7f,
        serviceType: String = "text"
    ): String = withContext(Dispatchers.IO) {
        
        val apiKey = getApiKey()
        require(apiKey.isNotBlank()) { "OpenAI API key not configured" }
        
        // Predict cost before making the request
        val historyText = conversationHistory.map { it.content }.take(10)
        val costPrediction = costPredictionUtils.predictRequestCost(
            modelName = ApiConfiguration.OpenAI.GPT_5_MINI,
            inputText = userMessage,
            expectedOutputTokens = maxTokens,
            conversationHistory = historyText
        )
        
        if (costPrediction.isSuccess) {
            Timber.i("$serviceType request cost prediction: ~${costPrediction.estimatedTotalTokens} tokens ≈ $${String.format("%.4f", costPrediction.estimatedCost)}")
            
            // Check spending limits before proceeding
            val spendingViolation = spendingLimitService.wouldExceedSpendingLimits(costPrediction.estimatedCost)
            if (spendingViolation != null) {
                Timber.w("$serviceType request blocked due to spending limit: ${spendingViolation.warningMessage}")
                throw IllegalStateException("Request blocked: ${spendingViolation.warningMessage}")
            }
            
            if (costPrediction.estimatedCost > 0.02) { // Higher threshold for non-chat requests
                Timber.w("High cost $serviceType request: $${String.format("%.4f", costPrediction.estimatedCost)}")
            }
        }
        
        val messages = buildMessageHistory(userMessage, conversationHistory)
        
        val request = OpenAIRequest(
            model = ApiConfiguration.OpenAI.GPT_5_MINI,
            messages = messages,
            maxCompletionTokens = maxTokens,
            responseFormat = null, // No JSON formatting for plain text responses
            stream = false
        )
        
        try {
            val response = makeApiCall(apiKey, request)
            val content = extractMessageContent(response)
            
            // Record token usage asynchronously - don't let it block the response
            launch {
                try {
                    recordTokenUsage(response, serviceType, request.model)
                } catch (e: Exception) {
                    Timber.w(e, "Token usage recording failed (non-blocking)")
                }
            }
            
            return@withContext content
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate OpenAI text response")
            throw e
        }
    }

    fun generateChatResponseStreamOnline(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        temperature: Float = 0.7f
    ): Flow<String> = flow {
        
        val apiKey = getApiKey()
        require(apiKey.isNotBlank()) { "OpenAI API key not configured" }
        
        val messages = buildMessageHistory(userMessage, conversationHistory)
        
        val request = OpenAIRequest(
            model = ApiConfiguration.OpenAI.GPT_5_MINI,
            messages = messages,
            maxCompletionTokens = ApiConfiguration.OpenAI.Chat.MAX_TOKENS,
            stream = true
        )
        
        try {
            val httpRequest = Request.Builder()
                .url(ApiConfiguration.buildOpenAIUrl(ApiConfiguration.OpenAI.CHAT_COMPLETION_ENDPOINT))
                .post(gson.toJson(request).toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            
            response.body?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data == "[DONE]") break
                        
                        try {
                            val streamResponse = gson.fromJson(data, OpenAIResponse::class.java)
                            val content = streamResponse.choices.firstOrNull()?.delta?.content as? String
                            if (!content.isNullOrBlank()) {
                                emit(content)
                            }
                        } catch (e: Exception) {
                            // Skip malformed chunks
                            continue
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate streaming OpenAI chat response")
            throw e
        }
    }.flowOn(Dispatchers.IO)

    private fun buildMessageHistory(
        userMessage: String,
        conversationHistory: List<ChatMessage>
    ): List<OpenAIMessage> {
        val messages = mutableListOf<OpenAIMessage>()
        
        // Add system message
        messages.add(
            OpenAIMessage(
                role = "system",
                content = """
                    You are a helpful, knowledgeable, and friendly AI assistant. You provide accurate, informative responses while being conversational and engaging.
                    
                    Guidelines:
                    - Be helpful and accurate
                    - Keep responses concise but informative
                    - Maintain a friendly, conversational tone
                    - If you don't know something, say so honestly
                    - Provide practical examples when helpful
                """.trimIndent() as Any
            )
        )
        
        // Add conversation history (last 10 messages to avoid token limits)
        conversationHistory.takeLast(10).forEach { message ->
            when (message) {
                is ChatMessage.User -> {
                    messages.add(OpenAIMessage(role = "user", content = message.content as Any))
                }
                is ChatMessage.AI -> {
                    messages.add(OpenAIMessage(role = "assistant", content = message.content as Any))
                }
            }
        }
        
        // Add current user message
        messages.add(OpenAIMessage(role = "user", content = userMessage as Any))
        
        return messages
    }

    private suspend fun makeApiCall(apiKey: String, request: OpenAIRequest, useExtendedTimeout: Boolean = false): OpenAIResponse {
        val httpRequest = Request.Builder()
            .url(ApiConfiguration.buildOpenAIUrl(ApiConfiguration.OpenAI.CHAT_COMPLETION_ENDPOINT))
            .post(gson.toJson(request).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        val clientToUse = if (useExtendedTimeout) storyHttpClient else httpClient
        val response = clientToUse.newCall(httpRequest).execute()
        val responseBody = response.body?.string() ?: ""
        
        Timber.d("OpenAI HTTP response: code=${response.code}, body length=${responseBody.length}")
        if (responseBody.isNotBlank()) {
            Timber.d("OpenAI response preview: ${responseBody.take(300)}...")
        }
        
        if (!response.isSuccessful) {
            val errorResponse = try {
                gson.fromJson(responseBody, OpenAIResponse::class.java)
            } catch (e: Exception) {
                null
            }
            
            val errorMessage = errorResponse?.error?.message 
                ?: "HTTP ${response.code}: ${response.message}"
            throw Exception("OpenAI API Error: $errorMessage")
        }
        
        return gson.fromJson(responseBody, OpenAIResponse::class.java)
    }

    private suspend fun recordTokenUsage(
        response: OpenAIResponse,
        serviceType: String,
        modelName: String
    ) {
        response.usage?.let { usage ->
            try {
                Timber.d("Recording token usage - Service: $serviceType, Model: '$modelName', Input: ${usage.promptTokens}, Output: ${usage.completionTokens}")
                tokenUsageRepository.recordTokenUsage(
                    serviceType = serviceType,
                    modelName = modelName,
                    inputTokens = usage.promptTokens,
                    outputTokens = usage.completionTokens
                )
                Timber.d("Recorded token usage: $serviceType - ${usage.promptTokens} input, ${usage.completionTokens} output tokens")
            } catch (e: Exception) {
                Timber.e(e, "Failed to record token usage for $serviceType")
            }
        }
    }

    private fun extractMessageContent(response: OpenAIResponse): String {
        Timber.d("Extracting message content from OpenAI response: ${response.choices.size} choices")
        
        response.error?.let { error ->
            Timber.e("OpenAI API Error: ${error.message}")
            throw Exception("OpenAI API Error: ${error.message}")
        }
        
        val firstChoice = response.choices.firstOrNull()
        if (firstChoice == null) {
            Timber.w("No choices in OpenAI response")
            throw Exception("No choices received from OpenAI")
        }
        
        Timber.d("First choice details: message=${firstChoice.message != null}, finishReason=${firstChoice.finishReason}")
        
        val rawContent = firstChoice.message?.content
        val content = when (rawContent) {
            is String -> rawContent.trim()
            else -> rawContent.toString().trim()
        }
        
        if (content.isBlank()) {
            Timber.w("Empty or null content in OpenAI response choice. FinishReason: ${firstChoice.finishReason}")
            Timber.w("Message object: ${firstChoice.message}")
            throw Exception("No response content received from OpenAI (finish_reason: ${firstChoice.finishReason})")
        }
        
        Timber.d("Extracted OpenAI content length: ${content.length}")
        return content
    }

    suspend fun analyzeImageWithBitmap(
        prompt: String,
        bitmap: Bitmap,
        maxTokens: Int = 1024,
        temperature: Float = 0.7f
    ): String {
        val base64Image = bitmapToBase64(bitmap)
        return generateImageAnalysisResponse(prompt, base64Image, maxTokens, temperature)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream) // 80% quality for balance
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    suspend fun generateImageWithDALLE(
        prompt: String,
        size: String = "1024x1024", // "256x256", "512x512", "1024x1024", "1792x1024", "1024x1792"
        quality: String = "standard", // "standard" or "hd"
        style: String = "vivid" // "vivid" or "natural"
    ): ByteArray = withContext(Dispatchers.IO) {
        
        val apiKey = getApiKey()
        require(apiKey.isNotBlank()) { "OpenAI API key not configured" }
        
        val requestBody = mapOf(
            "model" to "dall-e-3",
            "prompt" to prompt,
            "n" to 1,
            "size" to size,
            "quality" to quality,
            "style" to style,
            "response_format" to "b64_json"
        )
        
        val json = gson.toJson(requestBody)
        val requestBodyObj = json.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("${ApiConfiguration.OpenAI.BASE_URL}/images/generations")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBodyObj)
            .build()
        
        try {
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Timber.e("DALL-E API error: ${response.code} - $errorBody")
                throw Exception("DALL-E API error: ${response.code} - $errorBody")
            }
            
            val responseBody = response.body?.string()
                ?: throw Exception("Empty response from DALL-E API")
            
            // Parse the response to extract the base64 image data
            val dalleResponse = gson.fromJson(responseBody, DALLEResponse::class.java)
            val imageData = dalleResponse.data.firstOrNull()?.b64_json
                ?: throw Exception("No image data in DALL-E response")
            
            // Record DALL-E usage (no token count, but track the request)
            try {
                tokenUsageRepository.recordTokenUsage(
                    serviceType = "dalle_image",
                    modelName = "dall-e-3",
                    inputTokens = 0, // DALL-E pricing is per image, not per token
                    outputTokens = 1 // Count as 1 "output unit" representing 1 image
                )
                Timber.d("Recorded DALL-E image generation usage")
            } catch (e: Exception) {
                Timber.e(e, "Failed to record DALL-E usage")
            }
            
            // Decode base64 to byte array
            Base64.decode(imageData, Base64.DEFAULT)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate image with DALL-E")
            throw e
        }
    }

    suspend fun generateImageAnalysisResponse(
        prompt: String,
        imageBase64: String,
        maxTokens: Int = 1024,
        temperature: Float = 0.7f
    ): String = withContext(Dispatchers.IO) {
        
        val apiKey = getApiKey()
        require(apiKey.isNotBlank()) { "OpenAI API key not configured" }
        
        // Create message with image content
        val messages = listOf(
            OpenAIMessage(
                role = "user",
                content = listOf(
                    ContentPart(
                        type = "text",
                        text = prompt
                    ),
                    ContentPart(
                        type = "image_url",
                        imageUrl = ImageUrl(
                            url = "data:image/jpeg;base64,$imageBase64",
                            detail = "high" // Use high detail for better analysis
                        )
                    )
                )
            )
        )
        
        val request = OpenAIRequest(
            model = ApiConfiguration.OpenAI.GPT_5_MINI, // GPT-5 mini supports vision
            messages = messages,
            maxCompletionTokens = maxTokens,
            stream = false
        )
        
        try {
            val response = makeApiCall(apiKey, request)
            val content = extractMessageContent(response)
            
            // Record token usage asynchronously - don't let it block the response
            launch {
                try {
                    recordTokenUsage(response, "image_analysis", request.model)
                } catch (e: Exception) {
                    Timber.w(e, "Token usage recording failed (non-blocking)")
                }
            }
            
            return@withContext content
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate OpenAI image analysis response")
            throw e
        }
    }

    suspend fun generateStoryContent(
        prompt: String,
        maxTokens: Int = 4000,
        temperature: Float = 0.8f
    ): String = withContext(Dispatchers.IO) {
        
        val apiKey = getApiKey()
        require(apiKey.isNotBlank()) { "OpenAI API key not configured" }
        
        val messages = listOf(
            OpenAIMessage(
                role = "system", 
                content = """You are a creative storytelling expert specializing in children's and young adult literature. You excel at creating engaging, age-appropriate stories with compelling characters, vivid descriptions, and meaningful themes. Always return properly formatted JSON responses.""" as Any
            ),
            OpenAIMessage(
                role = "user",
                content = prompt as Any
            )
        )
        
        val request = OpenAIRequest(
            model = ApiConfiguration.OpenAI.GPT_5_MINI,
            messages = messages,
            maxCompletionTokens = maxTokens,
            temperature = temperature,
            stream = false
        )
        
        try {
            val response = makeApiCall(apiKey, request, useExtendedTimeout = true)
            val content = extractMessageContent(response)
            
            // Record token usage asynchronously - don't let it block the response
            launch {
                try {
                    recordTokenUsage(response, "story", request.model)
                } catch (e: Exception) {
                    Timber.w(e, "Token usage recording failed (non-blocking)")
                }
            }
            
            return@withContext content
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate story content with OpenAI")
            throw e
        }
    }

    suspend fun generateConversationalResponse(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        personalityType: String = "helpful"
    ): String = withContext(Dispatchers.IO) {
        
        val personalityPrompt = when (personalityType) {
            "friendly" -> "You are a warm, friendly AI assistant who loves to chat and help people. Use a casual, upbeat tone."
            "professional" -> "You are a professional, knowledgeable AI assistant. Provide clear, accurate information in a polite, business-like manner."
            "creative" -> "You are a creative, imaginative AI assistant. Feel free to use analogies, examples, and creative explanations."
            "educational" -> "You are an educational AI tutor. Focus on teaching and explaining concepts clearly with examples."
            else -> "You are a helpful, balanced AI assistant providing accurate and friendly responses."
        }
        
        val messages = mutableListOf<OpenAIMessage>()
        messages.add(OpenAIMessage(role = "system", content = personalityPrompt as Any))
        
        // Add conversation history
        conversationHistory.takeLast(8).forEach { message ->
            when (message) {
                is ChatMessage.User -> {
                    messages.add(OpenAIMessage(role = "user", content = message.content as Any))
                }
                is ChatMessage.AI -> {
                    messages.add(OpenAIMessage(role = "assistant", content = message.content as Any))
                }
            }
        }
        
        messages.add(OpenAIMessage(role = "user", content = userMessage as Any))
        
        val request = OpenAIRequest(
            model = ApiConfiguration.OpenAI.GPT_5_MINI,
            messages = messages,
            maxCompletionTokens = ApiConfiguration.OpenAI.Chat.MAX_TOKENS
        )
        
        try {
            val apiKey = getApiKey()
            val response = makeApiCall(apiKey, request)
            val content = extractMessageContent(response)
            
            // Record token usage asynchronously - don't let it block the response
            launch {
                try {
                    recordTokenUsage(response, "conversation", request.model)
                } catch (e: Exception) {
                    Timber.w(e, "Token usage recording failed (non-blocking)")
                }
            }
            
            return@withContext content
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate OpenAI conversational response")
            throw e
        }
    }

    suspend fun generateQuizQuestionsOnline(
        subject: String,
        topic: String,
        difficulty: String,
        questionTypes: List<String>,
        count: Int,
        previousQuestions: List<String> = emptyList(),
        studentName: String? = null,
        gradeLevel: Int? = null,
        country: String? = null
    ): String = withContext(Dispatchers.IO) {

        val apiKey = getApiKey()
        require(apiKey.isNotBlank()) { "OpenAI API key not configured" }

        val avoidanceContext = if (previousQuestions.isNotEmpty()) {
            val lastFew = previousQuestions.takeLast(8).joinToString("\n- ") {
                "\"${it.take(100)}...\""
            }
            """
            - CRITICAL REQUIREMENT: Your questions must be COMPLETELY DIFFERENT from these recent questions:
            - $lastFew
            - Do NOT create variations or similar versions of these questions
            - Avoid the same topics, formats, or approaches used in the above questions
            - Use entirely different angles, concepts, and question structures
            """.trimIndent()
        } else ""

        val studentContext = buildString {
            if (studentName != null) append("Student: $studentName\n")
            if (gradeLevel != null) append("Grade Level: $gradeLevel\n")
            if (country != null) append("Educational Context: $country curriculum standards\n")
        }

        val questionTypesList = questionTypes.joinToString(", ")

        // Add randomization for creative question generation
        val creativeSeed = System.currentTimeMillis() % 1000
        val questionAngles = listOf(
            "scenario-based application",
            "compare and contrast",
            "cause and effect analysis",
            "problem-solving approach",
            "real-world connection",
            "analytical thinking",
            "creative interpretation",
            "cultural perspective",
            "historical context",
            "modern implications"
        )
        val selectedAngle = questionAngles[(creativeSeed % questionAngles.size).toInt()]

        val questionStyles = listOf(
            "unexpected perspective",
            "advanced application",
            "interdisciplinary connection",
            "critical analysis",
            "practical demonstration",
            "conceptual understanding",
            "innovative thinking",
            "contemporary relevance"
        )
        val selectedStyle = questionStyles[(creativeSeed / 100 % questionStyles.size).toInt()]

        val quizPrompt = """
        You are a creative educational expert. Generate $count UNIQUE and INNOVATIVE quiz questions for a ${gradeLevel?.let { "Grade $it" } ?: ""} student.
        
        GENERATION PARAMETERS:
        Subject: $subject
        Topic: $topic
        Difficulty: $difficulty
        Question Types: $questionTypesList
        Creative Focus: $selectedAngle
        Question Style: $selectedStyle
        Random Seed: $creativeSeed
        ${studentContext.trim()}
        
        STRICT REQUIREMENTS:
        - ABSOLUTELY FORBIDDEN: Basic recall questions like "What is the capital of...", "Who was...", "When did...", "Where is..."
        - ABSOLUTELY FORBIDDEN: Simple definition questions like "What is...", "Define...", "List..."
        - MANDATORY: Focus on $selectedAngle with $selectedStyle approach
        - Create questions that require deeper thinking and analysis
        - Use unexpected angles and creative scenarios
        - Make questions intellectually engaging and thought-provoking
        - Incorporate real-world applications and modern contexts
        - Each question must be distinctly different from typical textbook questions
        $avoidanceContext
        
        CREATIVITY GUIDELINES:
        - Use "What if..." scenarios
        - Ask about implications, connections, and relationships
        - Focus on problem-solving and critical thinking
        - Incorporate current events or modern examples
        - Ask "Why might..." or "How would..." questions
        - Use comparative analysis between concepts
        
        Return ONLY this JSON object (no code fences, no text):
        {
          "questions": [
            {
              "question": "...",
              "type": "MULTIPLE_CHOICE",
              "options": ["A","B","C","D"],
              "correctAnswer": "A",
              "explanation": "..."
            }
          ]
        }

        
        IMPORTANT: Use only these question types:
        - "MULTIPLE_CHOICE" (with 4 options)
        - "TRUE_FALSE" (with options ["True", "False"])
        - "FILL_IN_BLANK" (no options needed)
        - "SHORT_ANSWER" (no options needed)
        
        Generate the questions NOW with maximum creativity and uniqueness in JSON format:
        """.trimIndent()

        val messages = listOf(
            OpenAIMessage(role = "system", content = "You are an innovative educational expert who specializes in creating UNIQUE, CREATIVE, and INTELLECTUALLY CHALLENGING quiz questions. You NEVER create basic recall questions or typical textbook questions. Your questions always require higher-order thinking, analysis, and creative problem-solving. Generate questions in the exact JSON format requested." as Any),
            OpenAIMessage(role = "user", content = quizPrompt as Any)
        )

        val request = OpenAIRequest(
            model = ApiConfiguration.OpenAI.GPT_5_MINI,
            messages = messages,
            maxCompletionTokens = 4000,  // Increased from 2048 to 4000 to prevent truncation
            responseFormat = mapOf("type" to "json_object"),
            stream = false
        )

        try {
            Timber.d("Making OpenAI quiz questions request: ${request.messages.size} messages, model: ${request.model}")
            val response = makeApiCall(apiKey, request)
            Timber.d("OpenAI quiz questions response received: ${response.choices.size} choices")
            val content = extractMessageContent(response)
            
            // Record token usage asynchronously - don't let it block the response
            launch {
                try {
                    recordTokenUsage(response, "quiz", request.model)
                } catch (e: Exception) {
                    Timber.w(e, "Token usage recording failed (non-blocking)")
                }
            }
            
            return@withContext content
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate OpenAI quiz questions")
            throw e
        }
    }


    suspend fun generateCurriculumAwareQuiz(
        subject: String,
        gradeLevel: Int,
        topic: String,
        count: Int,
        country: String? = null,
        studentName: String? = null,
        previousQuestions: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        
        val apiKey = getApiKey()
        require(apiKey.isNotBlank()) { "OpenAI API key not configured" }
        
        val avoidanceContext = if (previousQuestions.isNotEmpty()) {
            val lastFew = previousQuestions.takeLast(8).joinToString("\n- ") { 
                "\"${it.take(100)}...\""
            }
            """
            - CRITICAL REQUIREMENT: Your questions must be COMPLETELY DIFFERENT from these recent questions:
            - $lastFew
            - Do NOT create variations or similar versions of these questions
            - Avoid the same topics, formats, or approaches used in the above questions
            - Use entirely different angles, concepts, and question structures
            """.trimIndent()
        } else ""
        
        // Enhanced creativity for curriculum questions too
        val creativeSeed = System.currentTimeMillis() % 1000
        val curriculumAngles = listOf(
            "practical life application",
            "cross-cultural comparison", 
            "historical cause-and-effect",
            "modern technology impact",
            "environmental connections",
            "social implications",
            "economic relationships",
            "scientific methodology",
            "creative problem-solving",
            "ethical considerations"
        )
        val selectedCurriculumAngle = curriculumAngles[(creativeSeed % curriculumAngles.size).toInt()]
        
        val complexityLevels = listOf(
            "analyze relationships between concepts",
            "evaluate different perspectives",
            "synthesize information from multiple sources", 
            "apply knowledge to new situations",
            "compare and contrast different approaches",
            "predict outcomes based on given conditions",
            "justify reasoning with evidence",
            "design solutions to complex problems"
        )
        val selectedComplexity = complexityLevels[(creativeSeed / 100 % complexityLevels.size).toInt()]

        val curriculumPrompt = """
        You are an innovative curriculum specialist. Generate $count CREATIVE and ENGAGING quiz questions for a Grade $gradeLevel student.
        
        GENERATION PARAMETERS:
        Subject: $subject
        Topic: $topic
        ${if (country != null) "Curriculum Context: $country educational standards" else ""}
        ${if (studentName != null) "Student: $studentName" else ""}
        Creative Approach: $selectedCurriculumAngle
        Thinking Level: $selectedComplexity
        Innovation Seed: $creativeSeed
        
        ENHANCED REQUIREMENTS:
        - ABSOLUTELY FORBIDDEN: Basic recall questions like "What is the capital of...", "Who was...", "When did...", "Where is..."
        - ABSOLUTELY FORBIDDEN: Simple definition questions like "What is...", "Define...", "List..."
        - COMPLETELY AVOID typical textbook questions and basic recall
        - MANDATORY: Focus on $selectedCurriculumAngle using $selectedComplexity
        - Create questions that challenge students to think beyond memorization
        - Use real-world scenarios, case studies, and hypothetical situations  
        - Incorporate modern contexts, technology, and current events
        - Ask questions that require analysis, evaluation, and creative thinking
        - Make each question intellectually stimulating and unique
        $avoidanceContext
        
        INNOVATION STRATEGIES:
        - Use "What would happen if..." scenarios
        - Ask about consequences, implications, and connections
        - Include "How might X affect Y?" questions
        - Use "Compare the advantages of..." approaches
        - Ask "Why do you think..." analytical questions
        - Include "What evidence supports..." reasoning questions
        - Use interdisciplinary connections and real-world applications
        
       Return ONLY this JSON object (no code fences, no text):
        {
          "questions": [
            {
              "question": "...",
              "type": "MULTIPLE_CHOICE",
              "options": ["A","B","C","D"],
              "correctAnswer": "A",
              "explanation": "..."
            }
          ]
        }
        
        IMPORTANT: Use only these question types:
        - "MULTIPLE_CHOICE" (with 4 options)
        - "TRUE_FALSE" (with options ["True", "False"])
        - "FILL_IN_BLANK" (no options needed)
        - "SHORT_ANSWER" (no options needed)
        
        Generate INNOVATIVE and THOUGHT-PROVOKING questions now:
        """.trimIndent()
        
        val messages = listOf(
            OpenAIMessage(role = "system", content = "You are an innovative curriculum specialist who creates UNIQUE, CREATIVE, and INTELLECTUALLY CHALLENGING quiz questions. You NEVER create basic recall questions, typical textbook questions, or simple definitions. Your questions always require higher-order thinking, analysis, synthesis, and creative problem-solving. Generate curriculum-aligned questions in the exact JSON format requested." as Any),
            OpenAIMessage(role = "user", content = curriculumPrompt as Any)
        )
        
        val request = OpenAIRequest(
            model = ApiConfiguration.OpenAI.GPT_5_MINI,
            messages = messages,
            responseFormat = mapOf("type" to "json_object"),
            maxCompletionTokens = 4000  // Increased from 2048 to 4000 to prevent truncation
        )
        
        try {
            Timber.d("Making OpenAI curriculum quiz request: ${request.messages.size} messages, model: ${request.model}")
            val response = makeApiCall(apiKey, request)
            Timber.d("OpenAI curriculum quiz response received: ${response.choices.size} choices")
            val content = extractMessageContent(response)
            
            // Record token usage asynchronously - don't let it block the response
            launch {
                try {
                    recordTokenUsage(response, "curriculum_quiz", request.model)
                } catch (e: Exception) {
                    Timber.w(e, "Token usage recording failed (non-blocking)")
                }
            }
            
            return@withContext content
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate OpenAI curriculum-aware quiz")
            throw e
        }
    }

    fun parseQuizResponse(response: String): List<Map<String, Any>> {
        if (response.isBlank()) return emptyList()

        // --- helpers (scoped to this function) ---
        fun inferType(q: Map<String, Any>): String {
            val opts = (q["options"] as? List<*>)?.map { it?.toString() ?: "" } ?: emptyList()
            return when {
                opts.map { it.lowercase() } == listOf("true", "false") -> "TRUE_FALSE"
                opts.isNotEmpty() -> "MULTIPLE_CHOICE"
                (q["question"] as? String)?.contains("____") == true -> "FILL_IN_BLANK"
                else -> "SHORT_ANSWER"
            }
        }

        fun normalizeQuestion(q: Map<String, Any>): Map<String, Any> {
            val question = (q["question"] as? String)?.trim().orEmpty()
            val explanation = (q["explanation"] as? String)?.trim().orEmpty()
            val optionsRaw = (q["options"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            var correct = (q["correctAnswer"] as? String)?.trim().orEmpty()

            val rawType = (q["type"] as? String)?.trim()?.uppercase()
            val type = when (rawType) {
                "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_IN_BLANK", "SHORT_ANSWER" -> rawType
                else -> inferType(q)
            } ?: "SHORT_ANSWER"

            return when (type) {
                "MULTIPLE_CHOICE" -> {
                    // Ensure exactly 4 options
                    val fixedOptions = if (optionsRaw.size == 4) optionsRaw else
                        (optionsRaw + listOf("A", "B", "C", "D")).take(4)

                    // If still empty somehow, provide a hard fallback
                    val safeOptions = if (fixedOptions.isEmpty()) listOf("A", "B", "C", "D") else fixedOptions

                    // Normalize correctAnswer:
                    // - If user supplied a letter, convert to option text
                    // - If blank or invalid, default to first option
                    val letterToIndex = mapOf("A" to 0, "B" to 1, "C" to 2, "D" to 3)
                    val normalizedCorrect = when {
                        correct.isBlank() -> safeOptions.first()
                        correct.uppercase() in letterToIndex.keys -> {
                            val idx = letterToIndex[correct.uppercase()]!!
                            safeOptions.getOrNull(idx) ?: safeOptions.first()
                        }
                        correct !in safeOptions -> safeOptions.first()
                        else -> correct
                    }

                    mapOf(
                        "question" to question,
                        "type" to "MULTIPLE_CHOICE",
                        "options" to safeOptions,
                        "correctAnswer" to normalizedCorrect,
                        "explanation" to explanation
                    )
                }

                "TRUE_FALSE" -> {
                    val tf = listOf("True", "False")
                    val normalized = when {
                        correct.equals("true", true) -> "True"
                        correct.equals("false", true) -> "False"
                        else -> "True"
                    }
                    mapOf(
                        "question" to question,
                        "type" to "TRUE_FALSE",
                        "options" to tf,
                        "correctAnswer" to normalized,
                        "explanation" to explanation
                    )
                }

                "FILL_IN_BLANK", "SHORT_ANSWER" -> {
                    // Your ViewModel warns on empty correctAnswer — provide a safe default
                    if (correct.isBlank()) correct = "Answers will vary"
                    mapOf(
                        "question" to question,
                        "type" to type,
                        "correctAnswer" to correct,
                        "explanation" to explanation
                    )
                }

                else -> {
                    // Unknown -> coerce to SHORT_ANSWER safely
                    if (correct.isBlank()) correct = "Answers will vary"
                    mapOf(
                        "question" to question,
                        "type" to "SHORT_ANSWER",
                        "correctAnswer" to correct,
                        "explanation" to explanation
                    )
                }
            }
        }

        fun legacyParseInner(resp: String): List<Map<String, Any>> {
            val jsonString = extractJsonFromResponse(resp)
            if (jsonString.isBlank()) {
                Timber.w("No JSON content extracted from response")
                Timber.w("Original response preview: ${resp.take(300)}...")
                return emptyList()
            }

            // Try array first
            parseAsJsonArray(jsonString)?.let { return it.map(::normalizeQuestion) }
            // Then single object
            parseAsJsonObject(jsonString)?.let { return it.map(::normalizeQuestion) }

            Timber.w("Failed legacy parse; jsonString preview: ${jsonString.take(300)}")
            return emptyList()
        }
        // --- end helpers ---

        val trimmed = response.trim()

        // Prefer JSON-object-with-questions when using JSON mode (OpenAI)
        if (trimmed.startsWith("{") && trimmed.contains("\"questions\"")) {
            return try {
                @Suppress("UNCHECKED_CAST")
                val root = gson.fromJson(trimmed, Map::class.java) as Map<String, Any>
                val arr = root["questions"] as? List<*>
                val items = arr?.mapNotNull { it as? Map<String, Any> } ?: emptyList()
                val normalized = items.map(::normalizeQuestion)
                Timber.d("Parsed ${normalized.size} questions from object-with-questions")
                normalized
            } catch (e: Exception) {
                Timber.d(e, "Object-with-questions parse failed; falling back to legacy")
                legacyParseInner(response)
            }
        }

        // Fallback: legacy path for non-JSON-mode outputs (arrays, fenced text, mixed prose)
        return legacyParseInner(response)
    }





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

        // PRIORITIZE JSON array detection first (for multiple questions)
        val arrayStart = cleaned.indexOf('[')
        val arrayEnd = cleaned.lastIndexOf(']')
        
        if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
            val jsonString = cleaned.substring(arrayStart, arrayEnd + 1)
            val finalJson = repairAndCleanJson(jsonString)
            Timber.d("Extracted JSON array: ${finalJson.take(100)}...")
            return finalJson
        }

        // Fallback to single JSON object if no array is found
        val objectJson = extractSingleObjectJson(cleaned)
        if (objectJson.isNotBlank()) {
            Timber.d("Extracted JSON object: ${objectJson.take(100)}...")
        } else {
            Timber.w("No valid JSON found in response")
        }
        return objectJson
    }

    private fun repairAndCleanJson(jsonString: String): String {
        // First apply basic cleaning
        val basicCleaned = cleanJsonString(jsonString)
        
        // Simple repairs without complex regex patterns
        return basicCleaned
            // Add missing required fields for questions that only have "question" and "type"
            .replace(
                Regex("""(\{\s*"question":\s*"[^"]*",\s*"type":\s*"[^"]*"\s*\})""")
            ) { match ->
                val questionObj = match.value.dropLast(1) // Remove closing }
                when {
                    questionObj.contains("\"type\": \"SHORT_ANSWER\"") -> 
                        "$questionObj, \"correctAnswer\": \"\", \"explanation\": \"\"}"
                    questionObj.contains("\"type\": \"FILL_IN_BLANK\"") -> 
                        "$questionObj, \"correctAnswer\": \"\", \"explanation\": \"\"}"
                    questionObj.contains("\"type\": \"TRUE_FALSE\"") -> 
                        "$questionObj, \"options\": [\"True\", \"False\"], \"correctAnswer\": \"True\", \"explanation\": \"\"}"
                    questionObj.contains("\"type\": \"MULTIPLE_CHOICE\"") -> 
                        "$questionObj, \"options\": [\"A\", \"B\", \"C\", \"D\"], \"correctAnswer\": \"A\", \"explanation\": \"\"}"
                    else -> 
                        "$questionObj, \"correctAnswer\": \"\", \"explanation\": \"\"}"
                }
            }
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

    private fun cleanJsonString(jsonString: String): String {
        return jsonString
            .replace(",\n]", "\n]")    // Remove trailing commas before array end
            .replace(", ]", " ]")      // Remove trailing commas before array end
            .replace(",]", "]")        // Remove trailing commas before array end
            .replace(",\n}", "\n}")    // Remove trailing commas in objects
            .replace(", }", " }")      // Remove trailing commas in objects
            .replace(",}", "}")        // Remove trailing commas in objects
            .replace("\\n", "")        // Remove escaped newlines that might break parsing
            .let { sanitizeJsonQuotes(it) }  // Fix unescaped quotes
            .trim()
    }

    private fun sanitizeJsonQuotes(jsonString: String): String {
        // Fix common JSON quote issues that break parsing
        // Handle unescaped quotes in string values that break JSON
        
        return jsonString
            // Fix unescaped quotes in explanation fields (most common issue)
            .replace(Regex("""("explanation":\s*"[^"]*)"([^"]*"[^"]*")""")) { match ->
                val prefix = match.groupValues[1]
                val problematic = match.groupValues[2]
                val fixed = problematic.replace("\"", "\\\"")
                "$prefix$fixed"
            }
            // Fix unescaped quotes in question fields
            .replace(Regex("""("question":\s*"[^"]*)"([^"]*"[^"]*")""")) { match ->
                val prefix = match.groupValues[1]
                val problematic = match.groupValues[2]
                val fixed = problematic.replace("\"", "\\\"")
                "$prefix$fixed"
            }
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

    private fun parseAsJsonArray(jsonString: String): List<Map<String, Any>>? {
        return try {
            if (jsonString.isBlank()) {
                Timber.w("Empty JSON string provided")
                return null
            }
            
            // Parse as List of Maps directly instead of Array
            @Suppress("UNCHECKED_CAST")
            val questionsList = gson.fromJson(jsonString, List::class.java) as? List<*>
            
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

    private fun parseAsJsonObject(jsonString: String): List<Map<String, Any>>? {
        return try {
            if (jsonString.isBlank()) {
                Timber.w("Empty JSON string provided for object parsing")
                return null
            }
            
            @Suppress("UNCHECKED_CAST")
            val singleQuestion = gson.fromJson(jsonString, Map::class.java) as? Map<String, Any>
            
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


}