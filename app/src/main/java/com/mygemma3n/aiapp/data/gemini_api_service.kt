// GeminiApiService.kt  ────────────────────────────────────────────────
package com.mygemma3n.aiapp.data

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.scale
import okhttp3.Request
import org.json.JSONArray
import java.util.Base64
import com.mygemma3n.aiapp.data.local.entities.TokenUsage
import com.mygemma3n.aiapp.data.repository.TokenUsageRepository
import com.mygemma3n.aiapp.config.ApiConfiguration
import javax.inject.Provider

/* ────────────────────────────────────────────────────────────────────
   SERVICE
   ─────────────────────────────────────────────────────────────────── */
@Singleton
class GeminiApiService @Inject constructor(
    private val context: Context,
    private val tokenUsageRepositoryProvider: Provider<TokenUsageRepository>
) {

    /* Model IDs - using centralized configuration */
    companion object {
        // Online models (delegated to centralized config)
        const val GEMINI_PRO_MODEL = ApiConfiguration.Online.GEMINI_PRO_MODEL
        const val GEMINI_FLASH_MODEL = ApiConfiguration.Online.GEMINI_FLASH_MODEL
        const val EMBEDDING_MODEL = ApiConfiguration.Online.EMBEDDING_MODEL
        const val GEMINI_IMAGE_MODEL = ApiConfiguration.Online.IMAGE_MODEL
        
        // Offline models (delegated to centralized config)
        const val PRIMARY_GENERATION_MODEL = ApiConfiguration.Offline.PRIMARY_GENERATION_MODEL
    }

    /* state */
    private var model:  GenerativeModel? = null
    private var apiKey: String?          = null
    private val http                     = OkHttpClient()

    /* init */
    suspend fun initialize(cfg: GeminiApiConfig) = withContext(Dispatchers.IO) {
        apiKey = cfg.apiKey
        val genCfg = generationConfig {
            temperature     = cfg.temperature
            topK            = cfg.topK
            topP            = cfg.topP
            maxOutputTokens = cfg.maxOutputTokens
        }
        model = GenerativeModel(cfg.modelName, cfg.apiKey, genCfg)
        Timber.d("Gemini initialised with model ${cfg.modelName}")
    }

    fun isInitialized(): Boolean = model != null
    private fun checkInit(): GenerativeModel =
        model ?: error("GeminiApiService not initialised – call initialize() first.")

    /* ───── embeddings (REST) ───── */
    suspend fun embedText(text: String): FloatArray = withContext(Dispatchers.IO) {
        val key = apiKey ?: error("Service not initialised")

        val url = ApiConfiguration.buildOnlineGeminiUrl(
            ApiConfiguration.Online.GENERATE_CONTENT_ENDPOINT,
            EMBEDDING_MODEL,
            key
        ).replace(":generateContent", ":embedContent")
        val body = JSONObject().apply {
            put("model", "models/$EMBEDDING_MODEL")
            put("content", JSONObject().apply {
                put("parts", listOf(JSONObject().apply { put("text", text) }))
            })
        }

        val req = okhttp3.Request.Builder()
            .url("$url?key=$key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Embed failed: HTTP ${resp.code}")
            val arr = JSONObject(resp.body!!.string())
                .getJSONObject("embedding")
                .getJSONArray("value")
            FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
        }
    }

    /* ───── text generation ───── */
    fun streamText(prompt: String): Flow<String> = flow {
        checkInit().generateContentStream(prompt)
            .mapNotNull { it.text }
            .collect(::emit)
    }.flowOn(Dispatchers.IO)

    suspend fun generateTextComplete(prompt: String, serviceType: String = "unknown"): String = withContext(Dispatchers.IO) {
        val result = checkInit().generateContent(prompt)
        val response = result.text ?: ""
        
        // Try to get actual token counts from usage metadata
        val actualTokenUsage = extractTokenUsageFromResult(result, prompt, response)
        
        try {
            val tokenUsageRepository = tokenUsageRepositoryProvider.get()
            Timber.d("Recording token usage: service=$serviceType, input=${actualTokenUsage.inputTokens}, output=${actualTokenUsage.outputTokens}")
            tokenUsageRepository.recordTokenUsage(
                serviceType = serviceType,
                modelName = model?.modelName ?: "unknown",
                inputTokens = actualTokenUsage.inputTokens,
                outputTokens = actualTokenUsage.outputTokens
            )
            Timber.d("Token usage recorded successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to record token usage")
        }
        
        response
    }
    
    suspend fun generateTextCompleteWithTokens(prompt: String, serviceType: String = "unknown"): Pair<String, TokenUsage> = withContext(Dispatchers.IO) {
        val result = checkInit().generateContent(prompt)
        val response = result.text ?: ""
        
        // Try to get actual token counts from usage metadata
        val actualTokenUsage = extractTokenUsageFromResult(result, prompt, response)
        
        try {
            val tokenUsageRepository = tokenUsageRepositoryProvider.get()
            Timber.d("Recording token usage: service=$serviceType, input=${actualTokenUsage.inputTokens}, output=${actualTokenUsage.outputTokens}")
            tokenUsageRepository.recordTokenUsage(
                serviceType = serviceType,
                modelName = model?.modelName ?: "unknown",
                inputTokens = actualTokenUsage.inputTokens,
                outputTokens = actualTokenUsage.outputTokens
            )
            Timber.d("Token usage recorded successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to record token usage")
        }
        
        response to actualTokenUsage
    }
    
    // Enhanced token extraction from API response
    private fun extractTokenUsageFromResult(
        result: com.google.ai.client.generativeai.type.GenerateContentResponse,
        prompt: String,
        response: String,
        hasImage: Boolean = false
    ): TokenUsage {
        return try {
            // Try to extract actual token counts from usageMetadata if available
            val usageMetadata = result.usageMetadata
            if (usageMetadata != null) {
                val inputTokens = usageMetadata.promptTokenCount ?: estimateTokens(prompt, hasImage)
                val outputTokens = usageMetadata.candidatesTokenCount ?: estimateTokens(response)
                
                Timber.d("Extracted actual token counts: input=$inputTokens, output=$outputTokens")
                TokenUsage(inputTokens, outputTokens)
            } else {
                // Fallback to estimation if usage metadata not available
                Timber.d("UsageMetadata not available, using estimation")
                val inputTokens = estimateTokens(prompt, hasImage)
                val outputTokens = estimateTokens(response)
                TokenUsage(inputTokens, outputTokens)
            }
        } catch (e: Exception) {
            Timber.w(e, "Error extracting token usage, falling back to estimation")
            val inputTokens = estimateTokens(prompt, hasImage)
            val outputTokens = estimateTokens(response)
            TokenUsage(inputTokens, outputTokens)
        }
    }

    // Improved token estimation function
    private fun estimateTokens(text: String, hasImage: Boolean = false): Int {
        // Basic estimation: ~4 characters per token for English text
        val baseTokens = (text.length / 4).coerceAtLeast(1)
        
        // Add estimated tokens for image processing if present
        val imageTokens = if (hasImage) 258 else 0 // Gemini typically uses ~258 tokens for image processing
        
        return baseTokens + imageTokens
    }

    /* ───── multimodal helpers ───── */
    suspend fun generateContent(block: Content): String = withContext(Dispatchers.IO) {
        checkInit().generateContent(block).text ?: ""
    }

    suspend fun generateContentWithImage(prompt: String, img: Bitmap): String {
        val resized = img.scale(512, 512)
        return generateContent(content { image(resized); text(prompt) })
    }

    /* one-off override for vision model etc. */
    suspend fun generateContentWithImageAndModel(
        modelName: String,
        prompt: String,
        image: Bitmap,
        serviceType: String = "unknown"
    ): String = withContext(Dispatchers.IO) {
        val key  = apiKey ?: error("Service not initialised")
        val cfg  = model?.generationConfig
            ?: generationConfig { 
                maxOutputTokens = if (serviceType == "ocr") 2048 else 512
                temperature = if (serviceType == "ocr") 0.1f else 0.7f
            }

        val visionModel = GenerativeModel(modelName, key, cfg)
        val result = visionModel.generateContent(
            content { image(image); text(prompt) }
        )
        val response = result.text ?: ""
        
        
        // Try to get actual token counts from usage metadata
        val actualTokenUsage = extractTokenUsageFromResult(result, prompt, response, hasImage = true)
        
        try {
            val tokenUsageRepository = tokenUsageRepositoryProvider.get()
            Timber.d("Recording vision token usage: service=$serviceType, input=${actualTokenUsage.inputTokens}, output=${actualTokenUsage.outputTokens}")
            tokenUsageRepository.recordTokenUsage(
                serviceType = serviceType,
                modelName = modelName,
                inputTokens = actualTokenUsage.inputTokens,
                outputTokens = actualTokenUsage.outputTokens
            )
            Timber.d("Vision token usage recorded successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to record vision token usage")
        }
        
        response
    }

    /** Dedicated OCR function using Gemini 2.5 Flash for superior handwriting recognition */
    suspend fun performOCR(image: Bitmap): String = withContext(Dispatchers.IO) {
        val ocrPrompt = """
            Extract all text from this image. This includes both printed text and handwritten text.
            
            Instructions:
            - Transcribe exactly what you see, preserving original formatting when possible
            - Include all text, even if partially visible or unclear
            - For handwritten text, do your best to interpret unclear letters
            - Maintain line breaks and paragraph structure
            - Do not add any explanations or descriptions
            - Only output the extracted text content
            - If no text is visible, respond with: "No text detected"
            
            Text content:
        """.trimIndent()
        
        return@withContext generateContentWithImageAndModel(
            modelName = GEMINI_FLASH_MODEL, // Gemini 2.5 Flash for best OCR performance
            prompt = ocrPrompt,
            image = image,
            serviceType = "ocr"
        )
    }

    // GeminiApiService.kt
    suspend fun generateImageBytes(prompt: String): ByteArray = withContext(Dispatchers.IO) {
        val key = apiKey ?: error("Service not initialised")

        val url = ApiConfiguration.buildOnlineGeminiUrl(
            ApiConfiguration.Online.GENERATE_CONTENT_ENDPOINT,
            GEMINI_IMAGE_MODEL,
            key
        )

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
            })
        }

        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Image generation failed: HTTP ${resp.code}")
            val parts = JSONObject(resp.body.string())
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")


            val b64 = (0 until parts.length())
                .asSequence()
                .map { parts.getJSONObject(it).optJSONObject("inlineData")?.optString("data") }
                .firstOrNull { !it.isNullOrEmpty() }
                ?: error("No image data returned")

            Base64.getDecoder().decode(b64)
        }
    }



    /* ───── quick template ───── */
    suspend fun translate(
        text: String,
        target: String,
        source: String = "auto"
    ): String = generateTextComplete(
        if (source == "auto") "Translate to $target: $text"
        else "Translate from $source to $target: $text"
    )

    /* cleanup */
    fun cleanup() { model = null; apiKey = null }
}

/* ───── utility extension: sanity-check API key ───── */
suspend fun GeminiApiService.validateKey(key: String): Boolean = try {
    initialize(GeminiApiConfig(apiKey = key, maxOutputTokens = 1200))
    generateTextComplete("Say OK", "validation")
    true
} catch (e: Exception) {
    Timber.e(e, "Key validation failed")
    false
}

/* legacy alias so old code still compiles */
typealias ApiConfig = GeminiApiConfig
suspend fun GeminiApiService.validateApiKey(k: String) = validateKey(k)