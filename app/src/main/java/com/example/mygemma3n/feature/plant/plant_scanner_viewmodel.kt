package com.example.mygemma3n.feature.plant

import android.graphics.Bitmap
import android.hardware.camera2.CameraManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.gemma.GemmaEngine
import com.example.mygemma3n.shared_utilities.generateText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// PlantScannerViewModel.kt

@HiltViewModel
class PlantScannerViewModel @Inject constructor(
    private val gemmaEngine: GemmaEngine,
    private val plantDatabase: PlantDatabase,
    private val cameraManager: CameraManager
) : ViewModel() {

    private val _scanState = MutableStateFlow(PlantScanState())
    val scanState: StateFlow<PlantScanState> = _scanState.asStateFlow()

    fun analyzeImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _scanState.update { it.copy(isAnalyzing = true) }

            // Prepare multimodal input with image
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

            // Enrich with local database
            val enrichedData = plantDatabase.getAdditionalInfo(analysis.species)

            _scanState.update {
                it.copy(
                    isAnalyzing = false,
                    currentAnalysis = analysis.copy(
                        additionalInfo = enrichedData
                    ),
                    scanHistory = it.scanHistory + analysis
                )
            }
        }
    }
}