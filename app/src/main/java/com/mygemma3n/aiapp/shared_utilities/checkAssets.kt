package com.mygemma3n.aiapp.shared_utilities


import android.app.Application
import android.content.Context
import com.mygemma3n.aiapp.BuildConfig
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Diagnostic utility to check asset files and help debug model loading issues
 */
object AssetDiagnostics {

    /**
     * Check and log all files in the assets/models directory
     */
    fun checkModelAssets(context: Context) {
        Timber.d("=== Asset Diagnostics Starting ===")

        try {
            // Check root assets
            val rootAssets = context.assets.list("") ?: emptyArray()
            Timber.d("Root assets: ${rootAssets.joinToString()}")

            // Check models directory
            if (rootAssets.contains("models")) {
                val modelAssets = context.assets.list("models") ?: emptyArray()
                Timber.d("Models directory contents: ${modelAssets.joinToString()}")

                // Check each model file
                modelAssets.forEach { fileName ->
                    try {
                        context.assets.open("models/$fileName").use { stream ->
                            val size = stream.available()
                            Timber.d("Model file: $fileName")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading model file: $fileName")
                    }
                }

                // Check for expected embedding model files
                val expectedModels = listOf(
                    "mobile_bert.tflite",
                    "average_word_embedder.tflite"
                )

                expectedModels.forEach { modelName ->
                    val exists = modelAssets.contains(modelName)
                    Timber.d("Expected model '$modelName': ${if (exists) "FOUND" else "MISSING"}")
                }

            } else {
                Timber.e("No 'models' directory found in assets!")
            }

        } catch (e: IOException) {
            Timber.e(e, "Error checking assets")
        }

        Timber.d("=== Asset Diagnostics Complete ===")
    }

    /**
     * Copy a model from assets to cache for debugging
     */
    fun copyModelToCache(
        context: Context,
        assetPath: String,
        fileName: String
    ): File? {
        return try {
            val cacheDir = File(context.cacheDir, "debug_models")
            cacheDir.mkdirs()

            val cacheFile = File(cacheDir, fileName)

            context.assets.open(assetPath).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Timber.d("Copied $assetPath to ${cacheFile.absolutePath}")
            Timber.d("Cache file size: ${formatFileSize(cacheFile.length())}")

            cacheFile
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy model to cache: $assetPath")
            null
        }
    }

    /**
     * Verify MediaPipe compatibility of a TFLite model
     */
    fun verifyTFLiteModel(context: Context, assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).use { stream ->
                // Read TFLite magic bytes
                val magic = ByteArray(4)
                stream.read(magic)

                // TFLite files should start with "TFL3" (0x54 0x46 0x4C 0x33)
                val isTFLite = magic[0] == 0x54.toByte() &&
                        magic[1] == 0x46.toByte() &&
                        magic[2] == 0x4C.toByte() &&
                        magic[3] == 0x33.toByte()

                Timber.d("Model $assetPath TFLite format check: ${if (isTFLite) "VALID" else "INVALID"}")
                Timber.d("Magic bytes: ${magic.joinToString { "%02X".format(it) }}")

                isTFLite
            }
        } catch (e: Exception) {
            Timber.e(e, "Error verifying TFLite model: $assetPath")
            false
        }
    }

    /**
     * Format file size for readable output
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "%.2f KB".format(size / 1024.0)
            size < 1024 * 1024 * 1024 -> "%.2f MB".format(size / (1024.0 * 1024))
            else -> "%.2f GB".format(size / (1024.0 * 1024 * 1024))
        }
    }
}

// Usage in your MainActivity or Application class:
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Run diagnostics in debug builds
        if (BuildConfig.DEBUG) {
            AssetDiagnostics.checkModelAssets(this)
        }
    }
}