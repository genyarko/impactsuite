package com.example.mygemma3n.data

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File

@Singleton
class TextEmbeddingService @Inject constructor(
    @ApplicationContext
    private val context: Context
) {
    companion object {
        private const val MODEL_ASSET = "models/universal_sentence_encoder.tflite"
    }

    // ✅ Correct direct assignment of TextEmbedder, not inside a lambda
    private val embedder: TextEmbedder by lazy {
        val (isDownloaded, modelPath) = resolveModelPath()
        val baseOpts = BaseOptions.builder()
            .apply {
                if (isDownloaded) {
                    // Use absolute file path for downloaded models
                    setModelAssetPath(modelPath)
                } else {
                    // Use asset path for bundled models
                    setModelAssetPath(modelPath)
                }
            }
            .build()
        val opts = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(baseOpts)
            .setL2Normalize(false)
            .build()
        TextEmbedder.createFromOptions(context, opts)
    }

    private fun resolveModelPath(): Pair<Boolean, String> {
        // 1. Check assets directory first (since universal_sentence_encoder.tflite stays in assets)
        val assetName = resolveAssetName()
        if (assetName != null) {
            // Copy from assets to cache for better performance
            val cachedModel = File(context.cacheDir, "models/universal_sentence_encoder.tflite")
            if (!cachedModel.exists()) {
                copyAssetToCache(assetName, cachedModel)
                Timber.i("Copied text embedding model from assets to cache: ${cachedModel.absolutePath}")
            } else {
                Timber.i("Using cached text embedding model: ${cachedModel.absolutePath}")
            }
            return Pair(true, cachedModel.absolutePath)
        }

        // 2. Check downloaded models directory (fallback for future scenarios)
        val downloadedModel = File(context.filesDir, "models/universal_sentence_encoder.tflite")
        if (downloadedModel.exists()) {
            Timber.i("Using downloaded text embedding model: ${downloadedModel.absolutePath}")
            return Pair(true, downloadedModel.absolutePath)
        }

        // 3. Final fallback to direct asset access
        Timber.w("Text embedding model not found anywhere - using direct asset path")
        return Pair(false, MODEL_ASSET)
    }

    private fun resolveAssetName(): String? {
        return try {
            context.assets.list("models")?.firstOrNull { 
                it.equals("universal_sentence_encoder.tflite", ignoreCase = true) 
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check assets for text embedding model")
            null
        }
    }

    private fun copyAssetToCache(assetName: String, cachedModel: File) {
        try {
            cachedModel.parentFile?.mkdirs()
            context.assets.open("models/$assetName").use { input ->
                cachedModel.outputStream().use { output -> 
                    input.copyTo(output) 
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy text embedding model from assets to cache")
            throw e
        }
    }

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        // ✅ Use correct API to retrieve embeddings list
        val firstEmbedding = embedder
            .embed(text)
            .embeddingResult()
            .embeddings()
            .first()
        val raw = firstEmbedding.floatEmbedding()

        // ✅ Explicit lambda types to avoid inference errors
        val norm = sqrt(raw.fold(0f) { acc: Float, v: Float -> acc + v * v })
        FloatArray(raw.size) { i -> raw[i] / norm }
    }
}
