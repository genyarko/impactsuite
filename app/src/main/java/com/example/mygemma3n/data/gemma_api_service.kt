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
    private val context: Context
) {

    /* ─────────── internal state ─────────── */
    private var generativeModel: GenerativeModel? = null
    private var apiKey: String? = null
    private val http = OkHttpClient()

    companion object {
        const val GEMINI_PRO_MODEL   = "gemini-1.5-pro"
        const val GEMINI_FLASH_MODEL = "gemini-1.5-flash"
        // Use the specific Gemma 3n model ID for embeddings from the online API
        const val EMBEDDING_MODEL    = "gemma-3n-e4b-it" // Or "gemma-3n-e2b-it" for a smaller variant
    }

    data class ApiConfig(
        val apiKey: String,
        val modelName: String  = GEMINI_FLASH_MODEL,
        val temperature: Float = 0.7f,
        val topK: Int          = 40,
        val topP: Float        = 0.95f,
        val maxOutputTokens: Int = 512
    )

    /* ─────────── initialisation ─────────── */
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

    fun isInitialized(): Boolean = generativeModel != null

    /* ─────────── embeddings (REST) ─────────── */
    suspend fun embedText(
        text: String,
        model: String = EMBEDDING_MODEL
    ): FloatArray = withContext(Dispatchers.IO) {
        val key = apiKey ?: error("Gemini API not initialised")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:embedContent"
        val bodyJson = """
            {
              "model":"models/$model",
              "content":{"parts":[{"text":${JSONObject.quote(text)} }]}
            }
        """.trimIndent()

        val req = okhttp3.Request.Builder()
            .url("$url?key=$key")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Embed call failed: ${resp.code}")
            val arr = JSONObject(resp.body!!.string()).getJSONArray("embedding")
            FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
        }
    }

    /* ─────────── text helpers ─────────── */
    suspend fun generateText(prompt: String): Flow<String> = flow {
        val model = generativeModel ?: error("Gemini API not initialised")
        model.generateContentStream(prompt).collect { chunk ->
            chunk.text?.let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateTextComplete(prompt: String): String = withContext(Dispatchers.IO) {
        val model = generativeModel ?: error("Gemini API not initialised")
        model.generateContent(prompt).text ?: ""
    }

    /* ─────────── generic multimodal helper ─────────── */
    suspend fun generateContent(prompt: Content): String = withContext(Dispatchers.IO) {
        val model = generativeModel ?: error("Gemini API not initialised")
        model.generateContent(prompt).text ?: ""
    }

    /* ─────────── convenience wrappers ─────────── */
    suspend fun generateContentWithImage(prompt: String, image: Bitmap): String =
        generateContent(content { image(image); text(prompt) })

    suspend fun generateQuiz(topic: String, difficulty: String = "medium", count: Int = 5): String =
        generateTextComplete(
            """
              Generate $count quiz questions about $topic at $difficulty level.
              Return JSON:
              {"questions":[{"question":"…","options":["A","B","C","D"],
              "correctAnswer":0,"explanation":"…"}]}
            """.trimIndent()
        )

    suspend fun translateText(text: String, target: String, source: String = "auto"): String =
        generateTextComplete(
            if (source == "auto")
                "Translate to $target: $text"
            else
                "Translate from $source to $target: $text"
        )

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
    fun cleanup() {
        generativeModel = null
        apiKey = null
    }
}

/* ─────────── quick API-key sanity check ─────────── */
suspend fun GeminiApiService.validateApiKey(testKey: String): Boolean = try {
    initialize(GeminiApiService.ApiConfig(apiKey = testKey, maxOutputTokens = 10))
    generateTextComplete("Say OK")
    true
} catch (e: Exception) {
    Timber.e(e, "API-key validation failed")
    false
}

