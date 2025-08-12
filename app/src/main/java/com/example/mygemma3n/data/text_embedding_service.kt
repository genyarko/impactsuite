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
import java.net.URL
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TextEmbeddingService @Inject constructor(
    @ApplicationContext
    private val context: Context
) {
    companion object {
        private const val MODEL_ASSET_OLD = "models/universal_sentence_encoder.tflite"
        private const val MODEL_ASSET_NEW = "ml/universal_sentence_encoder.tflite"
        // Emergency download link provided
        private const val EMERGENCY_DOWNLOAD_URL = "https://www.dropbox.com/scl/fi/01ipcfyvti1miwafptgie/universal_sentence_encoder.tflite?rlkey=fuqzrqc9673ejb6ul4szd4oaz&st=lue33dpr&dl=1"
    }

    private var embedder: TextEmbedder? = null
    private val initializationMutex = Mutex()
    private var isInitialized = false

    private suspend fun ensureEmbedderInitialized(): TextEmbedder? = initializationMutex.withLock {
        if (isInitialized) return embedder

        try {
            val modelFile = getOrDownloadModel()
            if (modelFile != null) {
                val baseOpts = BaseOptions.builder()
                    .setModelAssetPath(modelFile.absolutePath)
                    .build()
                val opts = TextEmbedder.TextEmbedderOptions.builder()
                    .setBaseOptions(baseOpts)
                    .setL2Normalize(false)
                    .build()
                embedder = TextEmbedder.createFromOptions(context, opts)
                Timber.d("TextEmbedder initialized successfully")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TextEmbedder")
            embedder = null
        }
        
        isInitialized = true
        return embedder
    }

    private suspend fun getOrDownloadModel(): File? = withContext(Dispatchers.IO) {
        // 1. Check new location first (ml/universal_sentence_encoder.tflite)
        val newAssetName = resolveAssetName("ml")
        if (newAssetName != null) {
            val cachedModel = File(context.cacheDir, "models/universal_sentence_encoder.tflite")
            if (!cachedModel.exists()) {
                copyAssetToCache("ml/$newAssetName", cachedModel)
                Timber.d("Copied embedding model from ml/ assets to cache")
            }
            return@withContext cachedModel
        }

        // 2. Check old location (models/universal_sentence_encoder.tflite)  
        val oldAssetName = resolveAssetName("models")
        if (oldAssetName != null) {
            val cachedModel = File(context.cacheDir, "models/universal_sentence_encoder.tflite")
            if (!cachedModel.exists()) {
                copyAssetToCache("models/$oldAssetName", cachedModel)
                Timber.d("Copied embedding model from models/ assets to cache")
            }
            return@withContext cachedModel
        }

        // 3. Check if already downloaded
        val downloadedModel = File(context.filesDir, "models/universal_sentence_encoder.tflite")
        if (downloadedModel.exists()) {
            Timber.d("Using existing downloaded embedding model")
            return@withContext downloadedModel
        }

        // 4. Try emergency download
        return@withContext tryEmergencyDownload(downloadedModel)
    }

    private suspend fun tryEmergencyDownload(downloadedModel: File): File? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Downloading Universal Sentence Encoder from emergency URL...")
            downloadedModel.parentFile?.mkdirs()
            
            URL(EMERGENCY_DOWNLOAD_URL).openStream().use { input ->
                downloadedModel.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            if (downloadedModel.length() > 0) {
                Timber.d("Successfully downloaded embedding model from emergency URL (${downloadedModel.length()} bytes)")
                return@withContext downloadedModel
            } else {
                Timber.w("Downloaded model file is empty")
                downloadedModel.delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Emergency download failed")
            downloadedModel.delete() // Clean up partial download
        }
        
        return@withContext null
    }

    // Legacy function - kept for compatibility but not used in new flow
    private fun resolveModelPath(): Pair<Boolean, String> {
        // Check new ml/ location first
        val newAssetName = resolveAssetName("ml")
        if (newAssetName != null) {
            return Pair(false, MODEL_ASSET_NEW)
        }
        
        // Check old models/ location
        val oldAssetName = resolveAssetName("models")
        if (oldAssetName != null) {
            return Pair(false, MODEL_ASSET_OLD)
        }

        // Final fallback
        return Pair(false, MODEL_ASSET_NEW)
    }

    private fun resolveAssetName(directory: String = "models"): String? {
        return try {
            context.assets.list(directory)?.firstOrNull { 
                it.equals("universal_sentence_encoder.tflite", ignoreCase = true) 
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check $directory assets for text embedding model")
            null
        }
    }

    private fun copyAssetToCache(assetPath: String, cachedModel: File) {
        try {
            cachedModel.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                cachedModel.outputStream().use { output -> 
                    input.copyTo(output) 
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy text embedding model from assets to cache: $assetPath")
            throw e
        }
    }

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val embedderInstance = ensureEmbedderInitialized()
        
        if (embedderInstance == null) {
            Timber.w("TextEmbedder not available - returning mock embedding")
            // Return deterministic mock embedding as fallback
            return@withContext FloatArray(512) { index ->
                val hash = text.hashCode() + index
                (hash % 1000) / 1000f
            }
        }

        try {
            // ✅ Use correct API to retrieve embeddings list
            val firstEmbedding = embedderInstance
                .embed(text)
                .embeddingResult()
                .embeddings()
                .first()
            val raw = firstEmbedding.floatEmbedding()

            // ✅ Explicit lambda types to avoid inference errors
            val norm = sqrt(raw.fold(0f) { acc: Float, v: Float -> acc + v * v })
            FloatArray(raw.size) { i -> raw[i] / norm }
        } catch (e: Exception) {
            Timber.e(e, "Embedding failed, returning mock embedding")
            // Return mock embedding on error
            FloatArray(512) { index ->
                val hash = text.hashCode() + index
                (hash % 1000) / 1000f
            }
        }
    }
}
