package com.example.mygemma3n.feature.plant

import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.UnifiedGemmaService.GenerationConfig
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * General image‑scanner ViewModel powered by on‑device Gemma 3n.
 * – Classifies *any* image (objects, scenes, etc.)
 * – If the label is a plant, also returns species & disease info.
 */
@HiltViewModel
class PlantScannerViewModel @Inject constructor(
    private val gemma: UnifiedGemmaService,
    private val plantDatabase: PlantDatabase
) : ViewModel() {

    /* ---------- UI state ---------- */
    private val _scanState = MutableStateFlow(ImageScanState())
    val scanState: StateFlow<ImageScanState> = _scanState.asStateFlow()

    /* ---------- Public API ---------- */
    fun analyzeImage(bitmap: Bitmap) = viewModelScope.launch {
        // 0 · UI → “busy”
        _scanState.update { it.copy(isAnalyzing = true, error = null) }

        try {
            /* 1 · Pre‑process --------------------------------------------------- */
            val square = bitmap.resizeToGemma(512)                         // Gemma‑3n vision sizes: 256/512/768 :contentReference[oaicite:0]{index=0}
            val mpImage: MPImage = BitmapImageBuilder(square).build()      // Convert → MPImage :contentReference[oaicite:1]{index=1}

            /* 2 · Lazy‑load local model --------------------------------------- */
            gemma.initialize()                                             // calls the vision graph setup internally

            /* 3 · Compose robust prompt -------------------------------------- */
            val prompt = buildPromptWithImage(
                """
            You are an on‑device vision assistant.
            1. Give the single best **general label** for the image’s main subject.
            2. *If* that subject is a plant:
               • Identify species
               • Detect disease (or "None")
               • Rate severity
               • List care recommendations
            Respond **only** in JSON:
            {
              "label":        string,
              "confidence":   float 0‑1,
              "plantSpecies": string | "N/A",
              "disease":      string | "None" | "N/A",
              "severity":     string,
              "recommendations": string[]
            }
            ---
            JSON Response:
            """.trimIndent()
            )

            /* 4 · Generate multimodal answer ---------------------------------- */
            val raw = gemma.generateResponse(prompt, mpImage)              // `generateResponse()` is the session‑level API :contentReference[oaicite:2]{index=2}

            /* 5 · Parse + enrich --------------------------------------------- */
            val analysis  = parseGeneralAnalysis(raw)
            val enriched  = analysis.plantSpecies?.let { plantDatabase.getAdditionalInfo(it) }

            _scanState.update {
                it.copy(
                    isAnalyzing     = false,
                    currentAnalysis = analysis.copy(additionalInfo = enriched),
                    scanHistory     = it.scanHistory + analysis
                )
            }

        } catch (e: Exception) {
            Timber.e(e)
            _scanState.update { it.copy(isAnalyzing = false, error = e.localizedMessage) }
        }
    }

    /** Insert the image token before the text – Gemma 3n best practice. */
    private fun buildPromptWithImage(text: String): String =
        "```img```<|image|>$text"                                   // image‑then‑text :contentReference[oaicite:8]{index=8}

    /** Resize to a model‑friendly square side. */
    private fun Bitmap.resizeToGemma(target: Int): Bitmap =
        if (width == target && height == target) this else scale(target, target)

    /* ---------- Lightweight JSON parser ---------- */
    private fun parseGeneralAnalysis(raw: String): GeneralAnalysis {
        val obj = JSONObject(raw.trim().removeSurrounding("```json", "```"))
        return GeneralAnalysis(
            id             = UUID.randomUUID().toString(),
            timestamp      = System.currentTimeMillis(),
            label          = obj.optString("label"),
            confidence     = obj.optDouble("confidence", 0.0).toFloat(),
            plantSpecies   = obj.optString("plantSpecies").takeIf { it != "N/A" },
            disease        = obj.optString("disease").takeIf { it != "None" && it != "N/A" },
            severity       = obj.optString("severity"),
            recommendations= obj.optJSONArray("recommendations")?.let { arr ->
                List(arr.length()) { i -> arr.getString(i) }
            } ?: emptyList(),
            additionalInfo = null
        )
    }
}

/* ---------- UI‑state & data classes ---------- */

data class ImageScanState(
    val isAnalyzing: Boolean            = false,
    val currentAnalysis: GeneralAnalysis? = null,
    val scanHistory: List<GeneralAnalysis> = emptyList(),
    val error: String?                  = null
)

data class GeneralAnalysis(
    val id: String,
    val timestamp: Long,
    val label: String,
    val confidence: Float,
    val plantSpecies: String?          = null,
    val disease: String?               = null,
    val severity: String?              = null,
    val recommendations: List<String>  = emptyList(),
    val additionalInfo: PlantInfo?     = null
)
