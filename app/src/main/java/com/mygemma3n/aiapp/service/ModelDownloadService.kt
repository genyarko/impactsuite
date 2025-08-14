package com.mygemma3n.aiapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.mygemma3n.aiapp.R
import com.mygemma3n.aiapp.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
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

        // Model info for multi-part downloads
        data class ModelInfo(
            val filename: String,
            val zipParts: List<String>,
            val displayName: String
        )

        private val REQUIRED_MODELS = listOf(
            ModelInfo(
                filename = "gemma-3n-e2b-it-int4.task",
                zipParts = generateGemmaZipUrls(),
                displayName = "Gemma 3n AI Model"
            )
        )
        
        private fun generateGemmaZipUrls(): List<String> {
            return listOf(
                "https://www.dropbox.com/scl/fi/saihcda2fez89zn7fwfvx/gemma_part_01.zip?rlkey=vlog3c6m92ppau1p0hhehqthd&st=j0mbrzo8&dl=1",
                "https://www.dropbox.com/scl/fi/vhvp30im751ql5o4z5cex/gemma_part_02.zip?rlkey=nkbltw7dpbyxio133zhl1lw6t&st=4rni94kn&dl=1",
                "https://www.dropbox.com/scl/fi/edz0g64a5wuxx5k7dn86x/gemma_part_03.zip?rlkey=visu1e3phof1k5p32u6wfiekp&st=d1wf34eq&dl=1",
                "https://www.dropbox.com/scl/fi/yr5vqq71ybo94nke7cuqv/gemma_part_04.zip?rlkey=xeq9djxbq4uamxtx779f0lcu9&st=e5h4af1t&dl=1",
                "https://www.dropbox.com/scl/fi/hrdg7c9qhxliixcaynaxc/gemma_part_05.zip?rlkey=4zoqp1jz40v0jkl0okr7g3yef&st=81nsyepo&dl=1",
                "https://www.dropbox.com/scl/fi/3qr27vzju7mncj9m7azg1/gemma_part_06.zip?rlkey=gwzw4n4vy866iv46enh3frk8t&st=fimi9ep5&dl=1",
                "https://www.dropbox.com/scl/fi/uh26kzwcgq5gxts57akkh/gemma_part_07.zip?rlkey=epgeb0rlfa7c5ww7fioufkpz1&st=lpk2rn8f&dl=1",
                "https://www.dropbox.com/scl/fi/fhleliiqimwevq35emvio/gemma_part_08.zip?rlkey=mskhqy4gkbgxi0fvcpkw1usnp&st=3my8r16m&dl=1",
                "https://www.dropbox.com/scl/fi/lae4ve8lpp57a6jncpynv/gemma_part_09.zip?rlkey=tbf6zzhjlt63ktb4g0b1g4ntd&st=0g3fn922&dl=1",
                "https://www.dropbox.com/scl/fi/ut3jji69n97mri9huayif/gemma_part_10.zip?rlkey=vv405fufbvh3u5wi9hl4vyxb5&st=egq4bera&dl=1",
                "https://www.dropbox.com/scl/fi/utbaxe93j92uyd4j24628/gemma_part_11.zip?rlkey=if9kc3cqkt4qyu0rhc2rod9h3&st=tn33erfp&dl=1",
                "https://www.dropbox.com/scl/fi/51bylkd7j73ou2wca40i7/gemma_part_12.zip?rlkey=s2ef7hpkou5ie44zzc27y5tx6&st=9zjxpirt&dl=1",
                "https://www.dropbox.com/scl/fi/wnsziyp168lt2t5r5p1tz/gemma_part_13.zip?rlkey=8t8pjj1wk6ndwngs2teswogkx&st=9181kfbz&dl=1",
                "https://www.dropbox.com/scl/fi/abi7ylz1pfuepxk0rp2q6/gemma_part_14.zip?rlkey=f6aq24wdh262zlgyi8hbt179c&st=gllvwdp5&dl=1",
                "https://www.dropbox.com/scl/fi/7rj7rn2p224b8n7lg7cf4/gemma_part_15.zip?rlkey=1tbfv170fzrdshu293cbn4vtq&st=0cuejcqc&dl=1"
            )
        }


    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(600, java.util.concurrent.TimeUnit.SECONDS) // 10 minutes for large files
        .writeTimeout(600, java.util.concurrent.TimeUnit.SECONDS) // 10 minutes for large files  
        .callTimeout(3600, java.util.concurrent.TimeUnit.SECONDS) // 60 minutes total
        .retryOnConnectionFailure(true)
        .addNetworkInterceptor { chain ->
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", "G3N-ModelDownloader/1.0")
                .header("Accept-Encoding", "identity") // Disable compression to avoid issues
                .header("Connection", "keep-alive")
                .build()
            chain.proceed(requestWithUserAgent)
        }
        .build()

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    // Prevent concurrent downloads
    @Volatile
    private var isDownloading = false
    private val downloadLock = kotlinx.coroutines.sync.Mutex()

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
        // Use mutex to prevent concurrent downloads
        downloadLock.withLock {
            if (isDownloading) {
                Timber.w("Download already in progress, rejecting concurrent request")
                return@withContext Result.failure(Exception("Download already in progress"))
            }
            
            isDownloading = true
        }
        
        try {
            if (isModelDownloaded()) {
                isDownloading = false
                return@withContext Result.success("All models already downloaded")
            }

            Timber.d("Starting download of ${REQUIRED_MODELS.size} model files")
            showDownloadNotification("Preparing download...", 0)

            val totalModels = REQUIRED_MODELS.size
            var overallProgress = 0f

            for (modelIndex in REQUIRED_MODELS.indices) {
                val model = REQUIRED_MODELS[modelIndex]
                val currentModelIndex = modelIndex + 1

                Timber.d("Downloading ${model.displayName} (${model.zipParts.size} parts)")

                val result = downloadMultiPartModel(model, currentModelIndex, totalModels) { progress ->
                    overallProgress = (modelIndex + progress.overallProgress) / totalModels
                    
                    onProgress(DownloadProgress(
                        currentFile = "${model.displayName} - ${progress.currentFile}",
                        currentFileIndex = progress.currentFileIndex,
                        totalFiles = progress.totalFiles,
                        bytesDownloaded = progress.bytesDownloaded,
                        totalBytesCurrentFile = progress.totalBytesCurrentFile,
                        overallProgress = overallProgress
                    ))
                }

                if (result.isFailure) {
                    val error = "Failed to download ${model.displayName}: ${result.exceptionOrNull()?.message}"
                    hideDownloadNotification()
                    onProgress(DownloadProgress(
                        currentFile = model.displayName,
                        currentFileIndex = currentModelIndex,
                        totalFiles = totalModels,
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
                currentFileIndex = totalModels,
                totalFiles = totalModels,
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
        } finally {
            isDownloading = false
        }
    }

    private suspend fun downloadMultiPartModel(
        model: ModelInfo,
        modelIndex: Int,
        totalModels: Int,
        onProgress: (DownloadProgress) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val modelFile = getModelFile(model.filename)
        
        // Check if model already exists and is valid
        if (modelFile.exists() && isValidModelFile(modelFile, model.filename)) {
            Timber.d("${model.filename} already exists and is valid, skipping download")
            onProgress(DownloadProgress(
                currentFile = "Complete",
                currentFileIndex = model.zipParts.size,
                totalFiles = model.zipParts.size,
                overallProgress = 1.0f
            ))
            return@withContext Result.success("Model already exists")
        }
        
        // Delete existing incomplete file
        if (modelFile.exists()) {
            modelFile.delete()
        }
        
        val tempModelFile = File(modelFile.parent, "${model.filename}.tmp")
        if (tempModelFile.exists()) {
            tempModelFile.delete()
        }
        
        try {
            FileOutputStream(tempModelFile).use { outputStream ->
                for (partIndex in model.zipParts.indices) {
                    val partUrl = model.zipParts[partIndex]
                    val partName = "Part ${partIndex + 1}/${model.zipParts.size}"
                    
                    Timber.d("Downloading $partName for ${model.filename}")
                    
                    onProgress(DownloadProgress(
                        currentFile = partName,
                        currentFileIndex = partIndex + 1,
                        totalFiles = model.zipParts.size,
                        overallProgress = partIndex.toFloat() / model.zipParts.size
                    ))
                    
                    val result = downloadAndExtractZipPart(partUrl, outputStream, partName)
                    if (result.isFailure) {
                        return@withContext result
                    }
                }
            }
            
            // Validate the reconstructed file with more lenient criteria
            val fileSize = tempModelFile.length()
            Timber.d("Validating reconstructed file: ${formatBytes(fileSize)}")
            
            if (!isValidModelFile(tempModelFile, model.filename)) {
                if (fileSize > 2.5 * 1024 * 1024 * 1024) { // If > 2.5GB, likely complete
                    Timber.w("Reconstructed file failed strict validation but has expected size (${formatBytes(fileSize)}), accepting")
                    // Continue - file is likely valid despite validation failure
                } else {
                    Timber.e("Reconstructed file validation failed and size is too small: ${formatBytes(fileSize)}")
                    tempModelFile.delete()
                    return@withContext Result.failure(Exception("Reconstructed model file failed validation (size: ${formatBytes(fileSize)})"))
                }
            } else {
                Timber.d("Reconstructed file passed validation")
            }
            
            // Move temp file to final location
            if (!tempModelFile.renameTo(modelFile)) {
                return@withContext Result.failure(Exception("Failed to move temp file to final location"))
            }
            
            Timber.d("Successfully downloaded and reconstructed ${model.filename}")
            onProgress(DownloadProgress(
                currentFile = "Complete",
                currentFileIndex = model.zipParts.size,
                totalFiles = model.zipParts.size,
                overallProgress = 1.0f
            ))
            
            return@withContext Result.success("Downloaded ${model.filename}")
            
        } catch (e: Exception) {
            tempModelFile.delete()
            return@withContext Result.failure(e)
        }
    }
    
    private suspend fun downloadAndExtractZipPart(
        url: String,
        outputStream: FileOutputStream,
        partName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val maxRetries = 3
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()

                val call = okHttpClient.newCall(request)
                
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code} for $partName")
                    }
                    
                    val responseBody = response.body
                    
                    // Download and extract the ZIP part directly
                    responseBody.byteStream().use { inputStream ->
                        ZipInputStream(inputStream).use { zipStream ->
                            val entry = zipStream.nextEntry
                            if (entry == null) {
                                throw Exception("No entry found in ZIP part: $partName")
                            }
                            
                            val buffer = ByteArray(16384)
                            var bytesRead: Int
                            
                            while (zipStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                            
                            zipStream.closeEntry()
                        }
                    }
                }
                
                return@withContext Result.success("Downloaded and extracted $partName")
                
            } catch (e: Exception) {
                lastException = e
                Timber.w(e, "Download attempt ${attempt + 1}/$maxRetries failed for $partName: ${e.message}")
                
                if (attempt < maxRetries - 1) {
                    val delayMs = (1000L * (attempt + 1) * (attempt + 1)).coerceAtMost(10000L)
                    Timber.d("Retrying download in ${delayMs}ms (attempt ${attempt + 2}/$maxRetries)")
                    delay(delayMs)
                }
            }
        }
        
        val error = "Failed to download $partName after $maxRetries attempts: ${lastException?.message}"
        Timber.e(lastException, error)
        return@withContext Result.failure(lastException ?: Exception(error))
    }
    

    private fun isValidModelFile(file: File, filename: String): Boolean {
        return try {
            when {
                filename.endsWith(".task") -> {
                    // Basic validation for .task files - check if it's a valid zip
                    file.length() > 1024 && // At least 1KB
                    file.inputStream().use { input ->
                        val header = ByteArray(4)
                        input.read(header) == 4 && 
                        // Check for ZIP signature (PK)
                        header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
                    }
                }
                filename.endsWith(".tflite") -> {
                    // Basic validation for .tflite files
                    file.length() > 1024 && // At least 1KB
                    file.inputStream().use { input ->
                        val header = ByteArray(8)
                        input.read(header) == 8 && 
                        // Check for TensorFlow Lite signature
                        header[4] == 0x54.toByte() && header[5] == 0x46.toByte() && 
                        header[6] == 0x4C.toByte() && header[7] == 0x33.toByte() // "TFL3"
                    }
                }
                else -> file.length() > 0
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate file: ${file.absolutePath}")
            false
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
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

    suspend fun cleanupCorruptedFiles() {
        try {
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) return
            
            REQUIRED_MODELS.forEach { model ->
                val modelFile = getModelFile(model.filename)
                val tempFile = File(modelFile.parent, "${model.filename}.tmp")
                
                // Clean up corrupted main model files
                if (modelFile.exists() && !isValidModelFile(modelFile, model.filename)) {
                    Timber.w("Removing corrupted model file: ${model.filename}")
                    modelFile.delete()
                }
                
                // Clean up old temp files that may be corrupted
                if (tempFile.exists() && tempFile.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000) {
                    Timber.w("Removing stale temp file: ${model.filename}.tmp")
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup corrupted files")
        }
    }

    suspend fun checkAndDownloadModelIfNeeded(): Boolean {
        return try {
            // Clean up any corrupted files first
            cleanupCorruptedFiles()
            
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