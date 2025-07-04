package com.example.mygemma3n.data

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiApiService @Inject constructor(
    private val context: Context // Context might be needed for some operations, but not directly for model calls
) {

    /* ─────────── internal state ─────────── */
    private var generativeModel: GenerativeModel? = null
    private var apiKey: String? = null
    private val http = OkHttpClient()

    companion object {
        // Define all your model constants directly within this companion object
        // This makes them directly accessible within ApiConfig and other methods of GeminiApiService
        const val PRIMARY_GENERATION_MODEL = "gemma-3n-e4b-it" // Gemma 3n as core generative model
        const val GEMINI_PRO_MODEL   = "gemini-1.5-pro"   // Still available if needed for specific use cases
        const val GEMINI_FLASH_MODEL = "gemini-1.5-flash" // Still available if needed for specific use cases
        const val EMBEDDING_MODEL    = "gemma-3n-e4b-it"    // Gemma 3n for embeddings
        // (often the same IT model works fine for both)

        data class ApiConfig(
            val apiKey: String,
            // Changed default to PRIMARY_GENERATION_MODEL, which is defined within this same companion object
            val modelName: String = PRIMARY_GENERATION_MODEL, // This now correctly references the constant above
            val temperature: Float = 0.7f,
            val topK: Int = 40,
            val topP: Float = 0.95f,
            val maxOutputTokens: Int = 512
        )
    }

    /* ─────────── initialisation ─────────── */
    /**
     * Initializes the Gemini API service with the provided configuration.
     * Must be called before making any API requests.
     */
    suspend fun initialize(cfg: ApiConfig) = withContext(Dispatchers.IO) {
        apiKey = cfg.apiKey
        val genCfg = generationConfig {
            temperature     = cfg.temperature
            topK            = cfg.topK
            topP            = cfg.topP
            maxOutputTokens = cfg.maxOutputTokens
        }
        generativeModel = GenerativeModel(
            modelName        = cfg.modelName,
            apiKey           = cfg.apiKey,
            generationConfig = genCfg
        )
        Timber.d("Gemini initialised with model ${cfg.modelName}")
    }

    /**
     * Checks if the Gemini API service has been initialized.
     */
    fun isInitialized(): Boolean = generativeModel != null

    /* ─────────── embeddings (REST API using OkHttpClient) ─────────── */
    /**
     * Generates embeddings for the given text using the specified model.
     * Defaults to [EMBEDDING_MODEL] (Gemma 3n).
     */
    suspend fun embedText(
        text: String,
        model: String = EMBEDDING_MODEL
    ): FloatArray = withContext(Dispatchers.IO) {
        val key = apiKey ?: error("Gemini API not initialised. Call initialize() first.")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:embedContent"

        // Use JSONObject.quote to correctly escape the text for JSON
        val bodyJson = """
            {
              "model":"models/$model",
              "content":{"parts":[{"text":${JSONObject.quote(text)} }]}
            }
        """.trimIndent()

        val req = okhttp3.Request.Builder()
            .url("$url?key=$key") // API key directly in URL for simplicity, but consider auth header for production
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "No error body"
                Timber.e("Embed call failed: ${resp.code}. Error: $errorBody")
                error("Embed call failed: ${resp.code} - $errorBody")
            }
            val jsonResponse = JSONObject(resp.body!!.string())
            // The structure for Gemma 3n embeddings via the API will likely be standard
            // as of July 2025. It typically nests the array under "embedding" and then "value".
            // Example: { "embedding": { "value": [0.1, 0.2, ...] } }
            val embeddingObject = jsonResponse.getJSONObject("embedding") // Get the "embedding" object
            val arr = embeddingObject.getJSONArray("value") // The actual array of floats is under "value"

            FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
        }
    }

    /* ─────────── text generation helpers (using Google Generative AI SDK) ─────────── */
    /**
     * Generates text in a streaming fashion.
     * Uses the model initialized with [initialize].
     */
    suspend fun generateText(prompt: String): Flow<String> = flow {
        val model = generativeModel ?: error("Gemini API not initialised. Call initialize() first.")
        model.generateContentStream(prompt).collect { chunk ->
            chunk.text?.let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generates a complete text response.
     * Uses the model initialized with [initialize].
     */
    suspend fun generateTextComplete(prompt: String): String = withContext(Dispatchers.IO) {
        val model = generativeModel ?: error("Gemini API not initialised. Call initialize() first.")
        model.generateContent(prompt).text ?: ""
    }

    /* ─────────── generic multimodal helper (using Google Generative AI SDK) ─────────── */
    /**
     * Generates content from a generic [Content] object, supporting multimodal input.
     * Uses the model initialized with [initialize].
     */
    suspend fun generateContent(prompt: Content): String = withContext(Dispatchers.IO) {
        val model = generativeModel ?: error("Gemini API not initialised. Call initialize() first.")
        model.generateContent(prompt).text ?: ""
    }

    /* ─────────── convenience wrappers ─────────── */
    /**
     * Generates text based on a prompt and an image.
     * Uses the model initialized with [initialize].
     */
    suspend fun generateContentWithImage(prompt: String, image: Bitmap): String =
        generateContent(content { image(image); text(prompt) })

    /**
     * Generates quiz questions in JSON format.
     * Uses the model initialized with [initialize].
     */
    suspend fun generateQuiz(topic: String, difficulty: String = "medium", count: Int = 5): String =
        generateTextComplete(
            """
              Generate $count quiz questions about $topic at $difficulty level.
              Return JSON:
              {"questions":[{"question":"…","options":["A","B","C","D"],
              "correctAnswer":0,"explanation":"…"}]}
            """.trimIndent()
        )

    /**
     * Translates text to a target language.
     * Uses the model initialized with [initialize].
     */
    suspend fun translateText(text: String, target: String, source: String = "auto"): String =
        generateTextComplete(
            if (source == "auto")
                "Translate to $target: $text"
            else
                "Translate from $source to $target: $text"
        )

    /**
     * Analyzes a plant image and provides details.
     * Uses the model initialized with [initialize].
     */
    suspend fun analyzePlantImage(image: Bitmap, extra: String = ""): String =
        generateContentWithImage(
            """
              Analyse this plant image and report:
              1. Plant type
              2. Diseases
              3. Severity
              4. Treatment
              
              $extra
            """.trimIndent(),
            image
        )

    /**
     * Generates a CBT-style response.
     * Uses the model initialized with [initialize].
     */
    suspend fun generateCBTResponse(userInput: String, history: List<String> = emptyList()): String =
        generateTextComplete(
            """
              ${if (history.isNotEmpty()) "History:\n${history.joinToString("\n")}\n\n" else ""}
              As a CBT coach respond empathetically to: "$userInput".
              1. Acknowledge
              2. Identify distortions
              3. Reframe
              4. Suggest exercise
            """.trimIndent()
        )

    /* ─────────── utils ─────────── */
    /**
     * Cleans up the generative model instance.
     */
    fun cleanup() {
        generativeModel = null
        apiKey = null
    }
}

/* ─────────── quick API-key sanity check ─────────── */
/**
 * Validates an API key by making a simple request.
 */
suspend fun GeminiApiService.validateApiKey(testKey: String): Boolean = try {
    // Temporarily initialize with the test key and minimal settings for a quick check
    initialize(GeminiApiService.Companion.ApiConfig(apiKey = testKey, maxOutputTokens = 10))
    generateTextComplete("Say OK") // Make a simple request
    true
} catch (e: Exception) {
    Timber.e(e, "API-key validation failed")
    false
}