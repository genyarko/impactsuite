// GeminiApiService.kt  ────────────────────────────────────────────────
package com.example.mygemma3n.data

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

/* ────────────────────────────────────────────────────────────────────
   SERVICE
   ─────────────────────────────────────────────────────────────────── */
@Singleton
class GeminiApiService @Inject constructor(
    private val context: Context
) {

    /* IDs published 2025-07-09 */
    companion object {
        const val PRIMARY_GENERATION_MODEL = "gemma-3n-e4b-it"
        const val GEMINI_PRO_MODEL        = "gemini-1.5-pro"
        const val GEMINI_FLASH_MODEL      = "gemini-1.5-flash"   // vision
        const val EMBEDDING_MODEL         = "embedding-001"
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

        val url  = "https://generativelanguage.googleapis.com/v1beta/models/$EMBEDDING_MODEL:embedContent"
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

    suspend fun generateTextComplete(prompt: String): String = withContext(Dispatchers.IO) {
        checkInit().generateContent(prompt).text ?: ""
    }

    /* ───── multimodal helpers ───── */
    suspend fun generateContent(block: Content): String = withContext(Dispatchers.IO) {
        checkInit().generateContent(block).text ?: ""
    }

    suspend fun generateContentWithImage(prompt: String, img: Bitmap): String {
        val resized = Bitmap.createScaledBitmap(img, 512, 512, true)
        return generateContent(content { image(resized); text(prompt) })
    }

    /* one-off override for vision model etc. */
    suspend fun generateContentWithImageAndModel(
        modelName: String,
        prompt: String,
        image: Bitmap
    ): String = withContext(Dispatchers.IO) {
        val key  = apiKey ?: error("Service not initialised")
        val cfg  = model?.generationConfig
            ?: generationConfig { maxOutputTokens = 512 }

        val visionModel = GenerativeModel(modelName, key, cfg)
        visionModel.generateContent(
            content { image(image); text(prompt) }
        ).text ?: ""
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
    initialize(GeminiApiConfig(apiKey = key, maxOutputTokens = 10))
    generateTextComplete("Say OK")
    true
} catch (e: Exception) {
    Timber.e(e, "Key validation failed")
    false
}

/* legacy alias so old code still compiles */
typealias ApiConfig = GeminiApiConfig
suspend fun GeminiApiService.validateApiKey(k: String) = validateKey(k)
