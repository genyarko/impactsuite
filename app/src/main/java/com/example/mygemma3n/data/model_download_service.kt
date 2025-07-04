package com.example.mygemma3n.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.asFlow
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

// Model Repository
@Singleton
class ModelRepository @Inject constructor(
    private val context: Context
) {
    private val modelsDir = File(context.filesDir, "models")
    private val metadataDir = File(context.filesDir, "model_metadata")

    init {
        modelsDir.mkdirs()
        metadataDir.mkdirs()
    }

    data class ModelInfo(
        val name: String,
        val path: String,
        val size: Long,
        val type: String,
        val checksum: String? = null,
        val downloadedAt: Long = System.currentTimeMillis()
    )

    suspend fun saveModel(
        name: String,
        file: File,
        type: String,
        checksum: String? = null
    ): ModelInfo = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, "$name.tflite")
        file.copyTo(targetFile, overwrite = true)

        val modelInfo = ModelInfo(
            name = name,
            path = targetFile.absolutePath,
            size = targetFile.length(),
            type = type,
            checksum = checksum
        )

        // Save metadata
        saveModelMetadata(modelInfo)

        modelInfo
    }

    private fun saveModelMetadata(modelInfo: ModelInfo) {
        val metadataFile = File(metadataDir, "${modelInfo.name}.json")
        // In production, use proper JSON serialization
        metadataFile.writeText("""
            {
                "name": "${modelInfo.name}",
                "path": "${modelInfo.path}",
                "size": ${modelInfo.size},
                "type": "${modelInfo.type}",
                "checksum": "${modelInfo.checksum ?: ""}",
                "downloadedAt": ${modelInfo.downloadedAt}
            }
        """.trimIndent())
    }

    fun getModelPath(name: String): String? {
        val modelFile = File(modelsDir, "$name.tflite")
        return if (modelFile.exists()) modelFile.absolutePath else null
    }

    fun isModelDownloaded(name: String): Boolean {
        return File(modelsDir, "$name.tflite").exists()
    }

    fun getAvailableModels(): List<ModelInfo> {
        return modelsDir.listFiles()
            ?.filter { it.extension == "tflite" }
            ?.mapNotNull { file ->
                val metadataFile = File(metadataDir, "${file.nameWithoutExtension}.json")
                if (metadataFile.exists()) {
                    // Parse metadata (simplified)
                    ModelInfo(
                        name = file.nameWithoutExtension,
                        path = file.absolutePath,
                        size = file.length(),
                        type = "gemma3n",
                        checksum = null
                    )
                } else null
            }
            ?: emptyList()
    }

    suspend fun deleteModel(name: String) = withContext(Dispatchers.IO) {
        File(modelsDir, "$name.tflite").delete()
        File(metadataDir, "$name.json").delete()
    }

    fun getModelSize(name: String): Long {
        val modelFile = File(modelsDir, "$name.tflite")
        return if (modelFile.exists()) modelFile.length() else 0L
    }
}

