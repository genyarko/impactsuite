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

    /* --------------------------------------------------------------------- */
    /* Public model variants                                                 */
    /* --------------------------------------------------------------------- */

    enum class ModelVariant(val fileName: String, val displayName: String) {
        /** Quant‑int4 2‑B bundle (≈1.5 GB). */
        FAST_2B("gemma-3n-e2b-it-int4.task", "Gemma 2B Fast"),
        /** Quant‑int4 4‑B bundle (≈2.5 GB). */
        QUALITY_4B("gemma-3n-e4b-it-int4.task", "Gemma 4B Quality")
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

            // Close any previous engine
            llm?.close()
            llm = null
            currentModel = null

            val modelPath = resolveModelBundle(variant)

            val engineOpts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)   // 512 hard‑cap for Gemma‑3n mobile graph
                .setMaxNumImages(1)
                .build()

            llm = LlmInference.createFromOptions(context, engineOpts)
            currentModel = variant
            Timber.i("Loaded ${variant.displayName} from $modelPath")
        }
    }

    /* --------------------------------------------------------------------- */
    /* One‑shot multimodal inference                                         */
    /* --------------------------------------------------------------------- */

    suspend fun generateResponse(
        prompt: String,
        image: MPImage?,
        temperature: Float = 0.8f,
        topK: Int = 40
    ): String = withContext(Dispatchers.IO) {
        val inference = llm ?: throw IllegalStateException("Model not initialised")

        val sessionOpts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
            .setTopK(topK)
            .setTemperature(temperature)
            .build()

        LlmInferenceSession.createFromOptions(inference, sessionOpts).use { sess ->
            sess.addQueryChunk(prompt)
            image?.let { sess.addImage(it) }
            sess.generateResponse()
        }
    }

    /* --------------------------------------------------------------------- */
    /* Text‑only helpers (streaming + async)                                 */
    /* --------------------------------------------------------------------- */

    suspend fun generateText(
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): Flow<String> = flow {
        val inference = llm ?: throw IllegalStateException("Model not initialised")
        val response = withContext(Dispatchers.Default) { inference.generateResponse(prompt) }
        emit(response)
    }.flowOn(Dispatchers.Default)

    suspend fun generateTextAsync(
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): String {
        val inference = llm ?: throw IllegalStateException("Model not initialised")
        return withContext(Dispatchers.Default) { inference.generateResponse(prompt) }
    }

    /* --------------------------------------------------------------------- */
    /* Model selection utilities                                             */
    /* --------------------------------------------------------------------- */

    suspend fun getAvailableModels(): List<ModelVariant> = withContext(Dispatchers.IO) {
        ModelVariant.values().filter { variant ->
            resolveAssetName(variant) != null || File(getCachedModelPath(variant)).exists()
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
        llm?.close()
        llm = null
        currentModel = null
    }

    fun isInitialized(): Boolean = llm != null
    fun getCurrentModel(): ModelVariant? = currentModel

    /* --------------------------------------------------------------------- */
    /* Internal helpers                                                      */
    /* --------------------------------------------------------------------- */

    private suspend fun resolveModelBundle(variant: ModelVariant): String = withContext(Dispatchers.IO) {
        val cached = File(getCachedModelPath(variant))
        if (cached.exists()) return@withContext cached.absolutePath

        val assetName = resolveAssetName(variant)
            ?: throw IllegalStateException("${variant.displayName} not packaged in assets/models")

        cached.parentFile?.mkdirs()
        context.assets.open("models/$assetName").use { input ->
            cached.outputStream().use { output -> input.copyTo(output) }
        }
        Timber.i("Copied $assetName → ${cached.absolutePath}")
        return@withContext cached.absolutePath
    }

    /** Locate the file in assets/models, case‑insensitive. */
    private fun resolveAssetName(variant: ModelVariant): String? =
        context.assets.list("models")?.firstOrNull { it.equals(variant.fileName, ignoreCase = true) }

    private fun getCachedModelPath(variant: ModelVariant): String =
        File(context.cacheDir, "models/${variant.fileName}").absolutePath

    companion object {
        // Remember to keep these extensions uncompressed in build.gradle:
        // android { packagingOptions { resources { noCompress += ["task", "mbundle", "tflite"] } } }
        private const val MAX_MOBILE_CONTEXT = 512
    }
}
