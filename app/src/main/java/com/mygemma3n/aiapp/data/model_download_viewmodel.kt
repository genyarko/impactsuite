package com.mygemma3n.aiapp.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context  // Add @ApplicationContext qualifier
) : ViewModel() {

    private val _downloadState = MutableStateFlow(ModelDownloadState())
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val assetPackManager: AssetPackManager = AssetPackManagerFactory.getInstance(context)
    private var stateUpdateListener: AssetPackStateUpdateListener? = null

    init {
        checkModelStatus()
    }

    fun checkModelStatus() {
        viewModelScope.launch {
            try {
                val packStates = assetPackManager.getPackStates(listOf(PACK_NAME)).await()
                val packState = packStates.packStates()[PACK_NAME]

                when (packState?.status()) {
                    AssetPackStatus.COMPLETED -> {
                        _downloadState.value = ModelDownloadState(
                            isDownloaded = true,
                            progress = 100f
                        )
                    }
                    AssetPackStatus.DOWNLOADING -> {
                        startMonitoringDownload()
                    }
                    else -> {
                        _downloadState.value = ModelDownloadState(
                            isDownloaded = false,
                            progress = 0f
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking model status")
                _downloadState.value = ModelDownloadState(
                    isDownloaded = false,
                    progress = 0f,
                    error = e.message
                )
            }
        }
    }

    fun downloadModel() {
        lateinit var listener: AssetPackStateUpdateListener
        listener = AssetPackStateUpdateListener { state ->
            if (state.name() == PACK_NAME) {
                when (state.status()) {
                    AssetPackStatus.DOWNLOADING -> {
                        val progress = if (state.totalBytesToDownload() > 0) {
                            (state.bytesDownloaded() * 100f / state.totalBytesToDownload())
                        } else 0f
                        _downloadState.value = _downloadState.value.copy(
                            isDownloading = true,
                            progress = progress
                        )
                    }
                    AssetPackStatus.COMPLETED -> {
                        _downloadState.value = ModelDownloadState(
                            isDownloaded = true,
                            progress = 100f
                        )
                        assetPackManager.unregisterListener(listener)
                        stateUpdateListener = null
                    }
                    AssetPackStatus.FAILED -> {
                        _downloadState.value = _downloadState.value.copy(
                            isDownloading = false,
                            error = "Download failed: ${state.errorCode()}"
                        )
                        assetPackManager.unregisterListener(listener)
                        stateUpdateListener = null
                    }
                }
            }
        }

        stateUpdateListener = listener
        assetPackManager.registerListener(listener)
        assetPackManager.fetch(listOf(PACK_NAME))
    }

    private fun startMonitoringDownload() {
        lateinit var listener: AssetPackStateUpdateListener
        listener = AssetPackStateUpdateListener { state ->
            if (state.name() == PACK_NAME) {
                when (state.status()) {
                    AssetPackStatus.DOWNLOADING -> {
                        val progress = if (state.totalBytesToDownload() > 0) {
                            (state.bytesDownloaded() * 100f / state.totalBytesToDownload())
                        } else 0f
                        _downloadState.value = _downloadState.value.copy(
                            isDownloading = true,
                            progress = progress
                        )
                    }
                    AssetPackStatus.COMPLETED -> {
                        _downloadState.value = ModelDownloadState(
                            isDownloaded = true,
                            progress = 100f
                        )
                        assetPackManager.unregisterListener(listener)
                        stateUpdateListener = null
                    }
                    AssetPackStatus.FAILED -> {
                        _downloadState.value = _downloadState.value.copy(
                            isDownloading = false,
                            error = "Download failed"
                        )
                        assetPackManager.unregisterListener(listener)
                        stateUpdateListener = null
                    }
                }
            }
        }

        stateUpdateListener = listener
        assetPackManager.registerListener(listener)
    }

    fun getModelPath(context: Context): String {
        // Copy asset to cache if not already present
        val tmp = java.io.File(context.cacheDir, "models/gemma-3n-E2B-it-int4.task")
        if (!tmp.exists()) {
            context.assets.open("models/gemma-3n-E2B-it-int4.task").use { input ->
                tmp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return tmp.absolutePath
    }


    override fun onCleared() {
        super.onCleared()
        stateUpdateListener?.let {
            assetPackManager.unregisterListener(it)
        }
    }

    companion object {
        private const val PACK_NAME = "gemma3n_assetpack"
    }
}

data class ModelDownloadState(
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null
)