// Download Worker
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val modelRepository: ModelRepository
) : CoroutineWorker(context, params) {





    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelUrl = inputData.getString(KEY_MODEL_URL) ?: return@withContext Result.failure()
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: return@withContext Result.failure()
        val modelType = inputData.getString(KEY_MODEL_TYPE) ?: "gemma-3n-2b"
        val expectedChecksum = inputData.getString(KEY_EXPECTED_CHECKSUM)

        try {
            setProgress(workDataOf(
                KEY_PROGRESS to 0,
                KEY_STATUS to "Starting download..."
            ))

            // Download model with progress tracking
            val modelFile = downloadModel(modelUrl, modelName) { progress ->
                setProgress(workDataOf(
                    KEY_PROGRESS to progress,
                    KEY_STATUS to "Downloading... ${progress}%"
                ))
            }

            // Verify model integrity
            setProgress(workDataOf(
                KEY_PROGRESS to 95,
                KEY_STATUS to "Verifying model..."
            ))

            if (!verifyModel(modelFile, modelType, expectedChecksum)) {
                modelFile.delete()
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "Model verification failed")
                )
            }

            // Save to repository
            val modelInfo = modelRepository.saveModel(
                name = modelName,
                file = modelFile,
                type = modelType,
                checksum = expectedChecksum
            )

            // Clean up temp file
            if (modelFile.absolutePath != modelInfo.path) {
                modelFile.delete()
            }

            Result.success(workDataOf(
                KEY_MODEL_PATH to modelInfo.path,
                KEY_MODEL_SIZE to modelInfo.size,
                KEY_PROGRESS to 100,
                KEY_STATUS to "Download complete"
            ))
        } catch (e: Exception) {
            Timber.e(e, "Failed to download model")
            Result.failure(workDataOf(
                KEY_ERROR to (e.message ?: "Unknown error")
            ))
        }
    }

    private suspend fun downloadModel(
        url: String,
        name: String,
        onProgress: suspend (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val tempDir = File(applicationContext.cacheDir, "downloads")
        tempDir.mkdirs()
        val tempFile = File(tempDir, "$name.tmp")

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()

            val fileLength = connection.contentLengthLong
            if (fileLength <= 0) {
                Timber.w("Unknown file size for download")
            }

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastProgressUpdate = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (fileLength > 0) {
                            val progress = ((totalBytesRead * 100) / fileLength).toInt()
                            // Update progress only when it changes
                            if (progress > lastProgressUpdate) {
                                lastProgressUpdate = progress
                                onProgress(progress.coerceIn(0, 100))
                            }
                        }
                    }
                }
            }

            tempFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun verifyModel(modelFile: File, modelType: String, expectedChecksum: String?): Boolean {
        // Verify file size ranges for different model types
        val validSize = when (modelType) {
            "gemma-3n-2b" -> modelFile.length() in 800_000_000L..2_500_000_000L
            "gemma-3n-4b" -> modelFile.length() in 1_600_000_000L..5_000_000_000L
            "gemma-3n-8b" -> modelFile.length() in 3_200_000_000L..9_000_000_000L
            else -> false
        }

        if (!validSize) {
            Timber.e("Invalid model size: ${modelFile.length()} bytes for type $modelType")
            return false
        }

        // Verify checksum if provided
        if (!expectedChecksum.isNullOrEmpty()) {
            val actualChecksum = calculateChecksum(modelFile)
            if (actualChecksum != expectedChecksum) {
                Timber.e("Checksum mismatch: expected=$expectedChecksum, actual=$actualChecksum")
                return false
            }
        }

        // Basic TFLite file verification
        return verifyTFLiteFormat(modelFile)
    }

    private fun calculateChecksum(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifyTFLiteFormat(file: File): Boolean {
        // Check TFLite magic number
        return try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic)
                // TFLite files start with "TFL3"
                String(magic) == "TFL3"
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val KEY_MODEL_URL = "model_url"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_MODEL_TYPE = "model_type"
        const val KEY_EXPECTED_CHECKSUM = "expected_checksum"
        const val KEY_MODEL_PATH = "model_path"
        const val KEY_MODEL_SIZE = "model_size"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"

        fun createWorkRequest(
            modelUrl: String,
            modelName: String,
            modelType: String = "gemma-3n-2b",
            expectedChecksum: String? = null
        ): OneTimeWorkRequest {
            val inputData = workDataOf(
                KEY_MODEL_URL to modelUrl,
                KEY_MODEL_NAME to modelName,
                KEY_MODEL_TYPE to modelType,
                KEY_EXPECTED_CHECKSUM to expectedChecksum
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .setRequiresBatteryNotLow(true)
                .build()

            return OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag("model_download")
                .addTag(modelName)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()
        }
    }
}

