package com.example.mygemma3n.feature.plant

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.GeminiApiService

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Plant Scanner feature using the Gemini API.
 */
@HiltViewModel
class PlantScannerViewModel @Inject constructor(
    private val geminiApiService: GeminiApiService,
    private val plantDatabase: PlantDatabase // This now correctly references the PlantDatabase from plant.kt
) : ViewModel() {

    private val _scanState = MutableStateFlow(PlantScanState())
    val scanState: StateFlow<PlantScanState> = _scanState.asStateFlow()

    /**
     * Analyze the bitmap using the online Gemini API and update state.
     */
    // In PlantScannerViewModel.kt, update the analyzeImage function

    fun analyzeImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _scanState.update { it.copy(isAnalyzing = true, error = null) }
            try {
                // Resize the bitmap for the multimodal model
                val resizedBitmap = resizeBitmapForGemma3n(bitmap, 512)

                // The prompt for the Gemini model.
                val prompt = """
                    Analyze this plant image and return a JSON object with the following fields:
                    - "species": The identified plant species.
                    - "confidence": A confidence score (0.0 to 1.0) for the species identification.
                    - "disease": Any identified disease, or "None" if healthy.
                    - "severity": The severity of the disease (e.g., "Low", "Moderate", "High"), or "N/A" if no disease.
                    - "recommendations": Brief recommendations for care or treatment, as a list of strings, or an empty list if healthy.

                    Ensure the response is a strictly valid JSON object.
                    Example:
                    {
                      "species": "Rose",
                      "confidence": 0.98,
                      "disease": "Black Spot",
                      "severity": "Moderate",
                      "recommendations": ["Remove affected leaves.", "Apply a fungicide." ]
                    }
                    ---
                    JSON Response:
                """.trimIndent()

                // THE FIX IS HERE: Call the new function with the specific model name
                val jsonResponse = geminiApiService.generateContentWithImageAndModel(
                    modelName = "gemini-1.5-flash", // Explicitly use the vision model
                    prompt = prompt,
                    image = resizedBitmap
                )

                // Log the raw JSON response for debugging
                println("Raw JSON Response from API: $jsonResponse")

                // Parse and enrich the analysis
                val analysis = parseAnalysisFromJson(jsonResponse, null)

                // Fetch additional info from the local database
                val enrichedInfo = plantDatabase.getAdditionalInfo(analysis.species)

                _scanState.update {
                    it.copy(
                        isAnalyzing = false,
                        currentAnalysis = analysis.copy(additionalInfo = enrichedInfo),
                        scanHistory = it.scanHistory + analysis
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _scanState.update {
                    it.copy(
                        isAnalyzing = false,
                        error = e.localizedMessage ?: "An unknown error occurred during analysis."
                    )
                }
            }
        }
    }

    /**
     * Resizes the input Bitmap to a specified target square dimension.
     *
     * Gemma 3n models are documented to natively support resolutions
     * of 256x256, 512x512, or 768x768 pixels for multimodal input.
     *
     * @param originalBitmap The bitmap to resize.
     * @param targetSize The desired square dimension (e.g., 256, 512, 768).
     * @return The resized Bitmap.
     */
    private fun resizeBitmapForGemma3n(originalBitmap: Bitmap, targetSize: Int): Bitmap {
        if (originalBitmap.width == targetSize && originalBitmap.height == targetSize) {
            return originalBitmap // No resize needed if already at target size
        }
        // Bitmap.createScaledBitmap scales the bitmap to the new width and height.
        // The `filter` parameter (true) enables bilinear filtering for smoother scaling.
        return Bitmap.createScaledBitmap(originalBitmap, targetSize, targetSize, true)
    }
}
