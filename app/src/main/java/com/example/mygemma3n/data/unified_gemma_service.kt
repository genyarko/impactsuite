package com.example.mygemma3n.data

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
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
 * Unified Gemma service built on MediaPipe LLM‑Inference.
 * Handles model discovery, packaging quirks, and one‑shot multimodal inference.
 *
 * FIXED: Proper session lifecycle management to prevent JNI crashes.
 */
@Singleton
class UnifiedGemmaService @Inject constructor(
    private val context: Context
) {
    /* --------------------------------------------------------------------- */
    /* State                                                                 */
    /* --------------------------------------------------------------------- */

    private var llm: LlmInference? = null
    private var currentModel: ModelVariant? = null
    private val mutex = Mutex()

    // Track current context length to prevent overflow
    private var currentContextLength = 0
    private var maxContextLength = 450 // Conservative limit, leaving room for response

    /* --------------------------------------------------------------------- */
    /* Public model variants                                                 */
    /* --------------------------------------------------------------------- */

    enum class ModelVariant(val fileName: String, val displayName: String) {
        /** Quant‑int4 2‑B bundle (≈1.5 GB). */
        FAST_2B("gemma-3n-e2b-it-int4.task", "Gemma 2B Fast"),
        /** Quant‑int4 4‑B bundle (≈2.5 GB). */
        QUALITY_4B("gemma-3n-e4b-it-int4.task", "Gemma 4B Quality")
    }

    data class GenerationConfig(
        val maxTokens: Int = 256,
        val temperature: Float = 0.7f,
        val topK: Int = 40,
        val randomSeed: Int = 0
    )

    /* --------------------------------------------------------------------- */
    /* Initialisation                                                        */
    /* --------------------------------------------------------------------- */

    suspend fun initialize(
        variant: ModelVariant = ModelVariant.FAST_2B,
        maxTokens: Int = 512,
        temperature: Float = 0.8f,
        topK: Int = 40
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (variant == currentModel && llm != null) return@withLock

            // Close any previous engine safely
            try {
                llm?.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing previous LLM instance")
            }
            llm = null
            currentModel = null
            currentContextLength = 0

            val modelPath = resolveModelBundle(variant)

            val engineOpts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)   // 512 hard‑cap for Gemma‑3n mobile graph
                .setMaxNumImages(1)
                .build()

            llm = LlmInference.createFromOptions(context, engineOpts)
            currentModel = variant

            // Set conservative context limit based on max tokens
            maxContextLength = maxTokens - 100 // Leave room for response

            Timber.i("Loaded ${variant.displayName} from $modelPath (context limit: $maxContextLength)")
        }
    }

    /* --------------------------------------------------------------------- */
    /* Enhanced text generation with proper session management              */
    /* --------------------------------------------------------------------- */

    suspend fun generateTextAsync(
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val inference = llm ?: throw IllegalStateException("Model not initialised")

            // Estimate prompt tokens (rough approximation: 1 token ≈ 4 characters)
            val estimatedPromptTokens = prompt.length / 4

            // If prompt alone exceeds our limit, truncate it
            val processedPrompt = if (estimatedPromptTokens > maxContextLength) {
                val maxChars = maxContextLength * 4
                Timber.w("Prompt too long ($estimatedPromptTokens tokens), truncating to $maxContextLength tokens")
                prompt.take(maxChars)
            } else {
                prompt
            }

            try {
                // Use session-based approach instead of direct generateResponse
                return@withLock generateWithSessionInternal(
                    inference,
                    processedPrompt,
                    null,
                    config
                )
            } catch (e: Exception) {
                Timber.e(e, "Generation failed, attempting recovery")

                // Try to recover by recreating the inference session
                try {
                    currentModel?.let { variant ->
                        // Close current instance safely
                        try {
                            llm?.close()
                        } catch (closeError: Exception) {
                            Timber.w(closeError, "Error during cleanup in recovery")
                        }
                        llm = null
                        currentModel = null

                        initialize(variant)
                        val recoveredInference = llm ?: throw IllegalStateException("Recovery failed")
                        return@withLock generateWithSessionInternal(
                            recoveredInference,
                            processedPrompt,
                            null,
                            config
                        )
                    }
                } catch (recoveryError: Exception) {
                    Timber.e(recoveryError, "Recovery attempt failed")
                }

                throw e
            }
        }
    }

    /* --------------------------------------------------------------------- */
    /* Session-based generation with proper lifecycle management            */
    /* --------------------------------------------------------------------- */

    private suspend fun generateWithSessionInternal(
        inference: LlmInference,
        prompt: String,
        image: MPImage?,
        config: GenerationConfig
    ): String = withContext(Dispatchers.IO) {
        val sessionOpts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(image != null).build())
            .setTopK(config.topK)
            .setTemperature(config.temperature)
            .build()

        var session: LlmInferenceSession? = null
        try {
            session = LlmInferenceSession.createFromOptions(inference, sessionOpts)
            session.addQueryChunk(prompt)
            image?.let { session.addImage(it) }

            val response = session.generateResponse()
            currentContextLength = 0 // Reset after successful generation
            return@withContext response
        } catch (e: Exception) {
            Timber.e(e, "Session generation failed")
            currentContextLength = 0 // Reset on error
            throw e
        } finally {
            // Ensure session is properly closed
            try {
                session?.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing session")
            }
        }
    }

    suspend fun generateWithSession(
        prompt: String,
        image: MPImage? = null,
        config: GenerationConfig = GenerationConfig()
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val inference = llm ?: throw IllegalStateException("Model not initialised")

            // Estimate tokens and check limits
            val estimatedTokens = prompt.length / 4
            if (estimatedTokens > maxContextLength) {
                throw IllegalArgumentException("Prompt too long: $estimatedTokens tokens (max: $maxContextLength)")
            }

            return@withLock generateWithSessionInternal(inference, prompt, image, config)
        }
    }

    /* --------------------------------------------------------------------- */
    /* One‑shot multimodal inference (FIXED)                                */
    /* --------------------------------------------------------------------- */

    suspend fun generateResponse(
        prompt: String,
        image: MPImage?,
        temperature: Float = 0.8f,
        topK: Int = 40
    ): String = withContext(Dispatchers.IO) {
        return@withContext generateWithSession(
            prompt = prompt,
            image = image,
            config = GenerationConfig(temperature = temperature, topK = topK)
        )
    }

    /* --------------------------------------------------------------------- */
    /* Text‑only helpers (streaming + async) - FIXED                         */
    /* --------------------------------------------------------------------- */

    suspend fun generateText(
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): Flow<String> = flow {
        val response = generateTextAsync(prompt, config)
        emit(response)
    }.flowOn(Dispatchers.Default)

    /* --------------------------------------------------------------------- */
    /* Utility methods for prompt optimization                               */
    /* --------------------------------------------------------------------- */

    /**
     * Estimate token count for a given text (rough approximation)
     */
    fun estimateTokens(text: String): Int {
        return text.length / 4 // Rough approximation
    }

    /**
     * Truncate prompt to fit within context window
     */
    fun truncatePrompt(prompt: String, maxTokens: Int = maxContextLength): String {
        val estimatedTokens = estimateTokens(prompt)
        return if (estimatedTokens <= maxTokens) {
            prompt
        } else {
            val maxChars = maxTokens * 4
            val truncated = prompt.take(maxChars)
            Timber.w("Truncated prompt from $estimatedTokens to $maxTokens tokens")
            truncated
        }
    }

    /**
     * Check if a prompt would exceed context limits
     */
    fun wouldExceedLimit(prompt: String): Boolean {
        return estimateTokens(prompt) > maxContextLength
    }

    /* --------------------------------------------------------------------- */
    /* Model selection utilities                                             */
    /* --------------------------------------------------------------------- */

    suspend fun getAvailableModels(): List<ModelVariant> = withContext(Dispatchers.IO) {
        ModelVariant.entries.filter { variant ->
            resolveAssetName(variant) != null || 
            File(getCachedModelPath(variant)).exists() ||
            File(getDownloadedModelPath(variant)).exists()
        }.also {
            Timber.d("Available models: ${it.map(ModelVariant::displayName)}")
        }
    }

    suspend fun getBestAvailableModel(): ModelVariant? = withContext(Dispatchers.IO) {
        val available = getAvailableModels()
        when {
            available.contains(ModelVariant.QUALITY_4B) -> ModelVariant.QUALITY_4B
            available.contains(ModelVariant.FAST_2B)    -> ModelVariant.FAST_2B
            else                                        -> null
        }
    }

    suspend fun initializeBestAvailable() = withContext(Dispatchers.IO) {
        initialize(getBestAvailableModel() ?: throw IllegalStateException("No Gemma models found in assets"))
    }

    suspend fun generateWithModel(
        prompt: String,
        modelVariant: ModelVariant,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): String {
        if (currentModel != modelVariant) initialize(modelVariant)
        return generateTextAsync(prompt, GenerationConfig(maxTokens, temperature))
    }

    /* --------------------------------------------------------------------- */
    /* House‑keeping                                                         */
    /* --------------------------------------------------------------------- */

    suspend fun cleanup() = mutex.withLock {
        try {
            llm?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error during cleanup")
        }
        llm = null
        currentModel = null
        currentContextLength = 0
    }

    fun isInitialized(): Boolean = llm != null
    fun getCurrentModel(): ModelVariant? = currentModel
    fun getCurrentContextLength(): Int = currentContextLength
    fun getMaxContextLength(): Int = maxContextLength

    /* --------------------------------------------------------------------- */
    /* Internal helpers                                                      */
    /* --------------------------------------------------------------------- */

    private suspend fun resolveModelBundle(variant: ModelVariant): String = withContext(Dispatchers.IO) {
        // 1. Check if already cached (from previous assets copy)
        val cached = File(getCachedModelPath(variant))
        if (cached.exists()) return@withContext cached.absolutePath

        // 2. Check assets directory first 
        val assetName = resolveAssetName(variant)
        if (assetName != null) {
            // Copy from assets to cache for performance
            cached.parentFile?.mkdirs()
            context.assets.open("models/$assetName").use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            }
            Timber.i("Copied $assetName from assets → ${cached.absolutePath}")
            return@withContext cached.absolutePath
        }

        // 3. Check downloaded models directory (if not in assets)
        val downloaded = File(getDownloadedModelPath(variant))
        if (downloaded.exists()) {
            Timber.i("Using downloaded model: ${downloaded.absolutePath}")
            return@withContext downloaded.absolutePath
        }

        // 4. Model not found anywhere - should trigger download
        throw IllegalStateException("${variant.displayName} not found in assets or downloads - download required")
    }

    /** Locate the file in assets/models, case‑insensitive. */
    private fun resolveAssetName(variant: ModelVariant): String? =
        context.assets.list("models")?.firstOrNull { it.equals(variant.fileName, ignoreCase = true) }

    private fun getCachedModelPath(variant: ModelVariant): String =
        File(context.cacheDir, "models/${variant.fileName}").absolutePath

    private fun getDownloadedModelPath(variant: ModelVariant): String =
        File(context.filesDir, "models/${variant.fileName}").absolutePath

    companion object {
        // Remember to keep these extensions uncompressed in build.gradle:
        // android { packagingOptions { resources { noCompress += ["task", "mbundle", "tflite"] } } }
        private const val MAX_MOBILE_CONTEXT = 512
    }
}