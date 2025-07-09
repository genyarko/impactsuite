// GemmaEngine.kt ───────────────────────────────────────────────────────
package com.example.mygemma3n.gemma

import android.content.Context
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.shared_utilities.PerformanceMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val performanceMonitor: PerformanceMonitor,
    private val geminiApiService: GeminiApiService
) {

    /* ───── remote model variants (for compatibility) ───── */
    enum class ModelVariant(val apiModel: String) {
        FAST_2B   (GeminiApiService.GEMINI_FLASH_MODEL),   // speed
        QUALITY_2B(GeminiApiService.GEMINI_PRO_MODEL),     // quality
        PRO_7B    (GeminiApiService.GEMINI_PRO_MODEL)      // highest quality
    }

    private val inferenceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentConfig: InferenceConfig? = null

    companion object {
        const val MAX_SEQUENCE_LENGTH = 8_192
    }

    /* ───── configs (most device-specific flags kept for API parity) ───── */
    data class InferenceConfig(
        val modelPath: String = "",
        val useGpu: Boolean = true,
        val useNnapi: Boolean = true,
        val numThreads: Int = 4,
        val enablePLE: Boolean = true,
        val kvCacheSize: Int = 4_096,
        val temperature: Float = 0.7f,
        val topK: Int = 40,
        val topP: Float = 0.95f
    )

    data class GenerationConfig(
        val maxNewTokens: Int = 256,
        val temperature: Float = 0.7f,
        val topK: Int = 40,
        val topP: Float = 0.95f,
        val repetitionPenalty: Float = 1.1f,
        val doSample: Boolean = true
    )

    /* ───── single-prompt helper (back-compat) ───── */
    suspend fun generateWithModel(
        prompt: String,
        modelVariant: ModelVariant,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): String {
        if (!geminiApiService.isInitialized()) {
            throw IllegalStateException("Gemini API not initialized. Please configure API key.")
        }
        return geminiApiService.generateTextComplete(prompt)
    }

    /* ───── init (just checks API readiness) ───── */
    suspend fun initialize(config: InferenceConfig) = withContext(Dispatchers.IO) {
        currentConfig = config
        if (!geminiApiService.isInitialized()) {
            Timber.w("Gemini API not initialized. Text generation will fail.")
        } else {
            Timber.d("Gemma engine ready with Gemini API backend")
        }
    }

    /* ───── streaming generation via API ───── */
    suspend fun generateText(
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): Flow<String> = flow {
        if (!geminiApiService.isInitialized()) {
            throw IllegalStateException("Gemini API not initialized")
        }

        val start = System.currentTimeMillis()
        try {
            geminiApiService.streamText(prompt).collect(::emit)

            val elapsed = System.currentTimeMillis() - start
            Timber.d("Generated text via API in ${elapsed} ms")

            performanceMonitor.recordInference(
                prefillTime = 0,
                decodeTime  = elapsed,
                tokens      = config.maxNewTokens,   // rough estimate
                model       = "gemini-api",
                feature     = "text_generation",
                cacheHitRate = 0f,
                delegateType = "CLOUD"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate text via API")
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /* ───── batch helper ───── */
    suspend fun batchGenerateText(
        prompts: List<String>,
        config: GenerationConfig = GenerationConfig()
    ): List<String> = withContext(Dispatchers.Default) {
        prompts.map { p ->
            async { geminiApiService.generateTextComplete(p) }
        }.awaitAll()
    }

    /* ───── misc info / cleanup ───── */
    fun getModelInfo(): ModelInfo = ModelInfo(0, 0, "CLOUD_API")

    fun cleanup() { /* nothing to do for cloud */ }

    data class ModelInfo(
        val inputTensorCount: Int,
        val outputTensorCount: Int,
        val delegateType: String
    )
}