// Model Download Manager
@Singleton
class ModelDownloadManager @Inject constructor(
    private val context: Context,
    private val workManager: WorkManager,
    private val modelRepository: ModelRepository
) {

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Int, val status: String) : DownloadState()
        data class Success(val modelPath: String, val modelSize: Long) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    data class DownloadRequest(
        val url: String,
        val name: String,
        val type: String,
        val checksum: String? = null
    )

    fun downloadModel(request: DownloadRequest): Flow<DownloadState> {
        val workRequest = ModelDownloadWorker.createWorkRequest(
            modelUrl = request.url,
            modelName = request.name,
            modelType = request.type,
            expectedChecksum = request.checksum
        )

        workManager.enqueue(workRequest)

        return workManager.getWorkInfoByIdLiveData(workRequest.id)
            .asFlow()
            .map { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
                        val status = workInfo.progress.getString(ModelDownloadWorker.KEY_STATUS)
                            ?: "Processing..."
                        DownloadState.Downloading(progress, status)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val modelPath = workInfo.outputData.getString(ModelDownloadWorker.KEY_MODEL_PATH)
                        val modelSize = workInfo.outputData.getLong(ModelDownloadWorker.KEY_MODEL_SIZE, 0L)
                        if (modelPath != null) {
                            DownloadState.Success(modelPath, modelSize)
                        } else {
                            DownloadState.Error("Model path not found")
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                        DownloadState.Error(error ?: "Download failed")
                    }
                    WorkInfo.State.CANCELLED -> {
                        DownloadState.Error("Download cancelled")
                    }
                    else -> DownloadState.Idle
                }
            }
            .flowOn(Dispatchers.IO)
    }

    fun cancelDownload(modelName: String) {
        workManager.cancelAllWorkByTag(modelName)
    }

    fun cancelAllDownloads() {
        workManager.cancelAllWorkByTag("model_download")
    }

    fun isModelAvailable(modelName: String): Boolean {
        return modelRepository.isModelDownloaded(modelName)
    }

    fun getAvailableModels(): List<ModelRepository.ModelInfo> {
        return modelRepository.getAvailableModels()
    }

    suspend fun deleteModel(modelName: String) {
        modelRepository.deleteModel(modelName)
    }

    fun getDownloadProgress(modelName: String): Flow<DownloadState> {
        return workManager.getWorkInfosByTagLiveData(modelName)
            .asFlow()
            .map { workInfos ->
                val activeWork = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                if (activeWork != null) {
                    val progress = activeWork.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
                    val status = activeWork.progress.getString(ModelDownloadWorker.KEY_STATUS)
                        ?: "Processing..."
                    DownloadState.Downloading(progress, status)
                } else {
                    DownloadState.Idle
                }
            }
            .flowOn(Dispatchers.IO)
    }

    // Example integration inside your ViewModel or service before inference or quiz generation

    // Add this function in your ViewModel or service that handles quiz generation
    suspend fun prepareAndGenerateQuiz(modelDownloadManager: ModelDownloadManager) {
        val request = ModelDownloadManager.DownloadRequest(
            url = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-2b-it-fast.tflite",
            name = "gemma-2b-it-fast",
            type = "gemma-3n-2b"
        )

        val state = if (modelDownloadManager.isModelAvailable(request.name)) {
            val info = modelDownloadManager.getAvailableModels().first { it.name == request.name }
            ModelDownloadManager.DownloadState.Success(info.path, info.size)
        } else {
            var result: ModelDownloadManager.DownloadState = ModelDownloadManager.DownloadState.Idle
            modelDownloadManager.downloadModel(request).collect { result = it }
            result
        }

        if (state is ModelDownloadManager.DownloadState.Success) {
            Timber.d("Model ready at ${state.modelPath}")
            // Initialize model and proceed with inference/quiz
        } else if (state is ModelDownloadManager.DownloadState.Error) {
            Timber.e("Cannot proceed with quiz generation: ${state.message}")
        }
    }


    suspend fun ensureModelDownloaded(manager: ModelDownloadManager, request: ModelDownloadManager.DownloadRequest): ModelDownloadManager.DownloadState {
        if (manager.isModelAvailable(request.name)) {
            val info = manager.getAvailableModels().first { it.name == request.name }
            return ModelDownloadManager.DownloadState.Success(info.path, info.size)
        }

        var result: ModelDownloadManager.DownloadState = ModelDownloadManager.DownloadState.Idle
        manager.downloadModel(request).collect { result = it }
        return result
    }


    companion object {
        // Gemma 3n model URLs (these would be real URLs in production)
        const val GEMMA_3N_2B_URL = "https://example.com/models/gemma_3n_2b_it.tflite"
        const val GEMMA_3N_4B_URL = "https://example.com/models/gemma_3n_4b_it.tflite"
        const val GEMMA_3N_8B_URL = "https://example.com/models/gemma_3n_8b_it.tflite"

        // Model metadata
        val MODEL_CONFIGS = mapOf(
            "gemma-3n-2b" to DownloadRequest(
                url = GEMMA_3N_2B_URL,
                name = "gemma_3n_2b_it",
                type = "gemma-3n-2b",
                checksum = null // Add real checksums in production
            ),
            "gemma-3n-4b" to DownloadRequest(
                url = GEMMA_3N_4B_URL,
                name = "gemma_3n_4b_it",
                type = "gemma-3n-4b",
                checksum = null
            )
        )
    }
}