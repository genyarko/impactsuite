package com.example.mygemma3n.data



import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val modelRepository: ModelRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelUrl = inputData.getString(KEY_MODEL_URL) ?: return@withContext Result.failure()
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: return@withContext Result.failure()

        try {
            setProgress(workDataOf(KEY_PROGRESS to 0))

            // Download model
            val modelFile = downloadModel(modelUrl, modelName) { progress ->
                setProgress(workDataOf(KEY_PROGRESS to progress))
            }

            // Verify and optimize model
            val optimizedModel = optimizeModel(modelFile)

            // Save to repository
            modelRepository.saveModel(modelName, optimizedModel)

            Result.success(workDataOf(KEY_MODEL_PATH to optimizedModel.absolutePath))
        } catch (e: Exception) {
            Timber.e(e, "Failed to download model")
            Result.failure()
        }
    }

    private suspend fun downloadModel(
        url: String,
        name: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = applicationContext.getDir("models", Context.MODE_PRIVATE)
        val modelFile = File(cacheDir, "$name.tflite")

        URL(url).openStream().use { input ->
            modelFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                val contentLength = URL(url).openConnection().contentLengthLong

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                    onProgress(progress)
                }
            }
        }

        modelFile
    }

    private suspend fun optimizeModel(modelFile: File): File = withContext(Dispatchers.IO) {
        // Apply model optimization (quantization, etc.)
        val optimizedFile = File(modelFile.parent, "${modelFile.nameWithoutExtension}_optimized.tflite")

        // In production, you would apply actual optimization here
        modelFile.copyTo(optimizedFile, overwrite = true)

        optimizedFile
    }

    companion object {
        const val KEY_MODEL_URL = "model_url"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_MODEL_PATH = "model_path"
        const val KEY_PROGRESS = "progress"

        fun createWorkRequest(modelUrl: String, modelName: String): OneTimeWorkRequest {
            val inputData = workDataOf(
                KEY_MODEL_URL to modelUrl,
                KEY_MODEL_NAME to modelName
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()

            return OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag("model_download")
                .build()
        }
    }
}