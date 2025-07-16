package com.example.mygemma3n.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified Gemma service using MediaPipe LLM Inference.
 * This replaces GemmaEngine, GemmaModelHandler, and GemmaModelManager.
 */
@Singleton
class UnifiedGemmaService @Inject constructor(
    private val context: Context
) {
    private var llmInference: LlmInference? = null
    private val mutex = Mutex()
    private var currentModel: ModelVariant? = null

    enum class ModelVariant(val fileName: String, val displayName: String) {
        FAST_2B("gemma-3n-E2B-it-int4.task", "Gemma 2B Fast"),
        QUALITY_4B("gemma-3n-E4B-it-int4.task", "Gemma 4B Quality")
    }

    data class GenerationConfig(
        val maxTokens: Int = 256,
        val temperature: Float = 0.7f,
        val topK: Int = 40,
        val randomSeed: Int = 0
    )

    /**
     * Check which models are available in assets or cache
     */
    suspend fun getAvailableModels(): List<ModelVariant> = withContext(Dispatchers.IO) {
        val availableModels = mutableListOf<ModelVariant>()

        ModelVariant.values().forEach { variant ->
            // Check cache first
            val cachedPath = getCachedModelPath(variant)
            if (File(cachedPath).exists()) {
                availableModels.add(variant)
            } else {
                // Check assets
                try {
                    val assetPath = "models/${variant.fileName}"
                    context.assets.open(assetPath).use {
                        // If we can open it, it exists
                        availableModels.add(variant)
                    }
                } catch (e: Exception) {
                    Timber.d("Model ${variant.displayName} not found in assets")
                }
            }
        }

        Timber.d("Available models: ${availableModels.map { it.displayName }}")
        return@withContext availableModels
    }

    /**
     * Get the best available model variant
     */
    suspend fun getBestAvailableModel(): ModelVariant? = withContext(Dispatchers.IO) {
        val available = getAvailableModels()
        return@withContext when {
            available.contains(ModelVariant.QUALITY_4B) -> ModelVariant.QUALITY_4B
            available.contains(ModelVariant.FAST_2B) -> ModelVariant.FAST_2B
            else -> null
        }
    }

    /**
     * Initialize or switch to a specific model variant.
     */
    suspend fun initialize(variant: ModelVariant = ModelVariant.FAST_2B) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Skip if already initialized with the same model
            if (currentModel == variant && llmInference != null) {
                Timber.d("Model ${variant.displayName} already initialized")
                return@withLock
            }

            // Close any existing instance
            llmInference?.close()
            llmInference = null

            try {
                // First check if model exists in cache
                val cachedModelPath = getCachedModelPath(variant)
                val modelPath = if (File(cachedModelPath).exists()) {
                    Timber.d("Using cached model: $cachedModelPath")
                    cachedModelPath
                } else {
                    // Copy from assets to cache
                    copyModelFromAssets(variant)
                }

                Timber.d("Initializing ${variant.displayName} from: $modelPath")

                // Create LlmInference with the model
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512) // Default max tokens
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                currentModel = variant

                Timber.d("${variant.displayName} initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize ${variant.displayName}")
                throw IllegalStateException("Failed to initialize ${variant.displayName}: ${e.message}", e)
            }
        }
    }

    /**
     * Initialize with the best available model
     */
    suspend fun initializeBestAvailable() = withContext(Dispatchers.IO) {
        val bestModel = getBestAvailableModel()
        if (bestModel != null) {
            initialize(bestModel)
        } else {
            throw IllegalStateException("No Gemma models found in assets")
        }
    }

    /**
     * Generate text with streaming support.
     */
    suspend fun generateText(
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): Flow<String> = flow {
        val inference = mutex.withLock {
            llmInference ?: throw IllegalStateException("Model not initialized. Call initialize() first.")
        }

        val response = withContext(Dispatchers.Default) {
            inference.generateResponse(prompt)
        }

        emit(response)
    }.flowOn(Dispatchers.Default)

    /**
     * Generate text as a single response (convenience method).
     */
    suspend fun generateTextAsync(
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): String {
        val inference = mutex.withLock {
            llmInference ?: throw IllegalStateException("Model not initialized. Call initialize() first.")
        }

        return withContext(Dispatchers.Default) {
            inference.generateResponse(prompt)
        }
    }

    /**
     * Convenience method for CrisisFunctionCalling compatibility.
     */
    suspend fun generateWithModel(
        prompt: String,
        modelVariant: ModelVariant,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): String {
        // Check if the requested model is available
        val availableModels = getAvailableModels()
        val modelToUse = if (availableModels.contains(modelVariant)) {
            modelVariant
        } else {
            // Fall back to best available
            availableModels.firstOrNull() ?: throw IllegalStateException("No models available")
        }

        // Ensure the model is initialized
        if (currentModel != modelToUse) {
            initialize(modelToUse)
        }

        return generateTextAsync(
            prompt,
            GenerationConfig(
                maxTokens = maxTokens,
                temperature = temperature
            )
        )
    }

    /**
     * Check if a model is initialized.
     */
    fun isInitialized(): Boolean = llmInference != null

    /**
     * Get the currently loaded model variant.
     */
    fun getCurrentModel(): ModelVariant? = currentModel

    /**
     * Release resources.
     */
    suspend fun cleanup() = mutex.withLock {
        llmInference?.close()
        llmInference = null
        currentModel = null
    }

    // Helper methods

    private fun getCachedModelPath(variant: ModelVariant): String {
        return File(context.cacheDir, "models/${variant.fileName}").absolutePath
    }

    private suspend fun copyModelFromAssets(variant: ModelVariant): String = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "models")
        cacheDir.mkdirs()

        val cachedFile = File(cacheDir, variant.fileName)

        // Copy from assets
        val assetPath = "models/${variant.fileName}"
        try {
            context.assets.open(assetPath).use { input ->
                cachedFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Timber.d("Copied model from assets to: ${cachedFile.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy model from assets: $assetPath")
            throw IllegalStateException("Model file '${variant.fileName}' not found in assets/models/", e)
        }

        cachedFile.absolutePath
    }

    companion object {
        private const val MAX_CONTEXT_TOKENS = 8192
    }
}