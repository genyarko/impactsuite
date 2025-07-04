// Updated GemmaModelManager.kt to use Gemini API
package com.example.mygemma3n.gemma

import android.content.Context
import com.example.mygemma3n.API_KEY
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
    /* Commented out local model caches
    private val modelCache = mutableMapOf<ModelConfig, Interpreter>()
    private val embeddingModelCache = mutableMapOf<String, Interpreter>()
    */

    private val cacheMutex = Mutex()
    private var currentModelConfig: ModelConfig? = null

    /* Commented out local delegates
    private val delegates: MutableMap<String, Delegate?> = mutableMapOf()
    */

    companion object {
        const val TOKENIZER_VOCAB_SIZE = 256000
        const val MAX_SEQUENCE_LENGTH = 8192
        const val EMBEDDING_DIM = 768

        // Model configurations mapped to Gemini API models
        const val MODEL_2B = "gemini-1.5-flash"     // Fast model
        const val MODEL_4B = "gemini-1.5-flash-8b"  // Balanced model
        const val MODEL_EMBEDDING = "embedding-001"  // Embedding model

        // API-based configurations
        const val API_2B_TOKENS = 2048
        const val API_3B_TOKENS = 4096
        const val API_4B_TOKENS = 8192
    }

    sealed class ModelConfig(
        val modelName: String,
        val activeParams: String,
        val maxTokens: Int = API_2B_TOKENS
    ) {
        object FAST_2B : ModelConfig(MODEL_2B, "2B", API_2B_TOKENS)
        object BALANCED_3B : ModelConfig(MODEL_4B, "3B", API_3B_TOKENS)
        object QUALITY_4B : ModelConfig(MODEL_4B, "4B", API_4B_TOKENS)

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
        try {
            // Get API key from storage or throw error
            val apiKey = getStoredApiKey()
                ?: throw IllegalStateException("API key not configured")

            // Initialize Gemini API with the selected model
            geminiApiService.initialize(
                GeminiApiService.ApiConfig(
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

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize API model")
            throw e
        }
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
        // This maintains compatibility with existing code
        return withContext(Dispatchers.Default) {
            IntArray(minOf(text.length, maxLength)) { it }
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
            // Generate text using API
            val generatedText = geminiApiService.generateTextComplete(prompt)

            val totalTime = System.currentTimeMillis() - startTime
            Timber.d("Generated text via API in ${totalTime}ms")

            // Record performance metrics
            performanceMonitor.recordInference(
                prefillTime = 0,
                decodeTime = totalTime,
                tokens = maxTokens,
                model = model,
                feature = "text_generation",
                cacheHitRate = 0f,
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
                getModel(config)
                Timber.d("API ready for model: ${config.activeParams}")
            } catch (e: Exception) {
                Timber.w(e, "Failed to prepare model: $config")
            }
        }
    }

    /**
     * Clear cache - now just clears API configuration
     */
    fun clearCache() = runBlocking {
        cacheMutex.withLock {
            /* Commented out local model cleanup
            modelCache.values.forEach { it.close() }
            modelCache.clear()
            embeddingModelCache.values.forEach { it.close() }
            embeddingModelCache.clear()
            delegates.values.forEach { it?.close() }
            delegates.clear()
            */

            // Clear API configuration
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
                inputShape = intArrayOf(1, config.maxTokens), // Simplified
                outputShape = intArrayOf(1, TOKENIZER_VOCAB_SIZE), // Simplified
                delegateType = "CLOUD_API"
            )
        }

        val loadedModels = currentModel?.let { mapOf(it.config to it) } ?: emptyMap()

        // Check which models are available via API
        val availableModels = listOf(
            ModelRepository.ModelInfo(
                name = "Gemini Flash",
                path = "cloud://gemini-1.5-flash",
                size = 0L, // Cloud model
                type = "gemini",
                checksum = null
            ),
            ModelRepository.ModelInfo(
                name = "Gemini Flash 8B",
                path = "cloud://gemini-1.5-flash-8b",
                size = 0L, // Cloud model
                type = "gemini",
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
            requireQuality -> ModelConfig.QUALITY_4B
            requireSpeed -> ModelConfig.FAST_2B
            else -> {
                // For API, we can always use the best model since there's no local resource constraint
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

