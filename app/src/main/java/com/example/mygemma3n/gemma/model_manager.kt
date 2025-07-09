package com.example.mygemma3n.gemma

import android.content.Context
import com.example.mygemma3n.API_KEY
import com.example.mygemma3n.data.GeminiApiConfig
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.ModelRepository
import com.example.mygemma3n.dataStore
import com.example.mygemma3n.shared_utilities.PerformanceMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class GemmaModelManager @Inject constructor(
    private val context: Context,
    val modelRepository: ModelRepository,
    private val performanceMonitor: PerformanceMonitor,
    private val geminiApiService: GeminiApiService // Add this dependency
) {
    private val cacheMutex = Mutex()
    private var currentModelConfig: ModelConfig? = null

    companion object {
        const val TOKENIZER_VOCAB_SIZE = 256000
        const val MAX_SEQUENCE_LENGTH = 8192
        const val EMBEDDING_DIM = 768

        // Model configurations mapped to Gemma 3n API models
        // Using "gemma-3n-e2b-it" for a faster/lighter option and "gemma-3n-e4b-it" for a more capable one.
        // These align with the constants you defined in GeminiApiService.
        const val MODEL_2B = GeminiApiService.PRIMARY_GENERATION_MODEL // "gemma-3n-e2b-it" if you want a distinct lighter model, or use PRIMARY_GENERATION_MODEL from ApiService
        const val MODEL_4B = GeminiApiService.PRIMARY_GENERATION_MODEL // "gemma-3n-e4b-it" if you want a distinct heavier model
        const val MODEL_EMBEDDING = GeminiApiService.EMBEDDING_MODEL  // The dedicated embedding model (Gemma 3n)

        // API-based configurations - these are more about context window size than model size
        const val API_2B_TOKENS = 2048
        const val API_3B_TOKENS = 4096
        const val API_4B_TOKENS = 8192
    }

    sealed class ModelConfig(
        val modelName: String,
        val activeParams: String,
        val maxTokens: Int = API_2B_TOKENS // Default to the smallest context window if not specified
    ) {
        // Updated to use Gemma 3n models.
        // Note: The "2B", "3B", "4B" suffixes here are abstract
        // to represent performance tiers, not actual model sizes in the API.
        object FAST_2B : ModelConfig(MODEL_2B, "Gemma-3n-Fast", API_2B_TOKENS)
        object BALANCED_3B : ModelConfig(MODEL_4B, "Gemma-3n-Balanced", API_3B_TOKENS)
        object QUALITY_4B : ModelConfig(MODEL_4B, "Gemma-3n-Quality", API_4B_TOKENS)

        override fun toString(): String = activeParams
    }

    /**
     * Get a configured API model instead of local interpreter
     */
    suspend fun getModel(config: ModelConfig): String = cacheMutex.withLock {
        // Ensure API is initialized with the right configuration
        if (!geminiApiService.isInitialized() || currentModelConfig != config) {
            withContext(Dispatchers.IO) {
                initializeApiModel(config)
            }
        }

        // Return the model name for API calls
        config.modelName
    }

    /**
     * Get embedding model - now returns API embedding model name
     */
    suspend fun getEmbeddingModel(): String = cacheMutex.withLock {
        if (!geminiApiService.isInitialized()) {
            throw IllegalStateException("Gemini API not initialized")
        }
        MODEL_EMBEDDING
    }

    private suspend fun initializeApiModel(config: ModelConfig) = withContext(Dispatchers.IO) {
        val apiKey = getStoredApiKey() ?: error("API key not configured")

        geminiApiService.initialize(
            GeminiApiConfig(               // ← no companion
                apiKey = apiKey,
                modelName = config.modelName,
                maxOutputTokens = config.maxTokens,
                temperature = 0.7f,
                topK = 40,
                topP = 0.95f
            )
        )
        currentModelConfig = config
        Timber.d("Initialized Gemini API with model: ${config.modelName}")
    }


    private suspend fun getStoredApiKey(): String? =
        withContext(Dispatchers.IO) {
            // `dataStore` now resolves unambiguously
            context.dataStore.data
                .map { it[API_KEY] }
                .first()
        }


    /**
     * Tokenize text - now a simplified version since API handles tokenization
     */
    suspend fun tokenize(text: String, maxLength: Int = MAX_SEQUENCE_LENGTH): IntArray {
        // API handles tokenization internally, so we return a dummy array
        // This maintains compatibility with existing code that expects an IntArray
        return withContext(Dispatchers.Default) {
            IntArray(minOf(text.length, maxLength)) { it } // Dummy values
        }
    }

    /**
     * Generate text using the Gemini API
     */
    suspend fun generateText(
        model: String, // Now just a model name string
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        topK: Int = 40,
        topP: Float = 0.95f
    ): String = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        if (!geminiApiService.isInitialized()) {
            throw IllegalStateException("Gemini API not initialized")
        }

        try {
            // Note: The generativeModel inside GeminiApiService should already be configured
            // with the correct modelName and other parameters from the last initializeApiModel call.
            // So, generateTextComplete will use the `currentModelConfig.modelName` implicitly.
            val generatedText = geminiApiService.generateTextComplete(prompt)

            val totalTime = System.currentTimeMillis() - startTime
            Timber.d("Generated text via API in ${totalTime}ms")

            // Record performance metrics
            performanceMonitor.recordInference(
                prefillTime = 0, // Not applicable for API
                decodeTime = totalTime,
                tokens = maxTokens, // Use requested maxTokens for metrics
                model = model, // Use the passed model string for metrics
                feature = "text_generation",
                cacheHitRate = 0f, // Not applicable for API
                delegateType = "CLOUD_API"
            )

            generatedText
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate text")
            throw e
        }
    }

    /**
     * Warm up models - now just ensures API is ready
     */
    suspend fun warmupModels() = withContext(Dispatchers.IO) {
        Timber.d("Checking API readiness...")

        // Try to initialize with each model configuration
        listOf(ModelConfig.FAST_2B, ModelConfig.BALANCED_3B, ModelConfig.QUALITY_4B).forEach { config ->
            try {
                getModel(config) // This will call initializeApiModel internally
                Timber.d("API ready for model: ${config.activeParams} (${config.modelName})")
            } catch (e: Exception) {
                Timber.w(e, "Failed to prepare model: ${config.activeParams}. Error: ${e.message}")
            }
        }
    }

    /**
     * Clear cache - now just clears API configuration
     */
    fun clearCache() = runBlocking {
        cacheMutex.withLock {
            geminiApiService.cleanup()
            currentModelConfig = null

            Timber.d("API configuration cleared")
        }
    }

    /**
     * Get model statistics - adapted for API usage
     */
    suspend fun getModelStats(): ModelStats {
        val currentModel = currentModelConfig?.let { config ->
            ModelInfo(
                config = config,
                inputShape = intArrayOf(1, config.maxTokens), // Simplified for API
                outputShape = intArrayOf(1, TOKENIZER_VOCAB_SIZE), // Simplified for API
                delegateType = "CLOUD_API"
            )
        }

        val loadedModels = currentModel?.let { mapOf(it.config to it) } ?: emptyMap()

        // List available models - these should reflect your intended Gemma 3n models
        val availableModels = listOf(
            ModelRepository.ModelInfo(
                name = "Gemma 3n (Fast/2B abstraction)",
                path = "cloud://${MODEL_2B}",
                size = 0L, // Cloud model
                type = "gemma",
                checksum = null
            ),
            ModelRepository.ModelInfo(
                name = "Gemma 3n (Balanced/4B abstraction)",
                path = "cloud://${MODEL_4B}",
                size = 0L, // Cloud model
                type = "gemma",
                checksum = null
            )
        )

        return ModelStats(
            loadedModels = loadedModels,
            availableModels = availableModels,
            totalMemoryUsedMB = 0 // No local memory usage with API
        )
    }

    /**
     * Select optimal model based on requirements
     */
    suspend fun selectOptimalModel(
        requireQuality: Boolean = false,
        requireSpeed: Boolean = false
    ): ModelConfig = withContext(Dispatchers.Default) {
        when {
            // If you want a distinct smaller Gemma 3n for speed, define it in GeminiApiService.
            // Otherwise, MODEL_2B and MODEL_4B in this file might point to the same model.
            requireSpeed -> ModelConfig.FAST_2B // Maps to MODEL_2B (e.g., "gemma-3n-e2b-it")
            requireQuality -> ModelConfig.QUALITY_4B // Maps to MODEL_4B (e.g., "gemma-3n-e4b-it")
            else -> {
                // Default to the balanced option
                ModelConfig.BALANCED_3B
            }
        }
    }

    /**
     * Get memory info - simplified since we're not using local models
     */
    private fun getMemoryInfo(): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return MemoryInfo(
            availableMemoryMB = (memoryInfo.availMem / (1024 * 1024)).toInt(),
            totalMemoryMB = (memoryInfo.totalMem / (1024 * 1024)).toInt(),
            lowMemory = memoryInfo.lowMemory
        )
    }

    /**
     * Get battery level - kept for compatibility
     */
    private fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    data class ModelInfo(
        val config: ModelConfig,
        val inputShape: IntArray,
        val outputShape: IntArray,
        val delegateType: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ModelInfo

            if (config != other.config) return false
            if (!inputShape.contentEquals(other.inputShape)) return false
            if (!outputShape.contentEquals(other.outputShape)) return false
            if (delegateType != other.delegateType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = config.hashCode()
            result = 31 * result + inputShape.contentHashCode()
            result = 31 * result + outputShape.contentHashCode()
            result = 31 * result + delegateType.hashCode()
            return result
        }
    }

    data class ModelStats(
        val loadedModels: Map<ModelConfig, ModelInfo>,
        val availableModels: List<ModelRepository.ModelInfo>,
        val totalMemoryUsedMB: Int
    )

    data class MemoryInfo(
        val availableMemoryMB: Int,
        val totalMemoryMB: Int,
        val lowMemory: Boolean
    )
}