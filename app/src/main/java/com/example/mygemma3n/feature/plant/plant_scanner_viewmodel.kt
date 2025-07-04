package com.example.mygemma3n.feature.plant

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.graphics.Bitmap
import android.hardware.camera2.CameraManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.feature.toList
import com.example.mygemma3n.gemma.GemmaEngine
import com.example.mygemma3n.shared_utilities.generateText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlantScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaEngine: GemmaEngine,
    private val plantDatabase: PlantDatabase,
    private val cameraManager: CameraManager
) : ViewModel() {

    private val _modelReady = MutableStateFlow(false)
    private val _scanState = MutableStateFlow(PlantScanState())
    val scanState: StateFlow<PlantScanState> = _scanState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                // Copy Gemma 3n model from assets if it hasn't been extracted yet
                val modelFile = File(context.cacheDir, "models/gemma-3n-E2B-it-int4.task")
                if (!modelFile.exists()) {
                    modelFile.parentFile?.mkdirs()
                    context.assets.open("models/gemma-3n-E2B-it-int4.task").use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                gemmaEngine.initialize(
                    GemmaEngine.InferenceConfig(
                        modelPath = modelFile.absolutePath,
                        useGpu = true,
                        useNnapi = true,
                        numThreads = 4,
                        enablePLE = true
                    )
                )
                _modelReady.value = true
            } catch (e: Exception) {
                _scanState.update { it.copy(error = "Model init failed: ${e.localizedMessage}") }
            }
        }
    }

    fun analyzeImage(bitmap: Bitmap) {
        viewModelScope.launch {
            if (!_modelReady.value) {
                _scanState.update {
                    it.copy(isAnalyzing = false, error = "Model is still loading, please wait…")
                }
                return@launch
            }

            _scanState.update { it.copy(isAnalyzing = true, error = null) }

            val imageInput = preprocessImage(bitmap)
            val prompt = """
                Analyze this plant image for:
                1. Plant species identification
                2. Any visible diseases or pest damage
                3. Health assessment (healthy/mild/moderate/severe)
                4. Recommended actions
                
                Provide response in JSON format:
                {
                    "species": "...",
                    "confidence": 0.95,
                    "disease": "...",
                    "severity": "...",
                    "recommendations": ["...", "..."]
                }
            """.trimIndent()

            val result = gemmaEngine.generateText(
                prompt = prompt,
                imageInput = imageInput,
                maxTokens = 300,
                temperature = 0.3f
            ).toList().joinToString("")

            val analysis = parseJsonResponse<PlantAnalysis>(result)
            val enriched = plantDatabase.getAdditionalInfo(analysis.species)

            _scanState.update {
                it.copy(
                    isAnalyzing = false,
                    currentAnalysis = analysis.copy(additionalInfo = enriched),
                    scanHistory = it.scanHistory + analysis
                )
            }
        }
    }
}
