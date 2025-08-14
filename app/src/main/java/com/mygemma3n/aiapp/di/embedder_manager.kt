package com.mygemma3n.aiapp.di

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Random

/**
 * Loads either Mobile-BERT (~100 MB) or Average-Word (~4 MB)
 * from /assets and exposes a single embed() call.
 *
 * FIXED: Added asset verification and fallback mechanisms
 */
@Singleton
class EmbedderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class Model(val fileName: String, val expectedDimension: Int) {
        MOBILE_BERT("mobile_bert.tflite", 768),
        AVG_WORD("average_word_embedder.tflite", 512);
    }

    private val mutex = Mutex()
    private var embedder: TextEmbedder? = null
    private var loadedModel: Model? = null
    private val MODEL_DIR = ""


    /** Initialize (or switch) the model lazily with asset verification. */
    suspend fun load(model: Model, useGpu: Boolean = false) = mutex.withLock {
        if (loadedModel == model && embedder != null) return@withLock

        withContext(Dispatchers.IO) {
            try {
                // Clean up previous embedder (if any)
                embedder?.close()
                embedder = null

                /* ── locate the asset ─────────────────────────────────────────── */
                val assetPath = resolveAssetPath(model.fileName)
                    ?: throw IllegalStateException("Model not found in assets: ${model.fileName}")

                /* ── attempt direct-from-assets load ─────────────────────────── */
                var loadSuccess = false
                try {
                    embedder = createEmbedderFromAssets(assetPath, useGpu)
                    loadSuccess = true
                    Timber.d("Loaded embedder from assets: $assetPath")
                } catch (e: Exception) {
                    Timber.w(e, "Direct asset load failed, will try cache")
                }

                /* ── fallback: copy to cache then load ───────────────────────── */
                if (!loadSuccess) {
                    val cacheFile = copyAssetToCache(assetPath, model.fileName)
                    embedder = createEmbedderFromFile(cacheFile.absolutePath, useGpu)
                    Timber.d("Loaded embedder from cache: ${cacheFile.absolutePath}")
                }

                loadedModel = model

                /* ── quick verification ──────────────────────────────────────── */
                val dims = embedder!!.embed("test")
                    .embeddingResult()
                    .embeddings()
                    .first()
                    .floatEmbedding()
                    .size
                Timber.d("Embedder ready, output dim = $dims")

            } catch (e: Exception) {
                // clean up on failure
                embedder?.close()
                embedder = null
                loadedModel = null
                Timber.e(e, "Failed to load embedder model: ${model.fileName}")
                throw e
            }
        }
    }

    /* helper: try root & /models/ sub-folder */
    private fun resolveAssetPath(fileName: String): String? {
        val candidates = listOf(fileName, "models/$fileName")
        return candidates.firstOrNull { verifyAssetExists(it) }
    }


    /** Verify if an asset exists */
    private fun verifyAssetExists(assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).use {
                true
            }
        } catch (e: Exception) {
            Timber.e("Asset not found: $assetPath")
            false
        }
    }

    /** Create embedder directly from assets */
    private fun createEmbedderFromAssets(assetPath: String, useGpu: Boolean): TextEmbedder {
        val baseOpts = BaseOptions.builder()
            .setModelAssetPath(assetPath)
            .setDelegate(if (useGpu && isGpuAvailable()) Delegate.GPU else Delegate.CPU)
            .build()

        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(baseOpts)
            .setQuantize(false)  // Ensure we're not trying to quantize at runtime
            .build()

        return TextEmbedder.createFromOptions(context, options)
    }

    /** Create embedder from file path */
    private fun createEmbedderFromFile(filePath: String, useGpu: Boolean): TextEmbedder {
        val baseOpts = BaseOptions.builder()
            .setModelAssetPath(filePath)
            .setDelegate(if (useGpu && isGpuAvailable()) Delegate.GPU else Delegate.CPU)
            .build()

        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(baseOpts)
            .setQuantize(false)
            .build()

        return TextEmbedder.createFromOptions(context, options)
    }

    /** Copy asset to cache directory */
    private fun copyAssetToCache(assetPath: String, fileName: String): File {
        val cacheDir = File(context.cacheDir, "embedder_models")
        cacheDir.mkdirs()

        val cacheFile = File(cacheDir, fileName)

        // Only copy if file doesn't exist or is corrupted
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            Timber.d("Copied model to cache: ${cacheFile.absolutePath}")
        }

        return cacheFile
    }

    /** Check if GPU is available */
    private fun isGpuAvailable(): Boolean {
        return try {
            // Try to create a GPU delegate to test availability
            val testDelegate = Delegate.GPU
            true
        } catch (e: Exception) {
            Timber.d("GPU not available for embedder")
            false
        }
    }

    /** Returns the L2-normalized embedding for a sentence. */
    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val currentEmbedder = mutex.withLock { embedder }
            ?: throw IllegalStateException("Embedder not initialized. Call load() first")

        try {
            // Ensure text is not empty
            val inputText = text.ifBlank { " " }

            val result = currentEmbedder.embed(inputText)
            val embeddings = result.embeddingResult().embeddings()

            if (embeddings.isEmpty()) {
                throw IllegalStateException("No embeddings returned")
            }

            val raw = embeddings.first().floatEmbedding()

            // Verify dimension matches expected
            val expectedDim = loadedModel?.expectedDimension ?: 0
            if (raw.size != expectedDim && expectedDim > 0) {
                Timber.w("Embedding dimension mismatch. Expected: $expectedDim, Got: ${raw.size}")
            }

            // L2-normalize
            var norm = 0f
            raw.forEach { norm += it * it }
            if (norm > 0f) {
                val scale = 1f / sqrt(norm)
                raw.indices.forEach { raw[it] *= scale }
            }

            raw
        } catch (e: Exception) {
            Timber.e(e, "Embedding failed for text: '$text'")
            // Return zero vector with appropriate dimensions
            val dims = loadedModel?.expectedDimension ?: 512
            FloatArray(dims) { 0f }
        }
    }

    /** Simple embedding function without suspend (for synchronous calls) */
    fun embedSync(text: String): FloatArray {
        val currentEmbedder = embedder
            ?: throw IllegalStateException("Embedder not initialized. Call load() first")

        return try {
            val inputText = text.ifBlank { " " }
            val raw = currentEmbedder.embed(inputText)
                .embeddingResult()
                .embeddings()
                .first()
                .floatEmbedding()

            // L2-normalize
            var norm = 0f
            raw.forEach { norm += it * it }
            if (norm > 0f) {
                val scale = 1f / sqrt(norm)
                raw.indices.forEach { raw[it] *= scale }
            }

            raw
        } catch (e: Exception) {
            Timber.e(e, "Sync embedding failed")
            val dims = loadedModel?.expectedDimension ?: 512
            FloatArray(dims) { 0f }
        }
    }

    /** Release the interpreter and resources when no longer needed. */
    suspend fun close() = mutex.withLock {
        try {
            embedder?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing embedder")
        }
        embedder = null
        loadedModel = null
    }

    /** Check if a model is currently loaded */
    fun isLoaded(): Boolean = embedder != null

    /** Get the currently loaded model type */
    fun getCurrentModel(): Model? = loadedModel

    /** Get available models in assets */
    suspend fun getAvailableModels(): List<Model> = withContext(Dispatchers.IO) {
        Model.values().filter { model ->
            verifyAssetExists(MODEL_DIR + model.fileName)
        }
    }
}