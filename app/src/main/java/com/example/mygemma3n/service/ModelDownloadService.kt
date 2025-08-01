package com.example.mygemma3n.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.mygemma3n.R
import com.example.mygemma3n.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val GOOGLE_DRIVE_DOWNLOAD_BASE = "https://drive.google.com/uc?export=download&id="
        private const val NOTIFICATION_CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 1001

        // DataStore keys
        private val MODEL_DOWNLOADED_KEY = booleanPreferencesKey("model_downloaded")
        private val FIRST_LAUNCH_KEY = booleanPreferencesKey("first_launch_completed")

        // Model info
        data class ModelInfo(
            val filename: String,
            val downloadUrl: String,
            val displayName: String
        )

        private val REQUIRED_MODELS = listOf(
            ModelInfo(
                filename = "gemma-3n-e2b-it-int4.task",
                downloadUrl = "https://www.dropbox.com/scl/fi/r54gxd5jmos6s5zbzki42/gemma-3n-E2B-it-int4.task?rlkey=yxg2ufwb6ge2mm8ceuugeendf&st=4ywciudb&dl=1",
                displayName = "Gemma 3n AI Model"
            ),
            ModelInfo(
                filename = "universal_sentence_encoder.tflite",
                downloadUrl = "https://www.dropbox.com/scl/fo/yfe7u3uch9pkk6qxwzt68/AGZ2ghv_QD7Ag2HhjYkKV78?rlkey=4ryi6jl9dkcj3y4vpm85spigm&st=d0l0pn4r&dl=1",
                displayName = "Universal Sentence Encoder"
            )
        )


    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    data class DownloadProgress(
        val currentFile: String? = null,
        val currentFileIndex: Int = 0,
        val totalFiles: Int = 0,
        val bytesDownloaded: Long = 0,
        val totalBytesCurrentFile: Long = 0,
        val overallProgress: Float = 0f,
        val isComplete: Boolean = false,
        val error: String? = null
    )

    suspend fun isFirstLaunch(): Boolean {
        return context.dataStore.data.map { preferences ->
            !(preferences[FIRST_LAUNCH_KEY] ?: false)
        }.first()
    }

    suspend fun markFirstLaunchComplete() {
        context.dataStore.edit { preferences ->
            preferences[FIRST_LAUNCH_KEY] = true
        }
    }

    suspend fun isModelDownloaded(): Boolean {
        // Check both DataStore flag and actual file existence for all models
        val flagExists = context.dataStore.data.map { preferences ->
            preferences[MODEL_DOWNLOADED_KEY] ?: false
        }.first()

        val allFilesExist = REQUIRED_MODELS.all { model ->
            getModelFile(model.filename).exists()
        }

        return flagExists && allFilesExist
    }

    private fun getModelFile(filename: String): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return File(modelsDir, filename)
    }

    suspend fun downloadModel(
        onProgress: (DownloadProgress) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (isModelDownloaded()) {
                return@withContext Result.success("All models already downloaded")
            }

            Timber.d("Starting download of ${REQUIRED_MODELS.size} model files")
            showDownloadNotification("Preparing download...", 0)

            val totalFiles = REQUIRED_MODELS.size
            var overallProgress = 0f

            for (modelIndex in REQUIRED_MODELS.indices) {
                val model = REQUIRED_MODELS[modelIndex]
                val currentFileIndex = modelIndex + 1

                Timber.d("Downloading ${model.displayName} (${currentFileIndex}/$totalFiles)")

                onProgress(DownloadProgress(
                    currentFile = model.displayName,
                    currentFileIndex = currentFileIndex,
                    totalFiles = totalFiles,
                    overallProgress = overallProgress
                ))

                val result = downloadSingleModel(model, currentFileIndex, totalFiles) { fileProgress ->
                    val fileProgressPercent = fileProgress / 100f
                    overallProgress = (modelIndex + fileProgressPercent) / totalFiles

                    onProgress(DownloadProgress(
                        currentFile = model.displayName,
                        currentFileIndex = currentFileIndex,
                        totalFiles = totalFiles,
                        bytesDownloaded = (fileProgress * fileProgressPercent).toLong(),
                        totalBytesCurrentFile = 100L,
                        overallProgress = overallProgress
                    ))
                }

                if (result.isFailure) {
                    val error = "Failed to download ${model.displayName}: ${result.exceptionOrNull()?.message}"
                    hideDownloadNotification()
                    onProgress(DownloadProgress(
                        currentFile = model.displayName,
                        currentFileIndex = currentFileIndex,
                        totalFiles = totalFiles,
                        overallProgress = overallProgress,
                        isComplete = true,
                        error = error
                    ))
                    return@withContext Result.failure(Exception(error))
                }
            }

            // Mark all models as downloaded
            context.dataStore.edit { preferences ->
                preferences[MODEL_DOWNLOADED_KEY] = true
            }

            // Models are now loaded directly from download location
            // No need to copy to assets folder

            val successMessage = "All models downloaded successfully (${REQUIRED_MODELS.size} files)"
            Timber.d(successMessage)

            showDownloadCompleteNotification()
            onProgress(DownloadProgress(
                currentFile = "Complete",
                currentFileIndex = totalFiles,
                totalFiles = totalFiles,
                overallProgress = 1.0f,
                isComplete = true
            ))

            return@withContext Result.success(successMessage)

        } catch (e: Exception) {
            val error = "Unexpected error during download: ${e.message}"
            Timber.e(e, error)
            hideDownloadNotification()
            onProgress(DownloadProgress(
                overallProgress = 0f,
                isComplete = true,
                error = error
            ))
            return@withContext Result.failure(e)
        }
    }

    private suspend fun downloadSingleModel(
        model: ModelInfo,
        fileIndex: Int,
        totalFiles: Int,
        onFileProgress: (Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val modelFile = getModelFile(model.filename)

            // Skip if file already exists and is valid
            if (modelFile.exists() && modelFile.length() > 0) {
                Timber.d("${model.filename} already exists, skipping download")
                onFileProgress(100)
                return@withContext Result.success("File already exists")
            }

            val directDownloadUrl = model.downloadUrl
            val request = Request.Builder()
                .url(directDownloadUrl)
                .build()

            val call = okHttpClient.newCall(request)

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val error = "HTTP ${response.code} for ${model.filename}"
                    Timber.e(error)
                    return@withContext Result.failure(Exception(error))
                }

                val responseBody = response.body

                val totalBytes = responseBody.contentLength()
                Timber.d("Downloading ${model.filename}: ${totalBytes} bytes")

                responseBody.byteStream().use { inputStream ->
                    FileOutputStream(modelFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesDownloaded = 0L
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            // Update progress
                            val fileProgress = if (totalBytes > 0) {
                                (bytesDownloaded * 100 / totalBytes).toInt()
                            } else 0

                            onFileProgress(fileProgress)

                            val overallProgress = (fileIndex - 1 + fileProgress / 100f) / totalFiles * 100
                            showDownloadNotification(
                                "Downloading ${model.displayName} ($fileIndex/$totalFiles)",
                                overallProgress.toInt()
                            )

                            // Allow cancellation
                            ensureActive()
                        }

                        outputStream.flush()
                    }
                }

                // Verify file was downloaded correctly
                if (!modelFile.exists() || modelFile.length() == 0L) {
                    val error = "Download verification failed for ${model.filename}"
                    Timber.e(error)
                    return@withContext Result.failure(Exception(error))
                }

                Timber.d("Successfully downloaded ${model.filename}: ${modelFile.length()} bytes")
                return@withContext Result.success("Downloaded ${model.filename}")
            }

        } catch (e: IOException) {
            val error = "Network error downloading ${model.filename}: ${e.message}"
            Timber.e(e, error)
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            val error = "Error downloading ${model.filename}: ${e.message}"
            Timber.e(e, error)
            return@withContext Result.failure(e)
        }
    }


    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Model Download",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of AI model download"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun showDownloadNotification(message: String, progress: Int) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading AI Model")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showDownloadCompleteNotification() {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AI Models Ready")
            .setContentText("All ${REQUIRED_MODELS.size} models downloaded successfully! App is ready to use.")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun hideDownloadNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    suspend fun checkAndDownloadModelIfNeeded(): Boolean {
        return try {
            if (isFirstLaunch() && !isModelDownloaded()) {
                Timber.d("First launch detected, starting model download")
                val result = downloadModel()
                markFirstLaunchComplete()
                result.isSuccess
            } else {
                Timber.d("Model already downloaded or not first launch")
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check/download model")
            false
        }
    }
}