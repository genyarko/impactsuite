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

    private var session: LlmInferenceSession? = null          // ← add this


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

    /* ---------- fix for vision‑enabled initialisation ---------- */
    suspend fun initialize(
        variant: ModelVariant = ModelVariant.FAST_2B
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (currentModel == variant && llmInference != null && session != null) return@withLock

            session?.close()
            llmInference?.close()
            session = null
            llmInference = null

            val modelPath = getCachedModelPath(variant).let { cached ->
                if (File(cached).exists()) cached else copyModelFromAssets(variant)
            }

            val engineOpts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setMaxNumImages(1)               // Gemma‑3n supports one image :contentReference[oaicite:0]{index=0}
                .build()

            llmInference = LlmInference.createFromOptions(context, engineOpts)

            val graphOpts = GraphOptions.builder()
                .setEnableVisionModality(true)    // turn on multimodal :contentReference[oaicite:1]{index=1}
                .build()

            val sessOpts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setGraphOptions(graphOpts)
                .setTopK(40)
                .setTemperature(0.8f)
                .build()

            session = LlmInferenceSession.createFromOptions(llmInference!!, sessOpts)
            currentModel = variant
            Timber.d("${variant.displayName} initialised with vision support")
        }
    }

    /* ---------- helper to run a prompt (text + optional image) ---------- */

    private fun newSession(): LlmInferenceSession = LlmInferenceSession.createFromOptions(
        llmInference!!,
        LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTemperature(0.8f)
            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
            .build()
    )

    suspend fun generateResponse(prompt: String, image: MPImage?): String =
        withContext(Dispatchers.IO) {
            requireNotNull(llmInference) { "Model not initialised" }
            newSession().use { sess ->          // fresh 0‑token context
                sess.addQueryChunk(prompt)
                image?.let { sess.addImage(it) }
                sess.generateResponse()
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