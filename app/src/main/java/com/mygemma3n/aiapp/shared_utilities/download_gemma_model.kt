package com.mygemma3n.aiapp.shared_utilities

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Add this to your ModelRepository or as a utility function

suspend fun downloadCorrectGemmaModel(context: Context): String? = withContext(Dispatchers.IO) {
    try {
        // Correct Gemma 2B model URLs for TFLite
        val modelUrls = listOf(
            // Gemma 2B IT (Instruction Tuned) models
            "https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-gpu-int4/2" to "gemma-2b-it-gpu-int4.tflite",
            "https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-cpu-int4/2" to "gemma-2b-it-cpu-int4.tflite",

            // Alternative: Use Gemma models from TensorFlow Hub
            "https://tfhub.dev/google/lite-model/gemma/tflite/2b-it-gpu-int4/1?lite-format=tflite" to "gemma-2b-it.tflite"
        )

        // For development, you can use a smaller test model
        val testModelUrl = "https://storage.googleapis.com/download.tensorflow.org/models/tflite/gpu/multi_add.tflite"

        // Download a test model first to verify the setup works
        val cacheDir = File(context.cacheDir, "models")
        cacheDir.mkdirs()

        val testFile = File(cacheDir, "test_model.tflite")
        if (!testFile.exists()) {
            downloadFile(testModelUrl, testFile)
        }

        // Return the test model path for now
        testFile.absolutePath
    } catch (e: Exception) {
        Timber.e(e, "Failed to download model")
        null
    }
}

private suspend fun downloadFile(url: String, outputFile: File) = withContext(Dispatchers.IO) {
    try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        connection.inputStream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        Timber.d("Downloaded file to: ${outputFile.absolutePath}")
    } catch (e: Exception) {
        Timber.e(e, "Download failed")
        throw e
    }
}