package com.example.mygemma3n.data

import android.content.Context
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import androidx.lifecycle.ViewModel

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val modelDownloadManager: ModelDownloadManager
) : ViewModel() {

    fun downloadGemmaModel() {
        val downloadRequest = ModelDownloadManager.DownloadRequest(
            url = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-2b-it-fast.tflite",
            name = "gemma-2b-it-fast",
            type = "gemma-3n-2b",
            checksum = null // Replace with actual checksum if available
        )

        viewModelScope.launch {
            val result = ensureModelDownloaded(downloadRequest)
            when (result) {
                is ModelDownloadManager.DownloadState.Success -> {
                    Timber.d("Model ensured: Path=${result.modelPath}, Size=${result.modelSize}")
                }
                is ModelDownloadManager.DownloadState.Error -> {
                    Timber.e("Model download failed: ${result.message}")
                }
                else -> {
                    Timber.d("Download state: $result")
                }
            }
        }
    }

    private suspend fun ensureModelDownloaded(request: ModelDownloadManager.DownloadRequest): ModelDownloadManager.DownloadState {
        if (modelDownloadManager.isModelAvailable(request.name)) {
            Timber.d("Model ${request.name} already available.")
            return ModelDownloadManager.DownloadState.Success(
                modelPath = modelDownloadManager.getAvailableModels().first { it.name == request.name }.path,
                modelSize = modelDownloadManager.getAvailableModels().first { it.name == request.name }.size
            )
        }

        var resultState: ModelDownloadManager.DownloadState = ModelDownloadManager.DownloadState.Idle

        modelDownloadManager.downloadModel(request).collect { state ->
            resultState = state
        }

        return resultState
    }
}